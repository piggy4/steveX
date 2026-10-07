package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class HugeMushroomBlock extends Block {
	public static final MapCodec<HugeMushroomBlock> CODEC = simpleCodec(HugeMushroomBlock::new);
	public static final BooleanProperty NORTH = PipeBlock.NORTH;
	public static final BooleanProperty EAST = PipeBlock.EAST;
	public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
	public static final BooleanProperty WEST = PipeBlock.WEST;
	public static final BooleanProperty UP = PipeBlock.UP;
	public static final BooleanProperty DOWN = PipeBlock.DOWN;
	private static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION;

	@Override
	public MapCodec<HugeMushroomBlock> codec() {
		return CODEC;
	}

	public HugeMushroomBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(
			this.stateDefinition.any().setValue(NORTH, true).setValue(EAST, true).setValue(SOUTH, true).setValue(WEST, true).setValue(UP, true).setValue(DOWN, true)
		);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		BlockGetter blockGetter = blockPlaceContext.getLevel();
		BlockPos blockPos = blockPlaceContext.getClickedPos();
		return this.defaultBlockState()
			.setValue(DOWN, !blockGetter.getBlockState(blockPos.below()).is(this))
			.setValue(UP, !blockGetter.getBlockState(blockPos.above()).is(this))
			.setValue(NORTH, !blockGetter.getBlockState(blockPos.north()).is(this))
			.setValue(EAST, !blockGetter.getBlockState(blockPos.east()).is(this))
			.setValue(SOUTH, !blockGetter.getBlockState(blockPos.south()).is(this))
			.setValue(WEST, !blockGetter.getBlockState(blockPos.west()).is(this));
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
		return blockState2.is(this)
			? blockState.setValue(PROPERTY_BY_DIRECTION.get(direction), false)
			: super.updateShape(blockState, levelReader, scheduledTickAccess, blockPos, direction, blockPos2, blockState2, randomSource);
	}

	@Override
	protected BlockState rotate(BlockState blockState, Rotation rotation) {
		return blockState.setValue(PROPERTY_BY_DIRECTION.get(rotation.rotate(Direction.NORTH)), blockState.getValue(NORTH))
			.setValue(PROPERTY_BY_DIRECTION.get(rotation.rotate(Direction.SOUTH)), blockState.getValue(SOUTH))
			.setValue(PROPERTY_BY_DIRECTION.get(rotation.rotate(Direction.EAST)), blockState.getValue(EAST))
			.setValue(PROPERTY_BY_DIRECTION.get(rotation.rotate(Direction.WEST)), blockState.getValue(WEST))
			.setValue(PROPERTY_BY_DIRECTION.get(rotation.rotate(Direction.UP)), blockState.getValue(UP))
			.setValue(PROPERTY_BY_DIRECTION.get(rotation.rotate(Direction.DOWN)), blockState.getValue(DOWN));
	}

	@Override
	protected BlockState mirror(BlockState blockState, Mirror mirror) {
		return blockState.setValue(PROPERTY_BY_DIRECTION.get(mirror.mirror(Direction.NORTH)), blockState.getValue(NORTH))
			.setValue(PROPERTY_BY_DIRECTION.get(mirror.mirror(Direction.SOUTH)), blockState.getValue(SOUTH))
			.setValue(PROPERTY_BY_DIRECTION.get(mirror.mirror(Direction.EAST)), blockState.getValue(EAST))
			.setValue(PROPERTY_BY_DIRECTION.get(mirror.mirror(Direction.WEST)), blockState.getValue(WEST))
			.setValue(PROPERTY_BY_DIRECTION.get(mirror.mirror(Direction.UP)), blockState.getValue(UP))
			.setValue(PROPERTY_BY_DIRECTION.get(mirror.mirror(Direction.DOWN)), blockState.getValue(DOWN));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(UP, DOWN, NORTH, EAST, SOUTH, WEST);
	}
}
