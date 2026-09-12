package name.modid.vision;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;

/**
 * v2.23（§7.11）反向通道读取器 —— 读取记忆侧 {@code MemoryCellReporter} 写的
 * {@code memory_cells.bin}，供 {@link DeletionJudge} 做逐块深度判定。
 *
 * <p>格式（{@code MemoryCellReporter} 写入，小端）：
 * <pre>{@code
 *   [0..3]   magic "SCEL"（4 字节）
 *   [4]      version（1 字节）：1 = 旧版（无维标签）；2 = v2.32 起（带 UTF-8 维 id）；
 *                              3 = v2.36 起（追加 translucent 段）；
 *                              4 = v2.37 起（追加 spriteEpoch + 非满形状几何段）；
 *                              5 = v2.37 七次修订起（追加信号缺失段，见下）
 *
 *   version = 5（v2.37 七次修订，§15.3）：在 version=4 全布局之后追加
 *   [..]     int signalLossCount（镜像中当前在场的信号缺失族格数；空段恒写 0）
 *   signalLossCount × {
 *     long   BlockPos.asLong
 *     ushort blockIdLen + UTF-8 block 注册 id（如 minecraft:tripwire；诊断 + 族扩展用，
 *            {@link SignalLossCorrector} 不依赖它做裁决——裁决只看"真实世界读到什么"）
 *   }
 *   <b>本段是 v5 的最后一段</b>。写入方 {@code MemoryCellReporter} 恒写该段（空也写 4 字节计数）。
 *   version=4 文件按 length 自洽解析：{@code signalLossCells} 恒空、其余逐位不变（无迁移负担）。
 *
 *   version = 4（v2.37，§4.3.2）：在 version=3 全布局之后追加
 *   [..]     long spriteEpoch（本文件几何段引用的 sprite alpha 表 epoch；见 {@link SpriteTableCache}。
 *            与 memory_sprites.bin 的 epoch 不符 ⇒ 整段几何作废 = fail-closed，欠删方向）
 *   [..]     int shapedCount（非满形状格数）
 *   shapedCount × {
 *     long   BlockPos.asLong
 *     ushort blockIdLen + UTF-8 block 注册 id（如 minecraft:oak_fence；承重字段，采集侧据此
 *            路由判据场 + 定 alpha 阈值，见 ShapedCellData.ShapedCell）
 *     byte   quadCount（上限 255；写入条数与计数严格一致）
 *     quadCount × {
 *       12 × float 顶点 p0..p3（格内局部 [0,1]，vanilla 原始顺序）
 *        8 × float UV（sprite 局部 [0,1]：uv0.u,uv0.v,uv1.u,uv1.v,...）
 *        3 × float 单位法线（自算 normalize(cross(p1−p0,p2−p0))）
 *        int       spriteIndex（本 epoch sprite 表下标；0 起）
 *     }   // 每条 quad 恒 96 字节
 *   }
 *
 *   version = 3（v2.36，§7.12）：
 *   [5..8]   int 维 id 字节长度 L（UTF-8）
 *   [9..9+L) UTF-8 dimensionId（记忆侧活动维）
 *   之后与 version=1 相同（整体偏移 +L+4）：
 *   [..]     int removalPixelThreshold（删除判定像素阈值，采集侧读取）
 *   [..]     double removalMaxRayDist（记忆侧距离球过滤半径，信息性）
 *   [..]     int opaqueCount（main/opaque 段格数：实心不透明 + 冻结实体占用格 + 岩浆）
 *   [..]     opaqueCount × 8 字节 long（BlockPos.asLong，小端）
 *   [..]     byte removalTranslucentEnabled（记忆侧 translucent 场开关，采集侧据此路由）
 *   [..]     int translucentCount（translucent 段格数：水 + 满格透明）
 *   [..]     translucentCount × 8 字节 long（BlockPos.asLong，小端）
 *
 *   version = 2（v2.32，读取兼容）：
 *   [5..8]   int 维 id 字节长度 L（UTF-8）
 *   [9..9+L) UTF-8 dimensionId（记忆侧活动维）
 *   [..]     int removalPixelThreshold
 *   [..]     double removalMaxRayDist
 *   [..]     int count（格数）
 *   [..]     count × 8 字节 long（BlockPos.asLong，小端）
 *   （无 translucent 段 → translucentCells 空、translucentEnabled=false，行为等同 v2.23）
 *
 *   version = 1（旧版，读取兼容）：
 *   [5..8]   int removalPixelThreshold
 *   [9..16]  double removalMaxRayDist
 *   [17..20] int count（格数）
 *   [21..]   count × 8 字节 long（BlockPos.asLong，小端）
 * }</pre>
 * <p>version=1 文件没有维标签 → {@link CellsData#dimension()} 为空串；采集侧据此视为
 * 「维度未知」、删除证据置空（宁可不删，不误删）。
 *
 * <p>读取语义（§7.11）：
 * <ul>
 *   <li><b>mtime 门控</b>：快照时 stat 文件 mtime，未变不读（复用上次解析结果）；</li>
 *   <li><b>半截写防护</b>：解析失败（写中途 / 损坏）→ 保留旧 mtime，下轮重试；</li>
 *   <li><b>优雅降级</b>：文件缺失（记忆侧离线）→ 空格清单 + 默认阈值 → 采集侧无删除证据
 *       → 只增不删（恢复后自愈：记忆侧下次写 cells，采集侧重新判定）。</li>
 * </ul>
 *
 * <p>文件路径 = 本采集器 {@code <gameDir>/stevex/vision/memory_cells.bin}（与 terrain.nbt 同目录；
 * 记忆侧 {@code MemoryConfig.resolveMemoryCellsFile} 自动探测到 terrain.nbt 所在目录、写同一路径）。
 */
public final class MemoryCellsReader {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR_NAME = "stevex/vision";
    private static final String FILE_NAME = "memory_cells.bin";
    private static final byte[] MAGIC = {'S', 'C', 'E', 'L'};
    /** 格式版本——1 = 旧版无维标签；2 = v2.32 起带 UTF-8 维 id；3 = v2.36 起带 translucent 段；
     *  4 = v2.37 起带 spriteEpoch + 非满形状几何段；5 = v2.37 七次修订起带信号缺失段（§15）。 */
    private static final int VERSION_1 = 1;
    private static final int VERSION_2 = 2;
    private static final int VERSION_3 = 3;
    private static final int VERSION_4 = 4;
    private static final int VERSION_5 = 5;
    /** 解析失败 / 文件缺失时的默认阈值（与设计 §7.11 默认一致；正常由文件头提供）。 */
    public static final int DEFAULT_PIXEL_THRESHOLD = 2;
    public static final double DEFAULT_MAX_RAY_DIST = 96.0;

    /**
     * 一帧 cells 数据：待判定记忆格（v2.36 分两段）+ 记忆侧下发的删除阈值 + v2.32 维标签。
     *
     * @param cells             main/opaque 段记忆格（实心不透明 + 冻结实体占用格 + 岩浆；main 场判）
     * @param translucentCells  v2.36 translucent 段记忆格（水 + 满格透明；按采集侧配置路由场判定，
     *                          version ≤ 2 旧文件 → 恒空）
     * @param translucentEnabled v2.36 记忆侧 translucent 场开关（随文件头下发；version ≤ 2 旧文件 → false）。
     *                          false = 采集侧对该段判据空集（幽灵暂留），与记忆侧配置对齐
     * @param pixelThreshold    越过像素删除阈值
     * @param maxRayDist        记忆侧距离球过滤半径（信息性）
     * @param dimension         记忆侧写入 cells 时所在的活动维（version=1 旧文件 / 缺失 → 空串 = 维度未知）
     * @param spriteEpoch       v2.37 几何段引用的 sprite alpha 表 epoch（version ≤ 3 旧文件 → 0）。
     *                          必须与 {@link SpriteTableCache} 已载入的 {@code memory_sprites.bin} epoch
     *                          一致，几何段才可用；不符 ⇒ 全段作废（fail-closed，欠删）
     * @param shapedCells       v2.37 非满形状格（{@link ShapedCellData.ShapedCell}；version ≤ 3 旧文件 → 空）。
     *                          判据场路由与 alpha 阈值在 {@code ObjectResolver} 侧解析（依赖采集侧
     *                          {@code cutoutLeaves} 设置，故不能在记忆侧定）
     * @param signalLossCells   v2.37 七次修订（§15）信号缺失族格（镜像中当前在场的绊线/绊线钩；
     *                          version ≤ 4 旧文件 → 空 = 该族不判删）。判据由
     *                          {@link SignalLossCorrector} 用"真实世界状态直读"给出，**不走深度场**
     */
    public record CellsData(
            List<BlockPos> cells,
            List<BlockPos> translucentCells,
            boolean translucentEnabled,
            int pixelThreshold,
            double maxRayDist,
            String dimension,
            long spriteEpoch,
            List<ShapedCellData.ShapedCell> shapedCells,
            List<SignalLossCell> signalLossCells
    ) {
        static final CellsData EMPTY = new CellsData(
                List.of(), List.of(), false, DEFAULT_PIXEL_THRESHOLD, DEFAULT_MAX_RAY_DIST, "", 0L,
                List.of(), List.of());
    }

    /**
     * v2.37 七次修订（设计 §15.3）信号缺失段的一个条目：格 + 镜像侧的方块注册 id。
     *
     * <p><b>blockId 在本通道里不是承重字段</b>（与几何段的对比见 §4.3.4）：裁决只看"真实世界读到
     * 什么"（{@link SignalLossCorrector}），与镜像侧记的是什么无关。它保留是为了诊断（能分清上报的
     * 是绊线还是绊线钩）与**族扩展**（将来若要按族细分规则，不必改文件格式）。
     */
    public record SignalLossCell(BlockPos pos, String blockId) {}

    private final Path filePath;
    private FileTime lastMtime;
    private CellsData cached;

    public MemoryCellsReader() {
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve(DIR_NAME);
        this.filePath = dir.resolve(FILE_NAME);
    }

    /**
     * 读取（mtime 门控）。文件缺失 → 空数据（优雅降级）；解析失败 → 保留旧 mtime、返回上次
     * 结果（或空），下轮重试。快照时调用，成本 = 一次 stat + 偶尔几十 KB 读。
     */
    public CellsData read() {
        if (!Files.exists(filePath)) {
            // 记忆侧离线 / 尚未写过 → 无删除证据 → 优雅降级；文件重新出现后 mtime 变化自然触发读取
            lastMtime = null;
            cached = null;
            return CellsData.EMPTY;
        }
        final FileTime mtime;
        try {
            mtime = Files.getLastModifiedTime(filePath);
        } catch (IOException e) {
            LOGGER.warn("[Vision] Failed to stat cells file {}: {}", filePath, e.getMessage());
            return cached == null ? CellsData.EMPTY : cached;
        }
        if (mtime.equals(lastMtime)) {
            return cached == null ? CellsData.EMPTY : cached; // 未变不读（mtime 门控）
        }

        final CellsData parsed = parse(filePath);
        if (parsed == null) {
            // 半截写 / 损坏 → 不推进 mtime，下轮重试（§7.4 语义）；先用上次结果兜底
            return cached == null ? CellsData.EMPTY : cached;
        }
        lastMtime = mtime; // 只在成功解析后才推进
        cached = parsed;
        LOGGER.debug("[Vision] Read {} memory cells (threshold={}, maxDist={}) from {}",
                parsed.cells().size(), parsed.pixelThreshold(), parsed.maxRayDist(), filePath);
        return parsed;
    }

    /** 解析二进制文件；失败返回 null（调用方不推进 mtime）。 */
    private static CellsData parse(final Path path) {
        try {
            final byte[] bytes = Files.readAllBytes(path);
            if (bytes.length < 21) {
                LOGGER.warn("[Vision] Cells file too short ({} bytes)", bytes.length);
                return null;
            }
            for (int i = 0; i < MAGIC.length; i++) {
                if (bytes[i] != MAGIC[i]) {
                    LOGGER.warn("[Vision] Cells file bad magic at {}: {}", path, path);
                    return null;
                }
            }
            final int ver = bytes[4];
            if (ver != VERSION_1 && ver != VERSION_2 && ver != VERSION_3 && ver != VERSION_4
                    && ver != VERSION_5) {
                LOGGER.warn("[Vision] Cells file unsupported version {}", ver);
                return null;
            }
            final ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            int o = 5;
            String dimension = "";
            if (ver != VERSION_1) {
                // v2.32：version≥2 → [5..8] 维 id 字节长度 L，[9..9+L) UTF-8 dimensionId
                final int len = buf.getInt(o);
                o += 4;
                if (len < 0 || o + len + 16 > bytes.length) {
                    LOGGER.warn("[Vision] Cells file bad dimension length {} (len={})", len, bytes.length);
                    return null;
                }
                dimension = new String(bytes, o, len, StandardCharsets.UTF_8);
                o += len;
            }
            final int threshold = buf.getInt(o);
            final double maxDist = buf.getDouble(o + 4);
            final int count = buf.getInt(o + 12);
            final int bodyOffset = o + 16;
            if (count < 0 || bytes.length < bodyOffset + (long) count * 8) {
                LOGGER.warn("[Vision] Cells file truncated (count={}, len={})", count, bytes.length);
                return null;
            }
            final List<BlockPos> cells = new ArrayList<>(count);
            buf.position(bodyOffset);
            for (int i = 0; i < count; i++) {
                cells.add(BlockPos.of(buf.getLong()));
            }
            if (ver < VERSION_3) {
                // v2.36：version ≤ 2 旧文件无 translucent 段 → translucentCells 空、开关 false（行为等同 v2.23）
                return new CellsData(cells, List.of(), false,
                        threshold <= 0 ? DEFAULT_PIXEL_THRESHOLD : threshold,
                        maxDist > 0 ? maxDist : DEFAULT_MAX_RAY_DIST, dimension, 0L, List.of(), List.of());
            }
            // v2.36：version=3 → opaque longs 之后追加 translucentEnabled byte + translucentCount + longs
            int p = bodyOffset + count * 8;
            if (bytes.length < p + 5) { // byte + int（translucentCount）
                LOGGER.warn("[Vision] Cells file truncated before translucent segment (len={})", bytes.length);
                return null;
            }
            final boolean translucentEnabled = bytes[p] != 0;
            final int tCount = buf.getInt(p + 1);
            final int tBodyOffset = p + 5;
            if (tCount < 0 || bytes.length < tBodyOffset + (long) tCount * 8) {
                LOGGER.warn("[Vision] Cells file truncated in translucent segment (tCount={}, len={})",
                        tCount, bytes.length);
                return null;
            }
            final List<BlockPos> translucentCells = new ArrayList<>(tCount);
            buf.position(tBodyOffset);
            for (int i = 0; i < tCount; i++) {
                translucentCells.add(BlockPos.of(buf.getLong()));
            }
            if (ver < VERSION_4) {
                return new CellsData(cells, translucentCells, translucentEnabled,
                        threshold <= 0 ? DEFAULT_PIXEL_THRESHOLD : threshold,
                        maxDist > 0 ? maxDist : DEFAULT_MAX_RAY_DIST, dimension, 0L, List.of(), List.of());
            }
            // v2.37：version=4 → spriteEpoch(8) + shapedCount(4) + 几何段
            final int gStart = tBodyOffset + tCount * 8;
            if (bytes.length < gStart + 12) { // long spriteEpoch + int shapedCount
                LOGGER.warn("[Vision] Cells file truncated before geometry segment (len={})", bytes.length);
                return null;
            }
            final long spriteEpoch = buf.getLong(gStart);
            final int sCount = buf.getInt(gStart + 8);
            final int sBodyOffset = gStart + 12;
            // 几何段是可变长记录，只能顺序走一遍。任一条越界 / 非法 → 整文件视为半截写
            // （返回 null，调用方不推进 mtime、下轮重试）——**不**做局部降级：局部降级会让
            // "哪些格进了几何段"随解析进度变化，是难以推理的方向；整段失败只走欠删。
            final List<ShapedCellData.ShapedCell> shaped = new ArrayList<>(Math.min(sCount, 4096));
            int sp = sBodyOffset;
            for (int i = 0; i < sCount; i++) {
                // ★ 字段顺序必须与写方 MemoryCellReporter 逐字节一致：
                //     long pos | ushort idLen | byte[idLen] blockId | byte quadCount | quads
                //   blockId 夹在 idLen 与 quadCount **之间**。曾把 quadCount 读在 blockId 之前
                //   （sp+10），于是 quadCount 取到了 id 的首字节（"minecraft:..." → 'm' = 109），
                //   其后每个条目全部错位 ⇒ 整文件判定为半截写 ⇒ EMPTY ⇒ 所有非满形状方块删不掉。
                if (bytes.length < sp + 8 + 2) { // pos(8) + idLen(2)
                    LOGGER.warn("[Vision] Cells file truncated in shaped entry {}/{} (len={})",
                            i, sCount, bytes.length);
                    return null;
                }
                final long posLong = buf.getLong(sp);
                final int idLen = buf.getShort(sp + 8) & 0xFFFF;
                sp += 10;
                if (idLen == 0 || bytes.length < sp + idLen + 1) { // idLen + id + quadCount(1)
                    LOGGER.warn("[Vision] Cells file bad block id (idLen={}, len={})", idLen, bytes.length);
                    return null;
                }
                final String blockId = new String(bytes, sp, idLen, StandardCharsets.UTF_8);
                sp += idLen;
                final int quadCount = bytes[sp] & 0xFF;
                sp += 1;
                final int quadBytes = quadCount * ShapedCellData.QUAD_BYTES;
                if (bytes.length < sp + quadBytes) {
                    LOGGER.warn("[Vision] Cells file truncated in shaped quads {}/{} (need={}, len={})",
                            i, sCount, quadBytes, bytes.length);
                    return null;
                }
                final ShapedCellData.Quad[] quads = new ShapedCellData.Quad[quadCount];
                buf.position(sp);
                for (int qi = 0; qi < quadCount; qi++) {
                    final float[] verts = new float[12];
                    final float[] uvs = new float[8];
                    for (int k = 0; k < 12; k++) verts[k] = buf.getFloat();
                    for (int k = 0; k < 8; k++) uvs[k] = buf.getFloat();
                    final float nx = buf.getFloat();
                    final float ny = buf.getFloat();
                    final float nz = buf.getFloat();
                    final int spriteIndex = buf.getInt();
                    quads[qi] = new ShapedCellData.Quad(verts, uvs, nx, ny, nz, spriteIndex);
                }
                sp += quadBytes;
                shaped.add(new ShapedCellData.ShapedCell(BlockPos.of(posLong), blockId, quads));
            }
            // v2.37 七次修订（§15.3）：version=5 → 信号缺失段（几何段之后、本文件最后一段）
            //   int signalLossCount × { long pos | ushort idLen | byte[idLen] blockId }
            // 与几何段同款纪律：字段顺序必须与写方 MemoryCellReporter 逐字节一致；任一条越界 /
            // 非法即整文件视为半截写（返回 null，不推进 mtime、下轮重试），不做局部降级。
            List<SignalLossCell> signalLoss = List.of();
            if (ver >= VERSION_5) {
                if (bytes.length < sp + 4) { // int signalLossCount
                    LOGGER.warn("[Vision] Cells file truncated before signal-loss segment (len={})",
                            bytes.length);
                    return null;
                }
                final int slCount = buf.getInt(sp);
                sp += 4;
                if (slCount < 0) {
                    LOGGER.warn("[Vision] Cells file bad signal-loss count {}", slCount);
                    return null;
                }
                final List<SignalLossCell> parsed = new ArrayList<>(Math.min(slCount, 4096));
                for (int i = 0; i < slCount; i++) {
                    if (bytes.length < sp + 8 + 2) { // pos(8) + idLen(2)
                        LOGGER.warn("[Vision] Cells file truncated in signal-loss entry {}/{} (len={})",
                                i, slCount, bytes.length);
                        return null;
                    }
                    final long posLong = buf.getLong(sp);
                    final int idLen = buf.getShort(sp + 8) & 0xFFFF;
                    sp += 10;
                    if (idLen == 0 || bytes.length < sp + idLen) {
                        LOGGER.warn("[Vision] Cells file bad signal-loss block id (idLen={}, len={})",
                                idLen, bytes.length);
                        return null;
                    }
                    final String blockId = new String(bytes, sp, idLen, StandardCharsets.UTF_8);
                    sp += idLen;
                    parsed.add(new SignalLossCell(BlockPos.of(posLong), blockId));
                }
                signalLoss = parsed;
            }
            // 自检：顺序解析必须恰好吃掉整个文件。不等即"游标与写方错位"——本类最危险的一类 bug
            // （顶点/UV 会被解释成别人的字段，几何 G ≠ R）。整文件作废并高声告警，绝不带病使用。
            // v5 起最后一段是信号缺失段（v4 文件 sp 停在几何段末，此处逐位等价于旧检查）。
            // （若将来再追加段落，须同时升版并按版本分支，勿放宽此检查。）
            if (sp != bytes.length) {
                LOGGER.warn("[Vision] Cells file format desync: consumed {} of {} bytes (ver={})",
                        sp, bytes.length, ver);
                return null;
            }
            return new CellsData(cells, translucentCells, translucentEnabled,
                    threshold <= 0 ? DEFAULT_PIXEL_THRESHOLD : threshold,
                    maxDist > 0 ? maxDist : DEFAULT_MAX_RAY_DIST, dimension, spriteEpoch, shaped, signalLoss);
        } catch (IOException e) {
            LOGGER.warn("[Vision] Failed to read cells file {}: {}", path, e.getMessage());
            return null;
        }
    }
}
