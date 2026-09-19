package name.modid.vision;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * 方块实体 NBT 持久化存储 —— 基于文件的增量保存（跨时间累积 union，按维分桶）。
 *
 * <p>行为：
 * <ul>
 *   <li>新方块实体 → 写入存储</li>
 *   <li>NBT 未变化  → 跳过</li>
 *   <li>NBT 已变化  → 更新存储</li>
 * </ul>
 *
 * <p>v2.32（世界类型区分，见 docs/世界类型区分与镜像复原设计方案.md）：内容按<b>维度</b>分桶——
 * 内存 {@code byDim: Map<dim, Map<posKey, StoredEntry>>}，agent 姿态字段同样每维一份
 * （{@code poseByDim}）。换维采集只更新自己那维的子图，其余维条目原样保留，坐标天然不再跨维碰撞。
 * 文件顶层 {@code { "currentDimension", "worlds": { <dim>: { agentPos..., blockEntities } } }}。
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
 *       "blockEntities": {
 *         "128,64,-32": { "typeId": "minecraft:chest", "block": "minecraft:chest",
 *                         "state": {"facing":"east","waterlogged":"false"},
 *                         "nbt": {...}, "timestamp": 1720000000 }, ...
 *       }
 *     },
 *     "minecraft:the_nether": { ... }
 *   }
 * }
 * }</pre>
 */
public class VisionBlockEntityStore {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR_NAME = "stevex/vision";
    private static final String FILE_NAME = "block_entities.nbt";
    private static final String KEY_BLOCK_ENTITIES = "blockEntities";
    private static final String KEY_TYPE_ID = "typeId";
    private static final String KEY_BLOCK = "block";
    private static final String KEY_STATE = "state";
    private static final String KEY_NBT = "nbt";
    private static final String KEY_AGENT_POS = "agentPos";
    private static final String KEY_AGENT_YAW = "agentYaw";
    private static final String KEY_AGENT_PITCH = "agentPitch";
    private static final String KEY_AGENT_FOV = "agentFov";
    /** v2.21：采集时刻世界时间（dayTime），记忆世界据此对齐昼夜（§7.10）。 */
    private static final String KEY_WORLD_TIME = "dayTime";
    private static final String KEY_TIMESTAMP = "timestamp";

    /** v2.32：维度 → 该维已存储方块实体（key = "x,y,z"）。外层保留插入序（先访问的维在前）。 */
    private final Map<String, Map<String, StoredEntry>> byDim = new LinkedHashMap<>();

    /** v2.32：维度 → 该维最后一次采集时的 agent 姿态（agentPos/…/dayTime），随该维桶持久化。 */
    private final Map<String, Pose> poseByDim = new LinkedHashMap<>();

    /** v2.32：最近一次写入所属维（文件顶层 currentDimension）。 */
    private String currentDimension = WorldsFile.LEGACY_DIMENSION;

    private final Path filePath;
    private boolean dirty;

    public VisionBlockEntityStore() {
        this.filePath = resolveFilePath();
        load();
    }

    // ==================== 公开接口 ====================

    /**
     * 将一批快照与已有存储对比，仅写入新增或变化的条目（限当前维子图）；同时把本次采集时
     * agent 所在坐标记录为<b>当前维</b>桶的最新 {@code agentPos}。
     *
     * @param snapshots 当前帧收集到的方块实体快照
     * @param agentPos 采集时观察者的相机（眼睛）双精度坐标（游戏精度），可为 null
     * @param agentYaw 采集时观察者水平朝向（度）
     * @param agentPitch 采集时观察者俯仰朝向（度）
     * @param agentFov 采集时观察者基础视场角（整数度，游戏精度）
     * @param worldTime 采集时世界时间（dayTime，游戏时间单位；无世界时 -1，v2.21）
     * @param dimensionId v2.32：采集时所在维 id，决定更新哪个维的子图 / 姿态
     * @return 统计信息 { "new": n, "updated": n, "skipped": n }
     */
    public Map<String, Integer> sync(final Map<BlockPos, VisionCollector.BlockEntitySnapshot> snapshots,
                                     final Vec3 agentPos,
                                     final float agentYaw,
                                     final float agentPitch,
                                     final int agentFov,
                                     final long worldTime,
                                     final String dimensionId) {
        int added = 0, updated = 0, skipped = 0;

        // v2.32：换维强制标记 dirty——确保新维首次出现（即使内容/姿态恰好一致）也会落盘建立桶。
        if (!dimensionId.equals(currentDimension)) {
            currentDimension = dimensionId;
            dirty = true;
        }
        // 当前维顶层姿态：只在发生变化时标记 dirty，从而刷新文件
        Pose pose = poseByDim.get(dimensionId);
        String newAgentPos = agentPosKey(agentPos);
        if (pose == null || !pose.agentPos.equals(newAgentPos)
                || Math.abs(agentYaw - pose.agentYaw) > 0.001f
                || Math.abs(agentPitch - pose.agentPitch) > 0.001f
                || agentFov != pose.agentFov
                || worldTime != pose.dayTime) {
            poseByDim.put(dimensionId, new Pose(newAgentPos, agentYaw, agentPitch, agentFov, worldTime));
            dirty = true;
        }

        Map<String, StoredEntry> entries = byDim.computeIfAbsent(dimensionId, k -> new LinkedHashMap<>());
        for (var entry : snapshots.entrySet()) {
            BlockPos pos = entry.getKey();
            VisionCollector.BlockEntitySnapshot snapshot = entry.getValue();
            String key = posToKey(pos);

            StoredEntry existing = entries.get(key);
            if (existing != null) {
                // 已存在 → 比较类型 / 方块 / 状态 / NBT
                if (entryEquals(existing, snapshot)) {
                    skipped++;
                    continue;
                }
                // 数据变了 → 更新
                existing.typeId = snapshot.typeId();
                existing.blockId = snapshot.blockId();
                existing.stateProps = Map.copyOf(snapshot.stateProps());
                existing.nbt = snapshot.nbt() != null ? snapshot.nbt().copy() : new CompoundTag();
                existing.timestamp = snapshot.timestamp();
                updated++;
                dirty = true;
            } else {
                // 新的 → 写入
                CompoundTag nbtCopy = snapshot.nbt() != null ? snapshot.nbt().copy() : new CompoundTag();
                entries.put(key, new StoredEntry(
                        nbtCopy,
                        snapshot.typeId(),
                        snapshot.blockId(),
                        Map.copyOf(snapshot.stateProps()),
                        snapshot.timestamp()
                ));
                added++;
                dirty = true;
            }
        }

        if (dirty) {
            save();
            dirty = false;
        }

        return Map.of("new", added, "updated", updated, "skipped", skipped);
    }

    /**
     * v2.38（设计 §4.1 <b>F1</b> / §7.1 <b>决策 H·I</b>）<b>唯一删除原语</b>在本 store 的落点 —— 持久修剪
     * 被"判据证明已消失"的格的方块实体记录。
     *
     * <p><b>为什么必须做</b>（§1.1 的对称性断裂）：本 store 是<b>增量 union、永不自删</b>的累积文件；方块被
     * 减量删掉后记录仍在 ⇒ 记忆侧回放把方块复活（告示牌/箱子/床的根因）。判据只管"方块本体没了"，本方法
     * 让"判据命中"这一事实落到记录上，从而把复活根因消除在持久层（而非靠记忆侧每帧挡）。
     *
     * <p><b>入参取并集</b>（决策 H）：调用方传入的是 {@code deletions ∪ signalLossDeletions}——两条通道
     * 的候选在同一 resolve 内都已就位。只覆盖 {@code deletions} 会让"信号缺失表加进来的方块"删了方块却
     * 留下负载（§14.4 的加表路线就此失效）。
     *
     * <p><b>块身份复核</b>（决策 I）：{@code expected} 的 value 是该格本代际由记忆侧上报的 blockId
     * （信号缺失段携带；main/translucent/shaped 段不带 ⇒ 空串）。非空时要求与记录<b>逐字相等</b>才修剪
     * ——防"同 pos 换了另一种方块"时误伤记录。<b>空串不作额外要求</b>：这些段的上报谓词与记忆侧执行守卫
     * 同族（上报 ⇒ 记忆侧持有该块 ⇒ 其守卫必放行），故"上报即持有"已构成决策 I 要的构造性保证。
     *
     * <p><b>不延迟</b>（决策 G）：BE 载荷是"每帧观测、可重建"的（{@code ObjectResolver.recordBlock} 在该
     * 方块再次可见时同帧回填），被误修剪的代价 = 一轮闪烁且可自愈 ⇒ 即时修剪。**与之相反**，
     * {@link ContainerMemoryStore} 的容器载荷不可重建，故那边是延迟修剪。
     *
     * <p><b>落盘时机</b>：本方法置 dirty 后<b>自行 save()</b>（F1 的承诺是"同帧落盘"）；调用方随后调用的
     * {@link #sync} 会看到 dirty=false 而不重复写。故一次修剪 = 一次整文件写，不会每帧发生。
     *
     * @param dimension 目标维（修剪只作用于该维子图）
     * @param expected  待修剪格 → 该格本代际上报的 blockId（无身份信息段用空串）
     * @return 统计 { "pruned": 已修剪, "kept": 身份不符/记录仍在而保留, "missing": 本就无记录 }
     */
    public Map<String, Integer> applyDeletions(final String dimension, final Map<BlockPos, String> expected) {
        final Map<String, StoredEntry> entries = byDim.get(dimension);
        if (entries == null || entries.isEmpty() || expected.isEmpty()) {
            return Map.of("pruned", 0, "kept", 0, "missing", 0);
        }
        int pruned = 0, kept = 0, missing = 0;
        for (Map.Entry<BlockPos, String> e : expected.entrySet()) {
            final String key = posToKey(e.getKey());
            final StoredEntry existing = entries.get(key);
            if (existing == null) {
                missing++;
                continue;
            }
            final String reportedId = e.getValue();
            if (reportedId != null && !reportedId.isBlank() && !reportedId.equals(existing.blockId)) {
                kept++;   // 决策 I：身份不符 ⇒ 不修剪（欠删方向安全）
                continue;
            }
            entries.remove(key);
            pruned++;
        }
        if (pruned > 0) {
            dirty = true;
            save();      // 同帧落盘（F1 的持久性承诺在此兑现）
            dirty = false;
        }
        return Map.of("pruned", pruned, "kept", kept, "missing", missing);
    }

    /** 存储中已有的方块实体总数（跨全部维）。 */
    public int size() {
        int total = 0;
        for (Map<String, StoredEntry> entries : byDim.values()) {
            total += entries.size();
        }
        return total;
    }

    /** 清除全部存储（内存 + 文件，跨全部维）。 */
    public void clear() {
        byDim.clear();
        poseByDim.clear();
        currentDimension = WorldsFile.LEGACY_DIMENSION;
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            LOGGER.warn("[Vision] Failed to delete store file: {}", e.getMessage());
        }
    }

    // ==================== 内部 ====================

    private static Path resolveFilePath() {
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve(DIR_NAME);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("[Vision] Failed to create directory {}: {}", dir, e.getMessage());
        }
        return dir.resolve(FILE_NAME);
    }

    private void load() {
        if (!Files.exists(filePath)) {
            LOGGER.info("[Vision] No existing store file, starting fresh.");
            return;
        }

        try {
            CompoundTag root = NbtIo.readCompressed(filePath, NbtAccounter.unlimitedHeap());
            if (root == null) return;

            WorldsFile.Result r = WorldsFile.read(root);
            currentDimension = r.currentDimension();
            for (Map.Entry<String, CompoundTag> e : r.worlds().entrySet()) {
                String dim = e.getKey();
                CompoundTag bucket = e.getValue();
                Map<String, StoredEntry> entries = new LinkedHashMap<>();
                CompoundTag beTag = bucket.getCompoundOrEmpty(KEY_BLOCK_ENTITIES);
                for (String key : beTag.keySet()) {
                    CompoundTag entry = beTag.getCompoundOrEmpty(key);
                    entries.put(key, new StoredEntry(
                            entry.getCompoundOrEmpty(KEY_NBT),
                            entry.getStringOr(KEY_TYPE_ID, ""),
                            entry.getStringOr(KEY_BLOCK, ""),
                            propsFromNbt(entry.getCompoundOrEmpty(KEY_STATE)),
                            entry.getLongOr(KEY_TIMESTAMP, 0L)
                    ));
                }
                byDim.put(dim, entries);
                poseByDim.put(dim, new Pose(
                        bucket.getStringOr(KEY_AGENT_POS, ""),
                        bucket.getFloatOr(KEY_AGENT_YAW, 0.0f),
                        bucket.getFloatOr(KEY_AGENT_PITCH, 0.0f),
                        bucket.getIntOr(KEY_AGENT_FOV, 0),
                        bucket.getLongOr(KEY_WORLD_TIME, -1L)
                ));
            }
            LOGGER.info("[Vision] Loaded {} stored block entities across {} dimension(s) from {}",
                    size(), byDim.size(), filePath);
        } catch (IOException e) {
            LOGGER.error("[Vision] Failed to load store file: {}", e.getMessage());
        }
    }

    private void save() {
        Map<String, CompoundTag> buckets = new LinkedHashMap<>();
        for (var de : byDim.entrySet()) {
            String dim = de.getKey();
            Pose pose = poseByDim.get(dim);
            CompoundTag bucket = new CompoundTag();
            if (pose != null) {
                bucket.putString(KEY_AGENT_POS, pose.agentPos);
                bucket.putFloat(KEY_AGENT_YAW, pose.agentYaw);
                bucket.putFloat(KEY_AGENT_PITCH, pose.agentPitch);
                bucket.putInt(KEY_AGENT_FOV, pose.agentFov);
                bucket.putLong(KEY_WORLD_TIME, pose.dayTime);
            }
            CompoundTag beTag = new CompoundTag();
            for (var e : de.getValue().entrySet()) {
                CompoundTag entry = new CompoundTag();
                entry.putString(KEY_TYPE_ID, e.getValue().typeId);
                entry.putString(KEY_BLOCK, e.getValue().blockId);
                entry.put(KEY_STATE, propsToNbt(e.getValue().stateProps));
                entry.put(KEY_NBT, e.getValue().nbt);
                entry.putLong(KEY_TIMESTAMP, e.getValue().timestamp);
                beTag.put(e.getKey(), entry);
            }
            bucket.put(KEY_BLOCK_ENTITIES, beTag);
            buckets.put(dim, bucket);
        }

        try {
            NbtIo.writeCompressed(WorldsFile.wrap(currentDimension, buckets), filePath);
            LOGGER.debug("[Vision] Saved {} block entities across {} dimension(s) to {}",
                    size(), byDim.size(), filePath);
        } catch (IOException ex) {
            LOGGER.error("[Vision] Failed to save store file: {}", ex.getMessage());
        }
    }

    private static String posToKey(final BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** 观察者眼睛坐标 → 存储字符串（双精度，游戏精度）；未知（null）时存空串。 */
    private static String agentPosKey(final Vec3 v) {
        return v == null ? "" : Double.toString(v.x) + "," + Double.toString(v.y) + "," + Double.toString(v.z);
    }

    private static boolean nbtEquals(final CompoundTag a, final CompoundTag b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /** 方块状态属性表 → NBT。 */
    private static CompoundTag propsToNbt(final Map<String, String> props) {
        CompoundTag tag = new CompoundTag();
        props.forEach(tag::putString);
        return tag;
    }

    /** NBT → 方块状态属性表（旧文件无该字段时返回空表）。 */
    private static Map<String, String> propsFromNbt(final CompoundTag tag) {
        Map<String, String> props = new LinkedHashMap<>();
        for (String key : tag.keySet()) {
            props.put(key, tag.getStringOr(key, ""));
        }
        return props;
    }

    /** 比较条目的全部字段（类型 / 方块 / 状态 / NBT），决定是否跳过或更新。 */
    private static boolean entryEquals(
            final StoredEntry existing,
            final VisionCollector.BlockEntitySnapshot snapshot
    ) {
        return existing.typeId.equals(snapshot.typeId())
                && existing.blockId.equals(snapshot.blockId())
                && existing.stateProps.equals(snapshot.stateProps())
                && nbtEquals(existing.nbt, snapshot.nbt());
    }

    // ==================== 内部数据结构 ====================

    /** 某维最后一次采集时的 agent 姿态（该维桶的顶层字段）。 */
    private record Pose(String agentPos, float agentYaw, float agentPitch, int agentFov, long dayTime) {}

    private static class StoredEntry {
        CompoundTag nbt;
        String typeId;
        String blockId;
        Map<String, String> stateProps;
        long timestamp;

        StoredEntry(final CompoundTag nbt, final String typeId,
                    final String blockId, final Map<String, String> stateProps,
                    final long timestamp) {
            this.nbt = nbt;
            this.typeId = typeId;
            this.blockId = blockId;
            this.stateProps = stateProps;
            this.timestamp = timestamp;
        }
    }
}
