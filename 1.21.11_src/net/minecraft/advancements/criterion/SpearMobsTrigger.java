package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;

public class SpearMobsTrigger extends SimpleCriterionTrigger<SpearMobsTrigger.TriggerInstance> {
	@Override
	public Codec<SpearMobsTrigger.TriggerInstance> codec() {
		return SpearMobsTrigger.TriggerInstance.CODEC;
	}

	public void trigger(ServerPlayer serverPlayer, int i) {
		this.trigger(serverPlayer, triggerInstance -> triggerInstance.matches(i));
	}

	public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<Integer> count) implements SimpleCriterionTrigger.SimpleInstance {
		public static final Codec<SpearMobsTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(SpearMobsTrigger.TriggerInstance::player),
					ExtraCodecs.POSITIVE_INT.optionalFieldOf("count").forGetter(SpearMobsTrigger.TriggerInstance::count)
				)
				.apply(instance, SpearMobsTrigger.TriggerInstance::new)
		);

		public static Criterion<SpearMobsTrigger.TriggerInstance> spearMobs(int i) {
			return CriteriaTriggers.SPEAR_MOBS_TRIGGER.createCriterion(new SpearMobsTrigger.TriggerInstance(Optional.empty(), Optional.of(i)));
		}

		public boolean matches(int i) {
			return this.count.isEmpty() || i >= this.count.get();
		}
	}
}
