package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class KelpBlock extends GrowingPlantHeadBlock implements LiquidBlockContainer {
	public static final MapCodec<KelpBlock> CODEC = simpleCodec(KelpBlock::new);
	private static final double GROW_PER_TICK_PROBABILITY = 0.14;
	private static final VoxelShape SHAPE = Block.column(16.0, 0.0, 9.0);

	@Override
	public MapCodec<KelpBlock> codec() {
		return CODEC;
	}

	public KelpBlock(BlockBehaviour.Properties properties) {
		super(properties, Direction.UP, SHAPE, true, 0.14);
	}

	@Override
	protected boolean canGrowInto(BlockState blockState) {
		return blockState.is(Blocks.WATER);
	}

	@Override
	protected Block getBodyBlock() {
		return Blocks.KELP_PLANT;
	}

	@Override
	protected boolean canAttachTo(BlockState blockState) {
		return !blockState.is(Blocks.MAGMA_BLOCK);
	}

	@Override
	public boolean canPlaceLiquid(@Nullable LivingEntity livingEntity, BlockGetter blockGetter, BlockPos blockPos, BlockState blockState, Fluid fluid) {
		return false;
	}

	@Override
	public boolean placeLiquid(LevelAccessor levelAccessor, BlockPos blockPos, BlockState blockState, FluidState fluidState) {
		return false;
	}

	@Override
	protected int getBlocksToGrowWhenBonemealed(RandomSource randomSource) {
		return 1;
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		FluidState fluidState = blockPlaceContext.getLevel().getFluidState(blockPlaceContext.getClickedPos());
		return fluidState.is(FluidTags.WATER) && fluidState.getAmount() == 8 ? super.getStateForPlacement(blockPlaceContext) : null;
	}

	@Override
	protected FluidState getFluidState(BlockState blockState) {
		return Fluids.WATER.getSource(false);
	}
}
