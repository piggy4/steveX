package com.example.memworld;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 容器内容记忆通道（设计 §5.2.2，v2.28 → v2.29 → v2.32 按维分桶）。
 *
 * <p>与视觉通道（{@link MemoryRestorer} / {@link TerrainRestorer} / {@link EntityRestorer}）
 * 独立的交互内容通道：只读采集侧交互会话提交的"容器/末影箱内容记忆"。容器内容属于 L2 交互层，
 * 绝不混入 L1 视觉 {@code block_entities.nbt}。
 *
 * <p>文件契约（由采集侧写入、本类读取，两段式单写入者）。v2.48 起源是<b>目录</b>
 * {@code containers/}：区文件 + {@code _index.nbt}（门控信号）+ {@code ender.nbt}（维级 / 全局标量）：
 * <pre>{@code
 * containers/
 *   _index.nbt                               // { dimensions: { 维: { 区键: updatedAt } } }
 *   ender.nbt                                // { version: 1, enderInventory: { items: [ … ] } }
 *   minecraft_overworld/
 *     r.0.0.nbt                              // 一个区：
 *       { dimension, regionX, regionZ, updatedAt, version: 1,
 *         containers: {
 *           "x,y,z": { "typeId": "minecraft:chest",      // 方块实体 id（建 BE / loadStatic 用）
 *                      "block": "minecraft:chest",       // 方块注册名
 *                      "state": {"facing":"east","type":"single"},       // 状态属性
 *                      "items": [ {"slot": 0, "item": <ItemStack 编码>}, … ] } } }  // 槽位 0..size-1
 * }</pre>
 *
 * <p><b>末影箱为什么单独一个文件</b>（实现期定案，非 §11.5 原文）：它是<b>玩家态</b>——跨维同一份、
 * 没有坐标，所以进不了区文件；它又只在玩家翻动末影箱时才变，与"哪些区变了"完全无关，所以不该进
 * {@code _index.nbt}——进去只会让索引在末影箱变化时白刷一次 mtime，把区门控搅乱。于是给它自己一份
 * 文件、自己的 mtime 门控。这与采集侧把 agent 姿态单飞成 {@code block_entities/_pose.nbt} 是同一条
 * 判据：<b>变化频率和作用域都不同的东西，分属不同文件</b>。
 *
 * <p><b>v2.48 分片给本通道省下的是"读取 + 解析"</b>，不是 reconcile 本身：每条记录都要过
 * {@link ItemStack#CODEC} 解码（贵），分片前任何一格容器变动都要把<b>所有维的所有容器</b>重解一遍；
 * 分片后只解 {@code updatedAt} 变了的区。{@link #reconcile} 仍按全量覆写走——那是本通道的<b>设计意图</b>
 * （每轮重申权威以捕获延迟放置的 BE 与玩家改动），与省 I/O 无关，不动。
 *
 * <p>item 序列化与 1.21.11 对齐：本版本无 {@code ItemStack.parse/save(registryAccess, tag)} 便捷方法，
 * 用 {@link ItemStack#CODEC} 经 {@code NbtOps} 编解码（采集侧写入侧与记忆侧解析侧保持同一路径）。
 *
 * <p>世界侧语义（§5.2.2 定案 A/B/C/D）：
 * <ul>
 *   <li><b>每条记录是只读权威引用</b>——每轮 reconcile 把记录内容覆写到世界对应容器；玩家改动被还原。</li>
 *   <li>容器按坐标重建：世界为空气 → 用记录 block+state 自足放置并挂 BE；世界方块同记录但缺 BE →
 *       补挂 BE（{@code BlockEntity.loadStatic} 最小 {id,x,y,z}）；世界方块冲突（terrain 视觉胜）→ 跳过并告警。</li>
 *   <li>末影箱是玩家态（§5.2.2 v2.29）：世界任意末影箱都显示同一内容，只能写本地玩家
 *       {@code getEnderChestInventory()}（无按块填充可能）。记录 = 全局 + 只读，v1 接受。</li>
 * </ul>
 *
 * <p>v2.32（世界类型区分，见 docs/世界类型区分与镜像复原设计方案.md §5.2.3）：{@code containers}
 * 按<b>维度</b>分桶读取；每轮 reconcile 只覆写 {@code level.dimension()} 对应维的容器（本通道由
 * {@link MemoryWorldManager} 用活动维 ServerLevel 驱动）——主世界容器坐标永不写下界、反之亦然。
 * 末影箱是<b>玩家态</b>（跨维全局同一份），故仍在<b>文件原始 root 顶层</b>读取（旧版单维文件里
 * 它也在顶层；若从 WorldsFile 包的 overworld 桶里读，旧文件末影段会被错误埋进桶内）。
 */
public class ContainerMemoryApplier {

    private static final Logger LOGGER = LoggerFactory.getLogger("stevex-test/memory");

    private static final String KEY_VERSION = "version";
    private static final String KEY_CONTAINERS = "containers";
    private static final String KEY_ENDER_INVENTORY = "enderInventory";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_ITEM = "item";
    private static final String KEY_SLOT = "slot";
    private static final String KEY_BLOCK = "block";
    private static final String KEY_STATE = "state";
    private static final String KEY_TYPE_ID = "typeId";

    /** v2.48：末影箱文件（玩家态，全局一份，独立门控）。 */
    private static final String ENDER_FILE_NAME = "ender.nbt";

    /** v2.48：索引文件的 mtime（本通道的主门控，同 §7.4）；未变 → 一个区文件都不读。 */
    private FileTime lastIndexMtime;
    /** v2.48：末影箱文件的 mtime（独立门控——它自有变化频率，见类 javadoc）。 */
    private FileTime lastEnderMtime;

    /**
     * v2.48：维 → 区键 → 位置 → 记录。**累积**的镜像（区文件只覆盖自己那一片，故不能整表替换），
     * 每轮 reconcile 据此全量覆写当前维。
     */
    private final Map<String, Map<String, Map<BlockPos, PosRecord>>> byDim = new LinkedHashMap<>();

    /** v2.48：索引 diff 缓存：维 → 区键 → 上次看到的 {@code updatedAt}。相同 ⇒ 该区不需要重读。 */
    private final Map<String, Map<String, Long>> indexCache = new LinkedHashMap<>();

    /** 末影箱是否在记录中（false = 不动玩家末影箱，避免覆写本地已有内容）。 */
    private boolean enderPresent;
    /** 末影箱记录内容（{@link #enderPresent} 为 true 时才是权威快照）。 */
    private List<ItemEntry> enderItems = List.of();

    /** 单次读取代际内已告警过的 key（世界冲突 / 缺 typeId 等），换新内容时清空，避免每轮刷屏。 */
    private final Set<String> warned = new HashSet<>();

    private int ticks;
    private int missingSourceCounter;

    /** v2.38（§7.1 决策 J）：本轮 reconcile 因墓碑 guard 未回放的记录数（只用于一行诊断日志，见 {@link #reconcile}）。 */
    private int tombstoneWithheld;

    /** 服务器（世界）启动 / 切换时调用，清空已应用状态。 */
    public void onServerStart() {
        lastIndexMtime = null;
        lastEnderMtime = null;
        byDim.clear();
        indexCache.clear();
        enderPresent = false;
        enderItems = List.of();
        warned.clear();
        ticks = 0;
        LOGGER.info("[MemoryWorld] Container memory applier ready");
    }

    /** 命令触发：强制重新读取（清索引缓存与两个 mtime 门控 ⇒ 下轮重读全部区文件）。 */
    public void forceRefresh() {
        lastIndexMtime = null;
        lastEnderMtime = null;
        indexCache.clear();
        warned.clear();
    }

    /**
     * 驱动一次轮询：mtime 变化时重读文件；之后（默认）每轮 reconcile 覆写世界，捕获"BE 稍后才由视觉
     * 通道放置 / 玩家改动被还原"等情况。文件缺失时暂停（同 {@link MemoryRestorer}）。
     *
     * <p>v2.32：每轮只 reconcile {@code level.dimension()} 对应维的容器（加全局末影箱）。
     *
     * <p><b>v2.38（§7.1 决策 J）</b>：多一个入参 {@code realityBlocks} —— 采集侧本帧在<b>现实世界</b>观测到的
     * 方块（{@code terrain.blocks}，即 {@code TerrainRestorer.TerrainData#blocks()}）。它只服务一处：
     * 墓碑 guard 的<b>清除条件</b>，即"现实重新观测到该格是记录里的那个容器 ⇒ 记录重新是权威、
     * 恢复正常回放"（{@link RemovalTombstones}）。<b>不能</b>用"当帧 deletions"当 guard：它会当帧自取消，
     * 让两条通道按 poll 周期对打（§7.1 J 的三步推导）。
     *
     * @param realityBlocks 采集侧本帧观测到的现实方块（可为 null = 该帧无视觉数据 → 一律不回放墓碑格）
     */
    public void tick(final ServerLevel level, final Map<BlockPos, TerrainRestorer.TerrainBlock> realityBlocks) {
        final MemoryConfig config = MemoryConfig.get();
        if (ticks++ % Math.max(1, config.pollIntervalTicks) != 0) return;

        final Path storeDir = config.resolveContainerDir();
        if (storeDir == null || !Files.isDirectory(storeDir)) {
            if (missingSourceCounter++ % 30 == 0) {
                LOGGER.warn("[MemoryWorld] Container store directory missing, updates paused (gameDir={}). "
                        + "Set 'containerFile' in config/stevex-test/memory.json.",
                        config.gameDirectory());
            }
            // 存储整个不见了 → 内存镜像也清空（旧版此处 current = FileData.EMPTY），否则残留记录
            // 会继续被覆写进世界。两个门控与索引缓存一并归零，目录重新出现后自然全量重读。
            lastIndexMtime = null;
            lastEnderMtime = null;
            indexCache.clear();
            byDim.clear();
            enderPresent = false;
            enderItems = List.of();
            return;
        }
        missingSourceCounter = 0;

        // 两个独立门控：索引（哪些区变了）+ 末影箱文件（玩家态）。任一有更新都算"内容变了"，
        // 后者不再顺带把前者也刷一遍。
        final boolean regionsChanged = pollRegions(level, storeDir);
        final boolean enderChanged = pollEnder(level, storeDir);
        final boolean changed = regionsChanged || enderChanged;
        if (changed) warned.clear();

        if (!hasAnyRecord()) return;

        // 内容变化 → 必然 reconcile（覆写语义，保证与采集同步）；内容未变但记录存在 →
        // 按配置每轮 reconcile（捕获延迟放置的 BE / 还原玩家改动）。
        if (changed || config.containerReconcileOnPoll) {
            reconcile(level, changed, realityBlocks);
        }
    }

    /** 是否还有任何可覆写的记录（容器或末影箱）——旧版 {@code FileData.isEmpty()} 的等价物。 */
    private boolean hasAnyRecord() {
        if (enderPresent) return true;
        for (final Map<String, Map<BlockPos, PosRecord>> regions : byDim.values()) {
            for (final Map<BlockPos, PosRecord> containers : regions.values()) {
                if (!containers.isEmpty()) return true;
            }
        }
        return false;
    }

    // ==================== 覆写 / 应用 ====================

    /**
     * v2.32：只覆写传入 level（= 活动维，见 {@link MemoryWorldManager}）对应维的容器 + 全局末影箱。
     * 其它维的容器记录留在内存镜像，镜像切回该维时再覆写。
     *
     * <p>v2.48：镜像按区分片存，这里把该维的<b>所有区</b>铺开遍历——reconcile 本就是全量覆写，
     * 分片只影响"记录从哪读进来"，不影响"往世界里写多少"。遍历顺序（区 → 位置）与旧版的插入序不同，
     * 但 {@link #applyPos} 逐格独立，顺序无影响。
     */
    private void reconcile(final ServerLevel level, final boolean warnConflicts,
                           final Map<BlockPos, TerrainRestorer.TerrainBlock> realityBlocks) {
        final String dimension = level.dimension().identifier().toString();
        final Map<String, Map<BlockPos, PosRecord>> regions = byDim.get(dimension);
        if (regions != null && !regions.isEmpty()) {
            tombstoneWithheld = 0;
            for (final Map<BlockPos, PosRecord> containers : regions.values()) {
                for (final Map.Entry<BlockPos, PosRecord> e : containers.entrySet()) {
                    applyPos(level, dimension, e.getKey(), e.getValue(), warnConflicts, realityBlocks);
                }
            }
            if (tombstoneWithheld > 0) {
                // 只在真有 withheld 时打一行（本通道常态是 0；打了就是每 poll 一行噪音）。数字口径 =
                // "记录被减量墓碑挡住、未自足回放"的格数——它与 §7.1 J 要防的箱子闪烁是同一件事的两面。
                LOGGER.info("[MemoryWorld] Container replay withheld [{}]: {} record(s) tombstoned by removal "
                        + "this session (§7.1 J; released when reality re-observes the container)",
                        dimension, tombstoneWithheld);
            }
        }
        if (enderPresent) {
            applyEnder(level, enderItems);
        }
    }

    /**
     * 把一条 per-pos 容器记录覆写到世界。情形分支：
     * <ol>
     *   <li>已有 {@link Container} BE → 直接填充（权威覆写，槽位逐格比对）；</li>
     *   <li>已有 BE 但非 {@link Container}（如末影箱这类不可填充占位）→ 外壳已在，无可填充，不动；</li>
     *   <li>BE 缺失：世界方块与记录相同 → 补挂 BE；世界为空气 → 自足放置记录 block+state
     *       （静默标记，同 §7.9 陷阱 ①）并补挂 BE；世界方块与记录不同 → terrain 视觉胜，跳过。</li>
     * </ol>
     */
    private void applyPos(final ServerLevel level, final String dimension, final BlockPos pos, final PosRecord rec,
                          final boolean warnConflicts,
                          final Map<BlockPos, TerrainRestorer.TerrainBlock> realityBlocks) {
        BlockState worldState = level.getBlockState(pos);
        if (!worldState.isAir()) {
            String worldId = BuiltInRegistries.BLOCK.getKey(worldState.getBlock()).toString();
            if (!worldId.equals(rec.blockId())) {
                if (warnConflicts) {
                    warnOnce("conflict@" + dimension + "/" + pos,
                            "[MemoryWorld] Pos {} [{}]: world block {} != recorded {}; skip (terrain visual wins)",
                            pos, dimension, worldId, rec.blockId());
                }
                return;
            }
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container c) {
            fill(c, rec.items(), dimension + "@" + pos);
            return;
        }
        if (be != null) return; // 非容器 BE 占位（如末影箱）：不可按块填充，外壳已由视觉放置

        if (!worldState.isAir()) {
            attach(level, dimension, pos, worldState, rec);
            return;
        }

        // 世界为空气：自足放置。
        //
        // ⚠ 原注释（v2.28 起，v2.38 §13.6 认定为**错误**）曾写作"自建块只可能是非实心容器，不是 DELETION
        //   候选，不会与减量冲突"。两处都不成立：
        //   ① 箱子族**就是**非满形状块（`ChestBlock.getShape` = Block.column(14,0,14)）；
        //   ② v2.38 批 2（§14.4）把箱子族 / 告示牌 / 床等 148 个方块加进信号缺失表后，它们**会被减量
        //      通道判删**（在此之前是"在任何段之外 ⇒ 不产生 deletions"的偶然安全，不是设计保证）。
        //   故这里必须有 guard，见下。
        //
        // v2.38（§7.1 决策 J）**墓碑 guard**：本会话减量通道实删过该格 ⇒ 不在空气位回放记录，否则
        //   两条通道按 poll 周期对打（镜像里箱子闪烁）。清除条件 = 现实重新观测到该格是记录里的那个
        //   容器（{@code terrain.blocks} 的 blockId 与记录一致）⇒ 记录重新是权威、恢复正常回放。
        //   这是"假阳性之后玩家又把箱子建回来"的恢复路径，也是冷启动语义不受影响的原因（墓碑为空 ⇒
        //   本 guard 恒不触发）。
        if (RemovalTombstones.get().contains(dimension, pos)) {
            final TerrainRestorer.TerrainBlock reality = realityBlocks == null ? null : realityBlocks.get(pos);
            if (reality == null || !reality.blockId().equals(rec.blockId())) {
                tombstoneWithheld++;
                return;
            }
            RemovalTombstones.get().clear(dimension, pos);
        }
        if (rec.blockId().isBlank()) {
            warnOnce("noBlock@" + dimension + "/" + pos,
                    "[MemoryWorld] Pos {} [{}]: air & record without block; skip", pos, dimension);
            return;
        }
        BlockState state = BlockStateUtil.fromSaved(rec.blockId(), rec.state());
        if (state.isAir()) {
            warnOnce("airState@" + dimension + "/" + pos,
                    "[MemoryWorld] Pos {} [{}]: cannot rebuild block {} state; skip",
                    pos, dimension, rec.blockId());
            return;
        }
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
        attach(level, dimension, pos, state, rec);
    }

    /** 补挂 BE（调用处保证 BE 缺失）：用记录 typeId（缺则按方块反查）loadStatic 最小 nbt，成功后填充。 */
    private void attach(final ServerLevel level, final String dimension, final BlockPos pos, final BlockState state,
                        final PosRecord rec) {
        String typeId = rec.typeId();
        if (typeId.isBlank()) typeId = beTypeIdFor(state);
        if (typeId == null) {
            warnOnce("noType@" + dimension + "/" + pos,
                    "[MemoryWorld] Pos {} [{}]: cannot attach BE for block {} (no typeId / BE type); skip",
                    pos, dimension, rec.blockId());
            return;
        }
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", typeId);
        nbt.putInt("x", pos.getX());
        nbt.putInt("y", pos.getY());
        nbt.putInt("z", pos.getZ());
        BlockEntity be = BlockEntity.loadStatic(pos, state, nbt, level.registryAccess());
        if (be == null) {
            warnOnce("attachFail@" + dimension + "/" + pos,
                    "[MemoryWorld] Pos {} [{}]: BlockEntity.loadStatic failed (type {}); skip",
                    pos, dimension, typeId);
            return;
        }
        be.setLevel(level);
        level.setBlockEntity(be);
        if (be instanceof Container c) {
            fill(c, rec.items(), dimension + "@" + pos);
        }
    }

    /** 按方块状态反查它的方块实体注册 id（typeId 缺失时兜底，遍历 BE 注册表匹配 validBlocks）。 */
    private static String beTypeIdFor(final BlockState state) {
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            if (type.isValid(state)) {
                Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
                return id == null ? null : id.toString();
            }
        }
        return null;
    }

    /**
     * 把记录槽位覆写到容器。逐格比对，仅写差异格（避免无谓 setChanged 刷 dirty）。
     * 记录范围外的槽位一律清空（记录 = 该容器内容的完整权威快照）。
     */
    private void fill(final Container c, final List<ItemEntry> items, final String where) {
        int size = c.getContainerSize();
        Map<Integer, ItemStack> expect = new HashMap<>();
        for (ItemEntry e : items) {
            if (e.slot() >= 0 && e.slot() < size && !e.stack().isEmpty()) {
                expect.put(e.slot(), e.stack());
            }
        }
        boolean changed = false;
        for (int i = 0; i < size; i++) {
            ItemStack want = expect.get(i);
            ItemStack got = c.getItem(i);
            ItemStack wantOrEmpty = want == null ? ItemStack.EMPTY : want;
            if (!sameStack(got, wantOrEmpty)) {
                c.setItem(i, wantOrEmpty.copy());
                changed = true;
            }
        }
        if (changed) {
            c.setChanged();
            LOGGER.info("[MemoryWorld] Container filled at {}", where);
        }
    }

    /** 末影箱 = 玩家态（§5.2.2 v2.29）：写入本地玩家的末影箱清单。无玩家时本轮跳过（下轮再试）。 */
    private void applyEnder(final ServerLevel level, final List<ItemEntry> items) {
        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        fill(players.get(0).getEnderChestInventory(), items, "player ender inventory");
    }

    private static boolean sameStack(final ItemStack a, final ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        return a.getCount() == b.getCount() && ItemStack.isSameItemSameComponents(a, b);
    }

    // ==================== 轮询：索引与末影箱 ====================

    /**
     * v2.48：轮询 {@code _index.nbt}（本通道的主 stat），只重读 {@code updatedAt} 变了的区文件。
     *
     * <p>门控形状与旧版一致——一轮一次 stat、mtime 未变就什么都不做，只是 stat 的对象从
     * {@code containers.nbt} 换成了索引。读失败 / 空索引不推进 {@link #lastIndexMtime}，下轮重试。
     *
     * @return 是否读进了新内容（旧版的 {@code changed}：文件变了就当 true，用于决定是否强制 reconcile）
     */
    private boolean pollRegions(final ServerLevel level, final Path storeDir) {
        final Path idx = storeDir.resolve(VisionRegions.INDEX_FILE_NAME);
        final FileTime mtime;
        try {
            mtime = Files.getLastModifiedTime(idx);
        } catch (IOException e) {
            return false; // 索引还没出现（或目录刚被清空）→ 保持门控，出现时自然触发
        }
        if (mtime.equals(lastIndexMtime)) return false;

        final Map<String, Map<String, Long>> fresh = VisionRegions.readIndex(storeDir);
        if (fresh.isEmpty()) return false; // 半截写 / 损坏 → 保留旧 mtime，下轮重试
        lastIndexMtime = mtime; // 只在成功读取后才推进

        int reread = 0;
        int failed = 0;
        int dropped = 0;
        for (final Map.Entry<String, Map<String, Long>> de : fresh.entrySet()) {
            final String dim = de.getKey();
            final String dimDirName = VisionRegions.dimDirName(dim);
            final Map<String, Map<BlockPos, PosRecord>> mirrored =
                    byDim.computeIfAbsent(dim, k -> new LinkedHashMap<>());
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
                mirrored.put(regionKey, readRegionContainers(root, level.registryAccess()));
                cached.put(regionKey, re.getValue());
                reread++;
            }

            // 本地有、新索引里没有的区：采集端把它删了（容器被清空的区会被整区摘掉，见 §11.11 的
            // ⑤ 剪枝）。**必须连镜像一并摘掉**——否则"记录已被删除"这件事永远到不了世界，
            // 该容器会被每一轮 reconcile 重新覆写回去。这是 ⑤ 与 ④ 的关键差别：④ 没有删除通道，
            // 所以它只摘索引缓存；⑤ 有，所以它必须一路摘到数据。
            final List<String> gone = new ArrayList<>();
            for (final String regionKey : cached.keySet()) {
                if (!de.getValue().containsKey(regionKey)) gone.add(regionKey);
            }
            for (final String regionKey : gone) {
                cached.remove(regionKey);
                mirrored.remove(regionKey);
                dropped++;
            }
        }

        if (failed > 0) {
            LOGGER.warn("[MemoryWorld] {} container region file(s) unreadable this poll — will retry", failed);
        } else if (reread > 0 || dropped > 0) {
            LOGGER.info("[MemoryWorld] Container index changed: re-read {} region(s), {} dropped", reread, dropped);
        }
        // reread == 0 但 dropped > 0 也算内容变了（记录被删是内容变更），故两者取或。
        return reread > 0 || dropped > 0;
    }

    /** 区文件 → 该区的容器记录表（位置键由采集端用 {@code "x,y,z"} 写入）。 */
    private Map<BlockPos, PosRecord> readRegionContainers(final CompoundTag regionRoot,
                                                          final HolderLookup.Provider registries) {
        final Map<BlockPos, PosRecord> containers = new LinkedHashMap<>();
        final CompoundTag containersTag = regionRoot.getCompoundOrEmpty(KEY_CONTAINERS);
        for (final String key : containersTag.keySet()) {
            final BlockPos pos = VisionRegions.parsePosKey(key);
            if (pos == null) continue;
            final CompoundTag entry = containersTag.getCompoundOrEmpty(key);
            containers.put(pos, new PosRecord(
                    entry.getStringOr(KEY_TYPE_ID, ""),
                    entry.getStringOr(KEY_BLOCK, ""),
                    readState(entry.getCompoundOrEmpty(KEY_STATE)),
                    readItems(entry.getListOrEmpty(KEY_ITEMS), registries)));
        }
        return containers;
    }

    /**
     * v2.48：轮询 {@code ender.nbt}（独立的第二个 stat），更新末影箱玩家态记录。
     *
     * <p>末影箱从<b>原始 root 顶层</b>读，<b>不经</b> {@link WorldsFile#read}——那条路会把整份 root 包成
     * overworld 桶，把末影段埋进桶内；而采集侧 load() 写的时候就是从原始 root 顶层取的，两侧对称。
     * 旧/新格式在此处位置相同（旧文件在 root 顶层，{@code ender.nbt} 也在 root 顶层），故不需要 legacy
     * 兼容分支——与 per-pos 容器不同，后者才依赖 {@code worlds} 分桶的回退。
     *
     * <p>文件缺席 → {@link #enderPresent} 置 false（= 不动玩家末影箱）。这与旧版"整个文件不见了 →
     * {@code FileData.EMPTY} → 不覆写"一致：文件在 = 记录是权威快照（哪怕物品表为空），文件不在 =
     * 本通道对此无话可说。
     *
     * @return 是否读进了新内容
     */
    private boolean pollEnder(final ServerLevel level, final Path storeDir) {
        final Path file = storeDir.resolve(ENDER_FILE_NAME);
        final FileTime mtime;
        try {
            mtime = Files.getLastModifiedTime(file);
        } catch (IOException e) {
            if (enderPresent) {
                enderPresent = false; // 文件被删 → 撤回权威，不再覆写
                enderItems = List.of();
                lastEnderMtime = null;
                return true;
            }
            return false;
        }
        if (mtime.equals(lastEnderMtime)) return false;

        final CompoundTag root;
        try {
            root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException | RuntimeException e) {
            // v2.48.1：连 RuntimeException 一起接——截断 gzip 流抛的是非受检的 ReportedNbtException。
            LOGGER.warn("[MemoryWorld] Failed to read ender chest record {}: {}", file, e.getMessage());
            return false; // 半截写 → 保留旧 mtime，下轮重试
        }
        if (root == null) return false;
        lastEnderMtime = mtime; // 只在成功读取后才推进

        final CompoundTag ender = root.getCompoundOrEmpty(KEY_ENDER_INVENTORY);
        enderPresent = !ender.isEmpty() && ender.contains(KEY_ITEMS);
        enderItems = enderPresent ? readItems(ender.getListOrEmpty(KEY_ITEMS), level.registryAccess()) : List.of();
        return true;
    }

    /**
     * 逐条解析物品列表：{slot, item} → 槽位 + {@link ItemStack}（item 缺失 / 无法解析的条目置空并告警一次）。
     * item 用 {@link ItemStack#CODEC} + {@code NbtOps} 解析，与 1.21.11 采集侧写入路径对称。
     */
    private List<ItemEntry> readItems(final ListTag list, final HolderLookup.Provider registries) {
        List<ItemEntry> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i).orElse(null);
            if (e == null) continue;
            int slot = e.getIntOr(KEY_SLOT, -1);
            if (slot < 0) continue;
            CompoundTag itemTag = e.getCompoundOrEmpty(KEY_ITEM);
            if (itemTag.isEmpty()) continue;
            ItemStack stack = parseItem(itemTag, registries);
            if (stack.isEmpty()) {
                warnOnce("badItem@" + slot + "#" + itemTag.hashCode(),
                        "[MemoryWorld] Unparsable item at slot {}: {}", slot, brief(itemTag));
            }
            out.add(new ItemEntry(slot, stack));
        }
        return out;
    }

    private static ItemStack parseItem(final CompoundTag tag, final HolderLookup.Provider registries) {
        try {
            return ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                    .resultOrPartial(err -> LOGGER.warn("[MemoryWorld] Item decode error: {}", err))
                    .orElse(ItemStack.EMPTY);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    private static Map<String, String> readState(final CompoundTag stateTag) {
        Map<String, String> state = new LinkedHashMap<>();
        for (String k : stateTag.keySet()) {
            state.put(k, stateTag.getStringOr(k, ""));
        }
        return state;
    }

    private static String brief(final CompoundTag tag) {
        String s = tag.toString();
        return s.length() <= 100 ? s : s.substring(0, 100) + "…";
    }

    private void warnOnce(final String key, final String fmt, final Object... args) {
        if (warned.add(key)) {
            LOGGER.warn(fmt, args);
        }
    }

    // ==================== 数据结构 ====================

    /** 一个槽位的记录：全局槽位 0..getContainerSize()-1 + 已解析好的 ItemStack（解析失败为 EMPTY）。 */
    private record ItemEntry(int slot, ItemStack stack) {}

    /** 一条 per-pos 容器记录：BE id（建/挂 BE）+ 方块 + 状态 + 槽位内容。 */
    private record PosRecord(String typeId, String blockId, Map<String, String> state, List<ItemEntry> items) {}
}
