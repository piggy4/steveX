package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import org.slf4j.Logger;

public record ConditionReference(ResourceKey<LootItemCondition> name) implements LootItemCondition {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final MapCodec<ConditionReference> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(ResourceKey.codec(Registries.PREDICATE).fieldOf("name").forGetter(ConditionReference::name))
			.apply(instance, ConditionReference::new)
	);

	@Override
	public LootItemConditionType getType() {
		return LootItemConditions.REFERENCE;
	}

	@Override
	public void validate(ValidationContext validationContext) {
		if (!validationContext.allowsReferences()) {
			validationContext.reportProblem(new ValidationContext.ReferenceNotAllowedProblem(this.name));
		} else if (validationContext.hasVisitedElement(this.name)) {
			validationContext.reportProblem(new ValidationContext.RecursiveReferenceProblem(this.name));
		} else {
			LootItemCondition.super.validate(validationContext);
			validationContext.resolver()
				.get(this.name)
				.ifPresentOrElse(
					reference -> reference.value().validate(validationContext.enterElement(new ProblemReporter.ElementReferencePathElement(this.name), this.name)),
					() -> validationContext.reportProblem(new ValidationContext.MissingReferenceProblem(this.name))
				);
		}
	}

	public boolean test(LootContext lootContext) {
		LootItemCondition lootItemCondition = lootContext.getResolver().get(this.name).map(Holder.Reference::value).orElse(null);
		if (lootItemCondition == null) {
			LOGGER.warn("Tried using unknown condition table called {}", this.name.identifier());
			return false;
		}

		LootContext.VisitedEntry<?> visitedEntry = LootContext.createVisitedEntry(lootItemCondition);
		if (lootContext.pushVisitedElement(visitedEntry)) {
			try {
				return lootItemCondition.test(lootContext);
			} finally {
				lootContext.popVisitedElement(visitedEntry);
			}
		} else {
			LOGGER.warn("Detected infinite loop in loot tables");
			return false;
		}
	}

	public static LootItemCondition.Builder conditionReference(ResourceKey<LootItemCondition> resourceKey) {
		return () -> new ConditionReference(resourceKey);
	}
}
