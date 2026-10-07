package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public interface LevelWriter {
	boolean setBlock(BlockPos blockPos, BlockState blockState, @Block.UpdateFlags int i, int j);

	default boolean setBlock(BlockPos blockPos, BlockState blockState, @Block.UpdateFlags int i) {
		return this.setBlock(blockPos, blockState, i, 512);
	}

	boolean removeBlock(BlockPos blockPos, boolean bl);

	default boolean destroyBlock(BlockPos blockPos, boolean bl) {
		return this.destroyBlock(blockPos, bl, null);
	}

	default boolean destroyBlock(BlockPos blockPos, boolean bl, @Nullable Entity entity) {
		return this.destroyBlock(blockPos, bl, entity, 512);
	}

	boolean destroyBlock(BlockPos blockPos, boolean bl, @Nullable Entity entity, int i);

	default boolean addFreshEntity(Entity entity) {
		return false;
	}
}
