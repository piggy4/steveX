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

    /**
     * §5.4 同款深度比较容差**上界**（float32 量化，v2.11：仅 ≤~100 格内成立；removalMaxRayDist=96
     * 钉在可靠区）。它是 {@link #deltaFor} 的封顶值，<b>不再被直接用于判据 2b</b>——见那里的说明。
     * 整格判据（{@link #test}，满格 {@code shapeMaxY = 1} ⇒ {@code δ_cell ≡ DELTA_GLOBAL}）仍直接用它。
     *
     * <p>{@code public} 是为了诊断：{@code ObjectResolver} 要报"本轮有多少格走了贴花收紧分支
     * （{@code δ_cell < DELTA_GLOBAL}）"（设计 §10 第 16 条）。
     */
    public static final double DELTA_GLOBAL = 0.05;

    /**
     * 逐格 δ 的收缩系数（设计 §3.2 2b-δ / §7 决策 O）：
     * {@code δ_cell = max(0, min(DELTA_GLOBAL, KAPPA · shapeMaxY))}，取 0.5 使"该格最有利像素"上的
     * 深度跳变仍是余量的 2 倍。（v2.39 起外层还有钳零与可行窗口判定，见 {@link #deltaFor} / 决策 Q。）
     */
    private static final double KAPPA = 0.5;

    /**
     * 深度量化台阶的闭式系数（设计 §4.2 / §14.5，跨文档同一口径）：
     * {@code Δz(1 ULP) = 2⁻²⁴·z²/B ≈ 2⁻²⁴·z²/n = 1.19209e-6·z²}（n = 近平面 = 0.05）。
     * <b>与远平面 f 无关</b>（B ≈ n）——只由近平面与 float32 的 24 位尾数定。
     *
     * <p>注：旧稿用的 {@code 2^-20·z² = 9.54e-7·z²} 比本值<b>小 1.25×</b>（仅相当于 0.8 ULP）。
     */
    private static final double QUANTUM_PER_Z2 = 1.19209e-6;

    /**
     * δ 的<b>噪声下界系数</b> c（设计 §7 <b>决策 Q</b>——它取代决策 P；`c` 的实测标定见 §10 第 17 条）：
     * {@code δ_cell ≥ c · QUANTUM_PER_Z2 · z²}。
     *
     * <p><b>为什么 δ 必须有下界</b>：决策 P 的"量化只会吞掉信号、不会凭空造出信号"只在比较两侧
     * <b>同为量化值</b>时成立；而判据 2b 比较的是<b>量化观测 Z vs 解析阈值 {@code t_shape_exit + δ}</b>
     * ——阈值没有量化。残差 {@code e = Z − t_surface} 的界是 <b>0.5 ULP</b>（正负皆可）⇒ 正残差
     * {@code e ≥ δ} 即<b>误删活体</b>。故 δ 必须高于噪声，与"δ 越小越能删"的可达性需求构成
     * 一个<b>双边窗口</b>。
     *
     * <p><b>为何取 1.0 而非 0.5</b>：0.5 是纯量化给出的对称界，但 {@code t_surface} 是对<b>记忆几何</b>
     * 解析求交、{@code Z} 来自<b>实时世界</b>渲染，两者只在"活体与记忆一致"时重合——取一整个台阶为
     * 该前提买保险。§10 第 17 条测出 {@code |e|} 上界后可下调（改这一个数即生效，无需动别处）。
     */
    public static final double DELTA_NOISE_C = 1.0; // public：ObjectResolver 的诊断行要打出来

    /**
     * 判据 2b 的深度比较余量，<b>逐格取值</b>（设计 §3.2 2b-δ，2026-09-11 五次修订）。
     *
     * <p><b>为什么不能是全局常量</b>（可达性必要条件）：判据 {@code Z ≥ t_shape_exit + δ} 要成立，
     * 观察面必须落在形状之外至少 δ。而形状能提供的深度跳变上限就是它自身的几何高度：
     * 命中点高度 {@code h_hit ≤ shapeMaxY}，跳变 {@code = h_hit / |dy|}，且射线是单位向量
     * ⇒ {@code |dy| ≤ 1} ⇒ 跳变 ≤ {@code shapeMaxY}。故 <b>δ ≥ shapeMaxY 时判据按构造不可满足</b>——
     * 该格无论是否真的消失都永不越票。
     *
     * <p><b>实测反例（本次修订的起因）</b>：红石粉 / 落叶是"贴地平铺贴花"，全部 quad 都在
     * {@code y = 1/64 = 0.0156}，而支撑块顶面就是格底 ⇒ 消失后能拿到的跳变恒为
     * {@code 1/64 / |dy| ≤ 0.0156 < DELTA_GLOBAL = 0.05} ⇒ 在旧的全局 δ 下<b>永不判消失</b>
     * （实测：几何段两端 epoch 一致、{@code shaped} 分母 712、越票恒 0）。
     *
     * <p><b>对现有行为的零回归</b>：{@code shapeMaxY ≥ DELTA_GLOBAL} 的形状（草丛/藤/竹/台阶/栅栏/墙…，
     * 均跨满格内 0..1）恒得 {@code δ_cell = DELTA_GLOBAL}，与修订前逐位相同。
     *
     * <p><b>为什么不取逐像素的命中高度 {@code h_hit}</b>：那会让"贴近地面的命中"处 δ 变小，
     * 而那恰是最容易受深度噪声影响、最需要余量的像素——方向反了。取格内最大值是"按该格最有利的
     * 像素定余量"，对弱像素只会更严，恒走欠删方向。
     *
     * <p><b>δ 必须有下界（v2.39 决策 Q，取代决策 P）</b>：旧论证"量化只能吞掉信号、不能凭空造出
     * 信号"只在比较两侧<b>同为量化值</b>时成立。2b 是<b>量化观测 Z vs 解析阈值</b>，残差
     * {@code e = Z − t_surface} 可正可负（界 0.5 ULP）⇒ 正残差 {@code ≥ δ} 即误删活体。
     * 故 δ 的取值是一个<b>双边窗口</b>：下界 {@link #deltaNoiseFloor}（抗噪），上界即本函数
     * （可达性）。窗口闭合时由 {@link #testShaped} 判为"不可判"并跳过，<b>不得</b>硬用上界，
     * 也<b>不得</b>回退到全局 δ（那会让红石粉/落叶重新失效）。
     */
    public static double deltaFor(final float shapeMaxY) {
        // 钳零（决策 Q）：h_max ≤ 0 时旧式 min() 会给出负 δ——比"零容差"还松，方向不安全。
        return Math.max(0.0, Math.min(DELTA_GLOBAL, KAPPA * shapeMaxY));
    }

    /**
     * δ 的<b>噪声下界</b>（设计 §7 决策 Q）：{@code c · 1.192e-6 · z²}（z = 观察距离，单位格）。
     *
     * <p>与 {@link #deltaFor} 的返回上界构成可行窗口 {@code [本值, deltaFor(h_max))}：
     * 低于下界 ⇒ 噪声能伪造越票（误删活体）；高于上界 ⇒ 判据按构造不可满足（永不删）。
     * <b>窗口闭合</b>（本值 ≥ 上界）⇒ 该格本帧在 2b 上两面都不可判。
     *
     * <p>贴花（{@code h_max = 1/64} ⇒ 上界 {@code 0.5/64 = 0.0078125}）的窗口在 {@code z = 80.95/√c}
     * 处闭合：c=1 → <b>81 格</b>、c=0.5 → 114.5 格、c=2 → 57 格（设计 §14.5）。注意 ε 侧也有一个
     * 114.5，那是"1/64 ÷ 1 ULP"的同一算式、问的是<b>落格可观测性</b>——两回事，勿混。
     *
     * @param z 相机到该格的观察距离<b>上界</b>（格粒度保守取值，见 {@link #testShaped}）
     */
    public static double deltaNoiseFloor(final double z) {
        return DELTA_NOISE_C * QUANTUM_PER_Z2 * z * z;
    }

    /** 命中点必须落在格内的容差（0：命中点与格面同源计算，不需要额外余量）。 */
    private static final double TINCELL_EPS = 0.0;

    /** Möller–Trumbore 行列式退化阈值（射线平行于三角形平面 / 零面积三角形 → 无命中）。 */
    private static final double MT_EPS = 1e-12;

    /** 重心坐标擦边阈值（§3.5 的弦长 ε 在重心空间的等价实现，见 {@link #testShaped} 的说明）。 */
    private static final double BARY_EPS = 1e-3;

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
                if (zOpaque >= tFar - DELTA_GLOBAL && ++over >= threshold) {
                    return true; // ≥2 像素一致证明 → 判消失（提前退出）
                }
            }
        }
        return false;
    }

    // ==================== v2.37：非满形状格判定（§3.2 两步法） ====================

    /**
     * v2.37（§3.2 / §5.1）<b>非满形状格</b>判定 —— 在整格 bbox 粗筛（步骤 1，与 {@link #test} 同款）
     * 之上，加第二层"射线 ∩ 记忆形状 → 重心插值 UV → 查 sprite alpha 掩码 → 按该层阈值判 discard"
     * 的精确求交（步骤 2a），比较基准换成<b>形状远出口</b>（步骤 2b）。
     *
     * <p><b>为什么需要它（§1）</b>：栅栏 / 铁栏杆 / 墙 / 玻璃板 / 十字植物这类"非满格"方块，
     * 其格内大部分体积是空的。整格判据（{@link #test}）把"射线只穿过空余区"也计为一票越过，
     * 于是<b>方块还在也会被删</b>——这是设计 §1 描述的"档 2/档 3 欠删与误删并存"里的误删侧。
     * 本方法把"一票"的定义收窄到"射线真的穿过了该方块的渲染足迹"。
     *
     * <p><b>逐步语义</b>：
     * <ol>
     *   <li><b>步骤 1（粗筛）</b>：格 bbox 投影 → 屏幕 bbox（像素中心约定 + 循环前裁剪，与 {@link #test}
     *       完全一致）；射线不穿格 bbox → 该像素无信息；</li>
     *   <li><b>步骤 2a（精筛）</b>：对格内每条 quad 的两个三角形做 Möller–Trumbore，命中须同时满足
     *       ① <b>正面</b> {@code dot(n_q, dir) < 0}（背面被地形渲染剔除，不写深度，命中它不构成证据）、
     *       ② <b>掩码通过</b>（重心插值出 sprite 局部 UV → 查该 sprite 的 alpha → {@code ≥ alphaThr}；
     *       不过则 {@code terrain.fsh} 会 discard，该处不写深度）、
     *       ③ <b>命中点在格内</b>（{@code t ∈ [t_entry, t_far]}，即射线与格 bbox 的交区间）；</li>
     *   <li><b>步骤 2b（比较）</b>：{@code t_shape_exit = max(t | 有效命中)}，该像素判"越过" ⟺
     *       {@code Z ≥ t_shape_exit + δ_cell} —— 即观察深度在<b>形状远出口之外</b>，射线穿过了整个形状
     *       而未停止。{@code δ_cell = max(0, min(DELTA_GLOBAL, KAPPA · shapeMaxY))} 是<b>逐格</b>量
     *       （见 {@link #deltaFor}）：全局常量 δ 对"贴地平铺贴花"（红石粉/落叶，几何全在 {@code y=1/64}）
     *       大于其整个几何高度 ⇒ 判据按构造不可满足 ⇒ 永不删；逐格化后对 {@code shapeMaxY ≥ DELTA_GLOBAL}
     *       的形状恒等于旧值（零回归）。
     *       <b>v2.39 追加双边窗口</b>（决策 Q）：δ 还需高于深度噪声下界
     *       {@link #deltaNoiseFloor}（量化观测 vs 解析阈值 ⇒ 残差可正），两者构成
     *       {@code [噪声下界, 可达性上界)}；<b>窗口闭合</b>的格在本循环<b>之前</b>即被跳过
     *       （见该处的说明），不计入任何证据。</li>
     * </ol>
     *
     * <p><b>为什么取 max 而不是 min（保守方向）</b>：若该方块存在，其<b>最近</b>的正面会在
     * {@code min(t)} 处写深度，故 {@code Z ≥ min(t) + δ} 已足以证明"没写"。取 {@code max} 是更<b>强</b>
     * 的条件（更难满足 ⇒ 更少删除），同时天然免疫"Spurious 命中把 max 抬高"以外的所有伪造——
     * 换句话说法：多算一个命中只会抬高 max、让删除更难；少算一个命中才会让删除更容易。
     *
     * <p><b>§3.5 的弦长 ε 在重心空间实现（实现期偏离，见交付说明）</b>：设计原文要求"有效命中的弦长
     * {@code < ε} 作废"。对<b>平面 quad</b>，射线与平面只交于一点，弦长恒 ≈ 0 ⇒ 照字面实现会把
     * <b>每一个</b>命中作废、整段几何失效。故改为等价且可达成的形式：<b>重心坐标</b>
     * {@code min(w0,w1,w2) < ε_bary} 视为擦边（覆盖 quad 外沿与对角线，正是浮点最不可靠处），
     * 且擦边时作废的是<b>整个像素</b>而非单条命中 —— 像素作废只减少证据 ⇒ 恒走欠删方向，
     * 是无条件安全的（若只作废单条命中，反而可能压低 {@code max} 让删除更容易，方向不安全）。
     *
     * <p><b>判据场</b>：逐格由 {@link ShapedCellData.ResolvedCell#translucentField()} 决定读 main 场
     * 还是 translucent 场。路由结果落到 translucent 场、而本次快照无 translucent 目标
     * （非 Fabulous / PBO 降级）⇒ 该格<b>不可判</b>（跳过，欠删）——绝不回退到 main 场：判据场与
     * 渲染该方块的那一层不同源时，读到的深度不是"该方块的表层深度"，比较无意义。
     *
     * @param snap 本次深度快照
     * @param unproj 本快照的反投影器
     * @param shapedCells 几何段解引用后的可判格（alpha 阈值 / 判据场已由 {@code ObjectResolver} 定好）
     * @param pixelThreshold 越过像素阈值（默认 2）
     * @param currentTerrain 本次可见方块集（这些格绝不判删）
     * @return 被证明消失的格列表（可为空）
     */
    public static List<BlockPos> testShaped(
            final DepthCapture.DepthSnapshot snap,
            final Unprojector unproj,
            final List<ShapedCellData.ResolvedCell> shapedCells,
            final int pixelThreshold,
            final Set<BlockPos> currentTerrain
    ) {
        return testShaped(snap, unproj, shapedCells, pixelThreshold, currentTerrain, null);
    }

    /**
     * {@link #testShaped} 的<b>诊断变体</b>：多一个出参，统计本轮"因 δ 可行窗口闭合而跳过"的格数
     * （设计 §7 决策 Q / §10 第 18 条）。判定语义与主重载<b>逐位相同</b>。
     *
     * <p><b>为什么要单独记账</b>：窗口闭合时该格<b>永不投票</b>，这与"判了但没删"在结果上完全一样，
     * 从删除数<b>反推不出来</b>——不记账的话，远距贴花集体失明会被误读成"识别准确"。跳过本身是
     * <b>欠删</b>方向（安全），但必须是<b>可见</b>的欠删。
     *
     * @param unjudgedOut 可选出参（{@code null} 或长度 ≥ 1）：{@code [0]} <b>累加</b>跳过格数
     */
    public static List<BlockPos> testShaped(
            final DepthCapture.DepthSnapshot snap,
            final Unprojector unproj,
            final List<ShapedCellData.ResolvedCell> shapedCells,
            final int pixelThreshold,
            final Set<BlockPos> currentTerrain,
            final int[] unjudgedOut
    ) {
        if (shapedCells.isEmpty()) return List.of();

        final Vec3 cam = snap.cameraPos();
        final double camX = cam.x, camY = cam.y, camZ = cam.z;
        final boolean hasTranslucentField = snap.hasTranslucentDepth();
        final List<BlockPos> deletions = new ArrayList<>();
        final int width = snap.width();
        final int height = snap.height();
        final float dFar = unproj.dFar();
        final int thr = Math.max(1, pixelThreshold);

        // 热循环复用的 scratch（本类单线程使用，与 Unprojector 同一约定）
        final double[] hit = new double[4];

        for (ShapedCellData.ResolvedCell cell : shapedCells) {
            final BlockPos pos = cell.pos();
            if (cell.quads().length == 0) continue;                 // 形状为空 → 无足迹 → 不可判（欠删）
            if (currentTerrain.contains(pos)) continue;
            if (cell.translucentField() && !hasTranslucentField) continue;
            // 相机在格内 → 格必然存在（与 judge 同款防御）
            if (pos.getX() <= camX && camX <= pos.getX() + 1.0
                    && pos.getY() <= camY && camY <= pos.getY() + 1.0
                    && pos.getZ() <= camZ && camZ <= pos.getZ() + 1.0) {
                continue;
            }
            // 决策 Q（设计 §7 / §14.5）：δ 的可行窗口 = [c·1.192e-6·z², κ·h_max)。
            // 窗口在远距闭合（贴花 z ≈ 81 格 @c=1）⇒ 该格本帧在 2b 上两面都不可判：低于下界是
            // 噪声驱动的误删活体，高于上界是判据按构造不可满足；故跳过（欠删方向安全）并记账。
            // z 取格粒度<b>上界</b>（中心距 + 1 格 ≥ 半对角 0.866）：下界随 z 单调增，取上界 ⇒
            // 只会多跳不会少跳，恒走欠删方向。
            final double zCell = cam.distanceTo(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) + 1.0;
            if (deltaNoiseFloor(zCell) >= deltaFor(cell.shapeMaxY())) {
                if (unjudgedOut != null && unjudgedOut.length > 0) unjudgedOut[0]++;
                continue; // 窗口闭合 → 不可判（欠删），不计入证据
            }
            if (provenGoneShaped(snap, unproj, cam, width, height, dFar, cell, thr, hit)) {
                deletions.add(pos);
            }
        }
        return deletions;
    }

    /** 单格非满形状判定：格 bbox 粗筛 → 逐像素"形状求交 + 掩码 + 远出口比较" → 越过 ≥ 阈值。 */
    private static boolean provenGoneShaped(
            final DepthCapture.DepthSnapshot snap,
            final Unprojector unproj,
            final Vec3 cam,
            final int width,
            final int height,
            final float dFar,
            final ShapedCellData.ResolvedCell cell,
            final int threshold,
            final double[] hit
    ) {
        final BlockPos pos = cell.pos();
        final double minX = pos.getX(), minY = pos.getY(), minZ = pos.getZ();
        final double maxX = minX + 1.0, maxY = minY + 1.0, maxZ = minZ + 1.0;

        // ① 格 bbox 投影 → 屏幕 bbox（与整格判据同款：超集 + 循环前裁剪到屏幕）
        float minPx = Float.MAX_VALUE, maxPx = -Float.MAX_VALUE;
        float minPy = Float.MAX_VALUE, maxPy = -Float.MAX_VALUE;
        int projected = 0;
        for (int i = 0; i < 8; i++) {
            final double cx = ((i & 4) == 0) ? minX : maxX;
            final double cy = ((i & 2) == 0) ? minY : maxY;
            final double cz = ((i & 1) == 0) ? minZ : maxZ;
            final float[] pp = unproj.projectToScreen(new Vec3(cx, cy, cz), width, height);
            if (pp == null) continue;
            projected++;
            minPx = Math.min(minPx, pp[0]); maxPx = Math.max(maxPx, pp[0]);
            minPy = Math.min(minPy, pp[1]); maxPy = Math.max(maxPy, pp[1]);
        }
        if (projected == 0) return false;

        final int x0 = Math.max(0, (int) Math.floor(minPx));
        final int x1 = Math.min(width - 1, (int) Math.ceil(maxPx));
        final int y0 = Math.max(0, (int) Math.floor(minPy));
        final int y1 = Math.min(height - 1, (int) Math.ceil(maxPy));

        final ShapedCellData.ResolvedQuad[] quads = cell.quads();
        final float alphaThr = cell.alphaThr();
        final boolean translucentField = cell.translucentField();
        // 逐格 δ（设计 §3.2 2b-δ）：每格算一次，不进像素循环。
        final double delta = deltaFor(cell.shapeMaxY());

        int over = 0;
        for (int py = y0; py <= y1; py++) {
            for (int px = x0; px <= x1; px++) {
                final Vec3 dir = unproj.pixelRay(px, py);
                if (dir == null) continue;
                // 步骤 1：射线必须穿过格 bbox（区间同时用作步骤 2a 的条件③：命中点在格内）
                final double[] interval = slabInterval(cam, dir, minX, minY, minZ, maxX, maxY, maxZ);
                if (interval == null) continue;
                final double tEnter = interval[0];
                final double tExit = interval[1];

                final double dx = dir.x, dy = dir.y, dz = dir.z;
                double shapeExit = Double.NEGATIVE_INFINITY; // 步骤 2b 的比较基准
                boolean anyHit = false;
                boolean graze = false;

                quadLoop:
                for (ShapedCellData.ResolvedQuad rq : quads) {
                    final ShapedCellData.Quad q = rq.quad();
                    final float[] v = q.vertices();
                    // 条件①：正面（背面被地形渲染剔除，不写深度 → 命中它不构成证据）
                    if (q.nx() * dx + q.ny() * dy + q.nz() * dz >= 0.0) continue;
                    for (int tri = 0; tri < 2; tri++) {
                        // 三角形 (p0,p1,p2) 与 (p0,p2,p3)：与模型下发顺序同源，两条同绕向 ⇒ 同一法线
                        final int i0 = 0;
                        final int i1 = tri == 0 ? 1 : 2;
                        final int i2 = tri == 0 ? 2 : 3;
                        if (!rayTriangle(cam, dx, dy, dz,
                                minX + v[i0 * 3], minY + v[i0 * 3 + 1], minZ + v[i0 * 3 + 2],
                                minX + v[i1 * 3], minY + v[i1 * 3 + 1], minZ + v[i1 * 3 + 2],
                                minX + v[i2 * 3], minY + v[i2 * 3 + 1], minZ + v[i2 * 3 + 2],
                                hit)) {
                            continue;
                        }
                        final double t = hit[0];
                        // 条件③：命中点须在格内（顶点未 clamp，超出格的部分不计入本格 —— §4.3.2）
                        if (t < tEnter - TINCELL_EPS || t > tExit + TINCELL_EPS) continue;
                        // 擦边守卫（§3.5 的重心空间实现）：作废整个像素，恒走欠删方向
                        if (hit[1] < BARY_EPS || hit[2] < BARY_EPS || hit[3] < BARY_EPS) {
                            graze = true;
                            break quadLoop;
                        }
                        // 条件②：掩码通过（不过则该 texel 被 terrain.fsh discard，不写深度）
                        final float[] uv = q.uvs();
                        final float u = (float) (hit[1] * uv[i0 * 2] + hit[2] * uv[i1 * 2] + hit[3] * uv[i2 * 2]);
                        final float vv = (float) (hit[1] * uv[i0 * 2 + 1] + hit[2] * uv[i1 * 2 + 1] + hit[3] * uv[i2 * 2 + 1]);
                        if (!rq.passes(u, vv, alphaThr)) continue;
                        anyHit = true;
                        if (t > shapeExit) shapeExit = t;
                    }
                }

                if (graze || !anyHit) continue; // 擦边不可信 / 射线未触及形状 → 该像素不构成证据

                // 步骤 2b：观察深度在形状远出口之外 ⇒ 射线穿过整个形状而未停止
                final float d = translucentField ? snap.translucentDepthAt(px, py) : snap.depthAt(px, py);
                final double zObserved;
                if (d >= dFar) {
                    zObserved = Double.POSITIVE_INFINITY; // 该像素背后是天空 → 射线穿到远平面
                } else {
                    final Vec3 w = unproj.unprojectPixel(px, py, d);
                    if (w == null) continue;
                    zObserved = w.distanceTo(cam);
                }
                if (zObserved >= shapeExit + delta && ++over >= threshold) {
                    return true; // ≥2 像素一致证明 → 判消失（提前退出）
                }
            }
        }
        return false;
    }

    /**
     * Möller–Trumbore 射线-三角形求交（§3.2 步骤 2a）。
     *
     * <p>{@code dir} 须为<b>归一化</b>方向（{@link Unprojector#pixelRay} 的约定），结果 {@code t} 才是
     * 欧氏距离、可与 {@code unprojectPixel} 算出的距离直接比较。
     *
     * @param out 命中时写入 {@code {t, w0, w1, w2}}（w 为顶点 i0/i1/i2 对应的重心权重，和为 1）；
     *            <b>由调用方复用</b>以避免热循环分配
     * @return true = 命中（{@code t > 0} 且在三角形内）
     */
    private static boolean rayTriangle(
            final Vec3 origin, final double dx, final double dy, final double dz,
            final double ax, final double ay, final double az,
            final double bx, final double by, final double bz,
            final double cx, final double cy, final double cz,
            final double[] out
    ) {
        final double e1x = bx - ax, e1y = by - ay, e1z = bz - az;
        final double e2x = cx - ax, e2y = cy - ay, e2z = cz - az;
        // p = dir × e2
        final double px = dy * e2z - dz * e2y;
        final double py = dz * e2x - dx * e2z;
        final double pz = dx * e2y - dy * e2x;
        final double det = e1x * px + e1y * py + e1z * pz;
        if (Math.abs(det) < MT_EPS) return false; // 平行 / 退化（零面积三角形）
        final double inv = 1.0 / det;
        final double tx = origin.x - ax, ty = origin.y - ay, tz = origin.z - az;
        final double w1 = (tx * px + ty * py + tz * pz) * inv;
        if (w1 < 0.0 || w1 > 1.0) return false;
        // q = t × e1
        final double qx = ty * e1z - tz * e1y;
        final double qy = tz * e1x - tx * e1z;
        final double qz = tx * e1y - ty * e1x;
        final double w2 = (dx * qx + dy * qy + dz * qz) * inv;
        if (w2 < 0.0 || w1 + w2 > 1.0) return false;
        final double t = (e2x * qx + e2y * qy + e2z * qz) * inv;
        if (t <= 0.0) return false;
        out[0] = t;
        out[1] = 1.0 - w1 - w2; // w0（顶点 i0）
        out[2] = w1;            // 顶点 i1
        out[3] = w2;            // 顶点 i2
        return true;
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
