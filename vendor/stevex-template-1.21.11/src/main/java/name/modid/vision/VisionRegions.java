package name.modid.vision;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;

/**
 * v2.48（累积观测文件，见 docs/累积观测文件设计方案.md §11）：四个累积文件的 <b>region 分片</b>共用工具
 * —— 区坐标计算、目录 / 文件命名、区文件读写、{@code _index.nbt} 读写。
 *
 * <p><b>为什么切</b>（§11.1）：NBT 没有索引，读一个文件必须整份解压 + 建整棵树。①④⑤⑧ 都是"累积 union"，
 * 内容只增不减、散布在整个世界里，于是文件越大，<b>每一次更新</b>（采集端写、记忆端读、agent 读）要碰的
 * 数据就越多——与"只改了其中一格"的意图无关。按区切片后，一次更新只碰它所在的那个区。
 *
 * <p><b>区尺度照抄 MC</b>（用户 2026-10-07 定案）：32×32 chunk = {@value #BLOCK_SHIFT} 位 = 512×512 方块，
 * <b>全高度</b>（Y 不进区键，只按 X/Z 切）——与本 mod 既有的"每维桶"处理同向：Y 范围只有几百格，
 * 而 X/Z 无限，切分收益全在水平面。
 *
 * <p><b>两个陷阱，写死在此类</b>：
 * <ol>
 *   <li><b>quart 空间的移位不是 9</b>。④ 的键是 {@code QuartPos}（4 格 1 cell），区边长 512 方块 =
 *       128 quart = 2<sup>7</sup>，故 {@link #QUART_SHIFT} = 7。照抄 9 不会报错、不会崩，只会让 ④ 的
 *       "区"变成 128 方块见方——<b>同名不同义</b>，等到发现时文件已经写乱了。</li>
 *   <li><b>区键是纯函数</b>（{@code x >> 9}），因此区坐标不落盘也不影响正确性；落盘的
 *       {@code regionX}/{@code regionZ}/{@code dimension} 是<b>非权威的定位声明</b>（权威源 = 目录名与
 *       文件名），只用于错配自检。</li>
 * </ol>
 *
 * <p><b>区文件格式</b>（§11.5；{@code dimension} 为实施期补充，见下）：
 * <pre>{@code
 * // <store>/minecraft_overworld/r.0.0.nbt
 * {
 *   "dimension": "minecraft:overworld",   // 自报维（= 目录名，非权威）
 *   "regionX": 0,                         // 自报区坐标（= 文件名，非权威）
 *   "regionZ": 0,
 *   "updatedAt": 1720000000000,           // 本区最后一次写盘的墙钟毫秒
 *   "<leafKey>": { ... }                  // 桶内键名与条目形态逐字沿用分片前的形态
 * }
 * }</pre>
 *
 * <p><b>为什么区文件里还要写 {@code dimension}</b>（实施期发现，设计文档 §11.5 未预见）：目录名是
 * {@code dimId.replace(':', '_')}，<b>不可逆</b>——namespace 本身可以含 {@code _}（如 {@code my_mod:foo}
 * → {@code my_mod_foo}），回推成 {@code my:mod_foo} 就错了。而采集端<b>启动时必须读完所有区文件</b>才能
 * 重建内存 union，此时它还不知道本机出现过哪些维 id，只能靠目录名反推。与其猜，不如让文件自报——
 * 与已定案的 {@code regionX}/{@code regionZ} 是同一个原理，且让整棵树在 {@code _index.nbt} 丢失时
 * 仍可<b>精确</b>重建（索引只服务记忆端的门控，不是数据源）。
 *
 * <p><b>{@code _index.nbt}</b>（§11.9.3）：放在四个 store 目录的<b>根</b>（不在各维子目录里），
 * 这样记忆端每轮仍然只 stat <b>一个</b>文件就拿到"哪些区变了"——{@code MemoryConfig} 的
 * {@code pollIntervalTicks = 1}（"每 tick 1 次 stat，成本≈零"，MemoryConfig.java:23）这条
 * <b>已被写进设计的不变量不能被目录扫描破坏</b>。
 * <pre>{@code
 * { "dimensions": { "minecraft:overworld": { "0,0": 1720000000000, "-1,0": 1720000000123 } } }
 * }</pre>
 *
 * <p><b>写入顺序钉死</b>：{@link #commit} 先写全部区文件、最后写索引（两者都是原子写）。反过来的话，
 * 记忆端可能读到"索引说某某区更新了，而那个区文件还没落盘"——索引是门控，门控必须只指向已存在的东西。
 * 代价是崩在两次写之间时索引会<b>落后</b>一格（该区的这次更新要等它下次变更才可见）；这与 WAL 的
 * "数据先、指针后"是同一个取舍，且落后方向安全（不会读到不存在的东西）。采集端下次启动时
 * {@code load} 会从区文件自身的 {@code updatedAt} 重建索引并补写，故只是延迟、不会永久丢。
 *
 * <p>本类的对称实现在记忆端 {@code com.example.memworld}（另一个 mod，不在本 mod 的 classpath 上）——
 * <b>照抄而非调用</b>，与 {@code WorldsFile}、{@code MemoryCellReporter.writeAtomic} 同一约定。
 */
final class VisionRegions {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 区边长 = 32 chunk = 512 方块（照抄 MC 的 region 尺度，用户 2026-10-07 定案）。
     * 方块坐标 → 区：{@code x >> 9}。
     */
    static final int BLOCK_SHIFT = 9;

    /**
     * quart 坐标 → 区：{@code qx >> 7}。<b>不是 {@value #BLOCK_SHIFT}</b>——见类 javadoc 的陷阱 ①。
     * 512 方块 / 4 = 128 quart = 2<sup>7</sup>。
     */
    static final int QUART_SHIFT = 7;

    static final String KEY_DIMENSION = "dimension";
    static final String KEY_REGION_X = "regionX";
    static final String KEY_REGION_Z = "regionZ";
    static final String KEY_UPDATED_AT = "updatedAt";
    static final String KEY_DIMENSIONS = "dimensions";
    static final String INDEX_FILE_NAME = "_index.nbt";

    /** 区文件名前缀 / 后缀：{@code r.<X>.<Z>.nbt}。 */
    private static final String REGION_PREFIX = "r.";
    private static final String REGION_SUFFIX = ".nbt";

    private VisionRegions() {
    }

    // ==================== 区坐标与命名 ====================

    static int blockRegionX(final int blockX) {
        return blockX >> BLOCK_SHIFT;
    }

    static int blockRegionZ(final int blockZ) {
        return blockZ >> BLOCK_SHIFT;
    }

    /** 方块坐标 → 所属区键（"rx,rz"）。 */
    static String regionKeyOfBlock(final BlockPos pos) {
        return regionKey(blockRegionX(pos.getX()), blockRegionZ(pos.getZ()));
    }

    /** quart 坐标 → 所属区键（"rx,rz"）。④ 用（{@link #QUART_SHIFT}）。 */
    static String regionKeyOfQuart(final int quartX, final int quartZ) {
        return regionKey(quartX >> QUART_SHIFT, quartZ >> QUART_SHIFT);
    }

    /** 区坐标 → 区键（"rx,rz"；索引与内存 map 用）。 */
    static String regionKey(final int regionX, final int regionZ) {
        return regionX + "," + regionZ;
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

    /** 区坐标 → 区文件名（"r.<X>.<Z>.nbt"；权威定位源）。 */
    static String regionFileName(final int regionX, final int regionZ) {
        return REGION_PREFIX + regionX + "." + regionZ + REGION_SUFFIX;
    }

    /** 文件名 → 区坐标 {@code [rx, rz]}；不是区文件名 → null。 */
    static int[] parseRegionFileName(final String fileName) {
        if (!fileName.startsWith(REGION_PREFIX) || !fileName.endsWith(REGION_SUFFIX)) return null;
        final String body = fileName.substring(REGION_PREFIX.length(), fileName.length() - REGION_SUFFIX.length());
        final int dot = body.indexOf('.');
        if (dot <= 0 || dot == body.length() - 1) return null;
        try {
            return new int[]{Integer.parseInt(body.substring(0, dot)), Integer.parseInt(body.substring(dot + 1))};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 维 id → 维目录名（{@code :} → {@code _}）。<b>不可逆</b>——故区文件自报 {@code dimension}。 */
    static String dimDirName(final String dimensionId) {
        return dimensionId.replace(':', '_');
    }

    /** 位置键（"x,y,z"）→ {@link BlockPos}；格式不对 → null（加载 / 迁移 / 修剪路径用）。 */
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

    // ==================== 目录遍历 ====================

    /** store 根下的维子目录（只返回目录；索引文件与 {@code _pose.nbt} / {@code ender.nbt} 不在子目录里）。 */
    static List<Path> listDimDirs(final Path storeDir) {
        if (!Files.isDirectory(storeDir)) return List.of();
        try (Stream<Path> s = Files.list(storeDir)) {
            return s.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) {
            LOGGER.warn("[Vision] Failed to list region store dir {}: {}", storeDir, e.getMessage());
            return List.of();
        }
    }

    /** 某维目录下的全部区文件（按文件名排序，确定性）。 */
    static List<Path> listRegionFiles(final Path dimDir) {
        if (!Files.isDirectory(dimDir)) return List.of();
        try (Stream<Path> s = Files.list(dimDir)) {
            return s.filter(p -> Files.isRegularFile(p) && parseRegionFileName(p.getFileName().toString()) != null)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            LOGGER.warn("[Vision] Failed to list region dir {}: {}", dimDir, e.getMessage());
            return List.of();
        }
    }

    /** 区文件绝对路径（不创建目录；写路径请用 {@link #commit}，它会建）。 */
    static Path regionFile(final Path storeDir, final String dimensionId, final int regionX, final int regionZ) {
        return storeDir.resolve(dimDirName(dimensionId)).resolve(regionFileName(regionX, regionZ));
    }

    // ==================== 区文件读写 ====================

    /**
     * 组装区文件根 tag（§11.5）。{@code leafKey} 是桶内正文的键名（③ {@code blockEntities} /
     * ④ {@code cells} / ⑤ {@code containers} / ⑧ {@code blocks}）——<b>逐字沿用分片前的键名</b>，
     * 记忆端一套解析代码可读分片前后两种形态。
     */
    static CompoundTag regionRoot(final String dimensionId, final int regionX, final int regionZ,
                                  final long updatedAt, final String leafKey, final CompoundTag leaf) {
        final CompoundTag root = new CompoundTag();
        root.putString(KEY_DIMENSION, dimensionId);
        root.putInt(KEY_REGION_X, regionX);
        root.putInt(KEY_REGION_Z, regionZ);
        root.putLong(KEY_UPDATED_AT, updatedAt);
        root.put(leafKey, leaf);
        return root;
    }

    /**
     * 读一个区文件并做错配自检；{@code null} = 文件不存在 / 读不出来 / 错配。
     *
     * <p>调用方此时<b>只知道目录名</b>（{@code expectedDimDirName}，如 {@code minecraft_overworld}）——
     * 真正的维 id 要从文件自报的 {@code dimension} 读回来，这正是该字段存在的理由（见类 javadoc）。
     * 校验用的是 {@code dimDirName(自报维) == 目录名}，两边同规则，故不受 {@code _} 不可逆影响。
     *
     * <p>错配指自报的维 / 区坐标与文件<b>所在的位置</b>不符——权威源是目录名与文件名（§11.5）。
     * 此时返回 {@code null} 而不是容忍读入：内容是按绝对坐标键控的，放进错误的桶里等于把记录搬到另一个维，
     * 比丢掉更糟。错配只可能来自人为搬动文件，正常写入不会产生。
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
                LOGGER.error("[Vision] Region file misplaced: {} declares dim={} r.{}.{} but sits under {} as r.{}.{} — skipped",
                        file, dim.isEmpty() ? "<none>" : dim, rx, rz,
                        expectedDimDirName, expectedRegionX, expectedRegionZ);
                return null;
            }
            return root;
        } catch (IOException | RuntimeException e) {
            // v2.48.1：连 RuntimeException 一起接——截断 gzip 流抛的是非受检的 ReportedNbtException。
            LOGGER.warn("[Vision] Failed to read region file {}: {}", file, e.getMessage());
            return null;
        }
    }

    /** 区文件自报的 {@code updatedAt}（调用方已通过 {@link #readRegion} 校验过位置）。 */
    static long updatedAtOf(final CompoundTag regionRoot) {
        return regionRoot.getLongOr(KEY_UPDATED_AT, 0L);
    }

    /**
     * 旧单文件退役（§11.13 第 4 项）：读完拆写后<b>改名</b>加 {@code .migrated} 后缀，<b>不删</b>。
     *
     * <p>改名而非原地保留是<b>必须</b>的：记忆端的旧路径探测（{@code MemoryConfig.resolveSourceFile}）
     * 还在按 {@code stevex/vision/<name>.nbt} 找文件，旧文件留在原地会让它继续读到<b>已过期</b>的内容
     * ——比读不到更糟（读不到走新目录，读到旧文件则静默错）。也不删：迁移万一出错，旧文件是唯一的退路。
     */
    static void retireLegacyFile(final Path legacyFile) {
        if (!Files.isRegularFile(legacyFile)) return;
        final String name = legacyFile.getFileName().toString();
        final Path retired = legacyFile.resolveSibling(name + ".migrated");
        try {
            Files.move(legacyFile, retired, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[Vision] Legacy single file migrated & retired: {} → {}", name, retired.getFileName());
        } catch (IOException e) {
            LOGGER.warn("[Vision] Failed to retire legacy file {}: {}", legacyFile, e.getMessage());
        }
    }

    // ==================== 落盘（顺序钉死） ====================

    /** 一次待落盘的区文件：位置 + 已建好的根 tag（{@code updatedAt} 已写入其中）。 */
    record RegionWrite(String dimensionId, int regionX, int regionZ, CompoundTag root) {
    }

    /**
     * 落盘一批区文件，<b>最后</b>写索引（§11.9.3）。{@code writes} 为空则<b>连索引都不写</b>——
     * 这正是记忆端门控有效的前提：索引 mtime 只在真有条目变化时才动。
     *
     * <p>索引由本方法就地合入（{@code index} 是调用方的活索引：维 → 区键 → updatedAt），
     * 值取自各区文件的 {@code updatedAt}，故索引与文件永不互相矛盾。区文件是<b>权威</b>，
     * 索引是它的<b>派生</b>——这也是"启动时能从区文件重建索引"的原因。
     *
     * @param storeDir store 根目录（索引所在；维子目录由本方法创建）
     * @param writes 本次变更的区（只写变化的，未列出的区纹丝不动）
     * @param index 调用方的活索引，本方法就地更新
     */
    static void commit(final Path storeDir, final List<RegionWrite> writes,
                       final Map<String, Map<String, Long>> index) throws IOException {
        for (RegionWrite w : writes) {
            final Path file = regionFile(storeDir, w.dimensionId(), w.regionX(), w.regionZ());
            Files.createDirectories(file.getParent());
            UnionSaveScheduler.writeAtomic(w.root(), file);
            index.computeIfAbsent(w.dimensionId(), k -> new LinkedHashMap<>())
                    .put(regionKey(w.regionX(), w.regionZ()), updatedAtOf(w.root()));
        }
        if (!writes.isEmpty()) {
            writeIndex(storeDir, index);
        }
    }

    // ==================== _index.nbt ====================

    /** 读索引（§11.9.3）；不存在 / 损坏 → 空表（调用方随后从区文件重建）。 */
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
            // v2.48.1：连 RuntimeException 一起接——截断 gzip 流抛的是非受检的 ReportedNbtException。
            LOGGER.warn("[Vision] Failed to read region index {}: {}", idx, e.getMessage());
        }
        return out;
    }

    /** 写索引（原子写）。正常路径由 {@link #commit} 调用——不要在别处单独调，那会打破"区文件先"的顺序。 */
    static void writeIndex(final Path storeDir, final Map<String, Map<String, Long>> index) throws IOException {
        Files.createDirectories(storeDir);
        final CompoundTag dims = new CompoundTag();
        for (Map.Entry<String, Map<String, Long>> de : index.entrySet()) {
            final CompoundTag regions = new CompoundTag();
            for (Map.Entry<String, Long> re : de.getValue().entrySet()) {
                regions.putLong(re.getKey(), re.getValue());
            }
            dims.put(de.getKey(), regions);
        }
        final CompoundTag root = new CompoundTag();
        root.put(KEY_DIMENSIONS, dims);
        UnionSaveScheduler.writeAtomic(root, storeDir.resolve(INDEX_FILE_NAME));
    }

    /** 递归删除整棵 store 目录（{@code clear()} 用）。不存在 → 静默返回。 */
    static void deleteRecursively(final Path dir) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            // 反序：先删文件与空目录，最后删根
            for (Path p : s.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            LOGGER.warn("[Vision] Failed to delete region store {}: {}", dir, e.getMessage());
        }
    }

    /** 两个索引是否逐条相同（含区集合）——启动重建后据此决定要不要补写，避免无谓地动 mtime。 */
    static boolean indexEquals(final Map<String, Map<String, Long>> a, final Map<String, Map<String, Long>> b) {
        if (a.size() != b.size()) return false;
        for (Map.Entry<String, Map<String, Long>> e : a.entrySet()) {
            final Map<String, Long> other = b.get(e.getKey());
            if (other == null || !other.equals(e.getValue())) return false;
        }
        return true;
    }
}
