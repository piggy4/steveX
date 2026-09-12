package name.modid.vision;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * 地形方块 NBT 持久化存储 —— 每次采集整体覆盖写当前维的桶。
 *
 * <p>与 {@link VisionBlockEntityStore}（增量合并）不同，地形是"当前世界状态"的快照，
 * 每次 API 触发时用本次扫描的结果<b>整体替换</b>该维上次的内容。
 *
 * <p>v2.32（世界类型区分，见 docs/世界类型区分与镜像复原设计方案.md）：文件按维度分桶，顶层
 * {@code { "currentDimension", "worlds": { <dim>: <本维正文> } }}；正文形态（agent 姿态 + blocks
 * + deletions）与旧版逐字一致、每维一份。换维采集只覆写自己那维的桶，上一维的"最后可见"不再被清掉。
 * 旧版单维文件（无 {@code worlds} 键）由 {@link WorldsFile} 自动视为 overworld 桶。
 *
 * <p>文件格式（NBT）：
 * <pre>{@code
 * {
 *   "currentDimension": "minecraft:overworld",
 *   "worlds": {
 *     "minecraft:overworld": {
 *       "agentPos": "100.5,65.62,96.0",
 *       "agentYaw": -45.0,
 *       "agentPitch": 10.0,
 *       "agentFov": 70,
 *       "dayTime": 6000,
 *       "timestamp": 1720000000,
 *       "blocks": { "128,64,-32": { "block": "minecraft:stone", "state": {} }, ... },
 *       "deletions": [ 819874916943... , ... ],
 *       "signalLossDeletions": [ 819874916944... , ... ]
 *     },
 *     "minecraft:the_nether": { ... }
 *   }
 * }
 * }</pre>
 *
 * <p>v2（GPU 深度缓冲驱动）：不再写 {@code scannedSections}——记忆世界侧读到空集合 →
 * 移除权威永不触发 → 自动变成<b>纯累积语义</b>（只增不删，见设计 §6.1/§7.1）。
 *
 * <p>v2.23（§7.11）：每个维桶内顶层新增 {@code deletions}（BlockPos long 列表）——采集侧
 * {@link DeletionJudge} 用记忆侧反向通道 cells 文件逐块深度判定出的「被证明已消失」的记忆格。
 * 记忆世界侧 {@code DeletionApplier} 据此减量删除。v2.22 的 {@code surface}/@{@code skyRays} 字段已移除。
 *
 * <p>v2.37 七次修订（§15）：每个维桶内顶层再新增<b>并列键</b> {@code signalLossDeletions}（同型
 * BlockPos long 列表）——信号缺失族（绊线 / 绊线钩）的"被证明已消失"，由
 * {@link SignalLossCorrector} <b>状态直读</b>（而非深度推断）产出。与 {@code deletions} 分立两键
 * 是刻意的：记忆侧两条通道各有独立守卫，合并会让双保险失效（理由详见
 * {@link #KEY_SIGNAL_LOSS_DELETIONS}）。采集侧**不持有**该族名单，族由记忆侧上报什么定义。
 */
public class VisionTerrainStore {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR_NAME = "stevex/vision";
    private static final String FILE_NAME = "terrain.nbt";
    private static final String KEY_AGENT_POS = "agentPos";
    private static final String KEY_AGENT_YAW = "agentYaw";
    private static final String KEY_AGENT_PITCH = "agentPitch";
    private static final String KEY_AGENT_FOV = "agentFov";
    private static final String KEY_TIMESTAMP = "timestamp";
    /** v2.21：采集时刻世界时间（dayTime），记忆世界据此对齐昼夜（§7.10）。 */
    private static final String KEY_WORLD_TIME = "dayTime";
    private static final String KEY_BLOCKS = "blocks";
    private static final String KEY_BLOCK = "block";
    private static final String KEY_STATE = "state";
    /** v2.23（§7.11）：被证明消失的记忆格（BlockPos long 数组，采集侧 DeletionJudge 产出）。 */
    private static final String KEY_DELETIONS = "deletions";
    /**
     * v2.37 七次修订（§15.3）：<b>信号缺失族</b>（绊线 / 绊线钩）被证明消失的记忆格（同型 long 数组，
     * 采集侧 {@code SignalLossCorrector} 产出）。
     *
     * <p><b>为什么是并列键而不是并进 {@code deletions}</b>：两条通道的证据类型不同（深度推断 vs
     * 状态直读），记忆侧执行时各用各的守卫——{@code deletions} 走
     * {@code isDeletableContent ∪ isShapedDeletableContent}，本键走 {@code isSignalLossBlock}。
     * 若并为一键，记忆侧就只能挂一道守卫：要么把 {@code isSignalLossBlock} 加进原守卫（等于对
     * <b>所有</b>删除来源放宽，双保险失效），要么绊线过不了原守卫（通道静默失效）。分立两键使两道
     * 守卫的定义域互不相交（绊线既非 {@code isDeletableContent} 也非 {@code isShapedDeletableContent}），
     * 两条保险各自完整（§15.2）。
     */
    private static final String KEY_SIGNAL_LOSS_DELETIONS = "signalLossDeletions";

    private final Path filePath;

    /** v2.32：分桶镜像（维 id → 该维最后一次快照的桶正文），构造时从既有文件读入、每次 sync 整体写回。 */
    private final Map<String, CompoundTag> worlds = new LinkedHashMap<>();
    /** v2.32：最近一次写入所属维（文件顶层 currentDimension；镜像权威源，每快照必写）。 */
    private String currentDimension = WorldsFile.LEGACY_DIMENSION;

    public VisionTerrainStore() {
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve(DIR_NAME);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("[Vision] Failed to create directory {}: {}", dir, e.getMessage());
        }
        this.filePath = dir.resolve(FILE_NAME);
        loadExisting();
    }

    /**
     * 用本次采集结果整体覆写<b>当前维</b>的地形桶（v2：内容 = 本次<b>可见</b>方块，含半透明方块；
     * v2.32：其余维桶原样保留）。
     *
     * @param blocks 本次可见方块（key = 方块坐标）
     * @param deletions v2.23：被证明消失的记忆格（采集侧 DeletionJudge 产出，可空）
     * @param signalLossDeletions v2.37（§15）：被证明消失的<b>信号缺失族</b>格（采集侧
     *        {@code SignalLossCorrector} 状态直读产出，可空）。<b>恒不与 {@code deletions} 合并</b>，
     *        理由见 {@link #KEY_SIGNAL_LOSS_DELETIONS}
     * @param agentPos 采集时观察者的相机（眼睛）双精度坐标（游戏精度），可为 null
     * @param agentYaw 采集时观察者水平朝向（度）
     * @param agentPitch 采集时观察者俯仰朝向（度）
     * @param agentFov 采集时观察者基础视场角（整数度，游戏精度）
     * @param worldTime 采集时世界时间（dayTime，游戏时间单位；无世界时 -1，v2.21）
     * @param dimensionId v2.32：采集时所在维 id（{@code level.dimension().identifier()}），决定写哪个桶
     * @return 统计信息 { "blocks": n, "deletions": n, "signalLossDeletions": n }
     */
    public Map<String, Object> sync(
            final Map<BlockPos, VisionCollector.TerrainBlockSnapshot> blocks,
            final List<BlockPos> deletions,
            final List<BlockPos> signalLossDeletions,
            final Vec3 agentPos,
            final float agentYaw,
            final float agentPitch,
            final int agentFov,
            final long worldTime,
            final String dimensionId
    ) {
        currentDimension = dimensionId;
        CompoundTag bucket = new CompoundTag();
        bucket.putString(KEY_AGENT_POS, agentPosKey(agentPos));
        bucket.putFloat(KEY_AGENT_YAW, agentYaw);
        bucket.putFloat(KEY_AGENT_PITCH, agentPitch);
        bucket.putInt(KEY_AGENT_FOV, agentFov);
        bucket.putLong(KEY_WORLD_TIME, worldTime);
        bucket.putLong(KEY_TIMESTAMP, System.currentTimeMillis());

        CompoundTag blocksTag = new CompoundTag();
        for (var e : blocks.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString(KEY_BLOCK, e.getValue().blockId());
            entry.put(KEY_STATE, propsToNbt(e.getValue().stateProps()));
            blocksTag.put(posKey(e.getKey()), entry);
        }
        bucket.put(KEY_BLOCKS, blocksTag);

        // v2.23：deletions 恒写（空 = 本帧无删除证据）。旧文件无该键 → 记忆世界侧读为空列表，兼容。
        final long[] arr = new long[deletions.size()];
        for (int i = 0; i < deletions.size(); i++) {
            arr[i] = deletions.get(i).asLong();
        }
        bucket.put(KEY_DELETIONS, new LongArrayTag(arr));

        // v2.37（§15.3）：信号缺失族裁决恒写并列键（空 = 本帧无该族删除证据）。
        // 记忆侧读不到该键 → 空列表（旧文件 / 旧版采集侧）→ 该通道静默不删，只增不删，方向安全。
        final long[] slArr = new long[signalLossDeletions.size()];
        for (int i = 0; i < signalLossDeletions.size(); i++) {
            slArr[i] = signalLossDeletions.get(i).asLong();
        }
        bucket.put(KEY_SIGNAL_LOSS_DELETIONS, new LongArrayTag(slArr));

        worlds.put(dimensionId, bucket);
        writeFile();

        return Map.of("blocks", blocks.size(), "deletions", deletions.size(),
                "signalLossDeletions", signalLossDeletions.size());
    }

    // ==================== 内部 ====================

    /** 整体覆盖写（当前维桶已更新，其余维桶由内存镜像带出）。 */
    private void writeFile() {
        try {
            NbtIo.writeCompressed(WorldsFile.wrap(currentDimension, worlds), filePath);
            LOGGER.debug("[Vision] Saved terrain: {} dimension bucket(s), current={} → {}",
                    worlds.size(), currentDimension, filePath);
        } catch (IOException ex) {
            LOGGER.error("[Vision] Failed to save terrain store: {}", ex.getMessage());
        }
    }

    /** 构造时读入既有文件 → 分桶内存镜像（跨会话保留各维最后快照；旧版单维文件自动回退 overworld）。 */
    private void loadExisting() {
        if (!Files.exists(filePath)) return;
        try {
            CompoundTag root = NbtIo.readCompressed(filePath, NbtAccounter.unlimitedHeap());
            if (root == null) return;
            WorldsFile.Result r = WorldsFile.read(root);
            currentDimension = r.currentDimension();
            worlds.putAll(r.worlds());
            LOGGER.info("[Vision] Loaded terrain store: {} dimension bucket(s) from {} (current={})",
                    worlds.size(), filePath, currentDimension);
        } catch (Exception e) {
            LOGGER.warn("[Vision] Failed to load terrain store {}: {}", filePath, e.getMessage());
        }
    }

    private static String posKey(final BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** 观察者眼睛坐标 → 存储字符串（双精度，游戏精度）；未知（null）时存空串。 */
    private static String agentPosKey(final Vec3 v) {
        return v == null ? "" : Double.toString(v.x) + "," + Double.toString(v.y) + "," + Double.toString(v.z);
    }

    /** 方块状态属性表 → NBT。 */
    private static CompoundTag propsToNbt(final Map<String, String> props) {
        CompoundTag tag = new CompoundTag();
        props.forEach(tag::putString);
        return tag;
    }
}
