package net.minecraft.world.level.block;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

public interface BonemealableBlock {
	boolean isValidBonemealTarget(LevelReader levelReader, BlockPos blockPos, BlockState blockState);

	boolean isBonemealSuccess(Level level, RandomSource randomSource, BlockPos blockPos, BlockState blockState);

	void performBonemeal(ServerLevel serverLevel, RandomSource randomSource, BlockPos blockPos, BlockState blockState);

	static boolean hasSpreadableNeighbourPos(LevelReader levelReader, BlockPos blockPos, BlockState blockState) {
		return getSpreadableNeighbourPos(Direction.Plane.HORIZONTAL.stream().toList(), levelReader, blockPos, blockState).isPresent();
	}

	static Optional<BlockPos> findSpreadableNeighbourPos(Level level, BlockPos blockPos, BlockState blockState) {
		return getSpreadableNeighbourPos(Direction.Plane.HORIZONTAL.shuffledCopy(level.random), level, blockPos, blockState);
	}

	private static Optional<BlockPos> getSpreadableNeighbourPos(List<Direction> list, LevelReader levelReader, BlockPos blockPos, BlockState blockState) {
		for (Direction direction : list) {
			BlockPos blockPos2 = blockPos.relative(direction);
			if (levelReader.isEmptyBlock(blockPos2) && blockState.canSurvive(levelReader, blockPos2)) {
				return Optional.of(blockPos2);
			}
		}

		return Optional.empty();
	}

	default BlockPos getParticlePos(BlockPos blockPos) {
		return switch (this.getType()) {
			case NEIGHBOR_SPREADER -> blockPos.above();
			case GROWER -> blockPos;
		};
	}

	default BonemealableBlock.Type getType() {
		return BonemealableBlock.Type.GROWER;
	}

	enum Type {
		NEIGHBOR_SPREADER,
		GROWER;
	}
}
