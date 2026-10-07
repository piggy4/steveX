package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CactusBlock extends Block {
	public static final MapCodec<CactusBlock> CODEC = simpleCodec(CactusBlock::new);
	public static final IntegerProperty AGE = BlockStateProperties.AGE_15;
	public static final int MAX_AGE = 15;
	private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 16.0);
	private static final VoxelShape SHAPE_COLLISION = Block.column(14.0, 0.0, 15.0);
	private static final int MAX_CACTUS_GROWING_HEIGHT = 3;
	private static final int ATTEMPT_GROW_CACTUS_FLOWER_AGE = 8;
	private static final double ATTEMPT_GROW_CACTUS_FLOWER_SMALL_CACTUS_CHANCE = 0.1;
	private static final double ATTEMPT_GROW_CACTUS_FLOWER_TALL_CACTUS_CHANCE = 0.25;

	@Override
	public MapCodec<CactusBlock> codec() {
		return CODEC;
	}

	public CactusBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
	}

	@Override
	protected void tick(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, RandomSource randomSource) {
		if (!blockState.canSurvive(serverLevel, blockPos)) {
			serverLevel.destroyBlock(blockPos, true);
		}
	}

	@Override
	protected void randomTick(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, RandomSource randomSource) {
		BlockPos blockPos2 = blockPos.above();
		if (serverLevel.isEmptyBlock(blockPos2)) {
			int i = 1;
			int j = blockState.getValue(AGE);

			while (serverLevel.getBlockState(blockPos.below(i)).is(this)) {
				if (++i == 3 && j == 15) {
					return;
				}
			}

			if (j == 8 && this.canSurvive(this.defaultBlockState(), serverLevel, blockPos.above())) {
				double d = i >= 3 ? 0.25 : 0.1;
				if (randomSource.nextDouble() <= d) {
					serverLevel.setBlockAndUpdate(blockPos2, Blocks.CACTUS_FLOWER.defaultBlockState());
				}
			} else if (j == 15 && i < 3) {
				serverLevel.setBlockAndUpdate(blockPos2, this.defaultBlockState());
				BlockState blockState2 = blockState.setValue(AGE, 0);
				serverLevel.setBlock(blockPos, blockState2, 260);
				serverLevel.neighborChanged(blockState2, blockPos2, this, null, false);
			}

			if (j < 15) {
				serverLevel.setBlock(blockPos, blockState.setValue(AGE, j + 1), 260);
			}
		}
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return SHAPE_COLLISION;
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return SHAPE;
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
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockState blockState2 = levelReader.getBlockState(blockPos.relative(direction));
			if (blockState2.isSolid() || levelReader.getFluidState(blockPos.relative(direction)).is(FluidTags.LAVA)) {
				return false;
			}
		}

		BlockState blockState3 = levelReader.getBlockState(blockPos.below());
		return (blockState3.is(Blocks.CACTUS) || blockState3.is(BlockTags.SAND)) && !levelReader.getBlockState(blockPos.above()).liquid();
	}

	@Override
	protected void entityInside(
		BlockState blockState, Level level, BlockPos blockPos, Entity entity, InsideBlockEffectApplier insideBlockEffectApplier, boolean bl
	) {
		entity.hurt(level.damageSources().cactus(), 1.0F);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AGE);
	}

	@Override
	protected boolean isPathfindable(BlockState blockState, PathComputationType pathComputationType) {
		return false;
	}
}
