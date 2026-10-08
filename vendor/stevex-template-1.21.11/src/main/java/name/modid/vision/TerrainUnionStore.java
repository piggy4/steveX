package name.modid.vision;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;

/**
 * v2.47（累积观测文件，见 docs/累积观测文件设计方案.md）：只给 agent 读的<b>累积</b>地形数据。
 *
 * <p>与 {@link VisionTerrainStore}（每次整体覆写当前维 = 瞬时快照）的差别只有一条语义：本 store 是
 * <b>跨时间的并集</b>——本帧看得见的方块并入，被判据证明消失的方块移除，其余原样留下。于是 agent
 * 离开一处之后仍能读到"那儿有什么"，这正是"让 agent 读到累积记忆"的落点（设计 §1.1）。
 *
 * <p>三条约束（全部来自设计 §4.2，无一条是本类自创）：
 * <ol>
 *   <li><b>先移除、后并入</b>：并集是"当前状态的投影"而非"历史全集"（决策 A，用户 2026-10-07）。
 *       次序反了会让"本帧可见却也在删除清单里"的格复活。</li>
 *   <li><b>可见守卫</b>：本帧可见的格<b>不许删</b>——与记忆端 {@code DeletionApplier} 逐条同规则
 *       （{@code DeletionApplier.java:125-129}：{@code currentTerrain.contains(pos) → skip}）。
 *       两端同规则、各自独立执行，故结果可能漂移；这是结构性的，不是缺陷（设计 §6.1）。</li>
 *   <li><b>不参与删除裁决</b>：本 store 只<b>消费</b> {@code deletions} / {@code signalLossDeletions}，
 *       两份清单由 {@code DeletionJudge} / {@code SignalLossCorrector} 产出——判据与格式一个字不动
 *       （设计 §8）。</li>
 * </ol>
 *
 * <p><b>v2.48：region 分片</b>（设计 §11，用户 2026-10-07 定案）。分片前是单文件
 * {@code terrain_union.nbt}；分片后：
 * <pre>{@code
 * stevex/vision/terrain_union/
 *   _index.nbt                 // 维 → 区键 → updatedAt（只服务 agent；记忆端不读本 store）
 *   minecraft_overworld/
 *     r.0.0.nbt                // { dimension, regionX, regionZ, updatedAt,
 *                              //   blocks: { "-41,62,31": {block, state}, ... } }
 * }</pre>
 *
 * <p>区文件里 {@code blocks} 的键值形态与 {@code terrain.nbt} <b>逐字同形</b>——posKey / state 属性表
 * 的构造直接复用 {@link VisionTerrainStore} 的同一份实现（不是"照着写一遍"，那样迟早会分叉），
 * agent 侧一套解析代码可读两份数据。
 *
 * <p><b>分片顺带治好了决策 D 的一处代价</b>：{@link #publish} 原先把<b>整个世界</b>的并集浅拷贝一份
 * （10 万格量级 = 一次全量 map 拷贝，在渲染线程上），只为了让后台线程能安全落盘。分片后只要拷贝
 * <b>本次变更的那几个区</b>——渲染线程的成本从 O(整个世界) 降到 O(本次改动)，与"这帧其实只动了几格"
 * 成正比。
 *
 * <p>写盘仍走 {@link UnionSaveScheduler}（限频 + 后台线程 + 原子写，设计 §4.5 决策 D）。本类不新增任何
 * WebSocket API、不触记忆端（设计 §8）。
 */
public class TerrainUnionStore {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR_NAME = "stevex/vision";
    /** v2.48：分片后的 store 目录名（原单文件名去掉 {@code .nbt}）。 */
    private static final String STORE_DIR_NAME = "terrain_union";
    /** v2.48：分片前的单文件（构造时若存在则拆分迁移并退役）。 */
    private static final String LEGACY_FILE_NAME = "terrain_union.nbt";
    private static final String KEY_BLOCKS = "blocks";

    private final Path storeDir;
    private final Path legacyFilePath;
    private final UnionSaveScheduler scheduler;

    /**
     * 分维、分区的并集镜像：维 id → 区键 → 该区的（posKey → 方块条目 tag）。
     *
     * <p><b>只在渲染线程变更</b>（{@link #sync} 由 {@code ObjectResolver.resolve} 调用）。条目一经放入
     * 便<b>不再被修改</b>——每次更新都是整体替换成新 tag，所以发布给后台线程的浅拷贝不会与渲染线程竞态。
     */
    private final Map<String, Map<String, RegionBucket>> byDim = new LinkedHashMap<>();

    /** v2.48：索引镜像（维 → 区键 → updatedAt）。区文件是权威，本表是它的派生，落盘时就地更新。 */
    private final Map<String, Map<String, Long>> index = new LinkedHashMap<>();

    /**
     * 待落盘快照：维 → 区键 → 该区的冻结副本。渲染线程 {@link #publish} 合并写入，后台线程
     * {@link #flush} 整体取走。
     *
     * <p>用 {@link AtomicReference} 的 CAS 而非普通的 volatile 字段：两个线程都会<b>合并</b>式地推进它
     * （渲染线程并入新变更、后台线程写失败时把快照并回）。分片前不存在这个问题——那时每次 publish 都
     * 是全量拷贝，任一份快照都是"整个世界"，丢哪一份都无所谓；现在一份快照只覆盖几个区，丢了就是真丢，
     * 而 union 丢一条<b>删除</b>记录会留下永久幽灵（该格此后不再进记忆端镜像 ⇒ 永不重判）。
     */
    private final AtomicReference<Map<String, Map<String, RegionSnapshot>>> pending = new AtomicReference<>(Map.of());

    public TerrainUnionStore(final UnionSaveScheduler scheduler) {
        this.scheduler = scheduler;
        scheduler.registerWriter(this::flush);
        final Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve(DIR_NAME);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("[Vision] Failed to create directory {}: {}", dir, e.getMessage());
        }
        this.storeDir = dir.resolve(STORE_DIR_NAME);
        this.legacyFilePath = dir.resolve(LEGACY_FILE_NAME);
        loadExisting();
        migrateLegacyIfPresent();
    }

    /**
     * 把本帧的可见方块并入当前维的并集，并按删除清单<b>带可见守卫地</b>移除。
     *
     * @param blocks 本帧可见方块（key = 方块坐标，含半透明与几何段补采的全部来源，即 {@code terrain.nbt}
     *        的同一份输入）
     * @param deletions 被证明消失的记忆格（{@link DeletionJudge} 产出；三段合并后的总表）
     * @param signalLossDeletions 被证明消失的信号缺失族格（{@code SignalLossCorrector} 状态直读产出）
     * @param dimensionId 本帧所在维 id，决定并入/删除哪个维
     * @return 统计 { "added", "updated", "removed", "total" }
     */
    public Map<String, Object> sync(
            final Map<BlockPos, VisionCollector.TerrainBlockSnapshot> blocks,
            final List<BlockPos> deletions,
            final List<BlockPos> signalLossDeletions,
            final String dimensionId
    ) {
        final Map<String, RegionBucket> regions = byDim.computeIfAbsent(dimensionId, k -> new LinkedHashMap<>());
        final Set<BlockPos> visible = blocks.keySet();
        final Set<String> touched = new LinkedHashSet<>();

        // ① 先移除：被判删的格——带可见守卫（与记忆端 DeletionApplier.java:125-129 同规则）。
        //    两条通道各判各的，此处合并处理：对"这格还在不在"这一事实而言两者等价（同 v2.38 决策 H
        //    对持久层修剪的处理），守卫规则也完全相同（都只是"本帧可见则不许删"）。
        int removed = 0;
        removed += remove(deletions, visible, regions, touched);
        removed += remove(signalLossDeletions, visible, regions, touched);

        // ② 后并入：本帧可见方块。内容不变则不替换——既省内存，也让"条件写"（③）只在真有变化时触发。
        int added = 0;
        int updated = 0;
        for (Map.Entry<BlockPos, VisionCollector.TerrainBlockSnapshot> e : blocks.entrySet()) {
            final String key = VisionTerrainStore.posKey(e.getKey());
            final String regionKey = VisionRegions.regionKeyOfBlock(e.getKey());
            final RegionBucket bucket = regions.get(regionKey);
            final CompoundTag old = bucket == null ? null : bucket.blocks.get(key);
            final CompoundTag candidate = blockEntry(e.getValue());
            if (old == null) {
                regions.computeIfAbsent(regionKey, k -> new RegionBucket()).blocks.put(key, candidate);
                touched.add(regionKey);
                added++;
            } else if (!old.equals(candidate)) {
                bucket.blocks.put(key, candidate);
                touched.add(regionKey);
                updated++;
            }
        }

        // ③ 条件写：无变化则不置脏（重复看到同一批方块是常态，不该每帧写盘）。
        if (added + updated + removed > 0) {
            publish(dimensionId, touched);
            scheduler.requestSave();
        }

        int total = 0;
        for (RegionBucket b : regions.values()) total += b.blocks.size();
        return Map.of("added", added, "updated", updated, "removed", removed, "total", total);
    }

    // ==================== 内部 ====================

    /** 带可见守卫地移除一格批（两条删除通道共用；见 {@link #sync} 注释 ①）。 */
    private static int remove(final List<BlockPos> positions, final Set<BlockPos> visible,
                              final Map<String, RegionBucket> regions, final Set<String> touched) {
        int removed = 0;
        for (BlockPos pos : positions) {
            if (visible.contains(pos)) continue;
            final String regionKey = VisionRegions.regionKeyOfBlock(pos);
            final RegionBucket bucket = regions.get(regionKey);
            if (bucket != null && bucket.blocks.remove(VisionTerrainStore.posKey(pos)) != null) {
                touched.add(regionKey);
                removed++;
            }
        }
        return removed;
    }

    /** 一个区的并集镜像 + 该区最后一次写盘的墙钟毫秒。 */
    private static final class RegionBucket {
        final Map<String, CompoundTag> blocks = new LinkedHashMap<>();
        long updatedAt;
    }

    /** 方块条目 tag：与 {@code terrain.nbt} 的同名键逐字同形（见类 javadoc）。 */
    private static CompoundTag blockEntry(final VisionCollector.TerrainBlockSnapshot snapshot) {
        final CompoundTag entry = new CompoundTag();
        entry.putString(VisionTerrainStore.KEY_BLOCK, snapshot.blockId());
        entry.put(VisionTerrainStore.KEY_STATE, VisionTerrainStore.propsToNbt(snapshot.stateProps()));
        return entry;
    }

    /**
     * 渲染线程：把本次变更的区做<b>浅拷贝</b>并发布（设计 §4.5 决策 D 的第 1 级）。
     *
     * <p>浅拷贝是安全的：区里的条目 tag 自放入起不再被修改（{@link #sync} 只做整体替换），故后台线程
     * 读到的每个 tag 都是"已冻结"的；唯一会变的是区的 key 映射本身，而它已被复制。
     * <b>只拷贝本次变更的区</b>——这是分片带来的直接收益（见类 javadoc）。
     */
    private void publish(final String dimensionId, final Set<String> touchedRegions) {
        final Map<String, RegionBucket> regions = byDim.get(dimensionId);
        if (regions == null || touchedRegions.isEmpty()) return;
        final long now = System.currentTimeMillis();
        final Map<String, RegionSnapshot> fresh = new LinkedHashMap<>();
        for (String regionKey : touchedRegions) {
            final int[] rxrz = VisionRegions.parseRegionKey(regionKey);
            final RegionBucket bucket = regions.get(regionKey);
            if (rxrz == null || bucket == null) continue;
            bucket.updatedAt = now;
            fresh.put(regionKey, new RegionSnapshot(rxrz[0], rxrz[1], now, new LinkedHashMap<>(bucket.blocks)));
        }
        if (fresh.isEmpty()) return;
        // 并入而非替换：可能还有上一轮 publish 了、但尚未被 flush 取走的区（限频窗口内换维就会发生）。
        pending.updateAndGet(prev -> merge(prev, dimensionId, fresh));
    }

    /**
     * 后台线程（或关停线程）：把快照落盘。{@code synchronized} 是因为二者可能并发——虽然本方法只写
     * 自己的区文件、不共享 {@code .tmp}（每个区文件名不同），但索引是共用的，且失败的并回必须串行。
     */
    synchronized void flush() {
        final Map<String, Map<String, RegionSnapshot>> snap = pending.getAndSet(Map.of());
        if (snap.isEmpty()) return;
        try {
            final List<VisionRegions.RegionWrite> writes = new ArrayList<>();
            for (Map.Entry<String, Map<String, RegionSnapshot>> de : snap.entrySet()) {
                for (RegionSnapshot rs : de.getValue().values()) {
                    final CompoundTag blocksTag = new CompoundTag();
                    rs.blocks().forEach(blocksTag::put);
                    writes.add(new VisionRegions.RegionWrite(de.getKey(), rs.regionX(), rs.regionZ(),
                            VisionRegions.regionRoot(de.getKey(), rs.regionX(), rs.regionZ(), rs.updatedAt(),
                                    KEY_BLOCKS, blocksTag)));
                }
            }
            VisionRegions.commit(storeDir, writes, index);
            LOGGER.info("[Vision] Saved terrain union: {} block(s) over {} region file(s) of {} dimension(s) → {}",
                    totalBlocks(), writes.size(), snap.size(), storeDir);
        } catch (IOException e) {
            LOGGER.warn("[Vision] Failed to save terrain union {}: {}", storeDir, e.getMessage());
            // 并回待写：否则这次失败的内容要等到下一次采集**且再次变更**才会重试，而删除记录一旦丢失
            // 就是永久幽灵（不再进记忆端镜像 ⇒ 永不重判）。已有快照里的同名区更新，故只补空缺。
            pending.updateAndGet(prev -> mergeBack(snap, prev));
        }
    }

    /** 把 {@code fresh} 的区并入 {@code base}（同区键覆盖：新发布的更晚，取新的），返回新表——不改动入参。 */
    private static Map<String, Map<String, RegionSnapshot>> merge(
            final Map<String, Map<String, RegionSnapshot>> base,
            final String dimensionId,
            final Map<String, RegionSnapshot> fresh
    ) {
        final Map<String, Map<String, RegionSnapshot>> next = new LinkedHashMap<>();
        base.forEach((dim, m) -> next.put(dim, new LinkedHashMap<>(m)));
        next.computeIfAbsent(dimensionId, k -> new LinkedHashMap<>()).putAll(fresh);
        return next;
    }

    /** 写失败后把 {@code stale} 并回待写表：同区以 {@code current}（更新）为准，{@code stale} 只补空缺。 */
    private static Map<String, Map<String, RegionSnapshot>> mergeBack(
            final Map<String, Map<String, RegionSnapshot>> stale,
            final Map<String, Map<String, RegionSnapshot>> current
    ) {
        final Map<String, Map<String, RegionSnapshot>> next = new LinkedHashMap<>();
        stale.forEach((dim, m) -> next.put(dim, new LinkedHashMap<>(m)));
        current.forEach((dim, m) -> next.computeIfAbsent(dim, k -> new LinkedHashMap<>()).putAll(m));
        return next;
    }

    private int totalBlocks() {
        int total = 0;
        for (Map<String, RegionBucket> regions : byDim.values()) {
            for (RegionBucket b : regions.values()) total += b.blocks.size();
        }
        return total;
    }

    /** 构造时读入既有区文件 → 分维分区镜像（跨会话累积）。 */
    private void loadExisting() {
        int files = 0;
        for (Path dimDir : VisionRegions.listDimDirs(storeDir)) {
            final String dirName = dimDir.getFileName().toString();
            for (Path file : VisionRegions.listRegionFiles(dimDir)) {
                final int[] rxrz = VisionRegions.parseRegionFileName(file.getFileName().toString());
                if (rxrz == null) continue;
                final CompoundTag root = VisionRegions.readRegion(file, dirName, rxrz[0], rxrz[1]);
                if (root == null) continue;
                final String dim = root.getStringOr(VisionRegions.KEY_DIMENSION, "");
                final RegionBucket bucket = new RegionBucket();
                final CompoundTag blocksTag = root.getCompoundOrEmpty(KEY_BLOCKS);
                for (String key : blocksTag.keySet()) {
                    bucket.blocks.put(key, blocksTag.getCompoundOrEmpty(key));
                }
                bucket.updatedAt = VisionRegions.updatedAtOf(root);
                byDim.computeIfAbsent(dim, k -> new LinkedHashMap<>())
                        .put(VisionRegions.regionKey(rxrz[0], rxrz[1]), bucket);
                files++;
            }
        }
        if (files > 0) {
            LOGGER.info("[Vision] Loaded terrain union: {} block(s) over {} region file(s) of {} dimension(s) from {}",
                    totalBlocks(), files, byDim.size(), storeDir);
        }
        rebuildIndexIfNeeded();
    }

    /**
     * 索引是<b>派生</b>：从区文件各自的 {@code updatedAt} 重建；与盘上索引不符才补写（自愈）。
     * 本 store 的索引<b>只服务 agent</b>——记忆端不读 ⑧（§11.13 第 3 项）。
     */
    private void rebuildIndexIfNeeded() {
        final Map<String, Map<String, Long>> rebuilt = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, RegionBucket>> de : byDim.entrySet()) {
            final Map<String, Long> m = new LinkedHashMap<>();
            de.getValue().forEach((regionKey, bucket) -> m.put(regionKey, bucket.updatedAt));
            rebuilt.put(de.getKey(), m);
        }
        index.putAll(rebuilt);
        final Map<String, Map<String, Long>> onDisk = VisionRegions.readIndex(storeDir);
        if (!VisionRegions.indexEquals(rebuilt, onDisk)) {
            try {
                VisionRegions.writeIndex(storeDir, index);
                LOGGER.info("[Vision] Region index rebuilt from region files → {}", storeDir);
            } catch (IOException e) {
                LOGGER.warn("[Vision] Failed to rebuild region index {}: {}", storeDir, e.getMessage());
            }
        }
    }

    /**
     * 分片前单文件的拆分迁移（§11.13 第 4 项）：读旧 {@code terrain_union.nbt} → 按区拆写 → 退役旧文件。
     *
     * <p>与 {@link #loadExisting} 是<b>并集</b>关系而非二选一：迁到一半被杀进程，下次启动能接着迁完
     * （重复并入幂等）。迁完才退役旧文件，顺序不可反。
     */
    private void migrateLegacyIfPresent() {
        if (!Files.isRegularFile(legacyFilePath)) return;
        try {
            final CompoundTag root = NbtIo.readCompressed(legacyFilePath, NbtAccounter.unlimitedHeap());
            if (root == null) return;
            final long now = System.currentTimeMillis();
            final List<VisionRegions.RegionWrite> writes = new ArrayList<>();

            for (Map.Entry<String, CompoundTag> de : WorldsFile.read(root).worlds().entrySet()) {
                final String dim = de.getKey();
                final Map<String, RegionBucket> regions = byDim.computeIfAbsent(dim, k -> new LinkedHashMap<>());
                final CompoundTag blocksTag = de.getValue().getCompoundOrEmpty(KEY_BLOCKS);
                for (String key : blocksTag.keySet()) {
                    final BlockPos pos = VisionRegions.parsePosKey(key);
                    if (pos == null) continue;
                    final RegionBucket rb = regions
                            .computeIfAbsent(VisionRegions.regionKeyOfBlock(pos), k -> new RegionBucket());
                    rb.blocks.put(key, blocksTag.getCompoundOrEmpty(key));
                    rb.updatedAt = now;
                }
                for (Map.Entry<String, RegionBucket> re : regions.entrySet()) {
                    final int[] rxrz = VisionRegions.parseRegionKey(re.getKey());
                    if (rxrz == null) continue;
                    final CompoundTag tag = new CompoundTag();
                    re.getValue().blocks.forEach(tag::put);
                    writes.add(new VisionRegions.RegionWrite(dim, rxrz[0], rxrz[1],
                            VisionRegions.regionRoot(dim, rxrz[0], rxrz[1], now, KEY_BLOCKS, tag)));
                }
            }

            VisionRegions.commit(storeDir, writes, index);
            LOGGER.info("[Vision] Migrated legacy {} → {} region file(s) under {}",
                    LEGACY_FILE_NAME, writes.size(), storeDir);
            VisionRegions.retireLegacyFile(legacyFilePath);
        } catch (Exception e) {
            // 迁移失败不阻塞启动：旧文件保持原地（未退役），下次启动重试；本次以内存里的部分并集继续。
            LOGGER.warn("[Vision] Failed to migrate legacy {}: {}", LEGACY_FILE_NAME, e.getMessage());
        }
    }

    /** 待落盘的不可变快照：一个区的位置、写盘时刻、以及该区并集的浅拷贝。 */
    private record RegionSnapshot(int regionX, int regionZ, long updatedAt, Map<String, CompoundTag> blocks) {
    }
}
