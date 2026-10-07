package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class SlabBlock extends Block implements SimpleWaterloggedBlock {
	public static final MapCodec<SlabBlock> CODEC = simpleCodec(SlabBlock::new);
	public static final EnumProperty<SlabType> TYPE = BlockStateProperties.SLAB_TYPE;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	private static final VoxelShape SHAPE_BOTTOM = Block.column(16.0, 0.0, 8.0);
	private static final VoxelShape SHAPE_TOP = Block.column(16.0, 8.0, 16.0);

	@Override
	public MapCodec<? extends SlabBlock> codec() {
		return CODEC;
	}

	public SlabBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.defaultBlockState().setValue(TYPE, SlabType.BOTTOM).setValue(WATERLOGGED, false));
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState blockState) {
		return blockState.getValue(TYPE) != SlabType.DOUBLE;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(TYPE, WATERLOGGED);
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return switch ((SlabType)blockState.getValue(TYPE)) {
			case TOP -> SHAPE_TOP;
			case BOTTOM -> SHAPE_BOTTOM;
			case DOUBLE -> Shapes.block();
		};
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		BlockPos blockPos = blockPlaceContext.getClickedPos();
		BlockState blockState = blockPlaceContext.getLevel().getBlockState(blockPos);
		if (blockState.is(this)) {
			return blockState.setValue(TYPE, SlabType.DOUBLE).setValue(WATERLOGGED, false);
		}

		FluidState fluidState = blockPlaceContext.getLevel().getFluidState(blockPos);
		BlockState blockState2 = this.defaultBlockState().setValue(TYPE, SlabType.BOTTOM).setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
		Direction direction = blockPlaceContext.getClickedFace();
		return direction != Direction.DOWN && (direction == Direction.UP || !(blockPlaceContext.getClickLocation().y - blockPos.getY() > 0.5))
			? blockState2
			: blockState2.setValue(TYPE, SlabType.TOP);
	}

	@Override
	protected boolean canBeReplaced(BlockState blockState, BlockPlaceContext blockPlaceContext) {
		ItemStack itemStack = blockPlaceContext.getItemInHand();
		SlabType slabType = blockState.getValue(TYPE);
		if (slabType == SlabType.DOUBLE || !itemStack.is(this.asItem())) {
			return false;
		} else if (blockPlaceContext.replacingClickedOnBlock()) {
			boolean bl = blockPlaceContext.getClickLocation().y - blockPlaceContext.getClickedPos().getY() > 0.5;
			Direction direction = blockPlaceContext.getClickedFace();
			return slabType == SlabType.BOTTOM
				? direction == Direction.UP || bl && direction.getAxis().isHorizontal()
				: direction == Direction.DOWN || !bl && direction.getAxis().isHorizontal();
		} else {
			return true;
		}
	}

	@Override
	protected FluidState getFluidState(BlockState blockState) {
		return blockState.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(blockState);
	}

	@Override
	public boolean placeLiquid(LevelAccessor levelAccessor, BlockPos blockPos, BlockState blockState, FluidState fluidState) {
		return blockState.getValue(TYPE) != SlabType.DOUBLE ? SimpleWaterloggedBlock.super.placeLiquid(levelAccessor, blockPos, blockState, fluidState) : false;
	}

	@Override
	public boolean canPlaceLiquid(@Nullable LivingEntity livingEntity, BlockGetter blockGetter, BlockPos blockPos, BlockState blockState, Fluid fluid) {
		return blockState.getValue(TYPE) != SlabType.DOUBLE
			? SimpleWaterloggedBlock.super.canPlaceLiquid(livingEntity, blockGetter, blockPos, blockState, fluid)
			: false;
	}

	@Override
	protected BlockState updateShape(
		BlockState blockState,
		LevelReader levelReader,
		ScheduledTickAccess scheduledTickAccess,
		BlockPos blockPos,
		Direction direction,
		BlockPos blockPos2,
		BlockState blockState2,
		RandomSource randomSource
	) {
		if (blockState.getValue(WATERLOGGED)) {
			scheduledTickAccess.scheduleTick(blockPos, Fluids.WATER, Fluids.WATER.getTickDelay(levelReader));
		}

		return super.updateShape(blockState, levelReader, scheduledTickAccess, blockPos, direction, blockPos2, blockState2, randomSource);
	}

	@Override
	protected boolean isPathfindable(BlockState blockState, PathComputationType pathComputationType) {
		switch (pathComputationType) {
			case LAND:
				return false;
			case WATER:
				return blockState.getFluidState().is(FluidTags.WATER);
			case AIR:
				return false;
			default:
				return false;
		}
	}
}
