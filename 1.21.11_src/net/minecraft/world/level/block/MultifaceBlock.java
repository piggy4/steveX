package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class MultifaceBlock extends Block implements SimpleWaterloggedBlock {
	public static final MapCodec<MultifaceBlock> CODEC = simpleCodec(MultifaceBlock::new);
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	private static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION;
	protected static final Direction[] DIRECTIONS = Direction.values();
	private final Function<BlockState, VoxelShape> shapes;
	private final boolean canRotate;
	private final boolean canMirrorX;
	private final boolean canMirrorZ;

	@Override
	protected MapCodec<? extends MultifaceBlock> codec() {
		return CODEC;
	}

	public MultifaceBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(getDefaultMultifaceState(this.stateDefinition));
		this.shapes = this.makeShapes();
		this.canRotate = Direction.Plane.HORIZONTAL.stream().allMatch(this::isFaceSupported);
		this.canMirrorX = Direction.Plane.HORIZONTAL.stream().filter(Direction.Axis.X).filter(this::isFaceSupported).count() % 2L == 0L;
		this.canMirrorZ = Direction.Plane.HORIZONTAL.stream().filter(Direction.Axis.Z).filter(this::isFaceSupported).count() % 2L == 0L;
	}

	private Function<BlockState, VoxelShape> makeShapes() {
		Map<Direction, VoxelShape> map = Shapes.rotateAll(Block.boxZ(16.0, 0.0, 1.0));
		return this.getShapeForEachState(blockState -> {
			VoxelShape voxelShape = Shapes.empty();

			for (Direction direction : DIRECTIONS) {
				if (hasFace(blockState, direction)) {
					voxelShape = Shapes.or(voxelShape, map.get(direction));
				}
			}

			return voxelShape.isEmpty() ? Shapes.block() : voxelShape;
		}, WATERLOGGED);
	}

	public static Set<Direction> availableFaces(BlockState blockState) {
		if (!(blockState.getBlock() instanceof MultifaceBlock)) {
			return Set.of();
		}

		Set<Direction> set = EnumSet.noneOf(Direction.class);

		for (Direction direction : Direction.values()) {
			if (hasFace(blockState, direction)) {
				set.add(direction);
			}
		}

		return set;
	}

	public static Set<Direction> unpack(byte b) {
		Set<Direction> set = EnumSet.noneOf(Direction.class);

		for (Direction direction : Direction.values()) {
			if ((b & (byte)(1 << direction.ordinal())) > 0) {
				set.add(direction);
			}
		}

		return set;
	}

	public static byte pack(Collection<Direction> collection) {
		byte b = 0;

		for (Direction direction : collection) {
			b = (byte)(b | 1 << direction.ordinal());
		}

		return b;
	}

	protected boolean isFaceSupported(Direction direction) {
		return true;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		for (Direction direction : DIRECTIONS) {
			if (this.isFaceSupported(direction)) {
				builder.add(getFaceProperty(direction));
			}
		}

		builder.add(WATERLOGGED);
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

		if (!hasAnyFace(blockState)) {
			return Blocks.AIR.defaultBlockState();
		} else {
			return hasFace(blockState, direction) && !canAttachTo(levelReader, direction, blockPos2, blockState2)
				? removeFace(blockState, getFaceProperty(direction))
				: blockState;
		}
	}

	@Override
	protected FluidState getFluidState(BlockState blockState) {
		return blockState.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(blockState);
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return this.shapes.apply(blockState);
	}

	@Override
	protected boolean canSurvive(BlockState blockState, LevelReader levelReader, BlockPos blockPos) {
		boolean bl = false;

		for (Direction direction : DIRECTIONS) {
			if (hasFace(blockState, direction)) {
				if (!canAttachTo(levelReader, blockPos, direction)) {
					return false;
				}

				bl = true;
			}
		}

		return bl;
	}

	@Override
	protected boolean canBeReplaced(BlockState blockState, BlockPlaceContext blockPlaceContext) {
		return !blockPlaceContext.getItemInHand().is(this.asItem()) || hasAnyVacantFace(blockState);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		Level level = blockPlaceContext.getLevel();
		BlockPos blockPos = blockPlaceContext.getClickedPos();
		BlockState blockState = level.getBlockState(blockPos);
		return Arrays.stream(blockPlaceContext.getNearestLookingDirections())
			.map(direction -> this.getStateForPlacement(blockState, level, blockPos, direction))
			.filter(Objects::nonNull)
			.findFirst()
			.orElse(null);
	}

	public boolean isValidStateForPlacement(BlockGetter blockGetter, BlockState blockState, BlockPos blockPos, Direction direction) {
		if (this.isFaceSupported(direction) && (!blockState.is(this) || !hasFace(blockState, direction))) {
			BlockPos blockPos2 = blockPos.relative(direction);
			return canAttachTo(blockGetter, direction, blockPos2, blockGetter.getBlockState(blockPos2));
		} else {
			return false;
		}
	}

	public @Nullable BlockState getStateForPlacement(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, Direction direction) {
		if (!this.isValidStateForPlacement(blockGetter, blockState, blockPos, direction)) {
			return null;
		}

		BlockState blockState2;
		if (blockState.is(this)) {
			blockState2 = blockState;
		} else if (blockState.getFluidState().isSourceOfType(Fluids.WATER)) {
			blockState2 = this.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
		} else {
			blockState2 = this.defaultBlockState();
		}

		return blockState2.setValue(getFaceProperty(direction), true);
	}

	@Override
	protected BlockState rotate(BlockState blockState, Rotation rotation) {
		return !this.canRotate ? blockState : this.mapDirections(blockState, rotation::rotate);
	}

	@Override
	protected BlockState mirror(BlockState blockState, Mirror mirror) {
		if (mirror == Mirror.FRONT_BACK && !this.canMirrorX) {
			return blockState;
		} else {
			return mirror == Mirror.LEFT_RIGHT && !this.canMirrorZ ? blockState : this.mapDirections(blockState, mirror::mirror);
		}
	}

	private BlockState mapDirections(BlockState blockState, Function<Direction, Direction> function) {
		BlockState blockState2 = blockState;

		for (Direction direction : DIRECTIONS) {
			if (this.isFaceSupported(direction)) {
				blockState2 = blockState2.setValue(getFaceProperty(function.apply(direction)), blockState.getValue(getFaceProperty(direction)));
			}
		}

		return blockState2;
	}

	public static boolean hasFace(BlockState blockState, Direction direction) {
		BooleanProperty booleanProperty = getFaceProperty(direction);
		return blockState.getValueOrElse(booleanProperty, false);
	}

	public static boolean canAttachTo(BlockGetter blockGetter, BlockPos blockPos, Direction direction) {
		BlockPos blockPos2 = blockPos.relative(direction);
		BlockState blockState = blockGetter.getBlockState(blockPos2);
		return canAttachTo(blockGetter, direction, blockPos2, blockState);
	}

	public static boolean canAttachTo(BlockGetter blockGetter, Direction direction, BlockPos blockPos, BlockState blockState) {
		return Block.isFaceFull(blockState.getBlockSupportShape(blockGetter, blockPos), direction.getOpposite())
			|| Block.isFaceFull(blockState.getCollisionShape(blockGetter, blockPos), direction.getOpposite());
	}

	private static BlockState removeFace(BlockState blockState, BooleanProperty booleanProperty) {
		BlockState blockState2 = blockState.setValue(booleanProperty, false);
		return hasAnyFace(blockState2) ? blockState2 : Blocks.AIR.defaultBlockState();
	}

	public static BooleanProperty getFaceProperty(Direction direction) {
		return PROPERTY_BY_DIRECTION.get(direction);
	}

	private static BlockState getDefaultMultifaceState(StateDefinition<Block, BlockState> stateDefinition) {
		BlockState blockState = stateDefinition.any().setValue(WATERLOGGED, false);

		for (BooleanProperty booleanProperty : PROPERTY_BY_DIRECTION.values()) {
			blockState = blockState.trySetValue(booleanProperty, false);
		}

		return blockState;
	}

	protected static boolean hasAnyFace(BlockState blockState) {
		for (Direction direction : DIRECTIONS) {
			if (hasFace(blockState, direction)) {
				return true;
			}
		}

		return false;
	}

	private static boolean hasAnyVacantFace(BlockState blockState) {
		for (Direction direction : DIRECTIONS) {
			if (!hasFace(blockState, direction)) {
				return true;
			}
		}

		return false;
	}
}
