package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SugarCaneBlock extends Block {
	public static final MapCodec<SugarCaneBlock> CODEC = simpleCodec(SugarCaneBlock::new);
	public static final IntegerProperty AGE = BlockStateProperties.AGE_15;
	private static final VoxelShape SHAPE = Block.column(12.0, 0.0, 16.0);

	@Override
	public MapCodec<SugarCaneBlock> codec() {
		return CODEC;
	}

	public SugarCaneBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return SHAPE;
	}

	@Override
	protected void tick(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, RandomSource randomSource) {
		if (!blockState.canSurvive(serverLevel, blockPos)) {
			serverLevel.destroyBlock(blockPos, true);
		}
	}

	@Override
	protected void randomTick(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, RandomSource randomSource) {
		if (serverLevel.isEmptyBlock(blockPos.above())) {
			int i = 1;

			while (serverLevel.getBlockState(blockPos.below(i)).is(this)) {
				i++;
			}

			if (i < 3) {
				int j = blockState.getValue(AGE);
				if (j == 15) {
					serverLevel.setBlockAndUpdate(blockPos.above(), this.defaultBlockState());
					serverLevel.setBlock(blockPos, blockState.setValue(AGE, 0), 260);
				} else {
					serverLevel.setBlock(blockPos, blockState.setValue(AGE, j + 1), 260);
				}
			}
		}
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
		}

		return super.updateShape(blockState, levelReader, scheduledTickAccess, blockPos, direction, blockPos2, blockState2, randomSource);
	}

	@Override
	protected boolean canSurvive(BlockState blockState, LevelReader levelReader, BlockPos blockPos) {
		BlockState blockState2 = levelReader.getBlockState(blockPos.below());
		if (blockState2.is(this)) {
			return true;
		}

		if (blockState2.is(BlockTags.DIRT) || blockState2.is(BlockTags.SAND)) {
			BlockPos blockPos2 = blockPos.below();

			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockState blockState3 = levelReader.getBlockState(blockPos2.relative(direction));
				FluidState fluidState = levelReader.getFluidState(blockPos2.relative(direction));
				if (fluidState.is(FluidTags.WATER) || blockState3.is(Blocks.FROSTED_ICE)) {
					return true;
				}
			}
		}

		return false;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AGE);
	}
}
