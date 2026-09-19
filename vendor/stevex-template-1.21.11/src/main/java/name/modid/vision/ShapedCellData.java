package name.modid.vision;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * v2.37（设计 §4.3.2 / §5.1）采集侧几何段的数据形态 —— 记忆侧 {@code memory_cells.bin} v4 里
 * "quad 面清单 + sprite alpha 掩码"的解析结果。
 *
 * <p><b>表示什么</b>：该方块在<b>记忆侧</b>客户端烘焙模型里的实际渲染几何（设计 §0/§4.2）。
 * 采集侧对它做"射线 ∩ 三角形 → 重心插值 UV → 查 sprite alpha 表 → 按该层阈值判 discard"的
 * 精确求交（§3.2 2a），据此把"只穿格内空余区"的越票作废。
 *
 * <p><b>为什么用 {@code float[]} 而非 {@code Vector3f[]}</b>：几何段是逐像素热循环的数据源，
 * 平面数组零拆箱、零间接跳转，且与 {@code memory_cells.bin} 的 float32 布局一一对应（§4.3.2）。
 *
 * <p><b>顶点不做 clamp</b>（设计 §4.3.2）：超出格的部分保留原值，命中点由第一步的格 bbox 截断。
 * 这一点与盒表示不同——盒表示的盒参与比较基准，quad 表示的比较基准是命中 {@code t}，
 * 截断只需作用在"是否计入该格"上。
 */
public final class ShapedCellData {

    /** 几何段里每条 quad 的固定字节数：12+8+3 个 float + 1 个 int（= 96）。解析期用它做越界预检。 */
    public static final int QUAD_BYTES = (12 + 8 + 3) * Float.BYTES + Integer.BYTES;

    private ShapedCellData() {
    }

    /**
     * 一条 quad（相当于 GPU 收到的那份顶点数据）。
     *
     * @param vertices 12 个 float：{@code p0.xyz, p1.xyz, p2.xyz, p3.xyz}，格内局部 [0,1]，
     *                 <b>vanilla 原始顶点顺序</b>（不做 AABB、不重排）
     * @param uvs      8 个 float：{@code uv0.u, uv0.v, uv1.u, uv1.v, ...}，<b>sprite 局部</b> [0,1]
     *                 （记忆侧已用 {@code sprite.getU0()/getU1()/getV0()/getV1()} 从 atlas UV 反线性换算）
     * @param nx,ny,nz 单位法线（记忆侧自算 {@code normalize(cross(p1−p0, p2−p0))}）。
     *                 <b>不读 {@code BakedQuad.direction}</b>——它对非轴对齐 quad 一律被静默标成 UP
     *                 （{@code FaceBakery:90} 的 {@code requireNonNullElse(finalDirection, UP)}），不可用。
     *                 两侧共用同一个下发值 ⇒ 无约定分歧
     * @param spriteIndex 该 quad 所用 sprite 在本 epoch sprite 表里的下标；解析期已解引用成 alpha 掩码
     */
    public record Quad(
            float[] vertices,
            float[] uvs,
            float nx, float ny, float nz,
            int spriteIndex
    ) {
        /** 顶点 i（0..3）的格内局部坐标。 */
        public float vx(final int i) { return vertices[i * 3]; }
        public float vy(final int i) { return vertices[i * 3 + 1]; }
        public float vz(final int i) { return vertices[i * 3 + 2]; }
    }

    /**
     * 一个几何段条目：格 + 方块注册 id + quad 面清单。
     *
     * @param pos     该格
     * @param blockId 方块注册 id（如 {@code minecraft:oak_fence}）。<b>承重字段，不是诊断字段</b>
     *                （设计 §4.3.4）：判据场路由与 alpha 阈值的确定都必须在<b>采集侧</b>做——因为
     *                {@code ChunkSectionLayer} 的选择依赖采集侧客户端的 {@code cutoutLeaves} 设置
     * @param quads   该格的 quad 面清单（解析期已把不可用的 quad 丢弃，见 {@link ResolvedCell}）
     */
    public record ShapedCell(BlockPos pos, String blockId, Quad[] quads) {}

    /**
     * 解析 + sprite 解引用后的可判形态（{@link #quads} 已是"掩码已解析"的版本，
     * 采集侧主循环不再触碰 sprite 表）。
     *
     * @param alphaThr 该格渲染层对应的 {@code ALPHA_CUTOUT} 阈值（设计 §5.2 逐层阈值表）：
     *                 {@code SOLID} → 0（无 define，全通过）/ {@code CUTOUT} → 0.5 /
     *                 {@code TRANSLUCENT} → 0.01。逐 texel 判定 {@code alpha >= alphaThr} 才写深度
     * @param translucentField true = 现实该格写的是 <b>translucent</b> 深度场（Fabulous 下的
     *                 TRANSLUCENT 层），false = main 场
     * @param quads    可用 quad（alpha 表不可得 / 下标越界 / epoch 不符的已整条剔除 = fail-closed）
     * @param shapeMaxY 该格几何顶点在<b>格局部坐标</b>下的最大 {@code y}（= 设计 §3.2 2b-δ 的
     *                 {@code h_max}）。用于把判据 2b 的余量 δ 逐格化：
     *                 {@code δ_cell = min(δ_global, κ · shapeMaxY)}。
     *                 <p><b>为什么需要它</b>（可达性必要条件）：判据 {@code Z ≥ t_shape_exit + δ}
     *                 要成立，观察面必须落在形状之外至少 δ；而形状能提供的最大跳变就是它自身的几何
     *                 高度（{@code h_hit / |dy| ≤ shapeMaxY}，因 {@code |dy| ≤ 1}）。故 δ ≥ shapeMaxY 时
     *                 判据<b>按构造不可满足</b>——该格无论是否真的消失都永不越票。
     *                 <p><b>实测反例</b>：红石粉/落叶是"贴地平铺贴花"，全部 quad 都在 {@code y = 1/64}，
     *                 而支撑块顶面就是格底 ⇒ 消失后观察面只后退 {@code 1/64 / |dy| ≤ 0.0156 < 0.05}
     *                 ⇒ 旧全局 δ 下<b>永不判消失</b>。取 {@code shapeMaxY} 后 δ_cell = 1/128，恢复可判。
     *                 <p>取<b>格内最大值</b>而非逐像素的命中高度 {@code h_hit}：后者会让"贴近地面的
     *                 命中"处 δ 变小，而那恰是最需要余量的像素 ⇒ 方向反了。取最大值是"按该格最有利的
     *                 像素定余量"，对弱像素只会更严（恒走欠删方向）。
     */
    public record ResolvedCell(
            BlockPos pos,
            float alphaThr,
            boolean translucentField,
            ResolvedQuad[] quads,
            float shapeMaxY
    ) {}

    /**
     * 解引用后的 quad：{@link Quad} + 该 sprite 的 alpha 掩码。
     *
     * @param alpha    mip 0、逐 texel、行主序的 alpha（0..255，存为 byte）；<b>null = 全不透明</b>
     *                 （Opaque 短路的 sprite，如栅栏/铁栏杆/台阶的不透明贴图 → 掩码恒通过，零查表开销）
     * @param width,height 掩码尺寸（与 {@code alpha} 同源；{@code alpha == null} 时仅用于诊断）
     */
    public record ResolvedQuad(
            Quad quad,
            @Nullable byte[] alpha,
            int width,
            int height
    ) {
        /**
         * 逐 texel 掩码判定（设计 §3.2 2a 第 2 条）。
         *
         * <p>与 {@code terrain.fsh} 的 {@code sampleNearest} 同口径：把 sprite 局部 UV 换算成 texel
         * 坐标后取整查表（{@code uvTexelCoords = uv / pixelSize}，texel 中心在 {@code (n+0.5)·pixelSize}
         * ⇒ 下标 = {@code floor(uv · size)}），越界钳制到边缘（模型 UV 超出 sprite 的边角情形取
         * 该贴图自身的边缘 texel，方向保守）。
         *
         * @return true = 该 texel 写深度（不被 discard）
         */
        public boolean passes(final float u, final float v, final float alphaThr) {
            if (alpha == null) return true;                       // Opaque：无 ALPHA_CUTOUT define 或全 255
            if (alphaThr <= 0.0f) return true;                    // SOLID 层：无 define，任何 alpha 都写深度
            int tx = (int) Math.floor(u * width);
            int ty = (int) Math.floor(v * height);
            if (tx < 0) tx = 0; else if (tx >= width) tx = width - 1;
            if (ty < 0) ty = 0; else if (ty >= height) ty = height - 1;
            return (alpha[ty * width + tx] & 0xFF) >= alphaThr * 255.0f;
        }
    }
}
