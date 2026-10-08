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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;

/**
 * v2.31（生物群系，见 docs/生物群系复原设计方案.md）：群系 cell 持久化存储 —— 单调 union 覆盖写。
 *
 * <p>群系在游戏内以 <b>4×4×4 quart cell</b> 存储（每 cell 单值），本通道与地形/实体/容器各文件平行，
 * 记录"采集历史 union"的 3D cell → biomeId。同一 cell 内所有方块的 {@code getBiome} 返回值必然相同
 * （游戏存储保证），故<b>逐 cell 写、绝不逐方块写</b>——实现无需值比较。
 *
 * <p><b>v2.48：region 分片</b>（设计 §11，用户 2026-10-07 定案）。本类是全套四个累积文件里第一个改的，
 * 因为它最干净——单调、<b>没有删除通道</b>、<b>没有维级标量</b>，分片只影响"写到哪个文件"。分片前是
 * 单文件 {@code biomes.nbt}，每有新增就整份覆盖写；分片后是目录 {@code biomes/}：
 *
 * <pre>{@code
 * stevex/vision/biomes/
 *   _index.nbt                       // 维 → 区键 → updatedAt（记忆端的门控信号，§11.9.3）
 *   minecraft_overworld/
 *     r.0.0.nbt  r.0.1.nbt  r.-1.0.nbt
 *   minecraft_the_nether/
 *     r.0.0.nbt
 * }</pre>
 *
 * <p><b>区维度是 quart，不是方块</b>——本类算区键必须用 {@link VisionRegions#regionKeyOfQuart}
 * （{@code qx >> 7}），而<b>不能</b>用 {@code regionKeyOfBlock}（{@code x >> 9}）。两者都能跑通、都不会
 * 报错，但后者会把"区"定义成 128 方块见方，与其余三个文件同名不同义。理由见 {@code VisionRegions} 类
 * javadoc 的陷阱 ①。
 *
 * <p><b>只写变化的区</b>：一次 {@link #sync} 里新增的 cell 可能散布在几个区，只有<b>真正被写入</b>的那
 * 几个区文件（+ 索引）会落盘。这既省了写整个世界的 I/O，也让记忆端的门控有意义——索引 mtime 只在真有
 * 变更时才动，而它一动，记忆端就知道"去看索引说了哪几个区"，读取量 = O(变化的区) 而非 O(整个世界)。
 *
 * <p><b>union 语义不变</b>：构造时读入既有区文件并入内存（跨会话累积），每帧只对"不在当前维 union 中"
 * 的 cell 做一次 {@code level.getBiome} 解析（O(新增 cell)），新增并入 union。不做删除——真实世界的列
 * 群系是静态的，union 单调即正确。（注：单调也就意味着本文件是全套里唯一<b>没有</b>删除通道的，
 * ④ 是否补 GC 是设计 §11.12 里另记的正交议题，与分片无关。）
 */
public class VisionBiomeStore {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR_NAME = "stevex/vision";
    /** v2.48：分片后的 store 目录名（原单文件名去掉 {@code .nbt}）。 */
    private static final String STORE_DIR_NAME = "biomes";
    /** v2.48：分片前的单文件（构造时若存在则拆分迁移并退役）。 */
    private static final String LEGACY_FILE_NAME = "biomes.nbt";
    private static final String KEY_CELLS = "cells";

    /** v2.48：维度 → 区键 → 该区的 cell 镜像（cell 键 = "qx,qy,qz"）。区内的 cell 永不删除。 */
    private final Map<String, Map<String, RegionBucket>> byDim = new LinkedHashMap<>();

    /** v2.48：索引镜像（维 → 区键 → updatedAt）。区文件是权威，本表是它的派生，随写就地更新。 */
    private final Map<String, Map<String, Long>> index = new LinkedHashMap<>();

    private final Path storeDir;
    private final Path legacyFilePath;

    public VisionBiomeStore() {
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

    /** 单帧增量结果：全部维 union 总数 + 本帧新增 cell 数。 */
    public record Stats(int cells, int added) {}

    /**
     * 采样一帧候选方块并落盘（必须渲染线程调用——内部查 ClientLevel）。
     *
     * <p>候选 = 本帧可见方块集 + 相机 cell 锚点（ObjectResolver 已收集）。以 cell 去重后，
     * 仅对<b>当前维</b>未记录的 cell 解析 biome；有新增才写文件。
     *
     * @param dimensionId v2.32：采集时所在维 id，决定更新哪个维的 union
     * @return 采样统计；任何异常被吞掉并记日志（降级为 0 新增，不阻断视觉快照主链路）
     */
    public Stats sync(final ClientLevel level, final List<BlockPos> samplePoints, final String dimensionId) {
        try {
            final Map<String, RegionBucket> regions = byDim.computeIfAbsent(dimensionId, k -> new LinkedHashMap<>());

            // 按 cell 去重（同 cell 群系必然相同 → 只留一个代表采样点）。区键与 cell 键同源一次算好：
            // 两者都从同一组 QuartPos 导出，分开算迟早会有一处漏改 >>
            final Map<String, Candidate> candidates = new LinkedHashMap<>();
            for (BlockPos p : samplePoints) {
                if (p == null) continue;
                final int qx = QuartPos.fromBlock(p.getX());
                final int qz = QuartPos.fromBlock(p.getZ());
                candidates.putIfAbsent(qx + "," + QuartPos.fromBlock(p.getY()) + "," + qz,
                        new Candidate(p, VisionRegions.regionKeyOfQuart(qx, qz)));
            }

            int added = 0;
            final Set<String> touchedRegions = new LinkedHashSet<>();
            for (Map.Entry<String, Candidate> e : candidates.entrySet()) {
                final String cellKey = e.getKey();
                final String regionKey = e.getValue().regionKey();
                final RegionBucket existing = regions.get(regionKey);
                if (existing != null && existing.cells.containsKey(cellKey)) continue; // 已在当前维 union
                final String id = biomeIdAt(level, e.getValue().pos());
                if (id == null) continue; // 未注册群系（如自定义 registry 缺失）→ 跳过
                regions.computeIfAbsent(regionKey, k -> new RegionBucket()).cells.put(cellKey, id);
                touchedRegions.add(regionKey);
                added++;
            }

            if (added > 0) writeRegions(dimensionId, touchedRegions);
            LOGGER.debug("[Vision] Biome cells: total={}, added={}, regions={} (dim={})",
                    totalCells(), added, regions.size(), dimensionId);
            return new Stats(totalCells(), added);
        } catch (Exception ex) {
            LOGGER.warn("[Vision] Biome sampling failed: {}", ex.getMessage());
            return new Stats(totalCells(), 0);
        }
    }

    // ==================== 内部 ====================

    /** 一帧候选：采样点 + 该 cell 所属区键（区内单值，故取首见代表即可）。 */
    private record Candidate(BlockPos pos, String regionKey) {}

    /** 一个区的 cell 镜像 + 该区最后一次写盘的墙钟毫秒。 */
    private static final class RegionBucket {
        final Map<String, String> cells = new LinkedHashMap<>();
        long updatedAt;
    }

    /** 全部维 union 的 cell 总数（诊断统计用）。 */
    private int totalCells() {
        int total = 0;
        for (Map<String, RegionBucket> regions : byDim.values()) {
            for (RegionBucket b : regions.values()) {
                total += b.cells.size();
            }
        }
        return total;
    }

    /** 方块坐标 → cell 的群系 id；未注册群系（右支直接引用）返回 null。 */
    private static String biomeIdAt(final ClientLevel level, final BlockPos pos) {
        return level.getBiome(pos).unwrap().map(
                k -> k.identifier().toString(),   // 注册表引用 → "minecraft:plains"
                b -> (String) null);              // 直接引用（未注册）→ 跳过
    }

    /** 只写本帧真正变更的那几个区，最后写索引（顺序由 {@link VisionRegions#commit} 钉死）。 */
    private void writeRegions(final String dimensionId, final Set<String> regionKeys) {
        final Map<String, RegionBucket> regions = byDim.get(dimensionId);
        if (regions == null) return;
        final long now = System.currentTimeMillis();
        final List<VisionRegions.RegionWrite> writes = new ArrayList<>(regionKeys.size());
        for (String regionKey : regionKeys) {
            final int[] rxrz = VisionRegions.parseRegionKey(regionKey);
            final RegionBucket bucket = regions.get(regionKey);
            if (rxrz == null || bucket == null) continue;
            bucket.updatedAt = now;
            final CompoundTag cellsTag = new CompoundTag();
            bucket.cells.forEach(cellsTag::putString);
            writes.add(new VisionRegions.RegionWrite(dimensionId, rxrz[0], rxrz[1],
                    VisionRegions.regionRoot(dimensionId, rxrz[0], rxrz[1], now, KEY_CELLS, cellsTag)));
        }
        try {
            VisionRegions.commit(storeDir, writes, index);
            LOGGER.debug("[Vision] Saved biome cells: {} across {}/{} region(s) of {} dim(s) to {}",
                    totalCells(), writes.size(), regions.size(), byDim.size(), storeDir);
        } catch (IOException ex) {
            LOGGER.error("[Vision] Failed to save biome store {}: {}", storeDir, ex.getMessage());
        }
    }

    /**
     * 构造时读入既有区文件 → 内存镜像。{@code Files.exists} 判断读不到"没写过"与"坏了"，故逐区
     * {@code readRegion} 返回 null 即跳过，不抛。
     *
     * <p>索引是<b>派生</b>：从区文件各自的 {@code updatedAt} 重建。与盘上索引不符才补写——补写为了
     * 自愈（崩在"区文件已写、索引未写"之间 / 索引被手动删掉），不符判断为了避免每次启动都无谓地动
     * 索引 mtime（那会让记忆端白跑一轮 diff）。
     */
    private void loadExisting() {
        final Map<String, Map<String, Long>> rebuilt = new LinkedHashMap<>();
        for (Path dimDir : VisionRegions.listDimDirs(storeDir)) {
            final String dirName = dimDir.getFileName().toString();
            for (Path file : VisionRegions.listRegionFiles(dimDir)) {
                final int[] rxrz = VisionRegions.parseRegionFileName(file.getFileName().toString());
                if (rxrz == null) continue;
                final CompoundTag root = VisionRegions.readRegion(file, dirName, rxrz[0], rxrz[1]);
                if (root == null) continue;
                final String dim = root.getStringOr(VisionRegions.KEY_DIMENSION, "");
                final String regionKey = VisionRegions.regionKey(rxrz[0], rxrz[1]);
                final RegionBucket bucket = new RegionBucket();
                final CompoundTag cellsTag = root.getCompoundOrEmpty(KEY_CELLS);
                for (String key : cellsTag.keySet()) {
                    bucket.cells.put(key, cellsTag.getStringOr(key, ""));
                }
                bucket.updatedAt = VisionRegions.updatedAtOf(root);
                byDim.computeIfAbsent(dim, k -> new LinkedHashMap<>()).put(regionKey, bucket);
                rebuilt.computeIfAbsent(dim, k -> new LinkedHashMap<>()).put(regionKey, bucket.updatedAt);
            }
        }
        index.putAll(rebuilt);

        final Map<String, Map<String, Long>> onDisk = VisionRegions.readIndex(storeDir);
        if (!VisionRegions.indexEquals(rebuilt, onDisk)) {
            try {
                VisionRegions.writeIndex(storeDir, index);
                LOGGER.info("[Vision] Region index rebuilt from {} region file(s) → {}", countRegions(rebuilt), storeDir);
            } catch (IOException e) {
                LOGGER.warn("[Vision] Failed to rebuild region index {}: {}", storeDir, e.getMessage());
            }
        }
        if (!rebuilt.isEmpty()) {
            LOGGER.info("[Vision] Loaded {} existing biome cells over {} region(s) of {} dimension(s) from {}",
                    totalCells(), countRegions(rebuilt), rebuilt.size(), storeDir);
        }
    }

    private static int countRegions(final Map<String, Map<String, Long>> index) {
        int n = 0;
        for (Map<String, Long> m : index.values()) n += m.size();
        return n;
    }

    /**
     * 分片前单文件的拆分迁移（§11.13 第 4 项）：读旧 {@code biomes.nbt} → 按区拆写 → 旧文件退役改名。
     *
     * <p>与 {@link #loadExisting} 是<b>并集</b>关系而非二选一：两者都往同一份内存镜像里并，故"迁到一半
     * 被杀进程"下次启动能接着迁完（重复并入幂等）。迁完才退役旧文件，顺序不可反。
     */
    private void migrateLegacyIfPresent() {
        if (!Files.isRegularFile(legacyFilePath)) return;
        try {
            final CompoundTag root = NbtIo.readCompressed(legacyFilePath, NbtAccounter.unlimitedHeap());
            if (root == null) return;
            final long now = System.currentTimeMillis();
            final Map<String, Set<String>> touchedByDim = new LinkedHashMap<>();

            WorldsFile.read(root).worlds().forEach((dim, bucket) -> {
                final Map<String, RegionBucket> regions = byDim.computeIfAbsent(dim, k -> new LinkedHashMap<>());
                final Set<String> touched = touchedByDim.computeIfAbsent(dim, k -> new LinkedHashSet<>());
                final CompoundTag cellsTag = bucket.getCompoundOrEmpty(KEY_CELLS);
                for (String cellKey : cellsTag.keySet()) {
                    final int[] q = parseCellKey(cellKey);
                    final String id = cellsTag.getStringOr(cellKey, "");
                    if (q == null || id.isEmpty()) continue;
                    final String regionKey = VisionRegions.regionKeyOfQuart(q[0], q[2]);
                    final RegionBucket rb = regions.computeIfAbsent(regionKey, k -> new RegionBucket());
                    rb.cells.put(cellKey, id);
                    rb.updatedAt = now;
                    touched.add(regionKey);
                }
            });

            int migrated = 0;
            for (Map.Entry<String, Set<String>> de : touchedByDim.entrySet()) {
                writeRegions(de.getKey(), de.getValue());   // 与增量路径同一条落盘路径
                migrated += de.getValue().size();
            }
            LOGGER.info("[Vision] Migrated legacy {} → {} region file(s) under {}",
                    LEGACY_FILE_NAME, migrated, storeDir);
            VisionRegions.retireLegacyFile(legacyFilePath);
        } catch (Exception e) {
            // 迁移失败不阻塞启动：旧文件保持原地（未退役），下次启动重试；本次以内存里的部分并集继续。
            LOGGER.warn("[Vision] Failed to migrate legacy {}: {}", LEGACY_FILE_NAME, e.getMessage());
        }
    }

    /** cell 键（"qx,qy,qz"）→ 三个 quart 分量；格式不对 → null。 */
    private static int[] parseCellKey(final String cellKey) {
        final String[] parts = cellKey.split(",");
        if (parts.length != 3) return null;
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
