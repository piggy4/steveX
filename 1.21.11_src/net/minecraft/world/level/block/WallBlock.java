package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
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
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WallBlock extends Block implements SimpleWaterloggedBlock {
	public static final MapCodec<WallBlock> CODEC = simpleCodec(WallBlock::new);
	public static final BooleanProperty UP = BlockStateProperties.UP;
	public static final EnumProperty<WallSide> EAST = BlockStateProperties.EAST_WALL;
	public static final EnumProperty<WallSide> NORTH = BlockStateProperties.NORTH_WALL;
	public static final EnumProperty<WallSide> SOUTH = BlockStateProperties.SOUTH_WALL;
	public static final EnumProperty<WallSide> WEST = BlockStateProperties.WEST_WALL;
	public static final Map<Direction, EnumProperty<WallSide>> PROPERTY_BY_DIRECTION = ImmutableMap.copyOf(
		Maps.newEnumMap(Map.of(Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST))
	);
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	private final Function<BlockState, VoxelShape> shapes;
	private final Function<BlockState, VoxelShape> collisionShapes;
	private static final VoxelShape TEST_SHAPE_POST = Block.column(2.0, 0.0, 16.0);
	private static final Map<Direction, VoxelShape> TEST_SHAPES_WALL = Shapes.rotateHorizontal(Block.boxZ(2.0, 16.0, 0.0, 9.0));

	@Override
	public MapCodec<WallBlock> codec() {
		return CODEC;
	}

	public WallBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(
			this.stateDefinition
				.any()
				.setValue(UP, true)
				.setValue(NORTH, WallSide.NONE)
				.setValue(EAST, WallSide.NONE)
				.setValue(SOUTH, WallSide.NONE)
				.setValue(WEST, WallSide.NONE)
				.setValue(WATERLOGGED, false)
		);
		this.shapes = this.makeShapes(16.0F, 14.0F);
		this.collisionShapes = this.makeShapes(24.0F, 24.0F);
	}

	private Function<BlockState, VoxelShape> makeShapes(float f, float g) {
		VoxelShape voxelShape = Block.column(8.0, 0.0, f);
		int i = 6;
		Map<Direction, VoxelShape> map = Shapes.rotateHorizontal(Block.boxZ(6.0, 0.0, g, 0.0, 11.0));
		Map<Direction, VoxelShape> map2 = Shapes.rotateHorizontal(Block.boxZ(6.0, 0.0, f, 0.0, 11.0));
		return this.getShapeForEachState(blockState -> {
			VoxelShape voxelShape2 = blockState.getValue(UP) ? voxelShape : Shapes.empty();

			for (Entry<Direction, EnumProperty<WallSide>> entry : PROPERTY_BY_DIRECTION.entrySet()) {
				voxelShape2 = Shapes.or(voxelShape2, switch ((WallSide)blockState.getValue(entry.getValue())) {
					case NONE -> Shapes.empty();
					case LOW -> (VoxelShape)map.get(entry.getKey());
					case TALL -> (VoxelShape)map2.get(entry.getKey());
				});
			}

			return voxelShape2;
		}, WATERLOGGED);
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return this.shapes.apply(blockState);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return this.collisionShapes.apply(blockState);
	}

	@Override
	protected boolean isPathfindable(BlockState blockState, PathComputationType pathComputationType) {
		return false;
	}

	private boolean connectsTo(BlockState blockState, boolean bl, Direction direction) {
		Block block = blockState.getBlock();
		boolean bl2 = block instanceof FenceGateBlock && FenceGateBlock.connectsToDirection(blockState, direction);
		return blockState.is(BlockTags.WALLS) || !isExceptionForConnection(blockState) && bl || block instanceof IronBarsBlock || bl2;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		LevelReader levelReader = blockPlaceContext.getLevel();
		BlockPos blockPos = blockPlaceContext.getClickedPos();
		FluidState fluidState = blockPlaceContext.getLevel().getFluidState(blockPlaceContext.getClickedPos());
		BlockPos blockPos2 = blockPos.north();
		BlockPos blockPos3 = blockPos.east();
		BlockPos blockPos4 = blockPos.south();
		BlockPos blockPos5 = blockPos.west();
		BlockPos blockPos6 = blockPos.above();
		BlockState blockState = levelReader.getBlockState(blockPos2);
		BlockState blockState2 = levelReader.getBlockState(blockPos3);
		BlockState blockState3 = levelReader.getBlockState(blockPos4);
		BlockState blockState4 = levelReader.getBlockState(blockPos5);
		BlockState blockState5 = levelReader.getBlockState(blockPos6);
		boolean bl = this.connectsTo(blockState, blockState.isFaceSturdy(levelReader, blockPos2, Direction.SOUTH), Direction.SOUTH);
		boolean bl2 = this.connectsTo(blockState2, blockState2.isFaceSturdy(levelReader, blockPos3, Direction.WEST), Direction.WEST);
		boolean bl3 = this.connectsTo(blockState3, blockState3.isFaceSturdy(levelReader, blockPos4, Direction.NORTH), Direction.NORTH);
		boolean bl4 = this.connectsTo(blockState4, blockState4.isFaceSturdy(levelReader, blockPos5, Direction.EAST), Direction.EAST);
		BlockState blockState6 = this.defaultBlockState().setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
		return this.updateShape(levelReader, blockState6, blockPos6, blockState5, bl, bl2, bl3, bl4);
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

		if (direction == Direction.DOWN) {
			return super.updateShape(blockState, levelReader, scheduledTickAccess, blockPos, direction, blockPos2, blockState2, randomSource);
		} else {
			return direction == Direction.UP
				? this.topUpdate(levelReader, blockState, blockPos2, blockState2)
				: this.sideUpdate(levelReader, blockPos, blockState, blockPos2, blockState2, direction);
		}
	}

	private static boolean isConnected(BlockState blockState, Property<WallSide> property) {
		return blockState.getValue(property) != WallSide.NONE;
	}

	private static boolean isCovered(VoxelShape voxelShape, VoxelShape voxelShape2) {
		return !Shapes.joinIsNotEmpty(voxelShape2, voxelShape, BooleanOp.ONLY_FIRST);
	}

	private BlockState topUpdate(LevelReader levelReader, BlockState blockState, BlockPos blockPos, BlockState blockState2) {
		boolean bl = isConnected(blockState, NORTH);
		boolean bl2 = isConnected(blockState, EAST);
		boolean bl3 = isConnected(blockState, SOUTH);
		boolean bl4 = isConnected(blockState, WEST);
		return this.updateShape(levelReader, blockState, blockPos, blockState2, bl, bl2, bl3, bl4);
	}

	private BlockState sideUpdate(
		LevelReader levelReader, BlockPos blockPos, BlockState blockState, BlockPos blockPos2, BlockState blockState2, Direction direction
	) {
		Direction direction2 = direction.getOpposite();
		boolean bl = direction == Direction.NORTH
			? this.connectsTo(blockState2, blockState2.isFaceSturdy(levelReader, blockPos2, direction2), direction2)
			: isConnected(blockState, NORTH);
		boolean bl2 = direction == Direction.EAST
			? this.connectsTo(blockState2, blockState2.isFaceSturdy(levelReader, blockPos2, direction2), direction2)
			: isConnected(blockState, EAST);
		boolean bl3 = direction == Direction.SOUTH
			? this.connectsTo(blockState2, blockState2.isFaceSturdy(levelReader, blockPos2, direction2), direction2)
			: isConnected(blockState, SOUTH);
		boolean bl4 = direction == Direction.WEST
			? this.connectsTo(blockState2, blockState2.isFaceSturdy(levelReader, blockPos2, direction2), direction2)
			: isConnected(blockState, WEST);
		BlockPos blockPos3 = blockPos.above();
		BlockState blockState3 = levelReader.getBlockState(blockPos3);
		return this.updateShape(levelReader, blockState, blockPos3, blockState3, bl, bl2, bl3, bl4);
	}

	private BlockState updateShape(
		LevelReader levelReader, BlockState blockState, BlockPos blockPos, BlockState blockState2, boolean bl, boolean bl2, boolean bl3, boolean bl4
	) {
		VoxelShape voxelShape = blockState2.getCollisionShape(levelReader, blockPos).getFaceShape(Direction.DOWN);
		BlockState blockState3 = this.updateSides(blockState, bl, bl2, bl3, bl4, voxelShape);
		return blockState3.setValue(UP, this.shouldRaisePost(blockState3, blockState2, voxelShape));
	}

	private boolean shouldRaisePost(BlockState blockState, BlockState blockState2, VoxelShape voxelShape) {
		boolean bl = blockState2.getBlock() instanceof WallBlock && blockState2.getValue(UP);
		if (bl) {
			return true;
		}

		WallSide wallSide = blockState.getValue(NORTH);
		WallSide wallSide2 = blockState.getValue(SOUTH);
		WallSide wallSide3 = blockState.getValue(EAST);
		WallSide wallSide4 = blockState.getValue(WEST);
		boolean bl2 = wallSide2 == WallSide.NONE;
		boolean bl3 = wallSide4 == WallSide.NONE;
		boolean bl4 = wallSide3 == WallSide.NONE;
		boolean bl5 = wallSide == WallSide.NONE;
		boolean bl6 = bl5 && bl2 && bl3 && bl4 || bl5 != bl2 || bl3 != bl4;
		if (bl6) {
			return true;
		}

		boolean bl7 = wallSide == WallSide.TALL && wallSide2 == WallSide.TALL || wallSide3 == WallSide.TALL && wallSide4 == WallSide.TALL;
		return bl7 ? false : blockState2.is(BlockTags.WALL_POST_OVERRIDE) || isCovered(voxelShape, TEST_SHAPE_POST);
	}

	private BlockState updateSides(BlockState blockState, boolean bl, boolean bl2, boolean bl3, boolean bl4, VoxelShape voxelShape) {
		return blockState.setValue(NORTH, this.makeWallState(bl, voxelShape, TEST_SHAPES_WALL.get(Direction.NORTH)))
			.setValue(EAST, this.makeWallState(bl2, voxelShape, TEST_SHAPES_WALL.get(Direction.EAST)))
			.setValue(SOUTH, this.makeWallState(bl3, voxelShape, TEST_SHAPES_WALL.get(Direction.SOUTH)))
			.setValue(WEST, this.makeWallState(bl4, voxelShape, TEST_SHAPES_WALL.get(Direction.WEST)));
	}

	private WallSide makeWallState(boolean bl, VoxelShape voxelShape, VoxelShape voxelShape2) {
		if (bl) {
			return isCovered(voxelShape, voxelShape2) ? WallSide.TALL : WallSide.LOW;
		} else {
			return WallSide.NONE;
		}
	}

	@Override
	protected FluidState getFluidState(BlockState blockState) {
		return blockState.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(blockState);
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState blockState) {
		return !blockState.getValue(WATERLOGGED);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(UP, NORTH, EAST, WEST, SOUTH, WATERLOGGED);
	}

	@Override
	protected BlockState rotate(BlockState blockState, Rotation rotation) {
		switch (rotation) {
			case CLOCKWISE_180:
				return blockState.setValue(NORTH, blockState.getValue(SOUTH))
					.setValue(EAST, blockState.getValue(WEST))
					.setValue(SOUTH, blockState.getValue(NORTH))
					.setValue(WEST, blockState.getValue(EAST));
			case COUNTERCLOCKWISE_90:
				return blockState.setValue(NORTH, blockState.getValue(EAST))
					.setValue(EAST, blockState.getValue(SOUTH))
					.setValue(SOUTH, blockState.getValue(WEST))
					.setValue(WEST, blockState.getValue(NORTH));
			case CLOCKWISE_90:
				return blockState.setValue(NORTH, blockState.getValue(WEST))
					.setValue(EAST, blockState.getValue(NORTH))
					.setValue(SOUTH, blockState.getValue(EAST))
					.setValue(WEST, blockState.getValue(SOUTH));
			default:
				return blockState;
		}
	}

	@Override
	protected BlockState mirror(BlockState blockState, Mirror mirror) {
		switch (mirror) {
			case LEFT_RIGHT:
				return blockState.setValue(NORTH, blockState.getValue(SOUTH)).setValue(SOUTH, blockState.getValue(NORTH));
			case FRONT_BACK:
				return blockState.setValue(EAST, blockState.getValue(WEST)).setValue(WEST, blockState.getValue(EAST));
			default:
				return super.mirror(blockState, mirror);
		}
	}
}
