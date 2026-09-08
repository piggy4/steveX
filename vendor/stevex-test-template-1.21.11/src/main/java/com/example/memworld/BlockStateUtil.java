package com.example.memworld;

import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
        return Block.isShapeFullBlock(state.getShape(level, pos)) && !state.canOcclude();
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
