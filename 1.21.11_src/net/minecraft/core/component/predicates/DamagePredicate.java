package net.minecraft.core.component.predicates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;

public record DamagePredicate(MinMaxBounds.Ints durability, MinMaxBounds.Ints damage) implements DataComponentPredicate {
	public static final Codec<DamagePredicate> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				MinMaxBounds.Ints.CODEC.optionalFieldOf("durability", MinMaxBounds.Ints.ANY).forGetter(DamagePredicate::durability),
				MinMaxBounds.Ints.CODEC.optionalFieldOf("damage", MinMaxBounds.Ints.ANY).forGetter(DamagePredicate::damage)
			)
			.apply(instance, DamagePredicate::new)
	);

	@Override
	public boolean matches(DataComponentGetter dataComponentGetter) {
		Integer integer = dataComponentGetter.get(DataComponents.DAMAGE);
		if (integer == null) {
			return false;
		}

		int i = dataComponentGetter.getOrDefault(DataComponents.MAX_DAMAGE, 0);
		return !this.durability.matches(i - integer) ? false : this.damage.matches(integer);
	}

	public static DamagePredicate durability(MinMaxBounds.Ints ints) {
		return new DamagePredicate(ints, MinMaxBounds.Ints.ANY);
	}
}
