package com.example.memworld;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 记忆世界复原引擎。
 *
 * <p>由 {@link MemoryWorldManager} 在每个服务器 tick 驱动。定期读取源 NBT 文件，
 * <b>仅在文件内容发生变化时</b>对世界做增量更新：新增的放置、变化的覆盖。
 *
 * <p><b>纯累积语义（v2，§7）</b>：只增不删——条目永不移除；失效条目的清除职责移交
 * {@link TerrainRestorer}（方块类型改变且新方块无方块实体时经 {@link #clearStale} 主动清除），
 * 另在 {@link #place} 加世界权威校验兜底（防止重启 / 文件残留导致"石头里的箱子实体"类 ghosting）。
 *
 * <p>坐标处理：不做任何平移，NBT 里的方块坐标严格对应世界坐标。
 *
 * <p>v2.32（世界类型区分，见 docs/世界类型区分与镜像复原设计方案.md §5）：
 * <ul>
 *   <li>源文件（{@code block_entities.nbt}）按维分桶；已应用表 / 指纹 / 昼夜对齐状态<b>按维隔离</b>，
 *       每 tick 只对 {@code level.dimension()} 桶做 diff/apply（其它维桶不动，镜像切回时再 apply）；</li>
 *   <li>agent 姿态（位置/朝向/FOV）与昼夜时间随<b>该维桶</b>记录——读 pose 从当前维桶读、不再读顶层；
 *       姿态变化 → 返回 {@link AgentPose} 触发传送（换维首次驱动时即使姿态数值相同也会因维不同而触发）；</li>
 *   <li>同一文件内容（同一读取代际）同一维只交付一次 pose / 内容，换维由 {@link MemoryWorldManager}
 *       先经 {@link #currentPoseFor} 拿到目标维姿态做跨维传送、本类随后把该维内容铺到已加载区块。</li>
 * </ul>
 */
public class MemoryRestorer {

    private static final Logger LOGGER = LoggerFactory.getLogger("stevex-test/memory");

    private static final String KEY_BLOCK_ENTITIES = "blockEntities";
    private static final String KEY_BLOCK = "block";
    private static final String KEY_STATE = "state";
    private static final String KEY_NBT = "nbt";
    private static final String KEY_AGENT_POS = "agentPos";
    private static final String KEY_AGENT_YAW = "agentYaw";
    private static final String KEY_AGENT_PITCH = "agentPitch";
    private static final String KEY_AGENT_FOV = "agentFov";
    /** v2.21：采集时刻世界时间（dayTime），记忆世界据此对齐昼夜（§7.10）。 */
    private static final String KEY_WORLD_TIME = "dayTime";

    /** v2.19：基础视场角"缺失"哨兵值（旧文件无 agentFov）；FOV 合法范围为 [70,110]，-1 恒安全。 */
    private static final int FOV_MISSING = -1;

    /** v2.18：agent 位置比较容差（1 mm），过滤双精度坐标下的浮点抖动。 */
    private static final double POS_EPSILON = 1e-3;

    /** v2.32：已应用的世界状态按维隔离：维度 → 方块坐标 → 内容指纹（block+state+nbt）。累积，只增不删。 */
    private final Map<String, Map<BlockPos, String>> appliedByDim = new LinkedHashMap<>();

    /** v2.32：已应用版本的源文件内容指纹按维；该维未出现过（null）→ 需要应用。 */
    private final Map<String, String> appliedFingerprintByDim = new LinkedHashMap<>();

    /** v2.32：昼夜对齐状态按维（最近一次应用该维的 dayTime；旧文件无 → 无键 = -1 语义不应用）。 */
    private final Map<String, Long> lastDayTimeByDim = new LinkedHashMap<>();

    /** v2.32：最近一次成功读取解析出的各维桶（维度 → blocks + pose + dayTime），跨 tick 缓存。 */
    private Map<String, DimData> parsedBuckets = Map.of();

    /** v2.32：一次读取代际内已向调用方交付过数据的维集合。 */
    private final Set<String> servedThisRead = new HashSet<>();

    /** v2.13 mtime 门控（§7.4）：最近一次成功读取的源文件 mtime；未变 → 不读不解压。 */
    private FileTime lastMtime;

    private int ticks;
    private int missingSourceCounter;

    /** 服务器（世界）启动 / 切换时调用，清空已应用状态。 */
    public void onServerStart() {
        appliedByDim.clear();
        appliedFingerprintByDim.clear();
        lastDayTimeByDim.clear();
        parsedBuckets = Map.of();
        servedThisRead.clear();
        lastMtime = null;
        ticks = 0;
        LOGGER.info("[MemoryWorld] Restorer ready");
    }

    /** 命令触发：强制重新读取源文件。须同时清 mtime 门控，否则 mtime 相同会被提前拦下。 */
    public void forceRefresh() {
        appliedFingerprintByDim.clear();
        parsedBuckets = Map.of();
        servedThisRead.clear();
        lastMtime = null;
    }

    /**
     * v2.10：清除指定位置的旧方块实体记录（由 {@link TerrainRestorer} 在方块类型改变、且新方块
     * 无方块实体时调用）。v2.32 增加维度参数——只清<b>该维</b>已应用表里的记录。
     *
     * <p>{@code block_entities.nbt} 增量合并永不删除旧条目 → 若不清除，重启后该位置的旧 BE 会被
     * 重新放回世界（"石头里的箱子实体"类 ghosting）。世界内旧 BE 已由 TerrainRestorer 的 setBlock
     * 一并移除，这里只需从已应用表忘记它；若文件里仍有该条目，后续读取由 {@link #place} 的世界
     * 权威校验兜底跳过（自愈）。
     *
     * <p>该位置没有已应用记录时是廉价 no-op。
     */
    public void clearStale(final String dimension, final BlockPos pos) {
        Map<BlockPos, String> applied = appliedByDim.get(dimension);
        if (applied != null) applied.remove(pos);
    }

    /**
     * v2.32：指定维最后一次记录 / 缓存的 agent 姿态（换维时 {@link MemoryWorldManager} 先取它做
     * 跨维传送，目标 = 文件该维桶顶层 agentPos）。该维无数据 / 无姿态 → null。
     */
    public AgentPose currentPoseFor(final String dimension) {
        DimData data = parsedBuckets.get(dimension);
        return data == null ? null : data.pose;
    }

    /**
     * 驱动一次轮询：源文件 mtime 变化时读取（解析全文件各维桶）；之后只对<b>当前 level 维</b>桶做
     * 差异应用，并在有数据时返回该维的 agent 姿态（供 manager 决定是否传送——manager 负责与上次
     * 姿态 / 维度比较，本类每个"新内容代际 × 首次驱动该维"返回一次）。
     *
     * <p>返回值用于「跟随观察者视角」——agent 位置/朝向/维变化时 manager 据此传送玩家；无数据时
     * 返回 null。
     */
    public AgentPose tick(final ServerLevel level) {
        MemoryConfig config = MemoryConfig.get();
        if (ticks++ % Math.max(1, config.pollIntervalTicks) != 0) return null;

        Path source = config.resolveSourceFile();
        if (source == null || !Files.exists(source)) {
            // 每 30 次轮询（约 30 秒）告警一次，避免刷屏
            if (missingSourceCounter++ % 30 == 0) {
                LOGGER.warn("[MemoryWorld] Source file missing, updates paused (gameDir={}). "
                        + "Set 'sourceFile' in config/stevex-test/memory.json.",
                        config.gameDirectory());
            }
            lastMtime = null; // 文件重新出现后自然触发首次读取
            servedThisRead.clear();
            appliedFingerprintByDim.clear();
            return null;
        }
        missingSourceCounter = 0;

        // v2.13 mtime 门控（§7.4）：文件只在采集器侧 vision/snapshot 落盘时变化 → 以 mtime 作为
        // 快照到达信号。mtime 未变 → 不读不解压（空闲成本≈0）。
        final FileTime mtime;
        try {
            mtime = Files.getLastModifiedTime(source);
        } catch (IOException e) {
            LOGGER.warn("[MemoryWorld] Failed to stat source file {}: {}", source, e.getMessage());
            return null;
        }
        if (!mtime.equals(lastMtime)) {
            Map<String, DimData> parsed = readFile(source);
            if (parsed == null) return null; // 写入半截等 → 保留旧 mtime，下轮重试
            lastMtime = mtime; // 只在成功读取后才推进
            parsedBuckets = parsed;
            servedThisRead.clear();
        }

        final String dim = level.dimension().identifier().toString();
        final DimData data = parsedBuckets.get(dim);
        if (data == null) return null; // 该维还没有数据
        if (servedThisRead.contains(dim)) return null; // 本代际已交付
        servedThisRead.add(dim);

        // v2.21：世界时间对齐（§7.10），按维——只对当前维桶的 dayTime（采集时该维世界时间）对齐；
        // 与 advance_time=false 不冲突——setDayTime 直接设值，不依赖 tickTime 自增。
        if (data.dayTime >= 0) {
            Long last = lastDayTimeByDim.get(dim);
            if (last == null || last != data.dayTime) {
                level.setDayTime(data.dayTime);
                lastDayTimeByDim.put(dim, data.dayTime);
                LOGGER.info("[MemoryWorld] Day time synced [{}] to {} ({})", dim, data.dayTime,
                        data.dayTime % 24000L);
            }
        }

        // 内容指纹变化 → 同步方块实体
        String fp = fingerprint(data.blocks);
        if (!fp.equals(appliedFingerprintByDim.get(dim))) {
            appliedFingerprintByDim.put(dim, fp);
            sync(level, dim, data.blocks);
        }

        // agent 视角：交付当前维桶记录的姿态，是否真正传送由 manager 与上次姿态 / 维度比较后决定
        return data.pose;
    }

    // ==================== 差异计算与应用 ====================

    private void sync(final ServerLevel level, final String dimension, final Map<BlockPos, StoredBlock> current) {
        if (current.isEmpty()) return; // 空记忆，无需放置
        Map<BlockPos, String> applied = appliedByDim.computeIfAbsent(dimension, k -> new LinkedHashMap<>());

        Map<BlockPos, String> next = new LinkedHashMap<>();
        List<BlockPos> toPlace = new ArrayList<>();

        for (Map.Entry<BlockPos, StoredBlock> e : current.entrySet()) {
            BlockPos pos = e.getKey();
            String key = e.getValue().fingerprint();
            next.put(pos, key);
            if (!key.equals(applied.get(pos))) toPlace.add(pos);
        }

        if (toPlace.isEmpty()) return;

        int placed = 0;
        for (BlockPos pos : toPlace) {
            StoredBlock sb = current.get(pos);
            if (sb != null && place(level, pos, sb)) placed++;
        }

        applied.clear();
        applied.putAll(next);

        LOGGER.info("[MemoryWorld] Sync [{}]: +{} placed, total {} entries", dimension, placed, applied.size());
    }

    private boolean place(final ServerLevel level, final BlockPos pos, final StoredBlock sb) {
        try {
            BlockState state = BlockStateUtil.fromSaved(sb.blockId(), sb.state());
            // v2.10 世界权威校验：当前世界方块与 BE 记录的方块不一致（terrain 已把该位置换成别的方块
            // → 该 BE 条目失效），跳过放置，防止"石头里的箱子实体"类 ghosting。TerrainRestorer 在本类
            // 之前执行（见 MemoryWorldManager），此处读到的世界方块即最新地形。世界为空气时允许放置
            // （BE 数据是自身方块的权威来源）。
            BlockState worldState = level.getBlockState(pos);
            if (!worldState.isAir() && worldState.getBlock() != state.getBlock()) {
                return false;
            }
            // v2.21 静默放置（§7.9 陷阱 ①）：UPDATE_CLIENTS | UPDATE_SKIP_ALL_SIDEEFFECTS = 2 | 816 = 818。
            // 不含 UPDATE_NEIGHBORS(bit1) → 不触发邻居 updateShape / neighborChanged；
            // 不含 UPDATE_KNOWN_SHAPE(bit16) → 不传播形状更新；不含 UPDATE_SKIP_ON_PLACE 之外的效果（816 含
            // 512 SKIP_ON_PLACE / 256 SKIP_BE_SIDEEFFECTS / 32 SUPPRESS_DROPS / 16 KNOWN_SHAPE）→ 挂墙方块
            // 不因支撑方块未放置被破坏、红石线保留采集时连接、被替换方块不掉落物；保留 bit2 客户端同步。
            level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS);

            if (sb.nbt() != null && !sb.nbt().isEmpty()) {
                CompoundTag nbt = sb.nbt().copy();
                // 用目标坐标覆盖 nbt 里的位置字段，防止残留源世界坐标
                nbt.putInt("x", pos.getX());
                nbt.putInt("y", pos.getY());
                nbt.putInt("z", pos.getZ());

                BlockEntity be = BlockEntity.loadStatic(pos, state, nbt, level.registryAccess());
                if (be != null) {
                    be.setLevel(level);
                    level.setBlockEntity(be);
                }
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("[MemoryWorld] Failed to place {} at {}: {}", sb.blockId(), pos, e.getMessage());
            return false;
        }
    }

    // ==================== 读取源文件 ====================

    /**
     * 读取整份文件 → 各维 (blocks + pose + dayTime)。旧版单维文件经 {@link WorldsFile#read} 自动
     * 包成 overworld 桶 → 姿态 / 昼夜字段在旧文件顶层、恰为该"桶"的顶层，解析路径一致。
     */
    private Map<String, DimData> readFile(final Path source) {
        try {
            CompoundTag root = NbtIo.readCompressed(source, NbtAccounter.unlimitedHeap());
            if (root == null) return Map.of();

            WorldsFile.Result r = WorldsFile.read(root);
            Map<String, DimData> out = new LinkedHashMap<>();
            for (Map.Entry<String, CompoundTag> e : r.worlds().entrySet()) {
                CompoundTag bucket = e.getValue();
                Map<BlockPos, StoredBlock> blocks = new LinkedHashMap<>();
                CompoundTag beTag = bucket.getCompoundOrEmpty(KEY_BLOCK_ENTITIES);
                for (String key : beTag.keySet()) {
                    BlockPos pos = parsePos(key);
                    if (pos == null) continue;
                    CompoundTag entry = beTag.getCompoundOrEmpty(key);
                    String blockId = entry.getStringOr(KEY_BLOCK, "");
                    Map<String, String> state = readState(entry.getCompoundOrEmpty(KEY_STATE));
                    CompoundTag nbt = entry.getCompoundOrEmpty(KEY_NBT);
                    blocks.put(pos, new StoredBlock(blockId, state, nbt));
                }
                out.put(e.getKey(), new DimData(blocks, readPose(bucket), bucket.getLongOr(KEY_WORLD_TIME, -1L)));
            }
            return out;
        } catch (IOException e) {
            LOGGER.warn("[MemoryWorld] Failed to read source file {}: {}", source, e.getMessage());
            return null;
        }
    }

    /**
     * 从桶顶层读取 agent 视角（眼睛位置 + yaw/pitch + 基础视场角）。
     *
     * <p>v2.18：位置改为双精度眼睛坐标；旧整数格式（如 {@code "100,64,96"}）仍可解析
     * （{@link #parseVec3} 用 {@code Double.parseDouble} 兼容）。旧文件无
     * {@code agentYaw}/{@code agentPitch} → 朝向填 NaN，传送时沿用玩家当前朝向；
     * v2.19：旧文件无 {@code agentFov} → FOV 填 {@link #FOV_MISSING}，沿用玩家当前视场角。
     * 位置缺失 / 解析失败 → 返回 null。
     */
    private static AgentPose readPose(final CompoundTag bucket) {
        Vec3 pos = parseVec3(bucket.getStringOr(KEY_AGENT_POS, ""));
        if (pos == null) return null;
        float yaw = bucket.contains(KEY_AGENT_YAW) ? bucket.getFloatOr(KEY_AGENT_YAW, 0.0f) : Float.NaN;
        float pitch = bucket.contains(KEY_AGENT_PITCH) ? bucket.getFloatOr(KEY_AGENT_PITCH, 0.0f) : Float.NaN;
        int fov = bucket.contains(KEY_AGENT_FOV) ? bucket.getIntOr(KEY_AGENT_FOV, FOV_MISSING) : FOV_MISSING;
        return new AgentPose(pos, yaw, pitch, fov);
    }

    /** 两个 agent 视角是否「等价」（供 manager 决定是否真的需要传送，见 {@link MemoryWorldManager}）：
     *  位置差 < {@link #POS_EPSILON}，且 yaw/pitch 差在 0.5° 内（过滤抖动）。 */
    static boolean samePose(final AgentPose a, final AgentPose b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return posEquals(a.pos(), b.pos())
                && rotEquals(a.yaw(), b.yaw())
                && rotEquals(a.pitch(), b.pitch())
                && fovEquals(a.fov(), b.fov());
    }

    /** v2.18：位置比较（双精度，1 mm 容差）。 */
    private static boolean posEquals(final Vec3 a, final Vec3 b) {
        return Math.abs(a.x - b.x) < POS_EPSILON
                && Math.abs(a.y - b.y) < POS_EPSILON
                && Math.abs(a.z - b.z) < POS_EPSILON;
    }

    /** 朝向相等判定：任一为 NaN（旧文件未记录）视为相等；否则差在 0.5° 内。 */
    private static boolean rotEquals(final float x, final float y) {
        if (Float.isNaN(x) || Float.isNaN(y)) return true;
        return Math.abs(x - y) < 0.5f;
    }

    /** FOV 相等判定：任一为 {@link #FOV_MISSING}（旧文件未记录）视为相等；否则严格相等。 */
    private static boolean fovEquals(final int x, final int y) {
        return x == FOV_MISSING || y == FOV_MISSING || x == y;
    }

    private static Map<String, String> readState(final CompoundTag stateTag) {
        Map<String, String> state = new LinkedHashMap<>();
        for (String k : stateTag.keySet()) {
            state.put(k, stateTag.getStringOr(k, ""));
        }
        return state;
    }

    private static BlockPos parsePos(final String key) {
        String[] parts = key.split(",");
        if (parts.length != 3) return null;
        try {
            return new BlockPos(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 观察者眼睛坐标字符串 → Vec3（双精度）；旧整数格式（如 {@code "100,64,96"}）同样可解析。 */
    private static Vec3 parseVec3(final String key) {
        if (key == null || key.isBlank()) return null;
        String[] parts = key.split(",");
        if (parts.length != 3) return null;
        try {
            return new Vec3(
                    Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim())
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 内容指纹：整张表的字符串表示（同一文件内容读取结果稳定）。 */
    private static String fingerprint(final Map<BlockPos, StoredBlock> entries) {
        return entries.toString();
    }

    // ==================== 数据结构 ====================

    private record StoredBlock(String blockId, Map<String, String> state, CompoundTag nbt) {
        String fingerprint() {
            return blockId + "|" + state + "|" + nbt;
        }
    }

    /** agent 视角：眼睛位置（双精度，v2.18）+ 朝向（v2.15）+ 基础视场角（v2.19）。
     *  yaw/pitch 为 NaN、fov 为 {@link #FOV_MISSING} 表示旧文件未记录对应字段。 */
    public record AgentPose(Vec3 pos, float yaw, float pitch, int fov) {}

    /**
     * 一个维桶的一次读取结果：方块实体表 + agent 视角 + 世界时间（dayTime，v2.21）。
     * dayTime 为 {@code -1} 表示该维无该字段（不应用时间对齐）。
     */
    private record DimData(Map<BlockPos, StoredBlock> blocks, AgentPose pose, long dayTime) {}
}
