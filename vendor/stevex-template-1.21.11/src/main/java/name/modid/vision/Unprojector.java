package name.modid.vision;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * 反投影（Phase 2，§4）：把深度快照的每个非天空像素反投影回世界坐标，按方块格去重。
 *
 * <p>用途拆分（§4.3）：
 * <ul>
 *   <li><b>方块路</b>：{@link #visibleBlockHits} 产出去重点 {@code BlockPos.asLong → nudged Vec3}
 *       （沿射线远离相机推 ε 后的点，供 §5.1 方块直查 / air 近侧回退用）；</li>
 *   <li><b>实体候选路</b>：同一扫描里，对落在"有实体 section"（§5.3 桶，Phase 3 提供）的像素
 *       保留<b>原始表面点 W</b>（不推 ε），供 §5.3 正向像素归属。</li>
 * </ul>
 *
 * <p><b>数学（§4.1，v2.2 更正）</b>：{@code renderLevel} 的 {@code modelViewMatrix} 只含旋转、
 * 不含平移（相机平移在顶点着色器完成），故
 * {@code clip = P×MV×r → r = (P×MV)⁻¹×clip} 反推的是<b>相机相对坐标</b>，必须加回
 * {@code camPos} 才是世界坐标：{@code W = camPos + r}。
 *
 * <p><b>坐标约定</b>：深度数组按 GL 行序存储（行号自底向上，y=0 为最底行），本类全程沿用
 * <b>GL 行序（底->上）</b>约定——{@code ndcY = 2(y+0.5)/height − 1} 直接对 y 成立，等价于
 * 设计 §3.4 的"读回后垂直翻转再反投影"而无需 O(像素) 拷贝；像素中心约定见 §4.1（v2.11）。
 *
 * <p>本类为纯数学、无竞态，可在 API 线程执行（§8）。
 */
public class Unprojector {

    /**
     * 沿射线远离相机推 ε 的<b>落格量</b>（设计 §4.2.1，v2.39 决策 R-1）：
     * {@code nudged = W + r̂·ε}，再 {@code floor(nudged)} 定出该像素归属的格。
     *
     * <p><b>为什么从 0.05 下调到 1/128</b>（§4.2.1）：这个推移要同时满足两个约束，而两者由
     * <b>不相交的两群面</b>决定，单一常数调不出兼顾值——
     * <ul>
     *   <li><b>下界</b>（让落在<b>格界平面</b>上的面被确定性地推入其所属格，抵消 float32 深度量化
     *       带来的"硬币翻转"）：{@code ε > 0.5·Δz(1ULP) = 5.96e-7·z²} ⇒ 0.05 覆盖到 290 格，宽裕；</li>
     *   <li><b>上界</b>（不得把<b>贴着本格出口</b>的薄几何体推出本格）：{@code ε·|dy| <}
     *       可见面到本格出口的距离。实测最小厚度是 <b>1/64 = 0.015625</b>（睡莲 / 红石粉，几何全在
     *       格内 {@code y = 1/64}），而非设计早期估计的 1/16 ⇒ 俯角 &gt; 18.2° 时
     *       {@code 0.05·|dy| > 1/64}，像素被推进<b>下方的格</b>；睡莲下方是水（非空气）⇒ §5.1 的
     *       air 近侧回退不触发 ⇒ <b>睡莲整族失明</b>（实测：快照内 0 格 {@code lily_pad}）。</li>
     * </ul>
     * {@code 1/128 = 0.0078125} 对 1/64 给出 <b>2× 余量</b>（俯角 &gt; 60° 才越界），代价是
     * 下界的可靠半径 290 → <b>114 格</b>（仍覆盖 {@code removalMaxRayDist = 96}）。
     *
     * <p><b>不要</b>把它与 {@code ObjectResolver.PROBE_EPSILON}（回退恢复 / 实体盒膨胀探针）合并为
     * 一个常量：两者同值不同义——后者的作用是"探到相邻格"，不是"跨过格界"（§4.2.1 四常量表）。
     *
     * <p><b>残留限制</b>（决策 R-2 未定案）：ε 是<b>固定世界距离</b>，而所需推移量与俯角相关
     * （{@code ε_need ≈ 到最近格界平面的距离 + k·errBound(z)}）。故极掠射把薄贴花推到极远时
     * 仍会落错格——彻底修法见 §4.2.1 / Phase 11 的 R-2（边界对齐，需 {@code blockHits} 携带
     * 每像素实际推进量）。
     */
    public static final double LANDING_EPSILON = 1.0 / 128.0;

    private final DepthCapture.DepthSnapshot snap;
    private final float dFar;
    private final Matrix4f inversePv;
    private final Matrix4f worldToClip;
    private final double camX;
    private final double camY;
    private final double camZ;
    /** 每像素复用的裁剪向量（§8：本类单线程使用，无并发）。 */
    private final Vector4f scratch = new Vector4f();

    public Unprojector(final DepthCapture.DepthSnapshot snap) {
        this.snap = snap;
        this.dFar = DepthCapture.depthOfFarPlane(snap.projectionMatrix());
        // clip = P × MV × (world − camPos)  →  world = camPos + (P×MV)⁻¹ × clip（§4.1）
        // JOML mul(right,dest) = this*right
        this.inversePv = snap.projectionMatrix().mul(snap.modelViewMatrix(), new Matrix4f()).invert();
        // 世界坐标 → 裁剪坐标（反投影的逆，§5.4 投影方块 / §4.3 可见性查询用）：
        // worldToClip = P × MV × T(−camPos)
        final Matrix4f view = new Matrix4f().translation((float) -snap.cameraPos().x, (float) -snap.cameraPos().y, (float) -snap.cameraPos().z);
        this.worldToClip = snap.projectionMatrix().mul(snap.modelViewMatrix(), new Matrix4f()).mul(view, new Matrix4f());
        final Vec3 cam = snap.cameraPos();
        this.camX = cam.x;
        this.camY = cam.y;
        this.camZ = cam.z;
    }

    /** 天空阈值 = 远平面极限深度（v2.6，由投影矩阵推导，≈1.0）。 */
    public float dFar() {
        return dFar;
    }

    /**
     * 全量反投影 + 去重（§4.3，不做像素降采样）。
     *
     * @param entitySections 有实体 section 的 key 集合（§5.3 桶，Phase 3 由实体快照构建）；
     *                       null / 空 → 跳过实体候选收集（快速路径）
     * @param epsilon 沿射线推的落格量（设计 §4.2.1；正常路径用 {@link #LANDING_EPSILON}）
     * @return 反投影结果（方块去重点 + 实体候选原始点 W + 统计）
     */
    public UnprojectResult visibleBlockHits(final LongSet entitySections, final double epsilon) {
        final int width = snap.width();
        final int height = snap.height();
        final float[] depth = snap.depth();
        final boolean collectEntities = entitySections != null && !entitySections.isEmpty();

        final Long2ObjectOpenHashMap<Vec3> hits = new Long2ObjectOpenHashMap<>(4096, 0.75f);
        final List<Vec3> entityCandidates = collectEntities ? new ArrayList<>(256) : List.of();

        final Vector4f clip = scratch; // 每像素复用，零分配
        final double invW = 1.0 / width;
        final double invH = 1.0 / height;
        int nonSky = 0;
        int clipped = 0;
        // 诊断（§4.2.1 决策 R-2 的在线指示器）：本次推移**改变了所属格**的像素数。
        int boundaryResolved = 0;

        for (int y = 0; y < height; y++) {
            final double ndcY = 2.0 * (y + 0.5) * invH - 1.0;
            final int row = y * width;
            for (int x = 0; x < width; x++) {
                final float d = depth[row + x];
                if (d >= dFar) continue; // 天空 / 远平面极限深度（§4.2）
                nonSky++;

                final double ndcX = 2.0 * (x + 0.5) * invW - 1.0;
                final double ndcZ = 2.0 * d - 1.0;
                clip.set((float) ndcX, (float) ndcY, (float) ndcZ, 1.0f);
                inversePv.transform(clip);
                final float cw = clip.w;
                if (cw <= 0.0f) { // 近裁剪面内 / 相机背后 → 丢弃
                    clipped++;
                    continue;
                }
                final float inv = 1.0f / cw;
                final float rx = clip.x * inv;
                final float ry = clip.y * inv;
                final float rz = clip.z * inv;

                // 原始表面点 W = camPos + r（实体候选用，不推 ε）
                final double wx = camX + rx;
                final double wy = camY + ry;
                final double wz = camZ + rz;

                if (collectEntities) {
                    final long section = SectionPos.asLong(
                            SectionPos.blockToSectionCoord(wx),
                            SectionPos.blockToSectionCoord(wy),
                            SectionPos.blockToSectionCoord(wz));
                    if (entitySections.contains(section)) {
                        entityCandidates.add(new Vec3(wx, wy, wz));
                    }
                }

                // 沿射线远离相机推 ε 的去重点（方块路；零分配：先算 len，只有唯一方块才建 Vec3）
                final double len = Math.sqrt(rx * rx + ry * ry + rz * rz);
                if (len <= 1e-9) {
                    clipped++;
                    continue;
                }
                final double scale = 1.0 + epsilon / len;
                final long key = BlockPos.asLong(
                        Mth.floor(camX + rx * scale),
                        Mth.floor(camY + ry * scale),
                        Mth.floor(camZ + rz * scale));
                // 诊断（§4.2.1 决策 R-2）：W 与 nudged 落格不同的像素 = "floor(W) 本会落在别格、
                // 靠这次推移才落对"的那一群（即面恰好压在格界平面上的像素）。ε 仍在量化噪声之上时
                // 该计数稳定；一旦 ε 低于 0.5 ULP，这群像素的落格退化成掷硬币、计数会开始抖动
                // ——故它是"ε 是否仍满足下界"的在线指示器，也是 R-2 是否必须立项的判据。
                if (BlockPos.asLong(Mth.floor(wx), Mth.floor(wy), Mth.floor(wz)) != key) {
                    boundaryResolved++;
                }
                if (!hits.containsKey(key)) {
                    hits.put(key, new Vec3(camX + rx * scale, camY + ry * scale, camZ + rz * scale));
                }
            }
        }

        return new UnprojectResult(hits, entityCandidates, nonSky, clipped, boundaryResolved, epsilon);
    }

    /** 便捷重载：用 {@link #LANDING_EPSILON}、不收集实体候选。 */
    public UnprojectResult visibleBlockHits() {
        return visibleBlockHits(null, LANDING_EPSILON);
    }

    /**
     * 反投影单个像素 → 原始表面点 W（§5.4 精筛用，像素中心约定）。
     *
     * @return {@code W = camPos + r}；天空（d ≥ d_far）或近裁剪面内（clip.w ≤ 0）返回 null
     */
    public Vec3 unprojectPixel(final int x, final int y, final float d) {
        if (d >= dFar) return null;
        final double ndcX = 2.0 * (x + 0.5) / snap.width() - 1.0;
        final double ndcY = 2.0 * (y + 0.5) / snap.height() - 1.0;
        final double ndcZ = 2.0 * d - 1.0;
        scratch.set((float) ndcX, (float) ndcY, (float) ndcZ, 1.0f);
        inversePv.transform(scratch);
        final float cw = scratch.w;
        if (cw <= 0.0f) return null;
        final float inv = 1.0f / cw;
        return new Vec3(camX + scratch.x * inv, camY + scratch.y * inv, camZ + scratch.z * inv);
    }

    /**
     * 像素 → 世界射线方向（归一化，§5.4 精筛用）。mv 纯旋转 → 相机相对方向即世界方向。
     *
     * @return 归一化方向；近裁剪面内（clip.w ≤ 0）或退化返回 null
     */
    public Vec3 pixelRay(final int x, final int y) {
        final double ndcX = 2.0 * (x + 0.5) / snap.width() - 1.0;
        final double ndcY = 2.0 * (y + 0.5) / snap.height() - 1.0;
        scratch.set((float) ndcX, (float) ndcY, 1.0f, 1.0f);
        inversePv.transform(scratch);
        final float cw = scratch.w;
        if (cw <= 0.0f) return null;
        final float inv = 1.0f / cw;
        final float dx = scratch.x * inv;
        final float dy = scratch.y * inv;
        final float dz = scratch.z * inv;
        final double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len <= 1e-9) return null;
        return new Vec3(dx / len, dy / len, dz / len);
    }

    /**
     * 该像素远平面（depth=1.0）世界点与相机的距离，作为天空像素（Z_opaque=+∞）的推进上界
     * （v2.26 §5.4 区间射线推进用）。透视下远平面是 view-z=−F 的平面，射线越斜（近地平线）
     * 其距离 F/(dir·forward) 越大——用标量 F 会过早截断边缘像素可见的半透明，故按本像素
     * ndcZ=1.0（= 深度 1.0）反投影求精确距离。
     *
     * @return 远平面距离；近裁剪面内（clip.w≤0，正常像素不应发生）返回 +∞
     */
    public double farPlaneDistance(final int x, final int y) {
        final double ndcX = 2.0 * (x + 0.5) / snap.width() - 1.0;
        final double ndcY = 2.0 * (y + 0.5) / snap.height() - 1.0;
        scratch.set((float) ndcX, (float) ndcY, 1.0f, 1.0f);
        inversePv.transform(scratch);
        final float cw = scratch.w;
        if (cw <= 0.0f) return Double.POSITIVE_INFINITY;
        final float inv = 1.0f / cw;
        final float rx = scratch.x * inv;
        final float ry = scratch.y * inv;
        final float rz = scratch.z * inv;
        return Math.sqrt(rx * rx + ry * ry + rz * rz);
    }

    /** 世界坐标 → 屏幕像素（用本快照的 P×MV×T(−camPos) 完整矩阵，§5.4 投影用）。 */
    public float[] projectToScreen(final Vec3 world, final int width, final int height) {
        return projectToScreen(world, worldToClip, width, height);
    }

    /**
     * 世界坐标 → 屏幕像素（§4.2 / §5.4 投影用）。像素中心约定（v2.11）：
     * {@code px = (ndcX+1)·width/2 − 0.5}；返回 y 为 <b>GL 行号（自底向上）</b>，
     * 与深度数组 {@code depthAt(x, y)} 同一约定，可直接取深度。
     *
     * @param pv 投影 × 模型视图矩阵（含平移的完整矩阵）
     * @return {px, py}；点在相机背后（w≤0）或 NDC 越界时返回 null
     */
    public static float[] projectToScreen(final Vec3 world, final Matrix4f pv, final int width, final int height) {
        final Vector4f clip = new Vector4f((float) world.x, (float) world.y, (float) world.z, 1.0f);
        pv.transform(clip);
        if (clip.w <= 0.0f) return null;
        final float inv = 1.0f / clip.w;
        final float ndcX = clip.x * inv;
        final float ndcY = clip.y * inv;
        if (ndcX < -1.0f || ndcX > 1.0f || ndcY < -1.0f || ndcY > 1.0f) return null;
        return new float[]{(ndcX + 1.0f) * width * 0.5f - 0.5f, (ndcY + 1.0f) * height * 0.5f - 0.5f};
    }

    /** 反投影结果（§4.3 产出 + §6.2 统计）。 */
    public record UnprojectResult(
            /** 方块去重点：BlockPos.asLong → nudged 世界点（供 §5.1 直查 / air 近侧回退）。 */
            Long2ObjectOpenHashMap<Vec3> blockHits,
            /** 实体候选像素的原始表面点 W（仅落在有实体 section 的像素，§5.3 正向匹配用）。 */
            List<Vec3> entityCandidatePoints,
            /** 非天空像素数（d &lt; d_far）。 */
            int nonSkyPixels,
            /** 近裁剪面内 / 相机背后 / 长度退化而被丢弃的像素数。 */
            int clippedPixels,
            /**
             * 落格推移**改变了所属格**的像素数（设计 §4.2.1 决策 R-2 的在线指示器）。
             * 正常量级：与"压在格界平面上的面"的屏幕占比同阶（远小于 nonSkyPixels）；
             * 若它在相邻帧间大幅抖动，说明 ε 已低于 0.5 ULP 的量化下界 ⇒ 该距离上的落格不可信。
             */
            int boundaryResolvedPixels,
            /**
             * 本次落格推移**实际使用的** ε（即 {@code nudged = W + r̂·ε} 里的那个 ε）。
             *
             * <p>之所以要随结果带出来：§5.1 的 air 近侧回退要还原原始表面点，而
             * {@code W = nudged − dir·ε} 在数学上是这次推移的<b>逆运算</b>——回退必须用同一个 ε，
             * 否则还原出的 W 会沿射线偏 {@code Δε}，薄几何体（1/64）上就直接穿到后面那块去了。
             * 两值同源时这个耦合看不出来，一旦落格量单独下调就会立刻致命（v2.39 决策 R-1 的
             * 副作用，故把"实际推进量"作为结果的一部分显式传递，也是 §4.2.1 R-2 的方向）。
             */
            double landingEpsilon
    ) {
        public int uniqueBlockCount() {
            return blockHits.size();
        }
    }
}
