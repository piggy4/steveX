package com.example.memworld;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * v2.48（累积观测文件，见 docs/累积观测文件设计方案.md §11）：采集端 {@code name.modid.vision.VisionRegions}
 * 在记忆端的<b>对称实现</b> —— 区坐标计算、目录 / 文件命名、区文件与 {@code _index.nbt} 的<b>读取</b>。
 *
 * <p><b>照抄而非调用</b>：采集端与记忆端是两个独立的 mod / 独立的 jar，不在彼此的 classpath 上。
 * 这与 {@link WorldsFile}、{@code MemoryCellReporter.writeAtomic} 是同一个约定——两边各持一份同样的
 * 实现，靠"逐字同形"保持契约。故本类的常量、算法、日志措辞都应与采集端那份保持一致；改动要改两处。
 *
 * <p><b>记忆端只读</b>，故此处没有 {@code commit} / {@code writeIndex}。写者只有采集端。
 *
 * <p><b>为什么切分片</b>（§11.1）：NBT 没有索引，读一个文件必须整份解压 + 建整棵树。③④⑤ 都是累积
 * union，内容散布在整个世界里，于是文件越大，<b>每一次更新</b>要碰的数据就越多——与"只改了其中一格"
 * 的意图无关。按区切片 + 一份索引后，记忆端每轮<b>只 stat 一个文件</b>（索引），并且<b>只读真正变化的区</b>。
 *
 * <p><b>区尺度照抄 MC</b>：32×32 chunk = {@value #BLOCK_SHIFT} 位 = 512×512 方块，全高度（Y 不进区键）。
 * ④ 的键是 quart，故用 {@link #QUART_SHIFT} = 7（512/4 = 128 = 2<sup>7</sup>）——<b>照抄 9 不会报错，
 * 只会让 ④ 的"区"变成 128 方块见方</b>，与其他文件同名不同义。
 *
 * <p><b>错配自检</b>：区文件自报 {@code dimension} / {@code regionX} / {@code regionZ}，与它所在的
 * 目录名、文件名比对。<b>权威源是目录名与文件名</b>，自报字段只用于发现"文件被搬到别处了"。
 * {@code dimension} 必须自报而不是从目录名反推——目录名是 {@code dimId.replace(':', '_')}，
 * <b>不可逆</b>（namespace 本身可以含 {@code _}）。
 */
final class VisionRegions {

    private static final Logger LOGGER = LoggerFactory.getLogger("stevex-test/memory");

    /** 区边长 = 32 chunk = 512 方块。方块坐标 → 区：{@code x >> 9}。 */
    static final int BLOCK_SHIFT = 9;

    /** quart 坐标 → 区：{@code qx >> 7}（512 / 4 = 128 = 2^7）。<b>不是 {@value #BLOCK_SHIFT}</b>。 */
    static final int QUART_SHIFT = 7;

    static final String KEY_DIMENSION = "dimension";
    static final String KEY_REGION_X = "regionX";
    static final String KEY_REGION_Z = "regionZ";
    static final String KEY_UPDATED_AT = "updatedAt";
    static final String KEY_DIMENSIONS = "dimensions";
    static final String INDEX_FILE_NAME = "_index.nbt";

    private static final String REGION_PREFIX = "r.";
    private static final String REGION_SUFFIX = ".nbt";

    private VisionRegions() {
    }

    // ==================== 区坐标与命名 ====================

    /** 方块坐标 → 所属区键（"rx,rz"）。 */
    static String regionKeyOfBlock(final BlockPos pos) {
        return regionKey(pos.getX() >> BLOCK_SHIFT, pos.getZ() >> BLOCK_SHIFT);
    }

    /** quart 坐标 → 所属区键（"rx,rz"）。④ 用。 */
    static String regionKeyOfQuart(final int quartX, final int quartZ) {
        return regionKey(quartX >> QUART_SHIFT, quartZ >> QUART_SHIFT);
    }

    static String regionKey(final int regionX, final int regionZ) {
        return regionX + "," + regionZ;
    }

    /** 维 id → 维目录名（{@code :} → {@code _}；与采集端同规则）。 */
    static String dimDirName(final String dimensionId) {
        return dimensionId.replace(':', '_');
    }

    // ==================== 读取 ====================

    /**
     * 读索引（§11.9.3）：维 → 区键 → updatedAt。这是记忆端每轮的<b>唯一一次 stat</b>——
     * 索引只在真有区变化时才被采集端重写，故"mtime 未变 ⇒ 什么都不用做"这条门控前提成立
     * （{@code pollIntervalTicks = 1} 的"stat 成本≈零"，见 {@code MemoryConfig}）。
     *
     * <p>文件缺席 / 损坏 → 空表（调用方见空表即视为"还没有任何区"）。
     */
    static Map<String, Map<String, Long>> readIndex(final Path storeDir) {
        final Map<String, Map<String, Long>> out = new LinkedHashMap<>();
        final Path idx = storeDir.resolve(INDEX_FILE_NAME);
        if (!Files.isRegularFile(idx)) return out;
        try {
            final CompoundTag root = NbtIo.readCompressed(idx, NbtAccounter.unlimitedHeap());
            if (root == null) return out;
            final CompoundTag dims = root.getCompoundOrEmpty(KEY_DIMENSIONS);
            for (String dim : dims.keySet()) {
                final CompoundTag regions = dims.getCompoundOrEmpty(dim);
                final Map<String, Long> m = new LinkedHashMap<>();
                for (String rk : regions.keySet()) {
                    m.put(rk, regions.getLongOr(rk, 0L));
                }
                out.put(dim, m);
            }
        } catch (IOException | RuntimeException e) {
            // v2.48.1：连 RuntimeException 一起接——截断 gzip 流抛的是非受检的 ReportedNbtException，
            // 只接 IOException 会漏过去崩服。此处按"空表"降级，调用方见空表即不推进 mtime、下轮重试。
            LOGGER.warn("[MemoryWorld] Failed to read region index {}: {}", idx, e.getMessage());
        }
        return out;
    }

    /**
     * 读一个区文件并做错配自检；{@code null} = 文件不存在 / 读不出来 / 错配。
     *
     * <p>校验用 {@code dimDirName(自报维) == 目录名}，两边同规则，故不受 {@code _} 不可逆影响。
     * 错配时返回 {@code null} 而不是容忍读入：内容按绝对坐标键控，放进错误的维等于把记录搬了家。
     */
    static CompoundTag readRegion(final Path file, final String expectedDimDirName,
                                  final int expectedRegionX, final int expectedRegionZ) {
        if (!Files.isRegularFile(file)) return null;
        try {
            final CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            if (root == null) return null;
            final String dim = root.getStringOr(KEY_DIMENSION, "");
            final int rx = root.getIntOr(KEY_REGION_X, Integer.MIN_VALUE);
            final int rz = root.getIntOr(KEY_REGION_Z, Integer.MIN_VALUE);
            if (dim.isEmpty() || !dimDirName(dim).equals(expectedDimDirName)
                    || rx != expectedRegionX || rz != expectedRegionZ) {
                LOGGER.error("[MemoryWorld] Region file misplaced: {} declares dim={} r.{}.{} but sits under {} as r.{}.{} — skipped",
                        file, dim.isEmpty() ? "<none>" : dim, rx, rz,
                        expectedDimDirName, expectedRegionX, expectedRegionZ);
                return null;
            }
            return root;
        } catch (IOException | RuntimeException e) {
            // v2.48.1：同上——非受检的 ReportedNbtException 也要接住；返回 null ⇒ 该区的 updatedAt
            // 不推进 ⇒ 下一轮重试（这正是"半截写"应有的降级行为）。
            LOGGER.warn("[MemoryWorld] Failed to read region file {}: {}", file, e.getMessage());
            return null;
        }
    }

    /** 区文件路径（读路径不建目录）。 */
    static Path regionFile(final Path storeDir, final String dimensionId, final int regionX, final int regionZ) {
        return storeDir.resolve(dimDirName(dimensionId)).resolve(REGION_PREFIX + regionX + "." + regionZ + REGION_SUFFIX);
    }

    /** 区键（"rx,rz"）→ 区坐标 {@code [rx, rz]}；格式不对 → null。 */
    static int[] parseRegionKey(final String regionKey) {
        final int comma = regionKey.indexOf(',');
        if (comma <= 0 || comma == regionKey.length() - 1) return null;
        try {
            return new int[]{
                    Integer.parseInt(regionKey.substring(0, comma)),
                    Integer.parseInt(regionKey.substring(comma + 1))
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 位置键（"x,y,z"）→ {@link BlockPos}；格式不对 → null。 */
    static BlockPos parsePosKey(final String posKey) {
        final int c1 = posKey.indexOf(',');
        final int c2 = c1 < 0 ? -1 : posKey.indexOf(',', c1 + 1);
        if (c1 <= 0 || c2 < 0 || c2 == posKey.length() - 1 || posKey.indexOf(',', c2 + 1) >= 0) return null;
        try {
            return new BlockPos(
                    Integer.parseInt(posKey, 0, c1, 10),
                    Integer.parseInt(posKey, c1 + 1, c2, 10),
                    Integer.parseInt(posKey, c2 + 1, posKey.length(), 10));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
