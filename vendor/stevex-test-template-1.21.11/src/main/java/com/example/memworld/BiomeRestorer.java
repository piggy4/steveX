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
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
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
 * <p>读取采集侧写下的群系源 NBT 文件（{@code biomes.nbt}，与 terrain.nbt 同目录，v2.32 起按维
 * 分桶），把记录过的 <b>4×4×4 quart cell</b> 群系写进<b>当前活动维</b>已加载区块，并让客户端重着色。
 * 机制完全复用 vanilla {@code /fillbiome}（{@code FillBiomeCommand}）的公开写入口，无新增 Mixin：
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
 */
public class BiomeRestorer {

    private static final Logger LOGGER = LoggerFactory.getLogger("stevex-test/memory");

    private static final String KEY_CELLS = "cells";

    /** v2.32：已解析群系按维：维度 → cell key（"qx,qy,qz"）→ holder（union，单调累积，永不删除）。 */
    private final Map<String, Map<String, Holder<Biome>>> cellsByDim = new HashMap<>();
    /** v2.32：最近一次该维文件快照（cell key → id），用于检测新增/变化 cell。 */
    private final Map<String, Map<String, String>> prevIdsByDim = new HashMap<>();
    /** v2.32：含未应用 cell 的 chunk（ChunkPos.asLong）按维（本维待补填），chunk 坐标不编码维故必须分维。 */
    private final Map<String, Set<Long>> pendingByDim = new HashMap<>();
    /** 已警告过的缺失群系 id（防刷屏，跨维共享——id 在注册表里全局一致）。 */
    private final Set<String> warnedMissing = new HashSet<>();

    /** v2.13 mtime 门控（§7.4）：最近一次成功读取的源文件 mtime；未变 → 不读不解压。 */
    private FileTime lastMtime;
    /** Latest file buckets awaiting their own dimension's tick, even if mtime stays unchanged. */
    private Map<String, Map<String, String>> pendingIdsByDim = new HashMap<>();
    /** 每次成功读取时递增的版本号（诊断日志用）。 */
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
        lastMtime = null;
        pendingIdsByDim.clear();
        readVersion = 0;
        ticks = 0;
        LOGGER.info("[MemoryWorld] Biome restorer ready");
    }

    /** 命令触发：强制重新读取源文件（清 mtime 门控）。 */
    public void forceRefresh() {
        lastMtime = null;
        warnedMissing.clear();
    }

    /**
     * 驱动一次轮询：① 源文件 mtime 变化时缓存全部维度，各维首次被驱动时 diff 新 cell → 归组进本维
     * pending；② 无论是否读到新文件，都扫 pendingByDim[活动维] 对已加载块 apply（补填）。
     * 每 tick 只处理 {@code level.dimension()} 对应维——活动维之外的维桶不 diff / 不写区块。
     */
    public void tick(final ServerLevel level) {
        MemoryConfig config = MemoryConfig.get();
        final String dimension = level.dimension().identifier().toString();
        if (ticks++ % Math.max(1, config.pollIntervalTicks) != 0) {
            drainPending(level, dimension);
            return;
        }

        Path source = config.resolveBiomeFile();
        if (source == null || !Files.exists(source)) {
            if (missingSourceCounter++ % 30 == 0) {
                LOGGER.info("[MemoryWorld] Biome source file missing, biome restore paused (gameDir={}).",
                        config.gameDirectory());
            }
            lastMtime = null; // 文件重新出现后自然触发首次读取
            drainPending(level, dimension);
            return;
        }
        missingSourceCounter = 0;

        final FileTime mtime;
        try {
            mtime = Files.getLastModifiedTime(source);
        } catch (IOException e) {
            LOGGER.warn("[MemoryWorld] Failed to stat biome file {}: {}", source, e.getMessage());
            drainPending(level, dimension);
            return;
        }
        if (!mtime.equals(lastMtime)) {
            Map<String, Map<String, String>> idsByDim = readFile(source);
            if (idsByDim != null) {
                lastMtime = mtime; // 只在成功读取后才推进
                pendingIdsByDim = new HashMap<>(idsByDim);
            }
        }
        // A dimension switch can happen a tick after the file was read.
        Map<String, String> newIds = pendingIdsByDim.remove(dimension);
        if (newIds != null) applyDiff(level, dimension, newIds);
        drainPending(level, dimension);
    }

    // ==================== 文件读取 + 差异 ====================

    /**
     * 读取 biomes.nbt → 各维 cell key → biome id 表；读取失败返回 null（保留旧 mtime，下轮重试）。
     * 旧版单维文件（无 worlds 键）自动视为 overworld 桶。
     */
    private Map<String, Map<String, String>> readFile(final Path source) {
        try {
            CompoundTag root = NbtIo.readCompressed(source, NbtAccounter.unlimitedHeap());
            if (root == null) return Map.of();

            WorldsFile.Result r = WorldsFile.read(root);
            Map<String, Map<String, String>> byDim = new LinkedHashMap<>();
            for (Map.Entry<String, CompoundTag> e : r.worlds().entrySet()) {
                Map<String, String> ids = new HashMap<>();
                CompoundTag cellsTag = e.getValue().getCompoundOrEmpty(KEY_CELLS);
                for (String key : cellsTag.keySet()) {
                    ids.put(key, cellsTag.getStringOr(key, ""));
                }
                byDim.put(e.getKey(), ids);
            }
            return byDim;
        } catch (IOException e) {
            LOGGER.warn("[MemoryWorld] Failed to read biome file {}: {}", source, e.getMessage());
            return null;
        }
    }

    /** 该维新文件相对上次快照：解析新增/变化 cell，归组进该维 pendingChunks。 */
    private void applyDiff(final ServerLevel level, final String dimension, final Map<String, String> newIds) {
        readVersion++;
        Map<String, Holder<Biome>> cells = cellsByDim.computeIfAbsent(dimension, k -> new HashMap<>());
        Map<String, String> prevIds = prevIdsByDim.computeIfAbsent(dimension, k -> new HashMap<>());
        Set<Long> pending = pendingByDim.computeIfAbsent(dimension, k -> new HashSet<>());

        int changed = 0;
        int addedChunks = 0;
        for (Map.Entry<String, String> e : newIds.entrySet()) {
            String key = e.getKey();
            String prev = prevIds.get(key);
            if (prev != null && prev.equals(e.getValue())) continue; // 未见变化
            changed++;
            Holder<Biome> holder = resolveBiome(level, e.getValue());
            if (holder == null) continue; // 注册表查不到 → 跳过（已记 WARN）
            cells.put(key, holder);
            int[] q = parseCellKey(key);
            if (q != null && pending.add(ChunkPos.asLong(q[0] >> 2, q[2] >> 2))) {
                addedChunks++;
            }
        }
        prevIds.clear();
        prevIds.putAll(newIds);
        if (changed > 0) {
            LOGGER.info("[MemoryWorld] Biome file v{} [{}]: {} changed cells, +{} chunks pending (total {} cells)",
                    readVersion, dimension, changed, addedChunks, cells.size());
        }
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
