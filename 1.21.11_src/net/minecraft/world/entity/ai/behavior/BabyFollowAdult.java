package net.minecraft.world.entity.ai.behavior;

import java.util.function.Function;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class BabyFollowAdult {
	public static OneShot<LivingEntity> create(UniformInt uniformInt, float f) {
		return create(uniformInt, livingEntity -> f, MemoryModuleType.NEAREST_VISIBLE_ADULT, false);
	}

	public static OneShot<LivingEntity> create(
		UniformInt uniformInt, Function<LivingEntity, Float> function, MemoryModuleType<? extends LivingEntity> memoryModuleType, boolean bl
	) {
		return BehaviorBuilder.create(
			instance -> instance.group(
					instance.present(memoryModuleType), instance.registered(MemoryModuleType.LOOK_TARGET), instance.absent(MemoryModuleType.WALK_TARGET)
				)
				.apply(instance, (memoryAccessor, memoryAccessor2, memoryAccessor3) -> (serverLevel, livingEntity, l) -> {
					if (!livingEntity.isBaby()) {
						return false;
					} else {
						LivingEntity livingEntity2 = instance.get(memoryAccessor);
						if (livingEntity.closerThan(livingEntity2, uniformInt.getMaxValue() + 1) && !livingEntity.closerThan(livingEntity2, uniformInt.getMinValue())) {
							WalkTarget walkTarget = new WalkTarget(new EntityTracker(livingEntity2, bl, bl), function.apply(livingEntity), uniformInt.getMinValue() - 1);
							memoryAccessor2.set(new EntityTracker(livingEntity2, true, bl));
							memoryAccessor3.set(walkTarget);
							return true;
						} else {
							return false;
						}
					}
				})
		);
	}
}
