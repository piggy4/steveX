package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class TallSeagrassBlock extends DoublePlantBlock implements LiquidBlockContainer {
	public static final MapCodec<TallSeagrassBlock> CODEC = simpleCodec(TallSeagrassBlock::new);
	public static final EnumProperty<DoubleBlockHalf> HALF = DoublePlantBlock.HALF;
	private static final VoxelShape SHAPE = Block.column(12.0, 0.0, 16.0);

	@Override
	public MapCodec<TallSeagrassBlock> codec() {
		return CODEC;
	}

	public TallSeagrassBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return SHAPE;
	}

	@Override
	protected boolean mayPlaceOn(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
		return blockState.isFaceSturdy(blockGetter, blockPos, Direction.UP) && !blockState.is(Blocks.MAGMA_BLOCK);
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader levelReader, BlockPos blockPos, BlockState blockState, boolean bl) {
		return new ItemStack(Blocks.SEAGRASS);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		BlockState blockState = super.getStateForPlacement(blockPlaceContext);
		if (blockState != null) {
			FluidState fluidState = blockPlaceContext.getLevel().getFluidState(blockPlaceContext.getClickedPos().above());
			if (fluidState.is(FluidTags.WATER) && fluidState.getAmount() == 8) {
				return blockState;
			}
		}

		return null;
	}

	@Override
	protected boolean canSurvive(BlockState blockState, LevelReader levelReader, BlockPos blockPos) {
		if (blockState.getValue(HALF) == DoubleBlockHalf.UPPER) {
			BlockState blockState2 = levelReader.getBlockState(blockPos.below());
			return blockState2.is(this) && blockState2.getValue(HALF) == DoubleBlockHalf.LOWER;
		} else {
			FluidState fluidState = levelReader.getFluidState(blockPos);
			return super.canSurvive(blockState, levelReader, blockPos) && fluidState.is(FluidTags.WATER) && fluidState.getAmount() == 8;
		}
	}

	@Override
	protected FluidState getFluidState(BlockState blockState) {
		return Fluids.WATER.getSource(false);
	}

	@Override
	public boolean canPlaceLiquid(@Nullable LivingEntity livingEntity, BlockGetter blockGetter, BlockPos blockPos, BlockState blockState, Fluid fluid) {
		return false;
	}

	@Override
	public boolean placeLiquid(LevelAccessor levelAccessor, BlockPos blockPos, BlockState blockState, FluidState fluidState) {
		return false;
	}
}
