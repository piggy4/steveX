package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.sounds.AmbientDesertBlockSoundsPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TallDryGrassBlock extends DryVegetationBlock implements BonemealableBlock {
	public static final MapCodec<TallDryGrassBlock> CODEC = simpleCodec(TallDryGrassBlock::new);
	private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 16.0);

	@Override
	public MapCodec<TallDryGrassBlock> codec() {
		return CODEC;
	}

	public TallDryGrassBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return SHAPE;
	}

	@Override
	public void animateTick(BlockState blockState, Level level, BlockPos blockPos, RandomSource randomSource) {
		AmbientDesertBlockSoundsPlayer.playAmbientDryGrassSounds(level, blockPos, randomSource);
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader levelReader, BlockPos blockPos, BlockState blockState) {
		return BonemealableBlock.hasSpreadableNeighbourPos(levelReader, blockPos, Blocks.SHORT_DRY_GRASS.defaultBlockState());
	}

	@Override
	public boolean isBonemealSuccess(Level level, RandomSource randomSource, BlockPos blockPos, BlockState blockState) {
		return true;
	}

	@Override
	public void performBonemeal(ServerLevel serverLevel, RandomSource randomSource, BlockPos blockPos, BlockState blockState) {
		BonemealableBlock.findSpreadableNeighbourPos(serverLevel, blockPos, Blocks.SHORT_DRY_GRASS.defaultBlockState())
			.ifPresent(blockPosx -> serverLevel.setBlockAndUpdate(blockPosx, Blocks.SHORT_DRY_GRASS.defaultBlockState()));
	}
}
