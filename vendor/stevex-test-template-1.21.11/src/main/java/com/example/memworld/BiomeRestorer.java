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
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * v2.31（生物群系，见 docs/生物群系复原设计方案.md）+ v2.32（按维分桶，见 docs/世界类型区分与镜像复原设计方案.md）：
 * 记忆世界生物群系复原引擎。
 *
 * <p>读取采集侧写下的群系源（v2.48 起是目录 {@code biomes/} 下的区文件 + {@code _index.nbt}；分片前
 * 是单文件 {@code biomes.nbt}），把记录过的 <b>4×4×4 quart cell</b> 群系写进<b>当前活动维</b>已加载
 * 区块，并让客户端重着色。机制完全复用 vanilla {@code /fillbiome}（{@code FillBiomeCommand}）的公开
 * 写入口，无新增 Mixin：
 *
 * <ol>
 *   <li>{@code chunk.fillBiomesFromNoise(resolver, sampler)} —— resolver 对<b>记录过的 cell</b>
 *       返回记录群系、其余返回 {@code chunk.getNoiseBiome(...)}（当前值）→ 只覆盖不污染；</li>
 *   <li>{@code chunk.markUnsaved()} —— 群系随区块落盘持久化（客户端后续正常重载也正确）；</li>
 *   <li>{@code chunkMap.resendBiomesForChunks(List.of(chunk))} —— 向跟踪玩家发
 *       {@code ClientboundChunksBiomesPacket}，客户端即时重着色。</li>
 * </ol>
 *
 * <p>只处理<b>已加载</b>区块：文件变化把新增/变化的 cell 归组到本维 chunk 进 {@code pendingByDim}；
 * 每 tick 扫 {@code pendingByDim[活动维]}，对已加载的块立即 apply，未加载的留在集合——记忆玩家按采集
 * 姿态传送时区块自然加载，通常下一 tick 即被扫中补填（无需 CHUNK_LOAD 事件，实现更简）。
 *
 * <p>v2.32：cell 表 / 上次快照 / pending chunk 集合<b>按维隔离</b>（chunk 的 long 不编码维，主/下界
 * 同 chunk 坐标必须分桶防撞）；每 tick 只 diff / apply {@code level.dimension()} 对应维桶的 cells——
 * 主世界群系只写主世界区块、下界群系只写下界区块，坐标永不跨维碰撞。pending 只在活动维内按加载补填。
 *
 * <p>cell key 一律用文件同款字符串 {@code "qx,qy,qz"}（quart 坐标），避免跨端二进制打包约定。
 * cell 内群系必然相同（游戏以 cell 存群系）→ resolver 按 key 查表即可，逐 cell、绝不逐方块。
 *
 * <p>v2.48（§11）：源改为目录 + 索引，<b>每轮仍只 stat 一个文件</b>（{@code _index.nbt}），只有
 * {@code updatedAt} 变了的区才重新解压。三条不变量在分片后不变：
 * <ul>
 *   <li><b>union 单调</b>：{@link #cellsByDim} 只增不减——这与采集端 ④ 本身没有删除通道是一致的，
 *       所以这里<b>不做</b>"区从索引里消失就摘掉其 cell"的剪枝（那会与旧版整份重读的行为分叉：旧版
 *       在文件被删时同样保留旧 union）。</li>
 *   <li><b>{@link #prevIdsByDim} 仍是全维表</b>：虽然按区读入，但 diff 的基准必须是"该维上次见过的
 *       全部 cell"。只把变化的区的 cell 并进去即可——未变的区其 cell 本就与基准相等，并或不并都一样。</li>
 *   <li><b>群系解析照旧只在读到时就地做</b>：注册表是<b>服务器级</b>的，与当前是哪个维无关，故读别的维
 *       的区时用当前 level 查注册表得到的是同一个 holder（比旧版"只解析当前维"还更完整）。</li>
 * </ul>
 */
public class BiomeRestorer {

    private static final Logger LOGGER = LoggerFactory.getLogger("stevex-test/memory");

    private static final String KEY_CELLS = "cells";

    /** v2.32：已解析群系按维：维度 → cell key（"qx,qy,qz"）→ holder（union，单调累积，永不删除）。 */
    private final Map<String, Map<String, Holder<Biome>>> cellsByDim = new HashMap<>();
    /** v2.32：该维上次见过的全部 cell（cell key → id），用于检测新增/变化 cell。跨区累积，不随区重读而清空。 */
    private final Map<String, Map<String, String>> prevIdsByDim = new HashMap<>();
    /** v2.32：含未应用 cell 的 chunk（ChunkPos.asLong）按维（本维待补填），chunk 坐标不编码维故必须分维。 */
    private final Map<String, Set<Long>> pendingByDim = new HashMap<>();
    /** 已警告过的缺失群系 id（防刷屏，跨维共享——id 在注册表里全局一致）。 */
    private final Set<String> warnedMissing = new HashSet<>();

    /** v2.48：索引 diff 缓存：维 → 区键 → 上次看到的 {@code updatedAt}。相同 ⇒ 该区不需要重读。 */
    private final Map<String, Map<String, Long>> indexCache = new LinkedHashMap<>();

    /** v2.13 mtime 门控（§7.4）：索引文件的 mtime；未变 → 一个区文件都不读。 */
    private FileTime lastIndexMtime;
    /** 每次因索引变化而重读区文件时递增的版本号（诊断日志用）。 */
    private int readVersion;

    /** 单 tick 最多 apply 的 pending chunk 数（避免大 union 首读时一次夯住服务器）。 */
    private static final int MAX_APPLY_PER_TICK = 64;

    private int ticks;
    private int missingSourceCounter;

    /** 服务器（世界）启动 / 切换时调用，清空已应用状态（支持 resetOnLaunch 重建后全量重填）。 */
    public void onServerStart() {
        cellsByDim.clear();
        prevIdsByDim.clear();
        pendingByDim.clear();
        warnedMissing.clear();
        indexCache.clear();
        lastIndexMtime = null;
        readVersion = 0;
        ticks = 0;
        LOGGER.info("[MemoryWorld] Biome restorer ready");
    }

    /** 命令触发：强制重新读取（清索引缓存与 mtime 门控 ⇒ 下轮重读全部区文件）。 */
    public void forceRefresh() {
        indexCache.clear();
        lastIndexMtime = null;
        warnedMissing.clear();
    }

    /**
     * 驱动一次轮询：① 索引 mtime 变化时与本地缓存 diff，只重读 {@code updatedAt} 变了的区，把其中的
     * 新增/变化 cell 解析并归组进该维 pending；② 无论是否读到新内容，都扫 pendingByDim[活动维] 对已加载
     * 块 apply（补填）。每 tick 只处理 {@code level.dimension()} 对应维——活动维之外只累积 pending，不写区块。
     */
    public void tick(final ServerLevel level) {
        final MemoryConfig config = MemoryConfig.get();
        final String dimension = level.dimension().identifier().toString();
        if (ticks++ % Math.max(1, config.pollIntervalTicks) != 0) {
            drainPending(level, dimension);
            return;
        }

        final Path storeDir = config.resolveBiomeDir();
        if (storeDir == null || !Files.isDirectory(storeDir)) {
            if (missingSourceCounter++ % 30 == 0) {
                LOGGER.info("[MemoryWorld] Biome source directory missing, biome restore paused (gameDir={}).",
                        config.gameDirectory());
            }
            // 两个都归零：目录重新出现后自然触发首次读取；索引缓存也清空，使每个区都当作第一次见。
            lastIndexMtime = null;
            indexCache.clear();
            drainPending(level, dimension);
            return;
        }
        missingSourceCounter = 0;

        pollRegions(level, storeDir);
        drainPending(level, dimension);
    }

    // ==================== 索引轮询 + 差异 ====================

    /**
     * v2.48：轮询 {@code _index.nbt}（每轮唯一的 stat），只重读 {@code updatedAt} 变了的区文件。
     *
     * <p>门控形状与 v2.32 一致——仍是一轮一次 stat、mtime 未变就什么都不做，只是 stat 的对象从
     * {@code biomes.nbt} 换成了索引。采集端保证"索引只在真有区写出时才重写"，这条前提才成立。
     *
     * <p>读失败 / 空索引一律<b>不推进</b> {@link #lastIndexMtime}，下一轮重试（与旧版对源文件的处理同形）。
     */
    private void pollRegions(final ServerLevel level, final Path storeDir) {
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
        readVersion++;

        int reread = 0;
        int failed = 0;
        int changedCells = 0;
        int addedChunks = 0;
        indexCache.keySet().removeIf(dim -> !fresh.containsKey(dim));
        for (final Map.Entry<String, Map<String, Long>> de : fresh.entrySet()) {
            final String dim = de.getKey();
            final String dimDirName = VisionRegions.dimDirName(dim);
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
                cached.put(regionKey, re.getValue());
                reread++;
                final int[] counts = applyRegionCells(level, dim, root.getCompoundOrEmpty(KEY_CELLS));
                changedCells += counts[0];
                addedChunks += counts[1];
            }
            // 本地有、新索引里没有的区：只从索引缓存里摘掉（下次若再出现会当新区重读）。
            // **不动 cellsByDim / prevIdsByDim**——④ 是单调 union，与旧版"文件被删仍保留已累积 cell"一致。
            cached.keySet().removeIf(regionKey -> !de.getValue().containsKey(regionKey));
        }

        if (failed > 0) {
            LOGGER.warn("[MemoryWorld] {} biome region file(s) unreadable this poll — will retry", failed);
        } else if (reread > 0 && changedCells > 0) {
            LOGGER.info("[MemoryWorld] Biome index v{}: re-read {} region(s), {} changed cells, +{} chunks pending",
                    readVersion, reread, changedCells, addedChunks);
        }
    }

    /**
     * 把一个区里的 cell 并进该维 union，返回 {@code [changed, addedChunks]}。
     *
     * <p>{@code cells} 是<b>该维上次见过的全部 cell</b>（跨区累积），而 {@code regionCells} 只是一个区的
     * 快照——两者相减得到的正是"这个区里新增/变化的 cell"。未变的区其 cell 本就在 {@code cells} 里，
     * 故只并入变化的区的 cell 与整份重读等价。
     *
     * <p>注册表查表用传入的 {@code level}（当前活动维的 level）：注册表是<b>服务器级</b>的，与 level 属于
     * 哪个维无关，所以读别的维的区时同样能拿到正确的 holder。
     */
    private int[] applyRegionCells(final ServerLevel level, final String dimension, final CompoundTag cellsTag) {
        final Map<String, Holder<Biome>> cells = cellsByDim.computeIfAbsent(dimension, k -> new HashMap<>());
        final Map<String, String> prevIds = prevIdsByDim.computeIfAbsent(dimension, k -> new HashMap<>());
        final Set<Long> pending = pendingByDim.computeIfAbsent(dimension, k -> new HashSet<>());

        int changed = 0;
        int addedChunks = 0;
        for (final String key : cellsTag.keySet()) {
            final String id = cellsTag.getStringOr(key, "");
            final String prev = prevIds.get(key);
            if (prev != null && prev.equals(id)) continue; // 未见变化
            changed++;
            // 与旧版一致：无论能否解析都记账。注册表在一次会话内不变，解析不出来的 id 记了账才不会被
            // 每个区文件重读时反复重试（旧的整表替换恰好也是这个效果）。
            prevIds.put(key, id);
            final Holder<Biome> holder = resolveBiome(level, id);
            if (holder == null) continue; // 注册表查不到 → 跳过（已记 WARN）
            cells.put(key, holder);
            final int[] q = parseCellKey(key);
            if (q != null && pending.add(ChunkPos.asLong(q[0] >> 2, q[2] >> 2))) {
                addedChunks++;
            }
        }
        return new int[]{changed, addedChunks};
    }

    /** biome id 字符串 → holder；查不到 → WARN（每种 id 仅一次）并返回 null。 */
    private Holder<Biome> resolveBiome(final ServerLevel level, final String id) {
        try {
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, Identifier.parse(id));
            Optional<Holder.Reference<Biome>> ref =
                    level.registryAccess().lookupOrThrow(Registries.BIOME).get(key);
            if (ref.isEmpty()) {
                if (warnedMissing.add(id)) {
                    LOGGER.warn("[MemoryWorld] Biome '{}' not found in registry, skipped (same registry as capture?)", id);
                }
                return null;
            }
            return ref.get();
        } catch (Exception ex) {
            if (warnedMissing.add(id)) {
                LOGGER.warn("[MemoryWorld] Failed to resolve biome '{}': {}", id, ex.getMessage());
            }
            return null;
        }
    }

    /** "qx,qy,qz" → [qx, qy, qz]；解析失败 → null。 */
    private static int[] parseCellKey(final String key) {
        String[] p = key.split(",");
        if (p.length != 3) return null;
        try {
            return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================== 应用（/fillbiome 范式） ====================

    /** 扫描指定维 pendingChunks，对已加载块 apply（每 tick 限量）。 */
    private void drainPending(final ServerLevel level, final String dimension) {
        Set<Long> pending = pendingByDim.get(dimension);
        if (pending == null || pending.isEmpty()) return;
        ServerChunkCache cache = (ServerChunkCache) level.getChunkSource();
        int applied = 0;
        List<Long> toRemove = new ArrayList<>(Math.min(pending.size(), MAX_APPLY_PER_TICK));
        for (Long chunkKey : pending) {
            if (toRemove.size() >= MAX_APPLY_PER_TICK) break;
            int cx = ChunkPos.getX(chunkKey);
            int cz = ChunkPos.getZ(chunkKey);
            LevelChunk chunk = cache.getChunkNow(cx, cz);
            if (chunk == null) continue; // 未加载 → 留在集合等加载
            try {
                applyChunk(level, cache, chunk);
            } catch (Exception ex) {
                LOGGER.warn("[MemoryWorld] Biome apply failed at chunk ({},{}): {}", cx, cz, ex.getMessage());
                continue; // 失败保留 pending，下轮重试
            }
            toRemove.add(chunkKey);
            applied++;
        }
        pending.removeAll(toRemove);
        if (applied > 0) {
            LOGGER.info("[MemoryWorld] Biome applied [{}] to {} chunk(s), {} pending remain",
                    dimension, applied, pending.size());
        }
    }

    /**
     * 把该维记录群系写进单个已加载区块并重推客户端（等价 /fillbiome 的
     * {@code fillBiomesFromNoise} → {@code markUnsaved} → {@code resendBiomesForChunks}）。
     */
    private void applyChunk(final ServerLevel level, final ServerChunkCache cache, final LevelChunk chunk) {
        final Map<String, Holder<Biome>> cells = cellsByDim.get(level.dimension().identifier().toString());
        final Map<String, Holder<Biome>> table = cells == null ? Map.of() : cells;
        BiomeResolver resolver = (quartX, quartY, quartZ, sampler) -> {
            Holder<Biome> h = table.get(quartX + "," + quartY + "," + quartZ);
            return h != null ? h : chunk.getNoiseBiome(quartX, quartY, quartZ); // 未记录 → 保持现状
        };
        chunk.fillBiomesFromNoise(resolver, cache.randomState().sampler());
        chunk.markUnsaved();
        cache.chunkMap.resendBiomesForChunks(List.of((ChunkAccess) chunk));
    }
}
