package com.example.memworld;

import com.example.mixin.SpriteContentsAccessor;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * v2.37（设计 §4.2 / §4.3.1）记忆侧 <b>sprite alpha 掩码表</b> —— 按 sprite 缓存"mip 0 逐 texel alpha"，
 * 并被 {@code ModelGeometryCache} 在烘焙每条 quad 时 intern 出一个 <b>下标</b>；本表按下标被
 * {@code memory_cells.bin} v4 的几何段引用，表本体独立写成 {@code memory_sprites.bin}（<b>增量式</b>：
 * 只在首次用到某张新 sprite / 资源重载时重写一次，之后每轮 cells 重写都只带 4 B 下标）。
 *
 * <p><b>几何意义（设计 §3.4）</b>：{@code terrain.fsh:88-96} 的
 * {@code if (color.a < ALPHA_CUTOUT) discard;} 是<b>逐 texel</b> 的 ⇒ 只有逐 texel 的掩码才等于真实
 * 渲染足迹 {@code R}。这正是"档 3（alpha 镂空）整类欠删"消失的原因：不再需要"有一处透明就整条 quad
 * 不参与"，而是精确到 texel。本表是 {@code G = R} 中"贴图这一维"的全部来源。
 *
 * <p><b>读取路径</b>：{@code SpriteContents} 的公开入口只有 {@code isTransparent(frame, x, y)}
 * （alpha == 0 的布尔），拿不到 {@code ALPHA_CUTOUT} 需要的连续 alpha ⇒ 经
 * {@link SpriteContentsAccessor}（本版唯一 MixIn，只读）读 {@code originalImage}。
 *
 * <p><b>文件格式</b>（{@code memory_sprites.bin}，小端；与采集侧 {@code SpriteTableCache} 对应）：
 * <pre>{@code
 *   [0..3]    magic "SSPR"
 *   [4]       version = 1
 *   [5..12]   long   epoch      ← 进程启动 / 资源重载时重新生成的随机 long
 *   [13..16]  int    count
 *   count 条，每条：
 *              ushort nameLen + UTF-8 sprite 名（如 "minecraft:block/oak_planks"，即 atlas 标识）
 *              ushort width；ushort height
 *              byte   flags   bit0 = OPAQUE（全表 alpha = 255，后面不跟 alphaBytes）
 *                             bit1 = RLE（alphaBytes 为 [value, count] 对，count ∈ 1..255）
 *                             bit2 = ANIMATED（只写首帧，帧切换靠 epoch 变更重发）
 *              alphaBytes     ← mip 0、逐 texel、行主序；Opaque 时 0 字节；RLE 时前置 int rleLen
 * }</pre>
 *
 * <p><b>为什么 Opaque 标志是主力压缩</b>（设计 §4.3.1）：栅栏/铁栏杆/墙/压力板/台阶这类"细盒子 +
 * 不透明贴图"的本版主交付物全部命中 Opaque → <b>0 字节 alpha</b>；只有真正的镂空贴图（玻璃板、睡莲、
 * 十字植物）才付 width×height 字节。
 *
 * <p><b>线程约束（设计 §4.2 第 2 步）</b>：{@link #intern} 必须在<b>客户端线程</b>调用——
 * {@code SpriteContents} 随资源重载整体重建，跨线程读不安全。调用点 =
 * {@code ModelGeometryCache.tickClient()}（客户端 tick）。{@link #flushIfDirty} 可在服务器 tick
 * 调用（只读自己的字段 + 写文件），全部经 {@code synchronized} 串行化。
 */
public final class SpriteAlphaTable {

    private static final Logger LOGGER = LoggerFactory.getLogger("stevex-test/memory");
    private static final byte[] MAGIC = {'S', 'S', 'P', 'R'};
    private static final int VERSION = 1;
    private static final byte FLAG_OPAQUE = 0b001;
    private static final byte FLAG_RLE = 0b010;
    private static final byte FLAG_ANIMATED = 0b100;
    /** 资源重载后该 sprite 的 alpha 重读失败 → 保留占位、不发 alpha；采集侧对该下标 fail-closed。 */
    private static final byte FLAG_INVALID = 0b1000;

    /** 一张 sprite 的 alpha 掩码（展开 RLE 后；{@code alpha == null} 表示全不透明）。 */
    public record SpriteAlpha(int width, int height, byte[] alpha, boolean animated) {
        /** 该表是否"全通过"（Opaque）——采集侧据此跳过逐 texel 查表。 */
        public boolean opaque() {
            return alpha == null;
        }
    }

    private final Map<String, SpriteAlpha> byName = new HashMap<>();
    /** sprite 名 → 建表时的 {@code SpriteContents} 身份：资源重载后会换对象 ⇒ 据此精确检测失效。 */
    private final Map<String, Object> sourceIdentity = new HashMap<>();
    /** 下标 → sprite 名（写入顺序即下标顺序，只增不改 ⇒ 旧下标永远有效）。 */
    private final List<String> order = new ArrayList<>();
    private final Map<String, Integer> indexByName = new HashMap<>();

    private volatile long epoch;
    private boolean dirty;

    public SpriteAlphaTable() {
        this.epoch = newEpoch();
    }

    private static long newEpoch() {
        final long v = ThreadLocalRandom.current().nextLong();
        return v == 0 ? 1L : v; // 0 保留给"无 sprite 段"（version ≤ 3 / 几何段空）
    }

    /** 当前 epoch（随 cells 文件头下发；采集侧据此判定其 index→name 视图是否有效）。 */
    public synchronized long epoch() {
        return epoch;
    }

    /** 已 intern 的 sprite 数（诊断用）。 */
    public synchronized int size() {
        return order.size();
    }

    /**
     * 把一张 sprite 装表并返回其下标（幂等；同一 sprite 重复 intern 返回同一下标）。
     *
     * <p><b>必须在客户端线程调用</b>（读 {@code SpriteContents.originalImage}）。
     *
     * @return 下标；alpha 不可得（MixIn 未生效 / 读失败 / 尺寸非法）→ <b>-1</b>，调用方须让该 quad
     *         <b>整条不参与</b>（fail-closed，欠删）。<b>绝不回退到"整面全通过"</b>——那是
     *         {@code G ⊋ R}（§3.4），会把空余区射线变成假证据。
     */
    public synchronized int intern(final TextureAtlasSprite sprite) {
        if (sprite == null) return -1;
        final SpriteContents contents = sprite.contents();
        if (contents == null) return -1;
        final String name = contents.name().toString();
        final Object identity = contents;

        final Integer existing = indexByName.get(name);
        if (existing != null) {
            if (sourceIdentity.get(name) == identity) return existing; // 暖路径：命中缓存，零 I/O 零解码
            // 资源重载：同名 sprite 换了 SpriteContents 对象 → 旧 alpha 表已失效，重读并换 epoch
            //（epoch 变更强制采集侧重新载入 sprite 文件；采集侧按 name 缓存的旧表在此一并刷新）。
            final SpriteAlpha rebuilt = readAlpha(contents);
            if (rebuilt == null) {
                // 新内容读不出来 → 宁可让该 sprite 失效（引它的 quad 全部 fail-closed），也不留旧表硬算
                byName.remove(name);
                sourceIdentity.remove(name);
                epoch = newEpoch();
                dirty = true;
                LOGGER.warn("[MemoryWorld] Sprite alpha re-read failed after reload: {} (fail-closed)", name);
                return -1;
            }
            byName.put(name, rebuilt);
            sourceIdentity.put(name, identity);
            epoch = newEpoch();
            dirty = true;
            LOGGER.info("[MemoryWorld] Sprite alpha table invalidated by resource reload (epoch={})", epoch);
            return existing;
        }

        final SpriteAlpha alpha = readAlpha(contents);
        if (alpha == null) return -1;
        final int index = order.size();
        byName.put(name, alpha);
        sourceIdentity.put(name, identity);
        order.add(name);
        indexByName.put(name, index);
        dirty = true;
        LOGGER.debug("[MemoryWorld] Interned sprite {} #{} ({}x{}, {})",
                name, index, alpha.width(), alpha.height(), alpha.opaque() ? "opaque" : "masked");
        return index;
    }

    /** 读 {@code originalImage} 的 mip 0 首帧逐 texel alpha（客户端线程）；不可得返回 null。 */
    private static SpriteAlpha readAlpha(final SpriteContents contents) {
        final int width = contents.width();
        final int height = contents.height();
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096) return null;
        final NativeImage image;
        try {
            image = ((SpriteContentsAccessor) (Object) contents).stevex$getOriginalImage();
        } catch (Throwable t) {
            // MixIn 未生效 / 字段缺失 → 明确 fail-closed（设计 §9：唯一可能整体空转的依赖点）
            LOGGER.warn("[MemoryWorld] SpriteContents.originalImage accessor unavailable ({}); "
                    + "alpha masks disabled for this sprite (fail-closed)", t.toString());
            return null;
        }
        if (image == null) return null;
        try {
            final byte[] alpha = new byte[width * height];
            boolean allOpaque = true;
            boolean anyTransparent = false;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    final int a = ARGB.alpha(image.getPixel(x, y));
                    alpha[y * width + x] = (byte) a;
                    if (a != 255) allOpaque = false;
                    if (a == 0) anyTransparent = true;
                }
            }
            if (allOpaque) {
                // Opaque 短路：0 字节 alpha（本版主交付物——栅栏/铁栏杆/墙/压力板/台阶——全部命中）
                return new SpriteAlpha(width, height, null, contents.isAnimated());
            }
            return new SpriteAlpha(width, height, alpha, contents.isAnimated());
        } catch (Throwable t) {
            LOGGER.warn("[MemoryWorld] Failed to read sprite alpha ({}): {}", contents.name(), t.toString());
            return null;
        }
    }

    /**
     * 表内容有变化时原子重写 {@code memory_sprites.bin}（全量快照 + 当前 epoch）。
     *
     * <p><b>写入次序硬约束</b>（设计 §4.3.1）：调用方必须<b>先</b>调本方法、<b>再</b>写 cells 文件；
     * 反序会出现"cells 引用新 epoch 的下标、磁盘上却还是旧 epoch 的表"的窗口。
     *
     * @return true = 本次确实重写了（供诊断/验收 §10 第 15 条：暖机后本文件 mtime 应不变）
     */
    public synchronized boolean flushIfDirty(final Path target) {
        if (!dirty) return false;
        try {
            Files.createDirectories(target.getParent());
            final Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            final ByteBuffer buf = ByteBuffer.allocate(estimateSize()).order(ByteOrder.LITTLE_ENDIAN);
            buf.put(MAGIC);
            buf.put((byte) VERSION);
            buf.putLong(epoch);
            buf.putInt(order.size());
            for (String name : order) {
                // 注意：**必须逐条写出、不得跳过**——采集侧的 indexToName 是按顺序累加下标重建的，
                // 跳一条会让其后全部下标错位（把 A 的掩码当成 B 的）。失效条目写 FLAG_INVALID 占位，
                // 由采集侧对该下标 fail-closed（欠删）。
                final SpriteAlpha alpha = byName.get(name);
                final byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
                buf.putShort((short) nameBytes.length);
                buf.put(nameBytes);
                if (alpha == null) {
                    buf.putShort((short) 0);
                    buf.putShort((short) 0);
                    buf.put(FLAG_INVALID);
                    continue;
                }
                buf.putShort((short) alpha.width());
                buf.putShort((short) alpha.height());
                byte flags = 0;
                if (alpha.opaque()) flags |= FLAG_OPAQUE;
                if (alpha.animated()) flags |= FLAG_ANIMATED;
                byte[] payload = null;
                if (!alpha.opaque()) {
                    final byte[] raw = alpha.alpha();
                    final byte[] rle = rleEncode(raw);
                    if (rle != null && rle.length < raw.length) {
                        flags |= FLAG_RLE;
                        payload = rle;
                    } else {
                        payload = raw;
                    }
                }
                buf.put(flags);
                if (payload != null) {
                    if ((flags & FLAG_RLE) != 0) buf.putInt(payload.length);
                    buf.put(payload);
                }
            }
            final byte[] bytes = new byte[buf.position()];
            buf.flip();
            buf.get(bytes);
            Files.write(tmp, bytes);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFailure) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            LOGGER.info("[MemoryWorld] Wrote sprite alpha table: {} sprites, epoch={}, {} bytes -> {}",
                    order.size(), epoch, bytes.length, target);
            return true;
        } catch (Throwable e) {
            // 捕获面比 IOException 宽：estimateSize 漏算导致的 BufferOverflowException 是
            // RuntimeException，若漏出去会打断服务器 tick（本方法只是"少写一次文件"，不该致命）。
            LOGGER.warn("[MemoryWorld] Failed to write sprite table {}: {}", target, e.toString());
            return false;
        }
    }

    /** 估算缓冲区大小（宁大勿小：超了会 BufferOverflow，故留足余量）。 */
    private synchronized int estimateSize() {
        int size = 21;
        for (String name : order) {
            // 条目头对**所有**条目都要算：失效条目（byName 里为 null）写出的是
            // nameLen + name + 0 + 0 + FLAG_INVALID 的占位，同样占 7 + nameLen 字节。
            // 曾因 `if (alpha == null) continue;` 把占位漏算 → 缓冲区偏小（BufferOverflow 是
            // RuntimeException，不会被下面的 catch (IOException) 兜住，会直接把 tick 打崩）。
            size += 2 + name.getBytes(StandardCharsets.UTF_8).length + 2 + 2 + 1;
            final SpriteAlpha alpha = byName.get(name);
            if (alpha == null) continue;
            // RLE 最坏 = 2×raw（每 texel 一段），raw 最坏 = w*h；直接按 raw×2 + 4 留足
            size += alpha.opaque() ? 0 : alpha.width() * alpha.height() * 2 + 4;
        }
        return Math.max(64, size);
    }

    /** 游程编码：[value, count] 字节对，count ∈ 1..255。无收益时返回 null。 */
    private static byte[] rleEncode(final byte[] raw) {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(raw.length / 2);
        int i = 0;
        while (i < raw.length) {
            final byte v = raw[i];
            int run = 1;
            while (i + run < raw.length && raw[i + run] == v && run < 255) run++;
            out.write(v);
            out.write(run);
            i += run;
        }
        final byte[] encoded = out.toByteArray();
        return encoded.length < raw.length ? encoded : null;
    }

    /** 诊断：alpha 直方图（§10 第 1 条 MixIn 冒烟检查——确认 accessor 生效、非全 0/非异常）。 */
    public synchronized String describe(final String name) {
        final SpriteAlpha a = byName.get(name);
        if (a == null) return name + ": <not interned>";
        if (a.opaque()) return name + ": opaque (" + a.width() + "x" + a.height() + ")";
        int zero = 0, full = 0;
        for (byte b : a.alpha()) {
            final int v = b & 0xFF;
            if (v == 0) zero++;
            else if (v == 255) full++;
        }
        return String.format(java.util.Locale.ROOT, "%s: %dx%d alpha0=%d/%d full=%d/%d",
                name, a.width(), a.height(), zero, a.alpha().length, full, a.alpha().length);
    }
}
