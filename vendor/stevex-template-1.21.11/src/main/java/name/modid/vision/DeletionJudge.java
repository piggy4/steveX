package name.modid.vision;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * v2.23（§7.11）减量判定器 —— 对记忆侧反向通道上报的「记忆格」用本次深度快照做逐块判定，
 * 证明哪些格在现实中已不存在，产出 {@code deletions}。
 *
 * <p><b>核心原理（§7.11）</b>：某<b>实心 + 不透明</b>格 B 若在现实中存在，任何从相机出发的射线
 * 碰到其近表面即终止（正是深度测到的值）。故「存在某像素射线穿过了 B 的整格（该像素深度
 * ≥ B 远面距离 t_far）」⟺ B 已不存在。判定有几何证明，非启发式。
 *
 * <p>对每个记忆格 B（先跳过本次可见集 {@code currentTerrain} 内的格——可见格由 §5.1 放置/更新
 * 路径处理，不参与减量）：
 * <ol>
 *   <li>投影 B 的 8 角 → 屏幕 bbox（像素中心约定 §4.1，循环前裁剪到屏幕范围 §5.4）；</li>
 *   <li>逐 bbox 像素 p：射线(camPos→p) 与 B 的整格 AABB 做<b>手写 slab 求交</b>（§5.4，返回
 *       带符号 t_entry 与远面 t_far）；不相交 → continue；读该像素深度还原 {@code Z_opaque}
 *       （欧氏距离，与 t 同度量）；{@code Z_opaque ≥ t_far − δ} → 该像素射线穿过了 B 的整格
 *       → 越过计数++；</li>
 *   <li><b>越过计数 ≥ removalPixelThreshold（默认 2）→ B 被证明消失</b> → 进 {@code deletions}。</li>
 * </ol>
 *
 * <p><b>为何 ≥2 而非 1（§7.11）</b>：单像素可能是浮点擦边 / 深度量化误读（§4.2）；≥2 是 B 自身
 * 投影内<b>多条独立射线一致证明</b>。判定单快照完成——无需跨帧、无需门控，静态玩家一次快照即可删。
 *
 * <p><b>逐块而非逐像素 DDA（§7.11）</b>：DDA 对每个像素沿射线逐格走、多数穿空区域（浪费）；
 * 逐块只测「确实存在的块」的投影像素，成本 O(视锥内记忆块数 × 投影像素)（毫秒级），并天然覆盖
 * <b>背后是天空 / 背后是不透明块 / 被部分遮挡</b>三况——v2.22 的 surface/skyRays 两类证据被完全取代。
 *
 * <p>阈值来自 cells 文件头（记忆侧 {@code MemoryConfig.removalPixelThreshold} 随通道下发，单一来源）；
 * 解析失败用 {@link MemoryCellsReader#DEFAULT_PIXEL_THRESHOLD}。
 *
 * <p>v2.36（§7.12）{@link #testTranslucent}：main 场判据的<b>同构镜像</b>，逐像素深度换读 translucent
 * 目标（"首个半透明面"，主拷贝 = 无半透明在前）。只判记忆侧 translucent 段（水 / 满格透明）；
 * 由 {@code ObjectResolver} 按图形配置路由（Fabulous && hasTranslucentDepth 时调用）。
 */
public final class DeletionJudge {

    /** §5.4 同款深度比较容差（float32 量化，v2.11：仅 ≤~100 格内成立；removalMaxRayDist=96 钉在可靠区）。 */
    private static final double DELTA = 0.05;

    private DeletionJudge() {
    }

    /**
     * 对记忆格清单做逐块判定，产出被证明消失的格列表（v2.23 main 场，§7.11）。
     *
     * @param snap 本次深度快照（depth / cameraPos）
     * @param unproj 本快照的反投影器（pixelRay / unprojectPixel / projectToScreen / dFar）
     * @param memoryCells 记忆侧上报的待判定格（已按距离球过滤）
     * @param pixelThreshold 越过像素阈值（默认 2）
     * @param currentTerrain 本次可见方块集（这些格绝不判删，省一次投影 + 双保险）
     * @return 被证明消失的格列表（可为空）
     */
    public static List<BlockPos> test(
            final DepthCapture.DepthSnapshot snap,
            final Unprojector unproj,
            final List<BlockPos> memoryCells,
            final int pixelThreshold,
            final Set<BlockPos> currentTerrain
    ) {
        return judge(snap, unproj, memoryCells, pixelThreshold, currentTerrain, false);
    }

    /**
     * v2.36（§7.12）translucent 场判定变体 —— 与 {@link #test} 唯一差异是把逐像素深度读换成
     * <b>translucent 目标</b>（{@code translucentDepthAt}），bbox/slab/δ/阈值全复用。
     *
     * <p>语义（§7.12）：translucent 目标每像素 = main 拷贝（copyDepthFrom(main)）后被 TRANSLUCENT 组
     * 在前 LEQUAL 覆盖 → "首个半透明面"；"无半透明在前"被编码成主拷贝（{@code t == m}）而非独立空值。
     * 因此读<b>全体像素</b>（含 t==m）同式比较 {@code Z_translucent ≥ t_far − δ} 即可判该格表层消失——
     * 与 main 场判据唯一差异只在哨兵：不透明场"无表面 = 天空 1.0"，translucent 场"无半透明 = 主拷贝"
     * （主拷贝本身在远处时也是 1.0/dFar → ∞，代码路径相同）。
     *
     * <p>调用前提：仅 Fabulous 且 {@code snap.hasTranslucentDepth()}（第二路 PBO 回读成功）。
     * 无 translucent 目标（Fancy/Fast / PBO 降级）不可调用——水/满格透明不喂 main 场判据（§7.12
     * 判据场选择），此处防御性直接判空集（调用方 {@code ObjectResolver} 已按配置路由）。
     *
     * @param snap 本次深度快照（须含 translucentDepth，见 {@link DepthCapture.DepthSnapshot#hasTranslucentDepth()}）
     * @param unproj 本快照的反投影器（pixelRay / unprojectPixel / projectToScreen / dFar）
     * @param memoryCells translucent 段待判定格（水 / 满格透明，已按距离球过滤）
     * @param pixelThreshold 越过像素阈值（默认 2）
     * @param currentTerrain 本次可见方块集（这些格绝不判删，双保险）
     * @return 被证明表层消失的格列表（可为空）
     */
    public static List<BlockPos> testTranslucent(
            final DepthCapture.DepthSnapshot snap,
            final Unprojector unproj,
            final List<BlockPos> memoryCells,
            final int pixelThreshold,
            final Set<BlockPos> currentTerrain
    ) {
        if (!snap.hasTranslucentDepth()) return List.of(); // 防御：无 translucent 目标 → 不可判（宁欠勿删）
        return judge(snap, unproj, memoryCells, pixelThreshold, currentTerrain, true);
    }

    /** 共享判定主循环；{@code translucentField} 决定逐像素读 main 场还是 translucent 场。 */
    private static List<BlockPos> judge(
            final DepthCapture.DepthSnapshot snap,
            final Unprojector unproj,
            final List<BlockPos> memoryCells,
            final int pixelThreshold,
            final Set<BlockPos> currentTerrain,
            final boolean translucentField
    ) {
        if (memoryCells.isEmpty()) return List.of();

        final Vec3 cam = snap.cameraPos();
        final double camX = cam.x, camY = cam.y, camZ = cam.z;
        final List<BlockPos> deletions = new ArrayList<>();
        final int width = snap.width();
        final int height = snap.height();
        final float dFar = unproj.dFar();
        final int thr = Math.max(1, pixelThreshold);

        for (BlockPos pos : memoryCells) {
            // 可见格由 §5.1 放置/更新路径处理，不参与减量（§7.11 双保险 + 优化）
            if (currentTerrain.contains(pos)) continue;
            // 相机在格内 → 格必然存在（游泳/站在格内；防御性跳过）
            if (pos.getX() <= camX && camX <= pos.getX() + 1.0
                    && pos.getY() <= camY && camY <= pos.getY() + 1.0
                    && pos.getZ() <= camZ && camZ <= pos.getZ() + 1.0) {
                continue;
            }
            if (provenGone(snap, unproj, cam, width, height, dFar, pos, thr, translucentField)) {
                deletions.add(pos);
            }
        }
        return deletions;
    }

    /** 单格判定：投影 8 角 → bbox（裁剪到屏幕）→ 逐像素 ray-AABB + 深度比较 → 越过 ≥ 阈值。 */
    private static boolean provenGone(
            final DepthCapture.DepthSnapshot snap,
            final Unprojector unproj,
            final Vec3 cam,
            final int width,
            final int height,
            final float dFar,
            final BlockPos pos,
            final int threshold,
            final boolean translucentField
    ) {
        final double minX = pos.getX(), minY = pos.getY(), minZ = pos.getZ();
        final double maxX = minX + 1.0, maxY = minY + 1.0, maxZ = minZ + 1.0;

        // ① 投影 8 角 → 屏幕 bbox（超集；全角不可投影 → 盒在相机背后 / 完全出屏 → 无法判定，保留）
        float minPx = Float.MAX_VALUE, maxPx = -Float.MAX_VALUE;
        float minPy = Float.MAX_VALUE, maxPy = -Float.MAX_VALUE;
        final double[] cornersX = {minX, minX, minX, minX, maxX, maxX, maxX, maxX};
        final double[] cornersY = {minY, minY, maxY, maxY, minY, minY, maxY, maxY};
        final double[] cornersZ = {minZ, maxZ, minZ, maxZ, minZ, maxZ, minZ, maxZ};
        int projected = 0;
        for (int i = 0; i < 8; i++) {
            final float[] pp = unproj.projectToScreen(new Vec3(cornersX[i], cornersY[i], cornersZ[i]), width, height);
            if (pp == null) continue;
            projected++;
            minPx = Math.min(minPx, pp[0]); maxPx = Math.max(maxPx, pp[0]);
            minPy = Math.min(minPy, pp[1]); maxPy = Math.max(maxPy, pp[1]);
        }
        if (projected == 0) return false;

        // ② 循环前裁剪到屏幕范围（v2.10）：越界像素 depthAt 钳到 1.0 会静默读成天空 → 假阳性
        final int x0 = Math.max(0, (int) Math.floor(minPx));
        final int x1 = Math.min(width - 1, (int) Math.ceil(maxPx));
        final int y0 = Math.max(0, (int) Math.floor(minPy));
        final int y1 = Math.min(height - 1, (int) Math.ceil(maxPy));

        int over = 0;
        for (int py = y0; py <= y1; py++) {
            for (int px = x0; px <= x1; px++) {
                final Vec3 dir = unproj.pixelRay(px, py);
                if (dir == null) continue;
                final double[] interval = slabInterval(cam, dir, minX, minY, minZ, maxX, maxY, maxZ);
                if (interval == null) continue; // 射线不穿 B 的整格（bbox 超集 → continue 防误判）
                final double tFar = interval[1];

                // v2.36：按判据场选读 —— main 场 depthAt（§7.11）或 translucent 场 translucentDepthAt
                //（§7.12：主拷贝在远处时同样落 dFar → ∞，哨兵差异见 testTranslucent javadoc）
                final float d = translucentField ? snap.translucentDepthAt(px, py) : snap.depthAt(px, py);
                final double zOpaque;
                if (d >= dFar) {
                    zOpaque = Double.POSITIVE_INFINITY; // 该像素背后是天空 → 射线穿过 B 到远平面
                } else {
                    final Vec3 w = unproj.unprojectPixel(px, py, d);
                    if (w == null) continue;
                    zOpaque = w.distanceTo(cam);
                }
                if (zOpaque >= tFar - DELTA && ++over >= threshold) {
                    return true; // ≥2 像素一致证明 → 判消失（提前退出）
                }
            }
        }
        return false;
    }

    /**
     * 手写 slab 求交（§5.4/§12：vanilla {@code AABB.clip} 不支持带符号 t）。返回
     * {@code double[]{t_entry, t_far}}：近交点<b>带符号</b>（起点在盒内为负）、远交点为正；
     * 射线不穿盒返回 null。比较基准用<b>块远面 t_far</b>（§5.4 用近面 t_entry）。
     */
    private static double[] slabInterval(
            final Vec3 origin, final Vec3 dir,
            final double minX, final double minY, final double minZ,
            final double maxX, final double maxY, final double maxZ
    ) {
        final double ox = origin.x, oy = origin.y, oz = origin.z;
        double tmin = Double.NEGATIVE_INFINITY;
        double tmax = Double.POSITIVE_INFINITY;

        if (Math.abs(dir.x) < 1e-12) {
            if (ox < minX || ox > maxX) return null;
        } else {
            double t1 = (minX - ox) / dir.x;
            double t2 = (maxX - ox) / dir.x;
            if (t1 > t2) { final double t = t1; t1 = t2; t2 = t; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return null;
        }
        if (Math.abs(dir.y) < 1e-12) {
            if (oy < minY || oy > maxY) return null;
        } else {
            double t1 = (minY - oy) / dir.y;
            double t2 = (maxY - oy) / dir.y;
            if (t1 > t2) { final double t = t1; t1 = t2; t2 = t; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return null;
        }
        if (Math.abs(dir.z) < 1e-12) {
            if (oz < minZ || oz > maxZ) return null;
        } else {
            double t1 = (minZ - oz) / dir.z;
            double t2 = (maxZ - oz) / dir.z;
            if (t1 > t2) { final double t = t1; t1 = t2; t2 = t; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return null;
        }
        return new double[]{tmin, tmax};
    }
}
