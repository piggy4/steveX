package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TallGrassBlock extends VegetationBlock implements BonemealableBlock {
	public static final MapCodec<TallGrassBlock> CODEC = simpleCodec(TallGrassBlock::new);
	private static final VoxelShape SHAPE = Block.column(12.0, 0.0, 13.0);

	@Override
	public MapCodec<TallGrassBlock> codec() {
		return CODEC;
	}

	public TallGrassBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return SHAPE;
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader levelReader, BlockPos blockPos, BlockState blockState) {
		return getGrownBlock(blockState).defaultBlockState().canSurvive(levelReader, blockPos) && levelReader.isEmptyBlock(blockPos.above());
	}

	@Override
	public boolean isBonemealSuccess(Level level, RandomSource randomSource, BlockPos blockPos, BlockState blockState) {
		return true;
	}

	@Override
	public void performBonemeal(ServerLevel serverLevel, RandomSource randomSource, BlockPos blockPos, BlockState blockState) {
		DoublePlantBlock.placeAt(serverLevel, getGrownBlock(blockState).defaultBlockState(), blockPos, 2);
	}

	private static DoublePlantBlock getGrownBlock(BlockState blockState) {
		return (DoublePlantBlock)(blockState.is(Blocks.FERN) ? Blocks.LARGE_FERN : Blocks.TALL_GRASS);
	}
}
