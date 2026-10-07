package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

public class AdultSensor extends Sensor<LivingEntity> {
	@Override
	public Set<MemoryModuleType<?>> requires() {
		return ImmutableSet.of(MemoryModuleType.NEAREST_VISIBLE_ADULT, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
	}

	@Override
	protected void doTick(ServerLevel serverLevel, LivingEntity livingEntity) {
		livingEntity.getBrain()
			.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
			.ifPresent(nearestVisibleLivingEntities -> this.setNearestVisibleAdult(livingEntity, nearestVisibleLivingEntities));
	}

	protected void setNearestVisibleAdult(LivingEntity livingEntity, NearestVisibleLivingEntities nearestVisibleLivingEntities) {
		Optional<LivingEntity> optional = nearestVisibleLivingEntities.findClosest(
			livingEntity2 -> livingEntity2.getType() == livingEntity.getType() && !livingEntity2.isBaby()
		);
		livingEntity.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT, optional);
	}
}
