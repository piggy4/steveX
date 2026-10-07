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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class SeaPickleBlock extends VegetationBlock implements BonemealableBlock, SimpleWaterloggedBlock {
	public static final MapCodec<SeaPickleBlock> CODEC = simpleCodec(SeaPickleBlock::new);
	public static final int MAX_PICKLES = 4;
	public static final IntegerProperty PICKLES = BlockStateProperties.PICKLES;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	private static final VoxelShape SHAPE_ONE = Block.column(4.0, 0.0, 6.0);
	private static final VoxelShape SHAPE_TWO = Block.column(10.0, 0.0, 6.0);
	private static final VoxelShape SHAPE_THREE = Block.column(12.0, 0.0, 6.0);
	private static final VoxelShape SHAPE_FOUR = Block.column(12.0, 0.0, 7.0);

	@Override
	public MapCodec<SeaPickleBlock> codec() {
		return CODEC;
	}

	public SeaPickleBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(PICKLES, 1).setValue(WATERLOGGED, true));
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext blockPlaceContext) {
		BlockState blockState = blockPlaceContext.getLevel().getBlockState(blockPlaceContext.getClickedPos());
		if (blockState.is(this)) {
			return blockState.setValue(PICKLES, Math.min(4, blockState.getValue(PICKLES) + 1));
		}

		FluidState fluidState = blockPlaceContext.getLevel().getFluidState(blockPlaceContext.getClickedPos());
		boolean bl = fluidState.getType() == Fluids.WATER;
		return super.getStateForPlacement(blockPlaceContext).setValue(WATERLOGGED, bl);
	}

	public static boolean isDead(BlockState blockState) {
		return !blockState.getValue(WATERLOGGED);
	}

	@Override
	protected boolean mayPlaceOn(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
		return !blockState.getCollisionShape(blockGetter, blockPos).getFaceShape(Direction.UP).isEmpty()
			|| blockState.isFaceSturdy(blockGetter, blockPos, Direction.UP);
	}

	@Override
	protected boolean canSurvive(BlockState blockState, LevelReader levelReader, BlockPos blockPos) {
		BlockPos blockPos2 = blockPos.below();
		return this.mayPlaceOn(levelReader.getBlockState(blockPos2), levelReader, blockPos2);
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
			return Blocks.AIR.defaultBlockState();
		}

		if (blockState.getValue(WATERLOGGED)) {
			scheduledTickAccess.scheduleTick(blockPos, Fluids.WATER, Fluids.WATER.getTickDelay(levelReader));
		}

		return super.updateShape(blockState, levelReader, scheduledTickAccess, blockPos, direction, blockPos2, blockState2, randomSource);
	}

	@Override
	protected boolean canBeReplaced(BlockState blockState, BlockPlaceContext blockPlaceContext) {
		return !blockPlaceContext.isSecondaryUseActive() && blockPlaceContext.getItemInHand().is(this.asItem()) && blockState.getValue(PICKLES) < 4
			? true
			: super.canBeReplaced(blockState, blockPlaceContext);
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return switch (blockState.getValue(PICKLES)) {
			case 2 -> SHAPE_TWO;
			case 3 -> SHAPE_THREE;
			case 4 -> SHAPE_FOUR;
			default -> SHAPE_ONE;
		};
	}

	@Override
	protected FluidState getFluidState(BlockState blockState) {
		return blockState.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(blockState);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PICKLES, WATERLOGGED);
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader levelReader, BlockPos blockPos, BlockState blockState) {
		return !isDead(blockState) && levelReader.getBlockState(blockPos.below()).is(BlockTags.CORAL_BLOCKS);
	}

	@Override
	public boolean isBonemealSuccess(Level level, RandomSource randomSource, BlockPos blockPos, BlockState blockState) {
		return true;
	}

	@Override
	public void performBonemeal(ServerLevel serverLevel, RandomSource randomSource, BlockPos blockPos, BlockState blockState) {
		int i = 5;
		int j = 1;
		int k = 2;
		int l = 0;
		int m = blockPos.getX() - 2;
		int n = 0;

		for (int o = 0; o < 5; o++) {
			for (int p = 0; p < j; p++) {
				int q = 2 + blockPos.getY() - 1;

				for (int r = q - 2; r < q; r++) {
					BlockPos blockPos2 = new BlockPos(m + o, r, blockPos.getZ() - n + p);
					if (!blockPos2.equals(blockPos) && randomSource.nextInt(6) == 0 && serverLevel.getBlockState(blockPos2).is(Blocks.WATER)) {
						BlockState blockState2 = serverLevel.getBlockState(blockPos2.below());
						if (blockState2.is(BlockTags.CORAL_BLOCKS)) {
							serverLevel.setBlock(blockPos2, Blocks.SEA_PICKLE.defaultBlockState().setValue(PICKLES, randomSource.nextInt(4) + 1), 3);
						}
					}
				}
			}

			if (l < 2) {
				j += 2;
				n++;
			} else {
				j -= 2;
				n--;
			}

			l++;
		}

		serverLevel.setBlock(blockPos, blockState.setValue(PICKLES, 4), 2);
	}

	@Override
	protected boolean isPathfindable(BlockState blockState, PathComputationType pathComputationType) {
		return false;
	}
}
