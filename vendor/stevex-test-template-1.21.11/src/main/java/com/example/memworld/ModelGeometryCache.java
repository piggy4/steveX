package com.example.memworld;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * v2.37（设计 §4.2）记忆侧 <b>烘焙模型 quad 缓存</b> —— 把 {@code BlockState} 变成"该方块在客户端
 * 烘焙模型里的实际渲染几何"，表示为 <b>quad 面清单</b>（顶点 + sprite 局部 UV + 自算法线 + sprite
 * 下标），供 {@code MemoryCellReporter} 写进 {@code memory_cells.bin} v4 的几何段。
 *
 * <p><b>为什么几何源必须是客户端烘焙模型</b>（设计 §0/§4.2）：{@code BakedQuad} 的顶点就是提交给 GPU
 * 的那份数据（变体选择 / {@code multipart} / {@code BlockElementRotation} / UV / 烘焙期面剔除全部已
 * 应用）。任何 {@code BlockBehaviour.*Shape}（碰撞盒/拾取盒/遮挡盒）都是玩法近似，没有一个等于渲染
 * 足迹——拾取盒对栅栏横向高估 2×、对红石线高估约 5×，会直接把"活体格内空余区"的射线变成假证据 →
 * <b>误删活体</b>。
 *
 * <p><b>为什么在记忆侧算</b>（设计 §4.2）：几何源就是 {@code BlockState}，而 cells 本来就是记忆侧发的；
 * 采集侧手里只有 {@code blockId} + 属性，须重建 {@code BlockState}，而重建<b>有回退</b>（未知方块 →
 * AIR、非法属性跳过）——重建出的几何一旦小于记忆侧真实方块的足迹，就是低估 → 越票 → 误删。
 *
 * <p><b>四个承重前提</b>（设计 §4.2，逐条落在此处）：
 * <ol>
 *   <li><b>种子用 {@code state.getSeed(pos)}</b>（不是固定常量）——vanilla 自己就是
 *       {@code singleThreadRandom.setSeed(state.getSeed(pos))}（{@code BlockRenderDispatcher:53}）。
 *       位置相关的变体（{@code WeightedVariants}：高草/花/作物等）靠它选中与实景一致的模型；用固定
 *       种子会让这类方块拿到<b>与实景不同</b>的模型 → 几何对不上 → {@code G ⊋ R} 或欠删；</li>
 *   <li><b>必须在客户端线程取模型</b>——{@code getBlockModel} 依赖 {@code ModelManager}
 *       （{@code onResourceManagerReload} 填充），而 {@code MemoryCellReporter.tick} 跑在<b>服务器
 *       tick</b> 上，且 {@code collectParts} 与资源重载并发不安全。故本类走"<b>请求-客户端 tick 烘焙</b>"：
 *       {@link #get} 在服务器线程只读缓存、未命中则<b>登记请求并返回 null</b>，
 *       {@link #tickClient()} 在客户端 tick 批量烘焙。未就绪 → 该格本轮不进几何段（欠删，安全方向）；</li>
 *   <li><b>模型未就绪 → 跳过，绝不回退到拾取盒</b>——启动早期 / 资源重载窗口内 {@code getBlockModel}
 *       会回落到 missing model（quad 数 = 0）→ 自动欠删。<b>回退到拾取盒等于把 §0 的误删通道原样放回</b>；
 *       同理禁止"alpha 表不可得时按整面全通过处理"（那是 {@code G ⊋ R}）；</li>
 *   <li><b>alpha 表按 sprite 缓存、经 {@link SpriteAlphaTable} 增量下发</b>——本类只负责 intern 出下标。</li>
 * </ol>
 *
 * <p><b>法线为什么不读 {@code BakedQuad.direction}</b>（设计 §3.4/决策 L）：{@code FaceBakery:90} 是
 * {@code Objects.requireNonNullElse(finalDirection, Direction.UP)}，而 {@code calculateFacing} 对<b>非轴
 * 对齐</b> quad 返回 {@code null}（{@code findClosestDirection} 要求 {@code product >= 0}，
 * {@code FaceBakery:169}）→ 旋转 quad（十字植物/告示栏/斜坡铁轨）一律被静默标成 {@code UP}，
 * <b>不可用</b>。故自算 {@code normalize(cross(p1−p0, p2−p0))}：与 vanilla 的 {@code calculateFacing}
 * 同源，且<b>两侧共用下发值</b>，不存在约定分歧。退化（共线/非有限）→ 该 quad 丢弃（fail-closed）。
 *
 * <p><b>UV 为什么必须换算</b>：{@code FaceBakery:136} 存的是 <b>atlas 空间</b> UV
 * （{@code UVPair.pack(icon.getU(u), icon.getV(v))}），而掩码表是 sprite 局部逐 texel 的 ⇒ 用
 * {@code sprite.getU0()/getU1()/getV0()/getV1()} 反线性换算回 [0,1]，采集侧无需任何 atlas 几何。
 */
public final class ModelGeometryCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("stevex-test/memory");

    /** 单条 quad（设计 §4.3.2 的内存形态）：4 顶点（格内局部 [0,1]）+ 4 组 sprite 局部 UV + 单位法线。 */
    public record Quad(
            float[] vertices,   // 12 floats：p0.xyz, p1.xyz, p2.xyz, p3.xyz（vanilla 原始顶点顺序，不做 AABB）
            float[] uvs,        // 8 floats：uv0.u, uv0.v, uv1.u, uv1.v, ...
            float nx, float ny, float nz,
            int spriteIndex     // SpriteAlphaTable 下标；-1 不会出现在缓存结果里（已在烘焙期丢弃）
    ) {}

    /** 未命中缓存的烘焙请求（去重键 = 方块状态 + 变体种子）。 */
    private record Request(BlockState state, long seed) {}

    /** 缓存上限：超限整体清空（宁可重建，也不让几何缓存无界增长）。 */
    private static final int MAX_ENTRIES = 8192;

    private static final ModelGeometryCache INSTANCE = new ModelGeometryCache();

    private final SpriteAlphaTable sprites;
    /** {@code BlockState → (seed → quads)}。值可为 EMPTY（模型缺失 / quad 全被丢弃 = 该格欠删）。 */
    private final Map<BlockState, Map<Long, Quad[]>> cache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Request> pending = new ConcurrentLinkedQueue<>();
    private final Set<Request> pendingKeys = ConcurrentHashMap.newKeySet();
    private final AtomicInteger entries = new AtomicInteger();
    private final RandomSource random = RandomSource.create();

    private ModelGeometryCache() {
        this.sprites = new SpriteAlphaTable();
    }

    public static ModelGeometryCache get() {
        return INSTANCE;
    }

    /** sprite alpha 表（由 {@code MemoryCellReporter} 负责按序落盘）。 */
    public SpriteAlphaTable sprites() {
        return sprites;
    }

    /**
     * 取该格的被记住几何（<b>服务器 tick 安全</b>：只读缓存 + 登记请求，不做任何客户端调用）。
     *
     * @return quad 清单；{@code null} = 尚未烘焙（已登记，下个客户端 tick 补齐 → 本轮该格欠删）；
     *         空数组 = 已烘焙但**几何为空**（missing model / 全部 quad 法线退化 / alpha 表不可得）→
     *         调用方必须把该格<b>排除出几何段</b>（欠删），不可当成"整格盒"
     */
    public Quad[] get(final BlockState state, final BlockPos pos) {
        final long seed = state.getSeed(pos);
        final Map<Long, Quad[]> bySeed = cache.get(state);
        if (bySeed != null) {
            final Quad[] hit = bySeed.get(seed);
            if (hit != null) return hit;
        }
        final Request req = new Request(state, seed);
        if (pendingKeys.add(req)) {
            pending.add(req); // 未命中：登记一次，下个客户端 tick 烘焙（去重，不重复排队）
        }
        return null;
    }

    /**
     * 客户端 tick：批量烘焙排队中的 {@code BlockState}（<b>必须在客户端线程调用</b>）。
     *
     * @return 本次烘焙的入口数（诊断用）
     */
    public int tickClient() {
        if (pending.isEmpty()) return 0;
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getBlockRenderer() == null) return 0;

        int baked = 0;
        Request req;
        while ((req = pending.poll()) != null) {
            pendingKeys.remove(req);
            final Quad[] quads = bake(mc.getBlockRenderer(), req.state(), req.seed());
            bySeedMap(req.state()).put(req.seed(), quads);
            if (entries.incrementAndGet() > MAX_ENTRIES) {
                cache.clear();
                entries.set(0);
                pendingKeys.clear();
                LOGGER.info("[MemoryWorld] Model geometry cache exceeded {} entries; cleared", MAX_ENTRIES);
            }
            baked++;
        }
        if (baked > 0) {
            LOGGER.debug("[MemoryWorld] Baked geometry for {} block state/seed pairs (sprites interned: {})",
                    baked, sprites.size());
        }
        return baked;
    }

    private Map<Long, Quad[]> bySeedMap(final BlockState state) {
        return cache.computeIfAbsent(state, s -> new ConcurrentHashMap<>());
    }

    /** 单个 (BlockState, seed) 的烘焙主循环（客户端线程；见类 javadoc 四个承重前提）。 */
    private Quad[] bake(final BlockRenderDispatcher dispatcher, final BlockState state, final long seed) {
        final BlockStateModel model = dispatcher.getBlockModel(state);
        if (model == null) return new Quad[0];
        random.setSeed(seed);
        final List<BlockModelPart> parts = new ArrayList<>(2);
        model.collectParts(random, parts);

        final List<Quad> out = new ArrayList<>();
        for (BlockModelPart part : parts) {
            if (part == null) continue;
            // 六个朝向 + null（无剔除面）；两者集合不相交（SimpleModelWrapper 分桶返回），故不会重复
            for (Direction d : Direction.values()) collect(part.getQuads(d), out);
            collect(part.getQuads(null), out);
        }
        if (out.isEmpty()) return new Quad[0];
        return out.toArray(new Quad[0]);
    }

    private void collect(final List<BakedQuad> quads, final List<Quad> out) {
        if (quads == null || quads.isEmpty()) return;
        for (BakedQuad q : quads) {
            final Quad converted = convert(q);
            if (converted != null) out.add(converted);
        }
    }

    /** 单条 {@code BakedQuad} → {@link Quad}；任一步不可靠（法线退化 / sprite 表不可得）→ null（丢弃）。 */
    private Quad convert(final BakedQuad q) {
        final Vector3fc p0 = q.position0();
        final Vector3fc p1 = q.position1();
        final Vector3fc p2 = q.position2();
        final Vector3fc p3 = q.position3();
        if (p0 == null || p1 == null || p2 == null || p3 == null) return null;

        // ② 法线自算：vanilla 的 calculateFacing 用的同一个量（cross(p1−p0, p2−p0)），右手定则方向即
        //    该面朝外的方向；渲染器正面定义与之一致 ⇒ dot(n, rayDir) < 0 ⇔ 相机看到的是正面。
        final Vector3f e1 = new Vector3f(p1.x() - p0.x(), p1.y() - p0.y(), p1.z() - p0.z());
        final Vector3f e2 = new Vector3f(p2.x() - p0.x(), p2.y() - p0.y(), p2.z() - p0.z());
        final Vector3f n = e1.cross(e2, new Vector3f());
        final float len = n.length();
        if (!Float.isFinite(len) || len < 1e-9f) return null; // 顶点共线 / 非有限 → 丢弃（fail-closed）
        n.div(len);

        // ④ sprite 装表（增量下发）：alpha 表不可得 → 该 quad 整条不参与（绝不当成全通过）
        final TextureAtlasSprite sprite = q.sprite();
        final int spriteIndex = sprites.intern(sprite);
        if (spriteIndex < 0) return null;

        // ③ UV：atlas 空间 → sprite 局部 [0,1]（线性反变换；getU(off) = u0 + (u1-u0)*off）
        final float u0 = sprite.getU0();
        final float du = sprite.getU1() - u0;
        final float v0 = sprite.getV0();
        final float dv = sprite.getV1() - v0;

        final float[] vertices = {
                p0.x(), p0.y(), p0.z(),
                p1.x(), p1.y(), p1.z(),
                p2.x(), p2.y(), p2.z(),
                p3.x(), p3.y(), p3.z()
        };
        final float[] uvs = new float[8];
        for (int i = 0; i < 4; i++) {
            final float au = UVPair.unpackU(q.packedUV(i));
            final float av = UVPair.unpackV(q.packedUV(i));
            uvs[i * 2] = du == 0.0f ? 0.0f : (au - u0) / du;
            uvs[i * 2 + 1] = dv == 0.0f ? 0.0f : (av - v0) / dv;
        }
        return new Quad(vertices, uvs, n.x, n.y, n.z, spriteIndex);
    }
}
