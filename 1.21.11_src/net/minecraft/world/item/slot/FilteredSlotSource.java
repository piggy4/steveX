package net.minecraft.world.item.slot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.criterion.ItemPredicate;

public class FilteredSlotSource extends TransformedSlotSource {
	public static final MapCodec<FilteredSlotSource> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> commonFields(instance)
			.and(ItemPredicate.CODEC.fieldOf("item_filter").forGetter(filteredSlotSource -> filteredSlotSource.filter))
			.apply(instance, FilteredSlotSource::new)
	);
	private final ItemPredicate filter;

	private FilteredSlotSource(SlotSource slotSource, ItemPredicate itemPredicate) {
		super(slotSource);
		this.filter = itemPredicate;
	}

	@Override
	public MapCodec<FilteredSlotSource> codec() {
		return MAP_CODEC;
	}

	@Override
	protected SlotCollection transform(SlotCollection slotCollection) {
		return slotCollection.filter(this.filter);
	}
}
