package name.modid.vision;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

/**
 * v2.47（累积观测文件，见 docs/累积观测文件设计方案.md）：只给 agent 读的<b>累积</b>实体文件
 * {@code entities_union.nbt}。
 *
 * <p>与 {@link VisionEntityStore}（每次整体覆写当前维 = 瞬时快照）的差别只在<b>移除时机</b>：快照里
 * 实体一离开视野就没了，而并集要等到"它真的不在了"才移除——这正是 agent 需要的"离开后还记得"
 * （设计 §1.1）。移出视野只是停止更新，条目原样留着。
 *
 * <p><b>移除判据不是本类发明的，是复用记忆端已经在跑的那一条</b>（设计 §4.3）：记忆端
 * {@code EntityRestorer.allCellsEmpty} 判"该实体的<b>全部 AABB 占用格</b>是否都已被证明为空"，
 * 采集端逐位复现同一判据：
 * <pre>
 *   记忆端：EntityRestorer.allCellsEmpty(dim, uuid, provenEmpty)      // EntityRestorer.java:227-244
 *           provenEmpty = entityDeletions ∪ {相机格}                   // DeletionApplier.java:187-208
 *   本类  ：每格 ∈ {@link #provenEmpty} ？
 * </pre>
 * 之所以能"逐位一致"而不只是"接近"：两边算的是<b>同一帧、同一枚举口径</b>的同一个集合。实体的
 * AABB 格枚举口径（{@code floor(min)..floor(max)} 三轴闭区间）在代码里已经统一——记忆端写候选
 * （{@code MemoryCellReporter} 实体段）、采集端双保险（{@link ObjectResolver#visibleEntityCells}）、
 * 记忆端判全空（{@code allCellsEmpty}）三处同口径，本类经
 * {@link ObjectResolver#forEachBoxCell} 复用采集端那一份实现。
 *
 * <p>本类不消费 {@code deletions} / {@code signalLossDeletions}——那两条通道证明的是"这格没有方块"，
 * 与"这格有没有实体"正交（{@link VisionTerrainStore} 的 KEY_ENTITY_DELETIONS 注释详述了这条）。
 *
 * <p>文件格式（设计 §3.3）：
 * <pre>{@code
 * {
 *   "currentDimension": "minecraft:overworld",
 *   "worlds": {
 *     "minecraft:overworld": {
 *       "updatedAt": 1720000000000,
 *       "entities": {
 *         "069a79f4-...": { "id": 42, "type": "minecraft:zombie", "pos": [...], ...,
 *                           "cells": ["-39,64,31"] }
 *       }
 *     }
 *   }
 * }
 * }</pre>
 * 条目的载荷键（{@code item} / {@code nbt} / {@code living}）与 {@code entities.nbt} <b>逐字同形</b>
 * ——直接复用 {@link VisionEntityStore#entityEntry} 的同一份实现；{@code cells} 是并集侧独有字段。
 *
 * <p>写盘走 {@link UnionSaveScheduler}（限频 + 后台线程 + 原子写，设计 §4.5 决策 D）。
 */
public class EntityUnionStore {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR_NAME = "stevex/vision";
    private static final String FILE_NAME = "entities_union.nbt";
    private static final String KEY_UPDATED_AT = "updatedAt";
    private static final String KEY_ENTITIES = "entities";
    /**
     * 并集侧独有：该实体最后被观测到时的 AABB 占用格（{@code "x,y,z"} 字符串数组）。
     *
     * <p>它是 {@link #sync} 的移除判据的<b>唯一</b>输入。用可读字符串而非 {@code BlockPos.asLong}：
     * 本文件的读者是 agent，与 {@code blocks} 的键同风格，省掉一次位运算解码。
     *
     * <p>每次观测到该实体<b>整体重写</b>（实体在移动，旧格作废）——这是"更新"而非"并入"。
     */
    private static final String KEY_CELLS = "cells";

    private final Path filePath;
    private final UnionSaveScheduler scheduler;

    /** 分桶并集镜像：维 id → （uuid 字符串 → 实体条目 tag）。变更规则同 {@link TerrainUnionStore#byDim}。 */
    private final Map<String, LinkedHashMap<String, CompoundTag>> byDim = new LinkedHashMap<>();
    private final Map<String, Long> updatedAtByDim = new LinkedHashMap<>();
    private String currentDimension = WorldsFile.LEGACY_DIMENSION;
    private volatile Snapshot pending;

    public EntityUnionStore(final UnionSaveScheduler scheduler) {
        this.scheduler = scheduler;
        scheduler.registerWriter(this::flush);
        final Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve(DIR_NAME);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("[Vision] Failed to create directory {}: {}", dir, e.getMessage());
        }
        this.filePath = dir.resolve(FILE_NAME);
        loadExisting();
    }

    /**
     * 把本帧可见实体并入当前维的并集，并按 {@code allCellsEmpty} 判据移除已证不在的实体。
     *
     * @param entities 本帧可见实体（{@code entities.nbt} 的同一份输入；其 uuid 集合就是"本帧可见"守卫）
     * @param boxByUuid 实体的渲染帧 AABB（键与 {@code entities} 的 uuid 对应）。取自
     *        {@code DepthCapture.DepthSnapshot#entities}——{@code EntityLightSnapshot} 是它去掉盒的
     *        1:1 投影，故按 uuid 即可取回同一个盒
     * @param entityDeletions 被证明"现实中已无实体"的格子（{@link EntityPresenceCorrector} 直读产出）
     * @param cameraCell 采集时相机所在格。与记忆端同规则地计入 provenEmpty：该格被候选枚举<b>刻意排除</b>
     *        （相机在自己身上），不计入会让"与相机同格的实体"永远删不掉（{@code DeletionApplier.java:187-208}）
     * @param dimensionId 本帧所在维 id
     * @return 统计 { "added", "updated", "removed", "total" }
     */
    public Map<String, Object> sync(
            final List<VisionCollector.EntityLightSnapshot> entities,
            final Map<UUID, AABB> boxByUuid,
            final List<BlockPos> entityDeletions,
            final BlockPos cameraCell,
            final String dimensionId
    ) {
        currentDimension = dimensionId;
        final LinkedHashMap<String, CompoundTag> union =
                byDim.computeIfAbsent(dimensionId, k -> new LinkedHashMap<>());

        // ① 并入：本帧可见实体（连同其占用格）。内容不变则不替换（同 TerrainUnionStore ②）。
        //    守卫集直接用 UUID#toString 的字符串形态，与条目键同一形态——不必再解析回 UUID。
        final Set<String> frameUuids = new HashSet<>(entities.size() * 2 + 1);
        int added = 0;
        int updated = 0;
        for (VisionCollector.EntityLightSnapshot e : entities) {
            final String uuid = e.uuid().toString();
            frameUuids.add(uuid);
            final CompoundTag candidate = entityEntry(e, boxByUuid.get(e.uuid()));
            final CompoundTag old = union.get(uuid);
            if (old == null) {
                union.put(uuid, candidate);
                added++;
            } else if (!old.equals(candidate)) {
                union.put(uuid, candidate);
                updated++;
            }
        }

        // ② 移除：逐位复现记忆端的 allCellsEmpty（见类 javadoc）。守卫先行——本帧可见的实体不许删，
        //    与 DeletionApplier.java:199-200 的 currentEntityUuids.contains(uuid) → continue 同规则。
        final Set<BlockPos> provenEmpty = new HashSet<>(entityDeletions);
        if (cameraCell != null) provenEmpty.add(cameraCell);
        int removed = 0;
        if (!provenEmpty.isEmpty()) {
            for (var it = union.entrySet().iterator(); it.hasNext(); ) {
                final Map.Entry<String, CompoundTag> en = it.next();
                if (frameUuids.contains(en.getKey())) continue;
                if (allCellsProvenEmpty(en.getValue(), provenEmpty)) {
                    it.remove();
                    removed++;
                }
            }
        }

        if (added + updated + removed > 0) {
            publish(dimensionId);
            scheduler.requestSave();
        }
        return Map.of("added", added, "updated", updated, "removed", removed, "total", union.size());
    }

    // ==================== 内部 ====================

    /** 实体条目 tag = {@code entities.nbt} 的同名条目（复用同一实现）+ 并集侧的 {@code cells}。 */
    private static CompoundTag entityEntry(final VisionCollector.EntityLightSnapshot e, final AABB box) {
        final CompoundTag entry = VisionEntityStore.entityEntry(e);
        entry.put(KEY_CELLS, cellsTag(box));
        return entry;
    }

    /** AABB → 占用格字符串数组；口径经 {@link ObjectResolver#forEachBoxCell} 与记忆端统一（见类 javadoc）。 */
    private static ListTag cellsTag(final AABB box) {
        final ListTag list = new ListTag();
        if (box != null) {
            ObjectResolver.forEachBoxCell(box, pos -> list.add(StringTag.valueOf(
                    VisionTerrainStore.posKey(pos))));
        }
        return list;
    }

    /**
     * 复现记忆端 {@code EntityRestorer.allCellsEmpty}：该条目记录的全部占用格是否都 ∈ {@code provenEmpty}。
     *
     * <p><b>两条保守出口</b>（都返回 false = 不删，方向安全）：无 {@code cells} 字段 / 格集为空
     * （AABB 缺失或退化）、单格字符串解析失败。判据证明不了"全空"就不删——宁可留幽灵，不可误删记得的对象。
     */
    private static boolean allCellsProvenEmpty(final CompoundTag entry, final Set<BlockPos> provenEmpty) {
        final ListTag cells = entry.getListOrEmpty(KEY_CELLS);
        if (cells.isEmpty()) return false;
        for (int i = 0; i < cells.size(); i++) {
            final BlockPos pos = parseCell(cells.getStringOr(i, ""));
            if (pos == null) return false;
            if (!provenEmpty.contains(pos)) return false;
        }
        return true;
    }

    /** {@code "x,y,z"} → BlockPos；格式外 / 越界返回 null（调用方按"不可判"处理）。 */
    private static BlockPos parseCell(final String s) {
        final int c1 = s.indexOf(',');
        final int c2 = s.indexOf(',', c1 + 1);
        if (c1 < 0 || c2 < 0) return null;
        try {
            return new BlockPos(Integer.parseInt(s.substring(0, c1)),
                    Integer.parseInt(s.substring(c1 + 1, c2)),
                    Integer.parseInt(s.substring(c2 + 1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 渲染线程：浅拷贝发布（同 {@link TerrainUnionStore} 的理由——条目 tag 自放入起不再被修改）。 */
    private void publish(final String dimensionId) {
        final Map<String, Map<String, CompoundTag>> copy = new LinkedHashMap<>();
        byDim.forEach((dim, map) -> copy.put(dim, new LinkedHashMap<>(map)));
        updatedAtByDim.put(dimensionId, System.currentTimeMillis());
        pending = new Snapshot(copy, new LinkedHashMap<>(updatedAtByDim), currentDimension);
    }

    /** 后台线程 / 关停线程：落盘（互斥理由同 {@link TerrainUnionStore#flush}）。 */
    synchronized void flush() {
        final Snapshot snap = pending;
        if (snap == null) return;
        pending = null;
        try {
            UnionSaveScheduler.writeAtomic(buildRoot(snap), filePath);
            LOGGER.info("[Vision] Saved entity union: {} entit(ies) over {} dimension bucket(s), current={} → {}",
                    snap.byDim().getOrDefault(snap.currentDimension(), Map.of()).size(),
                    snap.byDim().size(), snap.currentDimension(), filePath);
        } catch (IOException e) {
            LOGGER.warn("[Vision] Failed to save entity union {}: {}", filePath, e.getMessage());
            if (pending == null) pending = snap;
        }
    }

    private static CompoundTag buildRoot(final Snapshot snap) {
        final Map<String, CompoundTag> worlds = new LinkedHashMap<>();
        final long now = System.currentTimeMillis();
        for (Map.Entry<String, Map<String, CompoundTag>> e : snap.byDim().entrySet()) {
            final CompoundTag entitiesTag = new CompoundTag();
            for (Map.Entry<String, CompoundTag> b : e.getValue().entrySet()) {
                entitiesTag.put(b.getKey(), b.getValue());
            }
            final CompoundTag bucket = new CompoundTag();
            bucket.putLong(KEY_UPDATED_AT, e.getKey().equals(snap.currentDimension())
                    ? now : snap.updatedAt().getOrDefault(e.getKey(), 0L));
            bucket.put(KEY_ENTITIES, entitiesTag);
            worlds.put(e.getKey(), bucket);
        }
        return WorldsFile.wrap(snap.currentDimension(), worlds);
    }

    private void loadExisting() {
        if (!Files.exists(filePath)) return;
        try {
            final CompoundTag root = NbtIo.readCompressed(filePath, NbtAccounter.unlimitedHeap());
            if (root == null) return;
            final WorldsFile.Result r = WorldsFile.read(root);
            currentDimension = r.currentDimension();
            r.worlds().forEach((dim, bucket) -> {
                final LinkedHashMap<String, CompoundTag> union = new LinkedHashMap<>();
                final CompoundTag entitiesTag = bucket.getCompoundOrEmpty(KEY_ENTITIES);
                for (String key : entitiesTag.keySet()) {
                    union.put(key, entitiesTag.getCompoundOrEmpty(key));
                }
                byDim.put(dim, union);
                updatedAtByDim.put(dim, bucket.getLongOr(KEY_UPDATED_AT, 0L));
            });
            int total = 0;
            for (Map<String, CompoundTag> m : byDim.values()) total += m.size();
            LOGGER.info("[Vision] Loaded entity union: {} entit(ies) over {} dimension bucket(s) from {} (current={})",
                    total, byDim.size(), filePath, currentDimension);
        } catch (Exception e) {
            LOGGER.warn("[Vision] Failed to load entity union {}: {}", filePath, e.getMessage());
        }
    }

    private record Snapshot(
            Map<String, Map<String, CompoundTag>> byDim,
            Map<String, Long> updatedAt,
            String currentDimension
    ) {
    }
}
