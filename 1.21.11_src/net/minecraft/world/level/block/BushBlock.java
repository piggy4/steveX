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

public class BushBlock extends VegetationBlock implements BonemealableBlock {
	public static final MapCodec<BushBlock> CODEC = simpleCodec(BushBlock::new);
	private static final VoxelShape SHAPE = Block.column(16.0, 0.0, 13.0);

	@Override
	public MapCodec<BushBlock> codec() {
		return CODEC;
	}

	public BushBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
		return SHAPE;
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader levelReader, BlockPos blockPos, BlockState blockState) {
		return BonemealableBlock.hasSpreadableNeighbourPos(levelReader, blockPos, blockState);
	}

	@Override
	public boolean isBonemealSuccess(Level level, RandomSource randomSource, BlockPos blockPos, BlockState blockState) {
		return true;
	}

	@Override
	public void performBonemeal(ServerLevel serverLevel, RandomSource randomSource, BlockPos blockPos, BlockState blockState) {
		BonemealableBlock.findSpreadableNeighbourPos(serverLevel, blockPos, blockState)
			.ifPresent(blockPosx -> serverLevel.setBlockAndUpdate(blockPosx, this.defaultBlockState()));
	}
}
