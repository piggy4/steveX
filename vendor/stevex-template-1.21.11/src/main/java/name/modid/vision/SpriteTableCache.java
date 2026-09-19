package name.modid.vision;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * v2.37（设计 §4.3.1 / §5.1）采集侧 <b>sprite alpha 掩码表读取器</b> —— 读记忆侧写的
 * {@code memory_sprites.bin}，把 {@link MemoryCellsReader} 解析出的几何段里的 {@code spriteIndex}
 * 解引用成逐 texel alpha 掩码。
 *
 * <p><b>为什么是单例</b>：{@link VisionApi} 每次快照都 <b>new</b> 一个 {@link MemoryCellsReader}
 * （它只做 mtime 门控 + 解析，无状态）；但 sprite 表是几十~几百 KB 的掩码，必须跨调用复用。
 * 故本类自行持有实例（{@link #get()}）与 mtime 门控。
 *
 * <p><b>文件格式</b>（{@code memory_sprites.bin}，小端；由记忆侧 {@code SpriteAlphaTable} 写入）：
 * <pre>{@code
 *   [0..3]    magic "SSPR"
 *   [4]       version = 1
 *   [5..12]   long   epoch
 *   [13..16]  int    count
 *   count 条，每条（下标 = 出现顺序，0 起）：
 *              ushort nameLen + UTF-8 sprite 名
 *              ushort width；ushort height
 *              byte   flags  bit0 = OPAQUE（无 alphaBytes）/ bit1 = RLE（前置 int rleLen）
 *                            bit2 = ANIMATED / bit3 = INVALID（占位，无 payload）
 *              alphaBytes    mip 0、逐 texel、行主序；Opaque / Invalid 时 0 字节
 * }</pre>
 *
 * <p><b>fail-closed 三处</b>（设计 §5.1，全部走向欠删）：
 * <ol>
 *   <li><b>epoch 不符</b>：{@code memory_cells.bin} 的 {@code spriteEpoch} 与本表载入的 epoch 不一致
 *       （或本文件缺失 / 未变但 epoch 未知）⇒ {@link #hasEpoch} 为 false，调用方<b>整段几何作废</b>。
 *       这是"磁盘上那份 cells 引用的下标表"与"我手上这份表"不同源的唯一判定点；</li>
 *   <li><b>下标越界 / 占位条目</b>（{@code FLAG_INVALID}）⇒ 该 quad 不参与；</li>
 *   <li><b>结构损坏</b>（半截写 / 长度对不上 / RLE 展开后尺寸不符）⇒ 保留上次解析结果、不推进
 *       mtime，本轮走"epoch 不符"路径整段作废。</li>
 * </ol>
 *
 * <p><b>刻意不做 name 键缓存</b>：资源重载后同名 sprite 的 alpha 会变（记忆侧据此换了 epoch）。
 * 若按 name 复用旧掩码，会把重载前的掩码套到重载后的几何上——那是 {@code G ≠ R}（§3.4），
 * 且方向不可控。本表的正确来源只有"当前磁盘文件"，故每次 mtime 变化都整表重建。
 *
 * <p><b>动画 sprite 一律作废</b>：记忆侧的掩码只含 {@code originalImage}（对动画贴图是
 * <b>整条帧带</b>，height = 帧高 × 帧数），其行主序布局与 {@code [0,1]} 的 sprite 局部 UV
 * 不对应 ⇒ 掩码不可用。故 {@code FLAG_ANIMATED} 的条目一律返回 null（该 quad 欠删）。
 * 本版受影响的是火（{@code minecraft:block/fire_*}）等少数非满形状方块。
 */
public final class SpriteTableCache {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR_NAME = "stevex/vision";
    private static final String FILE_NAME = "memory_sprites.bin";
    private static final byte[] MAGIC = {'S', 'S', 'P', 'R'};
    private static final int VERSION = 1;
    private static final byte FLAG_OPAQUE = 0b001;
    private static final byte FLAG_RLE = 0b010;
    private static final byte FLAG_ANIMATED = 0b100;
    private static final byte FLAG_INVALID = 0b1000;
    /** 单张掩码的 texel 上限（与记忆侧 4096×4096 对称，防损坏文件撑爆内存）。 */
    private static final int MAX_TEXELS = 4096 * 4096;

    private static final SpriteTableCache INSTANCE = new SpriteTableCache();

    public static SpriteTableCache get() {
        return INSTANCE;
    }

    /**
     * 一张 sprite 的 alpha 掩码（展开 RLE 后）。
     *
     * @param alpha 行主序逐 texel alpha（0..255 存为 byte）；<b>null = 全不透明（Opaque 短路）</b>，
     *              逐 texel 判定恒通过。"全不透明"与"该下标不可用"是<b>两种</b>语义：后者由
     *              {@code byIndex} 里的 <b>null 条目</b>表示，{@link #maskForIndex} 会把它转成 null
     *              返回、调用方整条 quad 剔除。两者绝不混淆。
     */
    public record Mask(@Nullable byte[] alpha, int width, int height) {
        /** 该掩码是否可用（false = fail-closed，引它的 quad 须整条剔除）。 */
        public boolean usable() {
            return width > 0 && height > 0;
        }

        /** Opaque 短路（无 ALPHA_CUTOUT define 的层 / 贴图全 255）→ 逐 texel 判定恒通过。 */
        public boolean opaque() {
            return alpha == null;
        }
    }

    private final Path filePath;
    private FileTime lastMtime;
    private long epoch;
    private Mask[] byIndex = new Mask[0];

    private SpriteTableCache() {
        this.filePath = Minecraft.getInstance().gameDirectory.toPath()
                .resolve(DIR_NAME).resolve(FILE_NAME);
    }

    /** 本表当前载入的 epoch（0 = 无表 / 解析失败 / 文件缺失）；诊断用。 */
    public synchronized long epoch() {
        return epoch;
    }

    /** 当前可用（usable）的掩码条数；诊断用。 */
    public synchronized int size() {
        int n = 0;
        for (Mask m : byIndex) {
            if (m != null && m.usable()) n++;
        }
        return n;
    }

    /**
     * {@code memory_cells.bin} 的几何段是否可判 —— 即磁盘上那份 cells 引用的下标表是否就是本表。
     *
     * <p><b>唯一的"整段可用"判据</b>（设计 §5.1 第 1 条）：{@code cellsEpoch == 0}（version ≤ 3
     * 压根没有几何段）恒 false；文件缺失 / 半截写（epoch 保持上一次的值或 0）→ 不符 → false。
     *
     * <p>本方法会触发 mtime 门控读盘，故须在几何段开判前调一次。
     */
    public synchronized boolean hasEpoch(final long cellsEpoch) {
        readIfNeeded();
        return cellsEpoch != 0L && epoch == cellsEpoch;
    }

    /**
     * 几何段下标 → alpha 掩码。
     *
     * @return null = 该下标不可用（越界 / INVALID 占位 / 动画 / 内容损坏）→ <b>调用方须让该 quad
     *         整条不参与</b>（fail-closed）。绝不回退到"整面全通过"——那是把空余区射线变成假证据
     *         （{@code G ⊋ R}，§3.4），会产生误删活体的假阳性
     */
    public synchronized @Nullable Mask maskForIndex(final int index) {
        readIfNeeded();
        if (index < 0 || index >= byIndex.length) return null;
        final Mask m = byIndex[index];
        return (m == null || !m.usable()) ? null : m;
    }

    // ==================== 读盘 ====================

    /** mtime 门控读盘；文件缺失 → epoch=0（整段作废）；解析失败 → 保留上次结果（不推进 mtime）。 */
    private void readIfNeeded() {
        if (!Files.exists(filePath)) {
            // 记忆侧离线 / 尚未写过 → 无掩码 → 几何段整段作废（欠删，安全方向）
            lastMtime = null;
            epoch = 0L;
            byIndex = new Mask[0];
            return;
        }
        final FileTime mtime;
        try {
            mtime = Files.getLastModifiedTime(filePath);
        } catch (IOException e) {
            LOGGER.warn("[Vision] Failed to stat sprite table {}: {}", filePath, e.getMessage());
            return;
        }
        if (mtime.equals(lastMtime)) return; // 未变不读（mtime 门控）

        final Parsed parsed = parse(filePath);
        if (parsed == null) {
            // 半截写 / 损坏 → 不推进 mtime，下轮重试；本轮保持旧 epoch（多半与 cells 不符 ⇒ 欠删）
            return;
        }
        lastMtime = mtime;
        epoch = parsed.epoch();
        byIndex = parsed.byIndex();
        LOGGER.debug("[Vision] Read sprite alpha table: {} entries ({} usable), epoch={} from {}",
                byIndex.length, size(), epoch, filePath);
    }

    private record Parsed(long epoch, Mask[] byIndex) {}

    /** 解析 {@code memory_sprites.bin}；结构性失败返回 null（调用方不推进 mtime）。 */
    private static Parsed parse(final Path path) {
        final byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            LOGGER.warn("[Vision] Failed to read sprite table {}: {}", path, e.getMessage());
            return null;
        }
        if (bytes.length < 17) {
            LOGGER.warn("[Vision] Sprite table too short ({} bytes)", bytes.length);
            return null;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (bytes[i] != MAGIC[i]) {
                LOGGER.warn("[Vision] Sprite table bad magic: {}", path);
                return null;
            }
        }
        if (bytes[4] != VERSION) {
            LOGGER.warn("[Vision] Sprite table unsupported version {}", bytes[4]);
            return null;
        }
        final ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        final long epoch = buf.getLong(5);
        final int count = buf.getInt(13);
        if (epoch == 0L || count < 0) {
            LOGGER.warn("[Vision] Sprite table bad header (epoch={}, count={})", epoch, count);
            return null;
        }
        final Mask[] byIndex = new Mask[count];
        final Set<String> seen = new HashSet<>();
        int p = 17;
        for (int i = 0; i < count; i++) {
            if (bytes.length < p + 7) { // nameLen(2) + width(2) + height(2) + flags(1)
                LOGGER.warn("[Vision] Sprite table truncated at entry {}/{}", i, count);
                return null;
            }
            final int nameLen = buf.getShort(p) & 0xFFFF;
            p += 2;
            if (nameLen == 0 || bytes.length < p + nameLen + 5) {
                LOGGER.warn("[Vision] Sprite table bad name length {} at entry {}", nameLen, i);
                return null;
            }
            final String name = new String(bytes, p, nameLen, StandardCharsets.UTF_8);
            p += nameLen;
            final int width = buf.getShort(p) & 0xFFFF;
            final int height = buf.getShort(p + 2) & 0xFFFF;
            final byte flags = bytes[p + 4];
            p += 5;
            if (seen.add(name) == false) {
                // 记忆侧的下标表"只增不改"，同名不可能出现两次；出现即文件不符合约定 → 结构失败
                LOGGER.warn("[Vision] Sprite table duplicate entry {}", name);
                return null;
            }
            // 条目级失败一律落到"该下标不可用"（null），并**继续**解析后续条目——
            // 继续是为保住下标对齐（跳一条会让其后全部错位，把 A 的掩码发给 B）。
            //
            // ★ 硬约束：**任何** continue 之前都必须已经把该条目的 payload 消费干净。写方（记忆侧
            //   SpriteAlphaTable.flushIfDirty）对 ANIMATED 条目**照样写 payload**（payload 的有无只由
            //   OPAQUE / INVALID 决定），故"不可用"的判定必须在**读完 payload 之后**做——
            //   早退会让游标错位、其后每个条目都读到垃圾（实测踩过：kelp 是第一条 ANIMATED+RLE，
            //   早退导致整表解析失败 → epoch 恒为 0 → 采集侧几何段整段作废 → 所有非满形状方块删不掉）。
            if ((flags & FLAG_INVALID) != 0) {
                // 占位条目：写方只写 0 尺寸 + 无 payload，故此处可直接跳过
                byIndex[i] = null;
                continue;
            }
            if (width <= 0 || height <= 0 || (long) width * height > MAX_TEXELS) {
                LOGGER.warn("[Vision] Sprite table bad size {}x{} for {}", width, height, name);
                return null; // 尺寸非法 ⇒ payload 长度不可知 ⇒ 游标再也无法同步，只能整体作废
            }
            // 动画 sprite 的 originalImage 是整条帧带，与 [0,1] sprite UV 不对应 → 掩码不可用。
            // 但**仍须消费 payload**（见上方硬约束），故只记下决定、延后到读完之后再落 null。
            final boolean unusableAnimated = (flags & FLAG_ANIMATED) != 0;
            final int texels = width * height;
            if ((flags & FLAG_OPAQUE) != 0) {
                // 全不透明 → 0 字节 payload，恒通过
                byIndex[i] = unusableAnimated ? null : new Mask(null, width, height);
                continue;
            }
            if ((flags & FLAG_RLE) != 0) {
                if (bytes.length < p + 4) {
                    LOGGER.warn("[Vision] Sprite table truncated before RLE payload of {}", name);
                    return null;
                }
                final int rleLen = buf.getInt(p);
                p += 4;
                if (rleLen < 0 || (rleLen & 1) != 0 || bytes.length < p + rleLen) {
                    LOGGER.warn("[Vision] Sprite table bad RLE payload of {} (len={})", name, rleLen);
                    return null; // 长度非法 ⇒ payload 边界不可知 ⇒ 游标无法同步
                }
                if (unusableAnimated) {
                    p += rleLen; // 只消费、不解码
                    byIndex[i] = null;
                    continue;
                }
                final byte[] alpha = new byte[texels];
                int out = 0;
                boolean ok = true;
                for (int k = 0; k < rleLen; k += 2) {
                    final byte v = bytes[p + k];
                    final int run = bytes[p + k + 1] & 0xFF;
                    if (run == 0 || out + run > texels) {
                        ok = false;
                        break;
                    }
                    java.util.Arrays.fill(alpha, out, out + run, v);
                    out += run;
                }
                p += rleLen;
                if (!ok || out != texels) {
                    LOGGER.warn("[Vision] Sprite table RLE size mismatch for {} ({} != {})", name, out, texels);
                    byIndex[i] = null;
                    continue;
                }
                byIndex[i] = new Mask(alpha, width, height);
                continue;
            }
            if (bytes.length < p + texels) {
                LOGGER.warn("[Vision] Sprite table truncated in raw payload of {} (need={})", name, texels);
                return null;
            }
            final byte[] alpha = new byte[texels];
            System.arraycopy(bytes, p, alpha, 0, texels);
            p += texels;
            byIndex[i] = unusableAnimated ? null : new Mask(alpha, width, height);
        }
        if (p != bytes.length) {
            // 自检：解析必须恰好吃掉整个文件。不等即"游标与写方错位"——本类最危险的一类 bug
            //（每个下标都指向别人的掩码 ⇒ G ≠ R），故整表作废并高声告警，绝不带病使用。
            LOGGER.warn("[Vision] Sprite table format desync: consumed {} of {} bytes -> discarding table",
                    p, bytes.length);
            return null;
        }
        return new Parsed(epoch, byIndex);
    }
}
