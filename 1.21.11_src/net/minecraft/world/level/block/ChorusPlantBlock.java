package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;

public class ChorusPlantBlock extends PipeBlock {
	public static final MapCodec<ChorusPlantBlock> CODEC = simpleCodec(ChorusPlantBlock::new);

	@Override
	public MapCodec<ChorusPlantBlock> codec() {
		return CODEC;
	}

	public ChorusPlantBlock(BlockBehaviour.Properties properties) {
		super(10.0F, properties);
		this.registerDefaultState(
			this.stateDefinition
				.any()
				.setValue(NORTH, false)
				.setValue(EAST, false)
				.setValue(SOUTH, false)
				.setValue(WEST, false)
				.setValue(UP, false)
				.setValue(DOWN, false)
		);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		return getStateWithConnections(blockPlaceContext.getLevel(), blockPlaceContext.getClickedPos(), this.defaultBlockState());
	}

	public static BlockState getStateWithConnections(BlockGetter blockGetter, BlockPos blockPos, BlockState blockState) {
		BlockState blockState2 = blockGetter.getBlockState(blockPos.below());
		BlockState blockState3 = blockGetter.getBlockState(blockPos.above());
		BlockState blockState4 = blockGetter.getBlockState(blockPos.north());
		BlockState blockState5 = blockGetter.getBlockState(blockPos.east());
		BlockState blockState6 = blockGetter.getBlockState(blockPos.south());
		BlockState blockState7 = blockGetter.getBlockState(blockPos.west());
		Block block = blockState.getBlock();
		return blockState.trySetValue(DOWN, blockState2.is(block) || blockState2.is(Blocks.CHORUS_FLOWER) || blockState2.is(Blocks.END_STONE))
			.trySetValue(UP, blockState3.is(block) || blockState3.is(Blocks.CHORUS_FLOWER))
			.trySetValue(NORTH, blockState4.is(block) || blockState4.is(Blocks.CHORUS_FLOWER))
			.trySetValue(EAST, blockState5.is(block) || blockState5.is(Blocks.CHORUS_FLOWER))
			.trySetValue(SOUTH, blockState6.is(block) || blockState6.is(Blocks.CHORUS_FLOWER))
			.trySetValue(WEST, blockState7.is(block) || blockState7.is(Blocks.CHORUS_FLOWER));
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
		if (!blockState.canSurvive(levelReader, blockPos)) {
			scheduledTickAccess.scheduleTick(blockPos, this, 1);
			return super.updateShape(blockState, levelReader, scheduledTickAccess, blockPos, direction, blockPos2, blockState2, randomSource);
		} else {
			boolean bl = blockState2.is(this) || blockState2.is(Blocks.CHORUS_FLOWER) || direction == Direction.DOWN && blockState2.is(Blocks.END_STONE);
			return blockState.setValue(PROPERTY_BY_DIRECTION.get(direction), bl);
		}
	}

	@Override
	protected void tick(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, RandomSource randomSource) {
		if (!blockState.canSurvive(serverLevel, blockPos)) {
			serverLevel.destroyBlock(blockPos, true);
		}
	}

	@Override
	protected boolean canSurvive(BlockState blockState, LevelReader levelReader, BlockPos blockPos) {
		BlockState blockState2 = levelReader.getBlockState(blockPos.below());
		boolean bl = !levelReader.getBlockState(blockPos.above()).isAir() && !blockState2.isAir();

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos blockPos2 = blockPos.relative(direction);
			BlockState blockState3 = levelReader.getBlockState(blockPos2);
			if (blockState3.is(this)) {
				if (bl) {
					return false;
				}

				BlockState blockState4 = levelReader.getBlockState(blockPos2.below());
				if (blockState4.is(this) || blockState4.is(Blocks.END_STONE)) {
					return true;
				}
			}
		}

		return blockState2.is(this) || blockState2.is(Blocks.END_STONE);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
	}

	@Override
	protected boolean isPathfindable(BlockState blockState, PathComputationType pathComputationType) {
		return false;
	}
}
