package com.example.memworld;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 *
 * <p>v2.48（§11）：源不再是单个 {@code block_entities.nbt}，而是 {@code block_entities/} 目录下的
 * <b>区文件</b>加一份 {@code _index.nbt}。本类的三条不变量随之调整，但<b>对外行为一字未改</b>——
 * 记忆世界的最终内容仍与"每次整份重读"逐条一致：
 * <ul>
 *   <li><b>每轮仍只 stat 一个文件</b>：{@code _index.nbt} 只在真有区写出时才被采集端重写，所以
 *       "mtime 未变 ⇒ 什么都不用做"这条门控前提照旧成立（{@code pollIntervalTicks = 1} 的成本论证不变）；</li>
 *   <li><b>只读变化的区</b>：索引给出 {@code 维 → 区 → updatedAt}，与 {@link #indexCache} diff 后，
 *       只有 {@code updatedAt} 变了的区才重新解压。区文件是整区快照，故"整区替换"与"整份重读"等价；</li>
 *   <li><b>姿态单飞</b>：agent 姿态与世界时间挪进 {@code _pose.nbt}（维级标量、无坐标、逐帧变），
 *       由独立门控驱动，不参与索引——否则它会每帧刷新索引 mtime，把上一条门控彻底废掉。</li>
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

    /** v2.48：agent 姿态文件（维级标量，全维一份；采集端只在自己变化时才重写）。 */
    private static final String POSE_FILE_NAME = "_pose.nbt";

    /**
     * v2.48：内容镜像 —— 维 → 区键 → 该区的（方块坐标 → 条目）。<b>与采集端 ③ 的分片结构同形</b>。
     *
     * <p>由索引 diff 驱动<b>整区替换</b>：区文件是该区的完整快照，故"换了哪个区就读哪个区、整区换掉"，
     * 与"每次整份重读"在结果上等价，而代价只与变更的区成正比。
     */
    private final Map<String, Map<String, Map<BlockPos, StoredBlock>>> blocksByDim = new LinkedHashMap<>();

    /**
     * v2.48：已应用的世界状态，同样按维、<b>按区</b>隔离：维度 → 区键 → 方块坐标 → 内容指纹。
     *
     * <p>按区隔离是必须的，不是对称性洁癖：{@link #syncRegion} 靠"把本区的 {@code next} 整表换掉
     * {@code applied}"来剪掉已经消失的条目，作用域必须与"一次能拿到的完整快照"（= 一个区文件）对齐。
     * 若仍按整维记，区级更新就没法在不重走全维的前提下完成剪枝。
     */
    private final Map<String, Map<String, Map<BlockPos, String>>> appliedByDim = new LinkedHashMap<>();

    /** v2.48：昼夜对齐状态按维（最近一次应用该维的 dayTime；无记录 → -1 语义不应用）。 */
    private final Map<String, Long> lastDayTimeByDim = new LinkedHashMap<>();

    /** v2.48：维 → 该维最新 agent 姿态（来自 {@code _pose.nbt}；无姿态的维无键）。 */
    private final Map<String, AgentPose> poseByDim = new LinkedHashMap<>();

    /** v2.48：维 → 该维最新世界时间（来自 {@code _pose.nbt}）。 */
    private final Map<String, Long> dayTimeByDim = new LinkedHashMap<>();

    /** v2.48：索引 diff 缓存：维 → 区键 → 上次看到的 {@code updatedAt}。相同 ⇒ 该区不需要重读。 */
    private final Map<String, Map<String, Long>> indexCache = new LinkedHashMap<>();

    /** v2.48：索引说"这个区变了"、但内容尚未同步进世界的区（维 → 区键集）。{@link #syncDim} 消费后清空。 */
    private final Map<String, Set<String>> pendingSync = new LinkedHashMap<>();

    /** v2.32：一次读取代际内已向调用方交付过姿态的维集合。 */
    private final Set<String> servedThisRead = new HashSet<>();

    /** v2.13 mtime 门控（§7.4）：索引文件的 mtime；未变 → 一个区文件都不读。 */
    private FileTime lastIndexMtime;
    /** v2.48：姿态文件的 mtime（独立门控——它与区文件的变化频率完全不同，见 {@link VisionBlockEntityStore}）。 */
    private FileTime lastPoseMtime;

    private int ticks;
    private int missingSourceCounter;

    /** 服务器（世界）启动 / 切换时调用，清空已应用状态。 */
    public void onServerStart() {
        appliedByDim.clear();
        blocksByDim.clear();
        poseByDim.clear();
        dayTimeByDim.clear();
        indexCache.clear();
        pendingSync.clear();
        lastDayTimeByDim.clear();
        servedThisRead.clear();
        lastIndexMtime = null;
        lastPoseMtime = null;
        ticks = 0;
        LOGGER.info("[MemoryWorld] Restorer ready");
    }

    /**
     * 命令触发：强制重新读取。
     *
     * <p>清空 {@link #indexCache} 即"所有区都当作第一次见"⇒ 下一轮重读全部区文件；两个 mtime 门控也一并
     * 清掉，否则 mtime 相同会被提前拦下。
     */
    public void forceRefresh() {
        indexCache.clear();
        pendingSync.clear();
        servedThisRead.clear();
        lastIndexMtime = null;
        lastPoseMtime = null;
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
        final Map<String, Map<BlockPos, String>> regions = appliedByDim.get(dimension);
        if (regions == null) return;
        // 按区定位：区文件切分后已应用表也是分片的，全区扫一遍就不再是常数代价。
        final Map<BlockPos, String> applied = regions.get(VisionRegions.regionKeyOfBlock(pos));
        if (applied != null) applied.remove(pos);
    }

    /**
     * v2.32：指定维最后一次记录 / 缓存的 agent 姿态（换维时 {@link MemoryWorldManager} 先取它做
     * 跨维传送，目标 = 文件该维桶顶层 agentPos）。该维无数据 / 无姿态 → null。
     */
    public AgentPose currentPoseFor(final String dimension) {
        return poseByDim.get(dimension);
    }

    /**
     * v2.48：驱动一次轮询。源目录下现在有<b>两个</b>独立变化的输入，故有<b>两个</b>独立门控：
     *
     * <ol>
     *   <li>{@code _index.nbt}（{@link #pollRegions}）——"哪些区变了"。整份 buffered 内容只有它一个
     *       权威索引，所以它没变 ⇒ 一个区文件都不读。</li>
     *   <li>{@code _pose.nbt}（{@link #pollPose}）——agent 姿态与世界时间。它每帧都在动，若也进索引，
     *       索引就会被每帧重写、门控失效（这是实现期才暴露的约束，见 {@code VisionBlockEntityStore}）。</li>
     * </ol>
     *
     * <p>两者读到的东西汇合到同一份内存镜像里，之后只对<b>当前 level 维</b>做差异应用，并在有数据时
     * 返回该维的 agent 姿态（供 manager 决定是否传送——manager 负责与上次姿态 / 维度比较，本类每个
     * "新内容代际 × 首次驱动该维"返回一次）。
     *
     * <p>返回值用于「跟随观察者视角」——agent 位置/朝向/维变化时 manager 据此传送玩家；无数据时
     * 返回 null。
     */
    public AgentPose tick(final ServerLevel level) {
        final MemoryConfig config = MemoryConfig.get();
        if (ticks++ % Math.max(1, config.pollIntervalTicks) != 0) return null;

        final Path storeDir = config.resolveBlockEntityDir();
        if (storeDir == null || !Files.isDirectory(storeDir)) {
            // 每 30 次轮询（约 30 秒）告警一次，避免刷屏
            if (missingSourceCounter++ % 30 == 0) {
                LOGGER.warn("[MemoryWorld] Source store directory missing, updates paused (gameDir={}). "
                        + "Set 'sourceFile' in config/stevex-test/memory.json.",
                        config.gameDirectory());
            }
            // 两个门控都归零：目录重新出现后自然触发首次读取。索引缓存也一并清空，使重新出现时
            // 每个区都当作第一次见——与 v2.32 清 appliedFingerprintByDim 的意图一致（重新出现即重新
            // 走一遍差异，而差异本身会把无变化的部分判成 no-op）。
            lastIndexMtime = null;
            lastPoseMtime = null;
            indexCache.clear();
            servedThisRead.clear();
            return null;
        }
        missingSourceCounter = 0;

        // 先索引后姿态：索引决定"内容有没有变"，姿态决定"这一轮要不要交付"。顺序不影响结果，
        // 只影响日志里谁先出现。
        pollRegions(storeDir);
        pollPose(storeDir);

        final String dim = level.dimension().identifier().toString();
        if (!blocksByDim.containsKey(dim) && !poseByDim.containsKey(dim)) return null; // 该维还没有数据
        if (servedThisRead.contains(dim)) return null; // 本代际已交付
        servedThisRead.add(dim);

        // v2.21：世界时间对齐（§7.10），按维——只对当前维的 dayTime（采集时该维世界时间）对齐；
        // 与 advance_time=false 不冲突——setDayTime 直接设值，不依赖 tickTime 自增。
        final Long dayTime = dayTimeByDim.get(dim);
        if (dayTime != null && dayTime >= 0) {
            final Long last = lastDayTimeByDim.get(dim);
            if (last == null || !last.equals(dayTime)) {
                level.setDayTime(dayTime);
                lastDayTimeByDim.put(dim, dayTime);
                LOGGER.info("[MemoryWorld] Day time synced [{}] to {} ({})", dim, dayTime, dayTime % 24000L);
            }
        }

        // 内容同步只处理"索引说变了、还没同步进世界"的那些区。
        syncDim(level, dim);

        // agent 视角：交付当前维记录的姿态，是否真正传送由 manager 与上次姿态 / 维度比较后决定
        return poseByDim.get(dim);
    }

    // ==================== 差异计算与应用 ====================

    /**
     * v2.48：把"索引说变了"的区同步进世界。只遍历 {@link #pendingSync} 里点名的区——<b>这是切分的全部
     * 意义所在</b>：姿态文件每帧都在变、每帧都会让 {@link #servedThisRead} 归零，若此处按整维遍历，
     * 每帧的代价仍与全维成正比，读文件的省下的又全走回去了。
     */
    private void syncDim(final ServerLevel level, final String dimension) {
        final Set<String> dirty = pendingSync.get(dimension);
        if (dirty == null || dirty.isEmpty()) return;
        final Map<String, Map<BlockPos, StoredBlock>> regions = blocksByDim.get(dimension);
        if (regions == null) {
            dirty.clear();
            return;
        }
        // 先取出再清空：本轮只处理这些区；同步过程中若索引又变（同 tick 内不会，但别依赖这个）
        // 会重新攒进 pendingSync，下一 tick 再处理。
        final Set<String> todo = new LinkedHashSet<>(dirty);
        dirty.clear();

        int placedNew = 0;
        int rewritten = 0;
        int skipped = 0;
        for (final String regionKey : todo) {
            final Map<BlockPos, StoredBlock> current = regions.get(regionKey);
            if (current == null) continue; // 区在索引里已消失，applyIndex 已连镜像一并摘掉
            final int[] counts = syncRegion(level, dimension, regionKey, current);
            placedNew += counts[0];
            rewritten += counts[1];
            skipped += counts[2];
        }
        if (placedNew + rewritten + skipped == 0) return;

        int total = 0;
        for (final Map<BlockPos, StoredBlock> r : regions.values()) total += r.size();

        // §18.5-D：三种情况必须可分辨——旧的 `+N placed` 把"首次放置 / 内容重写 / 失败跳过"混成一个数，
        // 2026-09-12 的误判正是读它读出来的（§18.4 附注）。读法见 §10 第 22 条：
        // placed = 该 pos 首次放置；rewritten = 之前放过、本次内容或状态变了（**BE 内容收缩走这一支**）；
        // skipped = 没生效（下一帧会重试）。
        LOGGER.info(
                "[MemoryWorld] Sync [{}]: +{} placed, {} rewritten, {} skipped, {} region(s) touched, total {} entries",
                dimension, placedNew, rewritten, skipped, todo.size(), total);
    }

    /**
     * 把一个区同步进世界，返回 {@code [placed, rewritten, skipped]}。
     *
     * <p>作用域是<b>一个区</b>而非整个维：{@code applied} 按区隔离（见 {@link #appliedByDim}），"用 next
     * 整表换掉 applied"这一步就只剪掉<b>本区</b>里消失的条目。因为入参 {@code current} 就是一个区文件的
     * 完整快照，这个剪枝范围恰好不多不少——这正是把 {@code applied} 一并分片的原因。
     */
    private int[] syncRegion(final ServerLevel level, final String dimension, final String regionKey,
                             final Map<BlockPos, StoredBlock> current) {
        if (current.isEmpty()) return new int[]{0, 0, 0}; // 空区，无需放置
        final Map<BlockPos, String> applied = appliedByDim
                .computeIfAbsent(dimension, k -> new LinkedHashMap<>())
                .computeIfAbsent(regionKey, k -> new LinkedHashMap<>());

        final Map<BlockPos, String> next = new LinkedHashMap<>();
        final List<BlockPos> toPlace = new ArrayList<>();

        for (final Map.Entry<BlockPos, StoredBlock> e : current.entrySet()) {
            final BlockPos pos = e.getKey();
            final String key = e.getValue().fingerprint();
            next.put(pos, key);
            if (!key.equals(applied.get(pos))) toPlace.add(pos);
        }

        if (toPlace.isEmpty()) {
            applied.clear();
            applied.putAll(next);
            return new int[]{0, 0, 0};
        }

        int placedNew = 0;
        int rewritten = 0;
        int skipped = 0;
        for (final BlockPos pos : toPlace) {
            final StoredBlock sb = current.get(pos);
            final boolean isNew = applied.get(pos) == null;
            if (sb != null && place(level, pos, sb)) {
                if (isNew) placedNew++;
                else rewritten++;
            } else {
                // v2.38 §18.5-C：**失败/被拒的 pos 不记账**——把它在 applied 里的旧指纹留下（本来就没有就
                // 删掉），于是下一帧仍满足 `key != applied.get(pos)` ⇒ 继续进 toPlace 重试。
                // 原实现无条件 `applied.putAll(next)`，任何一次静默失败（如 loadStatic 返回 null、worldState
                // 守卫拒绝）都会被记成"已应用"⇒ 永不重试，只能靠重启自愈（§18.4-2）。
                skipped++;
                final String prev = applied.get(pos);
                if (prev == null) next.remove(pos);
                else next.put(pos, prev);
            }
        }

        applied.clear();
        applied.putAll(next);
        return new int[]{placedNew, rewritten, skipped};
    }

    /**
     * 放置一个 BE 记录（方块 + 方块实体内容），并**发布**这次改动。
     *
     * <p><b>返回值语义（§18.5-C）</b>：载荷里有内容、但实体没能装上（{@link BlockEntity#loadStatic} 返回
     * null）⇒ 返回 <b>false</b>，让调用方不记账、下一帧重试。只放方块（载荷无内容）或两者都放好 ⇒ true。
     *
     * <p><b>为何要"发布"（§18）</b>：内容收缩时方块状态一字不变，于是 `setBlock` 什么都不做、
     * `setBlockEntity` 也只是把实体塞进 map——**改动既到不了客户端、也到不了磁盘**。见下面两处发布调用。
     */
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
            //
            // 返回值 = "状态是否真的写了"：LevelChunk.setBlockState 对**同状态**直接返回 null
            // （LevelChunk.java:282）⇒ Level.setBlock 在 Level.java:224 `oldState == null → return false`，
            // **连 sendBlockUpdated 都走不到**（:237）。"方块没变、只有 BE 内容变了"正是这种情况。
            final boolean stateWritten =
                    level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS);

            boolean installed = false;
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
                    installed = true;
                }
            } else {
                installed = true; // 载荷无内容：本条目只负责方块，"实体没装上"这种情况不存在
            }

            if (installed) {
                // 发布 ①（客户端，§18.5-A）：状态没变时上面那次 setBlock 一个包都没发，必须显式补一下——
                // 与 vanilla CampfireBlockEntity.markUpdated() 同款（它就是 sendBlockUpdated(pos, state, state, 3)）。
                // 链路：ServerLevel.sendBlockUpdated(ServerLevel.java:1127) → ServerChunkCache.blockChanged(:464)
                // → ChunkHolder.blockChanged(:123，需 ticking chunk) → chunkHoldersToBroadcast
                // → ServerChunkCache.broadcastChangedChunks(:357) → ChunkHolder.broadcastChanges(:174)
                // → broadcastBlockEntityIfNeeded(:212) → getUpdatePacket()。状态变了的那次 setBlock 已经排过
                // 同样的队，无需重复发（故仅在 !stateWritten 时补）。
                if (!stateWritten) {
                    level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
                }
                // 发布 ②（落盘，§18.5-B）：Level.setBlockEntity → LevelChunk.setBlockEntity 只做
                // blockEntities.put，既不置脏也不发包（Level.java:698-703 / LevelChunk.java:400-410、427-455）；
                // 置脏的唯一入口是 Level.blockEntityChanged → markUnsaved（Level.java:875-879）。
                // **刻意不用 be.setChanged()**：它还会 updateNeighbourForOutputSignal（Level.java:994-1003，
                // 水平四向扫、命中比较器即**直接** neighborChanged）⇒ 会在冻结世界里把红石/比较器逻辑激活，
                // 破坏 §7.9 冻结不变量。直接调 blockEntityChanged 只做 markUnsaved，零副作用。
                level.blockEntityChanged(pos);
            }
            return installed;
        } catch (Exception e) {
            LOGGER.warn("[MemoryWorld] Failed to place {} at {}: {}", sb.blockId(), pos, e.getMessage());
            return false;
        }
    }

    // ==================== 轮询：索引与姿态 ====================

    /**
     * v2.48：轮询 {@code _index.nbt}（每轮唯一的 stat），把它与本地缓存 diff，只重读变化的区文件。
     *
     * <p><b>门控形状与 v2.32 完全一致</b>（§11.13-5）：仍然是一轮一次 stat、mtime 未变就什么都不做，
     * 只是 stat 的对象从数据文件换成了索引文件。前提由采集端保证——索引只在真有区写出时才重写
     * （{@code VisionRegions.commit} 里那句 {@code if (!writes.isEmpty())} 就是这条前提本身）。
     *
     * <p>读失败/空索引一律<b>不推进</b> {@link #lastIndexMtime}，于是下一轮重试；这与 v2.32 对源文件的
     * 处理同形（写入半截 → 保留旧 mtime）。
     */
    private void pollRegions(final Path storeDir) {
        final Path idx = storeDir.resolve(VisionRegions.INDEX_FILE_NAME);
        final FileTime mtime;
        try {
            mtime = Files.getLastModifiedTime(idx);
        } catch (IOException e) {
            return; // 索引还没出现（或目录刚被清空）→ 保持门控，出现时自然触发
        }
        if (mtime.equals(lastIndexMtime)) return;

        final Map<String, Map<String, Long>> fresh = VisionRegions.readIndex(storeDir);
        if (fresh == null) return; // 半截写 / 损坏 → 保留旧 mtime，下轮重试
        lastIndexMtime = mtime; // 只在成功读取后才推进
        applyIndex(storeDir, fresh);
        servedThisRead.clear();
    }

    /**
     * 把新索引合并进内存镜像：<b>只读变化的区文件</b>，整区替换。
     *
     * <p>区文件是该区的完整快照，所以"整区替换"与"读整份文件重建"在结果上等价——这正是 {@code updatedAt}
     * 能当 diff 键的原因（§11.9.3：它是内容级的，只在内容真的变了时才变，不是写入时间戳）。
     *
     * <p>反向的一步同样必要：本地有、新索引里没有的区，说明采集端把它删了（{@code clear()} / 手动删目录），
     * 必须连镜像和已应用表一并摘掉，否则增量合并会留下整份重读时本不存在的幽灵。
     */
    private void applyIndex(final Path storeDir, final Map<String, Map<String, Long>> fresh) {
        int reread = 0;
        int failed = 0;
        for (final String dim : new ArrayList<>(indexCache.keySet())) {
            if (fresh.containsKey(dim)) continue;
            indexCache.remove(dim);
            blocksByDim.remove(dim);
            appliedByDim.remove(dim);
            pendingSync.remove(dim);
        }
        for (final Map.Entry<String, Map<String, Long>> de : fresh.entrySet()) {
            final String dim = de.getKey();
            final String dimDirName = VisionRegions.dimDirName(dim);
            // 记录在索引里、但目录名/文件名对不上的区（区文件自检在 readRegion 里做）。
            final Map<String, Map<BlockPos, StoredBlock>> mirrored =
                    blocksByDim.computeIfAbsent(dim, k -> new LinkedHashMap<>());
            final Map<String, Long> cached = indexCache.computeIfAbsent(dim, k -> new LinkedHashMap<>());

            for (final Map.Entry<String, Long> re : de.getValue().entrySet()) {
                final String regionKey = re.getKey();
                if (re.getValue().equals(cached.get(regionKey))) continue; // 未变 → 不读
                final int[] rxrz = VisionRegions.parseRegionKey(regionKey);
                if (rxrz == null) continue;
                final CompoundTag root = VisionRegions.readRegion(
                        VisionRegions.regionFile(storeDir, dim, rxrz[0], rxrz[1]), dimDirName, rxrz[0], rxrz[1]);
                if (root == null) {
                    failed++; // 不推进该区的 updatedAt ⇒ 下一轮重试
                    continue;
                }
                mirrored.put(regionKey, readRegionEntries(root));
                cached.put(regionKey, re.getValue());
                pendingSync.computeIfAbsent(dim, k -> new LinkedHashSet<>()).add(regionKey);
                reread++;
            }

            // 索引里已消失的区：连同镜像与已应用表一并摘掉。
            final List<String> gone = new ArrayList<>();
            for (final String regionKey : cached.keySet()) {
                if (!de.getValue().containsKey(regionKey)) gone.add(regionKey);
            }
            for (final String regionKey : gone) {
                cached.remove(regionKey);
                mirrored.remove(regionKey);
                final Map<String, Map<BlockPos, String>> appliedRegions = appliedByDim.get(dim);
                if (appliedRegions != null) appliedRegions.remove(regionKey);
            }
        }
        if (failed > 0) {
            LOGGER.warn("[MemoryWorld] {} region file(s) unreadable this poll — will retry", failed);
        } else if (reread > 0) {
            LOGGER.info("[MemoryWorld] Region index changed: re-read {} region file(s)", reread);
        }
    }

    /** 区文件 → 该区的条目表。{@code blocks} 节内的位置键由采集端用 {@code "x,y,z"} 写入。 */
    private static Map<BlockPos, StoredBlock> readRegionEntries(final CompoundTag regionRoot) {
        final Map<BlockPos, StoredBlock> out = new LinkedHashMap<>();
        final CompoundTag beTag = regionRoot.getCompoundOrEmpty(KEY_BLOCK_ENTITIES);
        for (final String key : beTag.keySet()) {
            final BlockPos pos = VisionRegions.parsePosKey(key);
            if (pos == null) continue;
            final CompoundTag entry = beTag.getCompoundOrEmpty(key);
            out.put(pos, new StoredBlock(
                    entry.getStringOr(KEY_BLOCK, ""),
                    readState(entry.getCompoundOrEmpty(KEY_STATE)),
                    entry.getCompoundOrEmpty(KEY_NBT)));
        }
        return out;
    }

    /**
     * v2.48：轮询 {@code _pose.nbt}（独立的第二个 stat），更新各维姿态与世界时间。
     *
     * <p>这个文件承载"维级标量"——没有坐标，所以进不了区文件；又每帧都在变，所以进不了索引（会把索引的
     * mtime 每帧刷新一次，门控当场失效）。两个变量变化频率不同就该分属不同文件，这是实现期才暴露的约束，
     * 采集侧 {@code VisionBlockEntityStore} 的类注释里有完整推理。
     *
     * <p>姿态文件<b>整份覆盖</b>各维姿态（它本来就装着所有维）——与区文件的"整区替换"是同一个不变量：
     * 手上有一份完整快照时，就用它换掉整片。
     */
    private void pollPose(final Path storeDir) {
        final Path file = storeDir.resolve(POSE_FILE_NAME);
        final FileTime mtime;
        try {
            mtime = Files.getLastModifiedTime(file);
        } catch (IOException e) {
            return; // 还没有姿态文件（新装 / 尚未迁移）→ 保持门控
        }
        if (mtime.equals(lastPoseMtime)) return;

        final CompoundTag root;
        try {
            root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException | RuntimeException e) {
            // v2.48.1：连 RuntimeException 一起接——截断 gzip 流抛的是非受检的 ReportedNbtException。
            LOGGER.warn("[MemoryWorld] Failed to read agent pose {}: {}", file, e.getMessage());
            return; // 半截写 → 保留旧 mtime，下轮重试
        }
        if (root == null) return;
        lastPoseMtime = mtime; // 只在成功读取后才推进

        // 旧版单维文件经 WorldsFile.read 自动包成 overworld 桶 → 姿态字段在旧文件顶层、
        // 恰为该"桶"的顶层，解析路径一致（与 v2.32 相同）。
        final WorldsFile.Result r = WorldsFile.read(root);
        poseByDim.clear();
        dayTimeByDim.clear();
        for (final Map.Entry<String, CompoundTag> e : r.worlds().entrySet()) {
            final AgentPose pose = readPose(e.getValue());
            if (pose != null) poseByDim.put(e.getKey(), pose);
            dayTimeByDim.put(e.getKey(), e.getValue().getLongOr(KEY_WORLD_TIME, -1L));
        }
        servedThisRead.clear();
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

    // ==================== 数据结构 ====================

    private record StoredBlock(String blockId, Map<String, String> state, CompoundTag nbt) {
        String fingerprint() {
            return blockId + "|" + state + "|" + nbt;
        }
    }

    /** agent 视角：眼睛位置（双精度，v2.18）+ 朝向（v2.15）+ 基础视场角（v2.19）。
     *  yaw/pitch 为 NaN、fov 为 {@link #FOV_MISSING} 表示旧文件未记录对应字段。 */
    public record AgentPose(Vec3 pos, float yaw, float pitch, int fov) {}
}
