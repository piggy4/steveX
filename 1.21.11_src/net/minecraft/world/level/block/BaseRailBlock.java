package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public abstract class BaseRailBlock extends Block implements SimpleWaterloggedBlock {
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	private static final VoxelShape SHAPE_FLAT = Block.column(16.0, 0.0, 2.0);
	private static final VoxelShape SHAPE_SLOPE = Block.column(16.0, 0.0, 8.0);
	private final boolean isStraight;

	public static boolean isRail(Level level, BlockPos blockPos) {
		return isRail(level.getBlockState(blockPos));
	}

	public static boolean isRail(BlockState blockState) {
		return blockState.is(BlockTags.RAILS) && blockState.getBlock() instanceof BaseRailBlock;
	}

	protected BaseRailBlock(boolean bl, BlockBehaviour.Properties properties) {
		super(properties);
		this.isStraight = bl;
	}

	@Override
	protected abstract MapCodec<? extends BaseRailBlock> codec();

	public boolean isStraight() {
		return this.isStraight;
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return blockState.getValue(this.getShapeProperty()).isSlope() ? SHAPE_SLOPE : SHAPE_FLAT;
	}

	@Override
	protected boolean canSurvive(BlockState blockState, LevelReader levelReader, BlockPos blockPos) {
		return canSupportRigidBlock(levelReader, blockPos.below());
	}

	@Override
	protected void onPlace(BlockState blockState, Level level, BlockPos blockPos, BlockState blockState2, boolean bl) {
		if (!blockState2.is(blockState.getBlock())) {
			this.updateState(blockState, level, blockPos, bl);
		}
	}

	protected BlockState updateState(BlockState blockState, Level level, BlockPos blockPos, boolean bl) {
		blockState = this.updateDir(level, blockPos, blockState, true);
		if (this.isStraight) {
			level.neighborChanged(blockState, blockPos, this, null, bl);
		}

		return blockState;
	}

	@Override
	protected void neighborChanged(BlockState blockState, Level level, BlockPos blockPos, Block block, @Nullable Orientation orientation, boolean bl) {
		if (!level.isClientSide() && level.getBlockState(blockPos).is(this)) {
			RailShape railShape = blockState.getValue(this.getShapeProperty());
			if (shouldBeRemoved(blockPos, level, railShape)) {
				dropResources(blockState, level, blockPos);
				level.removeBlock(blockPos, bl);
			} else {
				this.updateState(blockState, level, blockPos, block);
			}
		}
	}

	private static boolean shouldBeRemoved(BlockPos blockPos, Level level, RailShape railShape) {
		if (!canSupportRigidBlock(level, blockPos.below())) {
			return true;
		}

		switch (railShape) {
			case ASCENDING_EAST:
				return !canSupportRigidBlock(level, blockPos.east());
			case ASCENDING_WEST:
				return !canSupportRigidBlock(level, blockPos.west());
			case ASCENDING_NORTH:
				return !canSupportRigidBlock(level, blockPos.north());
			case ASCENDING_SOUTH:
				return !canSupportRigidBlock(level, blockPos.south());
			default:
				return false;
		}
	}

	protected void updateState(BlockState blockState, Level level, BlockPos blockPos, Block block) {
	}

	protected BlockState updateDir(Level level, BlockPos blockPos, BlockState blockState, boolean bl) {
		if (level.isClientSide()) {
			return blockState;
		}

		RailShape railShape = blockState.getValue(this.getShapeProperty());
		return new RailState(level, blockPos, blockState).place(level.hasNeighborSignal(blockPos), bl, railShape).getState();
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, boolean bl) {
		if (!bl) {
			if (blockState.getValue(this.getShapeProperty()).isSlope()) {
				serverLevel.updateNeighborsAt(blockPos.above(), this);
			}

			if (this.isStraight) {
				serverLevel.updateNeighborsAt(blockPos, this);
				serverLevel.updateNeighborsAt(blockPos.below(), this);
			}
		}
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		FluidState fluidState = blockPlaceContext.getLevel().getFluidState(blockPlaceContext.getClickedPos());
		boolean bl = fluidState.getType() == Fluids.WATER;
		BlockState blockState = super.defaultBlockState();
		Direction direction = blockPlaceContext.getHorizontalDirection();
		boolean bl2 = direction == Direction.EAST || direction == Direction.WEST;
		return blockState.setValue(this.getShapeProperty(), bl2 ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH).setValue(WATERLOGGED, bl);
	}

	public abstract Property<RailShape> getShapeProperty();

	protected RailShape rotate(RailShape railShape, Rotation rotation) {
		return switch (rotation) {
			case CLOCKWISE_180 -> {
				switch (railShape) {
					case ASCENDING_EAST:
						yield RailShape.ASCENDING_WEST;
					case ASCENDING_WEST:
						yield RailShape.ASCENDING_EAST;
					case ASCENDING_NORTH:
						yield RailShape.ASCENDING_SOUTH;
					case ASCENDING_SOUTH:
						yield RailShape.ASCENDING_NORTH;
					case NORTH_SOUTH:
						yield RailShape.NORTH_SOUTH;
					case EAST_WEST:
						yield RailShape.EAST_WEST;
					case SOUTH_EAST:
						yield RailShape.NORTH_WEST;
					case SOUTH_WEST:
						yield RailShape.NORTH_EAST;
					case NORTH_WEST:
						yield RailShape.SOUTH_EAST;
					case NORTH_EAST:
						yield RailShape.SOUTH_WEST;
					default:
						throw new MatchException(null, null);
				}
			}
			case COUNTERCLOCKWISE_90 -> {
				switch (railShape) {
					case ASCENDING_EAST:
						yield RailShape.ASCENDING_NORTH;
					case ASCENDING_WEST:
						yield RailShape.ASCENDING_SOUTH;
					case ASCENDING_NORTH:
						yield RailShape.ASCENDING_WEST;
					case ASCENDING_SOUTH:
						yield RailShape.ASCENDING_EAST;
					case NORTH_SOUTH:
						yield RailShape.EAST_WEST;
					case EAST_WEST:
						yield RailShape.NORTH_SOUTH;
					case SOUTH_EAST:
						yield RailShape.NORTH_EAST;
					case SOUTH_WEST:
						yield RailShape.SOUTH_EAST;
					case NORTH_WEST:
						yield RailShape.SOUTH_WEST;
					case NORTH_EAST:
						yield RailShape.NORTH_WEST;
					default:
						throw new MatchException(null, null);
				}
			}
			case CLOCKWISE_90 -> {
				switch (railShape) {
					case ASCENDING_EAST:
						yield RailShape.ASCENDING_SOUTH;
					case ASCENDING_WEST:
						yield RailShape.ASCENDING_NORTH;
					case ASCENDING_NORTH:
						yield RailShape.ASCENDING_EAST;
					case ASCENDING_SOUTH:
						yield RailShape.ASCENDING_WEST;
					case NORTH_SOUTH:
						yield RailShape.EAST_WEST;
					case EAST_WEST:
						yield RailShape.NORTH_SOUTH;
					case SOUTH_EAST:
						yield RailShape.SOUTH_WEST;
					case SOUTH_WEST:
						yield RailShape.NORTH_WEST;
					case NORTH_WEST:
						yield RailShape.NORTH_EAST;
					case NORTH_EAST:
						yield RailShape.SOUTH_EAST;
					default:
						throw new MatchException(null, null);
				}
			}
			default -> railShape;
		};
	}

	protected RailShape mirror(RailShape railShape, Mirror mirror) {
		return switch (mirror) {
			case LEFT_RIGHT -> {
				switch (railShape) {
					case ASCENDING_NORTH:
						yield RailShape.ASCENDING_SOUTH;
					case ASCENDING_SOUTH:
						yield RailShape.ASCENDING_NORTH;
					case NORTH_SOUTH:
					case EAST_WEST:
					default:
						yield railShape;
					case SOUTH_EAST:
						yield RailShape.NORTH_EAST;
					case SOUTH_WEST:
						yield RailShape.NORTH_WEST;
					case NORTH_WEST:
						yield RailShape.SOUTH_WEST;
					case NORTH_EAST:
						yield RailShape.SOUTH_EAST;
				}
			}
			case FRONT_BACK -> {
				switch (railShape) {
					case ASCENDING_EAST:
						yield RailShape.ASCENDING_WEST;
					case ASCENDING_WEST:
						yield RailShape.ASCENDING_EAST;
					case ASCENDING_NORTH:
					case ASCENDING_SOUTH:
					case NORTH_SOUTH:
					case EAST_WEST:
					default:
						yield railShape;
					case SOUTH_EAST:
						yield RailShape.SOUTH_WEST;
					case SOUTH_WEST:
						yield RailShape.SOUTH_EAST;
					case NORTH_WEST:
						yield RailShape.NORTH_EAST;
					case NORTH_EAST:
						yield RailShape.NORTH_WEST;
				}
			}
			default -> railShape;
		};
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
	protected FluidState getFluidState(BlockState blockState) {
		return blockState.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(blockState);
	}
}
