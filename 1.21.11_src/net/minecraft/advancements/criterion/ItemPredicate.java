package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public record ItemPredicate(Optional<HolderSet<Item>> items, MinMaxBounds.Ints count, DataComponentMatchers components) implements Predicate<ItemStack> {
	public static final Codec<ItemPredicate> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				RegistryCodecs.homogeneousList(Registries.ITEM).optionalFieldOf("items").forGetter(ItemPredicate::items),
				MinMaxBounds.Ints.CODEC.optionalFieldOf("count", MinMaxBounds.Ints.ANY).forGetter(ItemPredicate::count),
				DataComponentMatchers.CODEC.forGetter(ItemPredicate::components)
			)
			.apply(instance, ItemPredicate::new)
	);

	public boolean test(ItemStack itemStack) {
		if (this.items.isPresent() && !itemStack.is(this.items.get())) {
			return false;
		} else {
			return !this.count.matches(itemStack.getCount()) ? false : this.components.test(itemStack);
		}
	}

	public static class Builder {
		private Optional<HolderSet<Item>> items = Optional.empty();
		private MinMaxBounds.Ints count = MinMaxBounds.Ints.ANY;
		private DataComponentMatchers components = DataComponentMatchers.ANY;

		public static ItemPredicate.Builder item() {
			return new ItemPredicate.Builder();
		}

		public ItemPredicate.Builder of(HolderGetter<Item> holderGetter, ItemLike... itemLikes) {
			this.items = Optional.of(HolderSet.direct(itemLike -> itemLike.asItem().builtInRegistryHolder(), itemLikes));
			return this;
		}

		public ItemPredicate.Builder of(HolderGetter<Item> holderGetter, TagKey<Item> tagKey) {
			this.items = Optional.of(holderGetter.getOrThrow(tagKey));
			return this;
		}

		public ItemPredicate.Builder withCount(MinMaxBounds.Ints ints) {
			this.count = ints;
			return this;
		}

		public ItemPredicate.Builder withComponents(DataComponentMatchers dataComponentMatchers) {
			this.components = dataComponentMatchers;
			return this;
		}

		public ItemPredicate build() {
			return new ItemPredicate(this.items, this.count, this.components);
		}
	}
}
