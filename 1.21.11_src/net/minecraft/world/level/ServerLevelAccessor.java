package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;

public interface ServerLevelAccessor extends LevelAccessor {
	ServerLevel getLevel();

	DifficultyInstance getCurrentDifficultyAt(BlockPos blockPos);

	default void addFreshEntityWithPassengers(Entity entity) {
		entity.getSelfAndPassengers().forEach(this::addFreshEntity);
	}
}
