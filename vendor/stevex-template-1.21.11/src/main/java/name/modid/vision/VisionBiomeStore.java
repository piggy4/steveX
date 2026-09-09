package name.modid.vision;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;

/**
 * v2.31（生物群系，见 docs/生物群系复原设计方案.md）：群系 cell NBT 持久化存储 —— 单调 union 覆盖写。
 *
 * <p>群系在游戏内以 <b>4×4×4 quart cell</b> 存储（每 cell 单值），本通道与地形/实体/容器各文件平行，
 * 记录"采集历史 union"的 3D cell → biomeId。同一 cell 内所有方块的 {@code getBiome} 返回值必然相同
 * （游戏存储保证），故<b>逐 cell 写、绝不逐方块写</b>——实现无需值比较。
 *
 * <p>v2.32（世界类型区分）：union 按<b>维度</b>分桶（{@code byDim: Map<dim, Map<cellKey, id>>}）——
 * 主/下界同 quart cell 不再撞键。文件顶层
 * {@code { "currentDimension", "worlds": { <dim>: { "timestamp", "cells": {...} } } }}。
 *
 * <p>文件格式（NBT，与 terrain.nbt 同目录）：
 * <pre>{@code
 * {
 *   "currentDimension": "minecraft:overworld",
 *   "worlds": {
 *     "minecraft:overworld": {
 *       "timestamp": 1720000000,
 *       "cells": { "321,16,-84": "minecraft:plains", ... }   // key = quart 坐标 qx,qy,qz
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p><b>union 语义</b>：构造时读入既有文件并入内存（跨会话累积），每帧只对"不在当前维 union 中"的 cell
 * 做一次 {@code level.getBiome} 解析（O(新增 cell)），新增并入 union；仅在 union 增长时整体覆盖写。
 * 不做删除——真实世界的列群系是静态的，union 单调即正确。
 */
public class VisionBiomeStore {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR_NAME = "stevex/vision";
    private static final String FILE_NAME = "biomes.nbt";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_CELLS = "cells";

    /** v2.32：维度 → 该维 union（cell key（"qx,qy,qz"）→ biome id）。加载并入既有文件，永不删除。 */
    private final Map<String, Map<String, String>> byDim = new LinkedHashMap<>();

    /** v2.32：最近一次采样所属维（文件顶层 currentDimension）。 */
    private String currentDimension = WorldsFile.LEGACY_DIMENSION;

    private final Path filePath;

    public VisionBiomeStore() {
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve(DIR_NAME);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("[Vision] Failed to create directory {}: {}", dir, e.getMessage());
        }
        this.filePath = dir.resolve(FILE_NAME);
        loadExisting();
    }

    /** 单帧增量结果：全部维 union 总数 + 本帧新增 cell 数。 */
    public record Stats(int cells, int added) {}

    /**
     * 采样一帧候选方块并落盘（必须渲染线程调用——内部查 ClientLevel）。
     *
     * <p>候选 = 本帧可见方块集 + 相机 cell 锚点（ObjectResolver 已收集）。以 cell 去重后，
     * 仅对<b>当前维</b>未记录的 cell 解析 biome；有新增才整体写文件。
     *
     * @param dimensionId v2.32：采集时所在维 id，决定更新哪个维的 union
     * @return 采样统计；任何异常被吞掉并记日志（降级为 0 新增，不阻断视觉快照主链路）
     */
    public Stats sync(final ClientLevel level, final List<BlockPos> samplePoints, final String dimensionId) {
        try {
            currentDimension = dimensionId;
            Map<String, String> cells = byDim.computeIfAbsent(dimensionId, k -> new LinkedHashMap<>());

            // 按 cell 去重（同 cell 群系必然相同 → 只留一个代表采样点；cell 间按首见去重已含 union 过滤）
            final Map<String, BlockPos> candidates = new LinkedHashMap<>();
            for (BlockPos p : samplePoints) {
                if (p == null) continue;
                candidates.putIfAbsent(cellKey(p), p);
            }

            int added = 0;
            for (Map.Entry<String, BlockPos> e : candidates.entrySet()) {
                if (cells.containsKey(e.getKey())) continue; // 已在当前维 union
                String id = biomeIdAt(level, e.getValue());
                if (id == null) continue; // 未注册群系（如自定义 registry 缺失）→ 跳过
                cells.put(e.getKey(), id);
                added++;
            }

            if (added > 0) writeFile();
            LOGGER.debug("[Vision] Biome cells: total={}, added={} (dim={})", totalCells(), added, dimensionId);
            return new Stats(totalCells(), added);
        } catch (Exception ex) {
            LOGGER.warn("[Vision] Biome sampling failed: {}", ex.getMessage());
            return new Stats(totalCells(), 0);
        }
    }

    // ==================== 内部 ====================

    /** 全部维 union 的 cell 总数（诊断统计用）。 */
    private int totalCells() {
        int total = 0;
        for (Map<String, String> cells : byDim.values()) {
            total += cells.size();
        }
        return total;
    }

    /** 方块坐标 → cell 的群系 id；未注册群系（右支直接引用）返回 null。 */
    private static String biomeIdAt(final ClientLevel level, final BlockPos pos) {
        return level.getBiome(pos).unwrap().map(
                k -> k.identifier().toString(),   // 注册表引用 → "minecraft:plains"
                b -> (String) null);              // 直接引用（未注册）→ 跳过
    }

    /** 方块坐标 → cell key（quart 坐标，"qx,qy,qz"）。 */
    private static String cellKey(final BlockPos pos) {
        return QuartPos.fromBlock(pos.getX()) + "," + QuartPos.fromBlock(pos.getY()) + "," + QuartPos.fromBlock(pos.getZ());
    }

    /** 整体覆盖写 union（无新增时不调用；各维桶由内存镜像带出）。 */
    private void writeFile() {
        try {
            Map<String, CompoundTag> buckets = new LinkedHashMap<>();
            for (var de : byDim.entrySet()) {
                CompoundTag bucket = new CompoundTag();
                bucket.putLong(KEY_TIMESTAMP, System.currentTimeMillis());
                CompoundTag cellsTag = new CompoundTag();
                de.getValue().forEach(cellsTag::putString);
                bucket.put(KEY_CELLS, cellsTag);
                buckets.put(de.getKey(), bucket);
            }
            NbtIo.writeCompressed(WorldsFile.wrap(currentDimension, buckets), filePath);
            LOGGER.debug("[Vision] Saved biome cells: {} across {} dimension(s) to {}",
                    totalCells(), byDim.size(), filePath);
        } catch (IOException ex) {
            LOGGER.error("[Vision] Failed to save biome store: {}", ex.getMessage());
        }
    }

    /** 构造时读入既有文件并入内存 union（跨会话累积、按维分桶）。文件不存在 / 损坏 → 空，不抛。 */
    private void loadExisting() {
        try {
            if (!Files.exists(filePath)) return;
            CompoundTag root = NbtIo.readCompressed(filePath, NbtAccounter.unlimitedHeap());
            if (root == null) return;
            WorldsFile.Result r = WorldsFile.read(root);
            currentDimension = r.currentDimension();
            for (Map.Entry<String, CompoundTag> e : r.worlds().entrySet()) {
                Map<String, String> cells = new LinkedHashMap<>();
                CompoundTag cellsTag = e.getValue().getCompoundOrEmpty(KEY_CELLS);
                for (String key : cellsTag.keySet()) {
                    cells.put(key, cellsTag.getStringOr(key, ""));
                }
                byDim.put(e.getKey(), cells);
            }
            LOGGER.info("[Vision] Loaded {} existing biome cells across {} dimension(s) from {}",
                    totalCells(), byDim.size(), filePath);
        } catch (Exception e) {
            LOGGER.warn("[Vision] Failed to load biome store {}: {}", filePath, e.getMessage());
        }
    }
}
