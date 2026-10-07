package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.BlockUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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

public class BigDripleafStemBlock extends HorizontalDirectionalBlock implements BonemealableBlock, SimpleWaterloggedBlock {
	public static final MapCodec<BigDripleafStemBlock> CODEC = simpleCodec(BigDripleafStemBlock::new);
	private static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.column(6.0, 0.0, 16.0).move(0.0, 0.0, 0.25).optimize());

	@Override
	public MapCodec<BigDripleafStemBlock> codec() {
		return CODEC;
	}

	public BigDripleafStemBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, false).setValue(FACING, Direction.NORTH));
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return SHAPES.get(blockState.getValue(FACING));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(WATERLOGGED, FACING);
	}

	@Override
	protected FluidState getFluidState(BlockState blockState) {
		return blockState.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(blockState);
	}

	@Override
	protected boolean canSurvive(BlockState blockState, LevelReader levelReader, BlockPos blockPos) {
		BlockPos blockPos2 = blockPos.below();
		BlockState blockState2 = levelReader.getBlockState(blockPos2);
		BlockState blockState3 = levelReader.getBlockState(blockPos.above());
		return (blockState2.is(this) || blockState2.is(BlockTags.BIG_DRIPLEAF_PLACEABLE)) && (blockState3.is(this) || blockState3.is(Blocks.BIG_DRIPLEAF));
	}

	protected static boolean place(LevelAccessor levelAccessor, BlockPos blockPos, FluidState fluidState, Direction direction) {
		BlockState blockState = Blocks.BIG_DRIPLEAF_STEM
			.defaultBlockState()
			.setValue(WATERLOGGED, fluidState.isSourceOfType(Fluids.WATER))
			.setValue(FACING, direction);
		return levelAccessor.setBlock(blockPos, blockState, 3);
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
		if ((direction == Direction.DOWN || direction == Direction.UP) && !blockState.canSurvive(levelReader, blockPos)) {
			scheduledTickAccess.scheduleTick(blockPos, this, 1);
		}

		if (blockState.getValue(WATERLOGGED)) {
			scheduledTickAccess.scheduleTick(blockPos, Fluids.WATER, Fluids.WATER.getTickDelay(levelReader));
		}

		return super.updateShape(blockState, levelReader, scheduledTickAccess, blockPos, direction, blockPos2, blockState2, randomSource);
	}

	@Override
	protected void tick(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, RandomSource randomSource) {
		if (!blockState.canSurvive(serverLevel, blockPos)) {
			serverLevel.destroyBlock(blockPos, true);
		}
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader levelReader, BlockPos blockPos, BlockState blockState) {
		Optional<BlockPos> optional = BlockUtil.getTopConnectedBlock(levelReader, blockPos, blockState.getBlock(), Direction.UP, Blocks.BIG_DRIPLEAF);
		if (optional.isEmpty()) {
			return false;
		}

		BlockPos blockPos2 = optional.get().above();
		BlockState blockState2 = levelReader.getBlockState(blockPos2);
		return BigDripleafBlock.canPlaceAt(levelReader, blockPos2, blockState2);
	}

	@Override
	public boolean isBonemealSuccess(Level level, RandomSource randomSource, BlockPos blockPos, BlockState blockState) {
		return true;
	}

	@Override
	public void performBonemeal(ServerLevel serverLevel, RandomSource randomSource, BlockPos blockPos, BlockState blockState) {
		Optional<BlockPos> optional = BlockUtil.getTopConnectedBlock(serverLevel, blockPos, blockState.getBlock(), Direction.UP, Blocks.BIG_DRIPLEAF);
		if (!optional.isEmpty()) {
			BlockPos blockPos2 = optional.get();
			BlockPos blockPos3 = blockPos2.above();
			Direction direction = blockState.getValue(FACING);
			place(serverLevel, blockPos2, serverLevel.getFluidState(blockPos2), direction);
			BigDripleafBlock.place(serverLevel, blockPos3, serverLevel.getFluidState(blockPos3), direction);
		}
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader levelReader, BlockPos blockPos, BlockState blockState, boolean bl) {
		return new ItemStack(Blocks.BIG_DRIPLEAF);
	}
}
