package net.minecraft.world.level.block;

import com.mojang.math.OctahedralGroup;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class StairBlock extends Block implements SimpleWaterloggedBlock {
	public static final MapCodec<StairBlock> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(BlockState.CODEC.fieldOf("base_state").forGetter(stairBlock -> stairBlock.baseState), propertiesCodec())
			.apply(instance, StairBlock::new)
	);
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
	public static final EnumProperty<StairsShape> SHAPE = BlockStateProperties.STAIRS_SHAPE;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	private static final VoxelShape SHAPE_OUTER = Shapes.or(Block.column(16.0, 0.0, 8.0), Block.box(0.0, 8.0, 0.0, 8.0, 16.0, 8.0));
	private static final VoxelShape SHAPE_STRAIGHT = Shapes.or(SHAPE_OUTER, Shapes.rotate(SHAPE_OUTER, OctahedralGroup.BLOCK_ROT_Y_90));
	private static final VoxelShape SHAPE_INNER = Shapes.or(SHAPE_STRAIGHT, Shapes.rotate(SHAPE_STRAIGHT, OctahedralGroup.BLOCK_ROT_Y_90));
	private static final Map<Direction, VoxelShape> SHAPE_BOTTOM_OUTER = Shapes.rotateHorizontal(SHAPE_OUTER);
	private static final Map<Direction, VoxelShape> SHAPE_BOTTOM_STRAIGHT = Shapes.rotateHorizontal(SHAPE_STRAIGHT);
	private static final Map<Direction, VoxelShape> SHAPE_BOTTOM_INNER = Shapes.rotateHorizontal(SHAPE_INNER);
	private static final Map<Direction, VoxelShape> SHAPE_TOP_OUTER = Shapes.rotateHorizontal(SHAPE_OUTER, OctahedralGroup.INVERT_Y);
	private static final Map<Direction, VoxelShape> SHAPE_TOP_STRAIGHT = Shapes.rotateHorizontal(SHAPE_STRAIGHT, OctahedralGroup.INVERT_Y);
	private static final Map<Direction, VoxelShape> SHAPE_TOP_INNER = Shapes.rotateHorizontal(SHAPE_INNER, OctahedralGroup.INVERT_Y);
	private final Block base;
	protected final BlockState baseState;

	@Override
	public MapCodec<? extends StairBlock> codec() {
		return CODEC;
	}

	public StairBlock(BlockState blockState, BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(
			this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, Half.BOTTOM).setValue(SHAPE, StairsShape.STRAIGHT).setValue(WATERLOGGED, false)
		);
		this.base = blockState.getBlock();
		this.baseState = blockState;
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState blockState) {
		return true;
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		boolean bl = blockState.getValue(HALF) == Half.BOTTOM;
		Direction direction = blockState.getValue(FACING);

		Map var10000 = switch ((StairsShape)blockState.getValue(SHAPE)) {
			case STRAIGHT -> bl ? SHAPE_BOTTOM_STRAIGHT : SHAPE_TOP_STRAIGHT;
			case OUTER_LEFT, OUTER_RIGHT -> bl ? SHAPE_BOTTOM_OUTER : SHAPE_TOP_OUTER;
			case INNER_RIGHT, INNER_LEFT -> bl ? SHAPE_BOTTOM_INNER : SHAPE_TOP_INNER;
		};

		return (VoxelShape)var10000.get(switch ((StairsShape)blockState.getValue(SHAPE)) {
			case STRAIGHT, OUTER_LEFT, INNER_RIGHT -> direction;
			case INNER_LEFT -> direction.getCounterClockWise();
			case OUTER_RIGHT -> direction.getClockWise();
		});
	}

	@Override
	public float getExplosionResistance() {
		return this.base.getExplosionResistance();
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		Direction direction = blockPlaceContext.getClickedFace();
		BlockPos blockPos = blockPlaceContext.getClickedPos();
		FluidState fluidState = blockPlaceContext.getLevel().getFluidState(blockPos);
		BlockState blockState = this.defaultBlockState()
			.setValue(FACING, blockPlaceContext.getHorizontalDirection())
			.setValue(
				HALF,
				direction != Direction.DOWN && (direction == Direction.UP || !(blockPlaceContext.getClickLocation().y - blockPos.getY() > 0.5)) ? Half.BOTTOM : Half.TOP
			)
			.setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
		return blockState.setValue(SHAPE, getStairsShape(blockState, blockPlaceContext.getLevel(), blockPos));
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

		return direction.getAxis().isHorizontal()
			? blockState.setValue(SHAPE, getStairsShape(blockState, levelReader, blockPos))
			: super.updateShape(blockState, levelReader, scheduledTickAccess, blockPos, direction, blockPos2, blockState2, randomSource);
	}

	private static StairsShape getStairsShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
		Direction direction = blockState.getValue(FACING);
		BlockState blockState2 = blockGetter.getBlockState(blockPos.relative(direction));
		if (isStairs(blockState2) && blockState.getValue(HALF) == blockState2.getValue(HALF)) {
			Direction direction2 = blockState2.getValue(FACING);
			if (direction2.getAxis() != blockState.getValue(FACING).getAxis() && canTakeShape(blockState, blockGetter, blockPos, direction2.getOpposite())) {
				if (direction2 == direction.getCounterClockWise()) {
					return StairsShape.OUTER_LEFT;
				}

				return StairsShape.OUTER_RIGHT;
			}
		}

		BlockState blockState3 = blockGetter.getBlockState(blockPos.relative(direction.getOpposite()));
		if (isStairs(blockState3) && blockState.getValue(HALF) == blockState3.getValue(HALF)) {
			Direction direction3 = blockState3.getValue(FACING);
			if (direction3.getAxis() != blockState.getValue(FACING).getAxis() && canTakeShape(blockState, blockGetter, blockPos, direction3)) {
				if (direction3 == direction.getCounterClockWise()) {
					return StairsShape.INNER_LEFT;
				}

				return StairsShape.INNER_RIGHT;
			}
		}

		return StairsShape.STRAIGHT;
	}

	private static boolean canTakeShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, Direction direction) {
		BlockState blockState2 = blockGetter.getBlockState(blockPos.relative(direction));
		return !isStairs(blockState2) || blockState2.getValue(FACING) != blockState.getValue(FACING) || blockState2.getValue(HALF) != blockState.getValue(HALF);
	}

	public static boolean isStairs(BlockState blockState) {
		return blockState.getBlock() instanceof StairBlock;
	}

	@Override
	protected BlockState rotate(BlockState blockState, Rotation rotation) {
		return blockState.setValue(FACING, rotation.rotate(blockState.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState blockState, Mirror mirror) {
		Direction direction = blockState.getValue(FACING);
		StairsShape stairsShape = blockState.getValue(SHAPE);
		switch (mirror) {
			case LEFT_RIGHT:
				if (direction.getAxis() == Direction.Axis.Z) {
					switch (stairsShape) {
						case OUTER_LEFT:
							return blockState.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.OUTER_RIGHT);
						case INNER_RIGHT:
							return blockState.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.INNER_LEFT);
						case INNER_LEFT:
							return blockState.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.INNER_RIGHT);
						case OUTER_RIGHT:
							return blockState.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.OUTER_LEFT);
						default:
							return blockState.rotate(Rotation.CLOCKWISE_180);
					}
				}
				break;
			case FRONT_BACK:
				if (direction.getAxis() == Direction.Axis.X) {
					switch (stairsShape) {
						case STRAIGHT:
							return blockState.rotate(Rotation.CLOCKWISE_180);
						case OUTER_LEFT:
							return blockState.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.OUTER_RIGHT);
						case INNER_RIGHT:
							return blockState.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.INNER_RIGHT);
						case INNER_LEFT:
							return blockState.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.INNER_LEFT);
						case OUTER_RIGHT:
							return blockState.rotate(Rotation.CLOCKWISE_180).setValue(SHAPE, StairsShape.OUTER_LEFT);
					}
				}
		}

		return super.mirror(blockState, mirror);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HALF, SHAPE, WATERLOGGED);
	}

	@Override
	protected FluidState getFluidState(BlockState blockState) {
		return blockState.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(blockState);
	}

	@Override
	protected boolean isPathfindable(BlockState blockState, PathComputationType pathComputationType) {
		return false;
	}
}
