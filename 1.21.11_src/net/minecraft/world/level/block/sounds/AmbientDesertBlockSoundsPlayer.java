package net.minecraft.world.level.block.sounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public class AmbientDesertBlockSoundsPlayer {
	private static final int IDLE_SOUND_CHANCE = 2100;
	private static final int DRY_GRASS_SOUND_CHANCE = 200;
	private static final int DEAD_BUSH_SOUND_CHANCE = 130;
	private static final int DEAD_BUSH_SOUND_BADLANDS_DECREASED_CHANCE = 3;
	private static final int SURROUNDING_BLOCKS_PLAY_SOUND_THRESHOLD = 3;
	private static final int SURROUNDING_BLOCKS_DISTANCE_HORIZONTAL_CHECK = 8;
	private static final int SURROUNDING_BLOCKS_DISTANCE_VERTICAL_CHECK = 5;
	private static final int HORIZONTAL_DIRECTIONS = 4;

	public static void playAmbientSandSounds(Level level, BlockPos blockPos, RandomSource randomSource) {
		if (level.getBlockState(blockPos.above()).is(Blocks.AIR)) {
			if (randomSource.nextInt(2100) == 0 && shouldPlayAmbientSandSound(level, blockPos)) {
				level.playLocalSound(blockPos.getX(), blockPos.getY(), blockPos.getZ(), SoundEvents.SAND_IDLE, SoundSource.AMBIENT, 1.0F, 1.0F, false);
			}
		}
	}

	public static void playAmbientDryGrassSounds(Level level, BlockPos blockPos, RandomSource randomSource) {
		if (randomSource.nextInt(200) == 0 && shouldPlayDesertDryVegetationBlockSounds(level, blockPos.below())) {
			level.playPlayerSound(SoundEvents.DRY_GRASS, SoundSource.AMBIENT, 1.0F, 1.0F);
		}
	}

	public static void playAmbientDeadBushSounds(Level level, BlockPos blockPos, RandomSource randomSource) {
		if (randomSource.nextInt(130) == 0) {
			BlockState blockState = level.getBlockState(blockPos.below());
			if ((blockState.is(Blocks.RED_SAND) || blockState.is(BlockTags.TERRACOTTA)) && randomSource.nextInt(3) != 0) {
				return;
			}

			if (shouldPlayDesertDryVegetationBlockSounds(level, blockPos.below())) {
				level.playLocalSound(blockPos.getX(), blockPos.getY(), blockPos.getZ(), SoundEvents.DEAD_BUSH_IDLE, SoundSource.AMBIENT, 1.0F, 1.0F, false);
			}
		}
	}

	public static boolean shouldPlayDesertDryVegetationBlockSounds(Level level, BlockPos blockPos) {
		return level.getBlockState(blockPos).is(BlockTags.TRIGGERS_AMBIENT_DESERT_DRY_VEGETATION_BLOCK_SOUNDS)
			&& level.getBlockState(blockPos.below()).is(BlockTags.TRIGGERS_AMBIENT_DESERT_DRY_VEGETATION_BLOCK_SOUNDS);
	}

	private static boolean shouldPlayAmbientSandSound(Level level, BlockPos blockPos) {
		int i = 0;
		int j = 0;
		BlockPos.MutableBlockPos mutableBlockPos = blockPos.mutable();

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			mutableBlockPos.set(blockPos).move(direction, 8);
			if (columnContainsTriggeringBlock(level, mutableBlockPos) && i++ >= 3) {
				return true;
			}

			j++;
			int k = 4 - j;
			int l = k + i;
			boolean bl = l >= 3;
			if (!bl) {
				return false;
			}
		}

		return false;
	}

	private static boolean columnContainsTriggeringBlock(Level level, BlockPos.MutableBlockPos mutableBlockPos) {
		int i = level.getHeight(Heightmap.Types.WORLD_SURFACE, mutableBlockPos) - 1;
		if (Math.abs(i - mutableBlockPos.getY()) > 5) {
			mutableBlockPos.move(Direction.UP, 6);
			BlockState blockState = level.getBlockState(mutableBlockPos);
			mutableBlockPos.move(Direction.DOWN);

			for (int j = 0; j < 10; j++) {
				BlockState blockState2 = level.getBlockState(mutableBlockPos);
				if (blockState.isAir() && canTriggerAmbientDesertSandSounds(blockState2)) {
					return true;
				}

				blockState = blockState2;
				mutableBlockPos.move(Direction.DOWN);
			}

			return false;
		} else {
			boolean bl = level.getBlockState(mutableBlockPos.setY(i + 1)).isAir();
			return bl && canTriggerAmbientDesertSandSounds(level.getBlockState(mutableBlockPos.setY(i)));
		}
	}

	private static boolean canTriggerAmbientDesertSandSounds(BlockState blockState) {
		return blockState.is(BlockTags.TRIGGERS_AMBIENT_DESERT_SAND_BLOCK_SOUNDS);
	}
}
