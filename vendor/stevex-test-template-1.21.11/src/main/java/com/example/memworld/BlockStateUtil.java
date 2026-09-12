package com.example.memworld;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;

/**
 * 方块状态序列化工具 —— 与 stevex 视觉采集器保存的格式互操作。
 *
 * <p>源文件中每个方块实体附带：
 * <ul>
 *   <li>{@code block}：方块注册名，如 {@code minecraft:chest}</li>
 *   <li>{@code state}：状态属性表，如 {@code {"facing":"east","waterlogged":"false"}}</li>
 * </ul>
 */
public final class BlockStateUtil {

    private BlockStateUtil() {}

    /** 从保存的 blockId + 状态属性重建 BlockState；未知方块 / 非法属性时回退默认状态。 */
    public static BlockState fromSaved(final String blockId, final Map<String, String> props) {
        if (blockId == null || blockId.isBlank()) return Blocks.AIR.defaultBlockState();

        Identifier id = Identifier.tryParse(blockId);
        Block block = id == null ? null : BuiltInRegistries.BLOCK.getValue(id);
        if (block == null) return Blocks.AIR.defaultBlockState();

        BlockState state = block.defaultBlockState();
        StateDefinition<Block, BlockState> definition = block.getStateDefinition();
        for (Map.Entry<String, String> e : props.entrySet()) {
            Property<?> property = definition.getProperty(e.getKey());
            if (property == null) continue;
            state = setValue(state, property, e.getValue());
        }
        return state;
    }

    /**
     * 实心（满形状）且不透明 —— 减量（§7.11）只删这类方块：只有它们会被深度缓冲"看到被挡/被挖掉"，
     * 也只有它们会被写进 cells 文件（{@link MemoryCellReporter}）与接受删除（{@link DeletionApplier}）。
     * 玻璃/栅栏/压力板等非满形状或透明方块不可删（欠删无害，v2.22 同，接受）。
     */
    public static boolean isSolidOpaque(final Level level, final BlockPos pos, final BlockState state) {
        if (state.isAir()) return false;
        return Block.isShapeFullBlock(state.getShape(level, pos)) && state.canOcclude();
    }

    /** 水族流体（水源/流动同族）——v2.36：Fabulous 下写 translucent 目标、归 translucent 段。 */
    public static boolean isWaterFluid(final BlockState state) {
        return !state.getFluidState().isEmpty() && state.getFluidState().is(FluidTags.WATER);
    }

    /** 非水流体（岩浆等）——v2.36：写 main 深度、归 main/opaque 段（Fabulous 下岩浆恒写 main，见 §7.12）。 */
    public static boolean isNonWaterFluid(final BlockState state) {
        return !state.getFluidState().isEmpty() && !state.getFluidState().is(FluidTags.WATER);
    }

    /**
     * 满格透明方块（v2.36，§7.12 translucent 段非流体成员）——玻璃块/染色玻璃/冰/遮光玻璃/蜂蜜块/
     * 史莱姆块等 {@code isShapeFullBlock} 且 {@code !canOcclude} 的满形状透明方块：真实世界它们写满整格、
     * 在场即自身近面截断射线，与 opaque 判据同前提（§7.12：限制项是"形状是否填满整格"，与透明度无关）。
     * 玻璃板/栅栏/压力板/红石线等非满形状被挡在段外（欠删，§7.12 边界①，双保险防误删）。
     */
    public static boolean isFullTransparentCell(final Level level, final BlockPos pos, final BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        // v2.37 修补（设计 §4.1 第 2 步）：RenderShape == INVISIBLE 的方块（air/barrier/bubble_column/
        // end_gateway/end_portal/light/liquid/structure_void/moving_piston）渲染足迹为零——若同时满足
        // isShapeFullBlock && !canOcclude（barrier/light/structure_void 等正是如此），旧口径会把它们塞进
        // translucent 段并用整格 bbox 判 → 对零足迹的东西过投票。此谓词独立于本版功能，v2.23/v2.36 亦适用。
        if (!hasModelRenderShape(state)) return false;
        return Block.isShapeFullBlock(state.getShape(level, pos)) && !state.canOcclude();
    }

    // ==================== v2.37（§7.13 / 非满形状方块减量） ====================

    /** 该方块是否由方块模型系统渲染（{@code RenderShape.MODEL}）。{@code INVISIBLE} = 渲染足迹为零。 */
    public static boolean hasModelRenderShape(final BlockState state) {
        return state.getRenderShape() == RenderShape.MODEL;
    }

    /**
     * v2.37 <b>信号缺失排除表</b>（设计 §4.1 第 6 步 / §2.2）：Fabulous 下不写任何已读深度场的方块。
     * 活体不产生可读信号 ⇒ 判"消失"必误删活体，属信号缺失而非几何问题——换任何几何源都救不了，
     * 故只有这一族需要手列。表维护在此处，记忆侧上报与采集侧判定两侧复用同一口径。
     */
    private static final Set<Block> SIGNAL_LOSS_BLOCKS = Set.of(
            Blocks.TRIPWIRE,        // 绊线：Fabulous 下画进 weather 目标、不写 main/translucent 深度
            Blocks.TRIPWIRE_HOOK    // 绊线钩（本体几何正常，但其激活/信号语义与绊线同族，一并欠删）
    );

    /** 是否属于信号缺失族（设计 §4.1 第 6 步）。 */
    public static boolean isSignalLossBlock(final Block block) {
        return SIGNAL_LOSS_BLOCKS.contains(block);
    }

    /**
     * v2.37（设计 §4.1）<b>单一上报谓词</b>——记忆侧几何段上报、采集侧几何求交、{@link DeletionApplier}
     * 放行守卫共用同一把尺（同文件、同侧，杜绝口径漂移）：
     *
     * <ol>
     *   <li>该格有<b>方块本体</b>（非空气）；纯水格/纯岩浆格由 {@code getRenderShape() != MODEL} 自动排除
     *       （{@code LiquidBlock} 返回 {@code INVISIBLE}），故含水非满块（有方块本体）仍是候选；</li>
     *   <li>{@code RenderShape == MODEL}（排除 {@code INVISIBLE} 族）；</li>
     *   <li><b>非满形状</b>——满格方块走 §7.11/§7.12 现段（整格盒是它的退化情形，进几何段零收益）；</li>
     *   <li>该格无方块实体（{@code getBlockEntity(pos) == null}）——几何在 BE renderer 里，不在方块模型
     *       系统内，{@code collectParts} 的 quad 数可能为 0 或只盖住底座 = <b>几何低估</b>（唯一方向错的
     *       一类）；</li>
     *   <li>不在信号缺失表（绊线/绊线钩）。</li>
     * </ol>
     *
     * <p><b>几何非空（设计 §4.1 第 5 步）不在此判</b>——它需要客户端烘焙模型（{@code ModelGeometryCache}），
     * 由 {@code MemoryCellReporter.computeCells} 在拿到 quad 清单后自动兜底（quad 数 = 0 → 不进几何段）。
     *
     * <p><b>判定次序是承重的</b>（设计 §4.4）：调用方必须<b>先形状后流体</b>——先问本谓词，命中者直接入
     * 几何段（无视 waterlogged），只有非候选格才走 §7.12 现分支。否则 waterlogged 栅栏会先被 translucent
     * 水段吞走，复现 v2.36 的"活本体被整格置空"误删缺陷。
     */
    public static boolean isShapedDeletableContent(final Level level, final BlockPos pos, final BlockState state) {
        if (state.isAir()) return false;                                   // ① 有方块本体
        if (!hasModelRenderShape(state)) return false;                     // ② RenderShape.MODEL
        if (isSignalLossBlock(state.getBlock())) return false;             // ⑤ 信号缺失族
        if (Block.isShapeFullBlock(state.getShape(level, pos))) return false; // ③ 满格走现段
        return level.getBlockEntity(pos) == null;                          // ④ 无 BE
    }

    /**
     * v2.36 减量可删内容（§7.12）：实心不透明 ∪ 流体（水/岩浆）∪ 满格透明 —— 与 cells 上报口径一致。
     * {@link DeletionApplier} 的删除放行守卫用它：只删"当前内容属于 cells 可报类别"的格（内容对得上才删），
     * 薄物/非满形状（玻璃板/栅栏/压力板/红石线等）被挡在可删集外，防 §7.11 边界①误删回归。
     */
    public static boolean isDeletableContent(final Level level, final BlockPos pos, final BlockState state) {
        final FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) return true; // 水 / 岩浆均在可删集（被证明消失即可置空）
        if (state.isAir()) return false;
        return isSolidOpaque(level, pos, state) || isFullTransparentCell(level, pos, state);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState setValue(
            final BlockState state, final Property<?> property, final String value
    ) {
        Property<T> typed = (Property<T>) property;
        Optional<T> parsed = typed.getValue(value);
        if (parsed.isEmpty()) {
            // 保存端用的是 String.valueOf(枚举常量)，可能存的是枚举名（如 "SINGLE"），
            // 而 getValue 只匹配 getSerializedName()（如 "single"）。这里兜底按枚举名匹配。
            for (T possible : typed.getPossibleValues()) {
                if (String.valueOf(possible).equals(value)) {
                    parsed = Optional.of(possible);
                    break;
                }
            }
        }
        return parsed.map(v -> state.setValue(typed, v)).orElse(state);
    }
}
