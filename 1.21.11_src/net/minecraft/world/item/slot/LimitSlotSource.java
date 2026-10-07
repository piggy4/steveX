package net.minecraft.world.item.slot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

public class LimitSlotSource extends TransformedSlotSource {
	public static final MapCodec<LimitSlotSource> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> commonFields(instance)
			.and(ExtraCodecs.POSITIVE_INT.fieldOf("limit").forGetter(limitSlotSource -> limitSlotSource.limit))
			.apply(instance, LimitSlotSource::new)
	);
	private final int limit;

	private LimitSlotSource(SlotSource slotSource, int i) {
		super(slotSource);
		this.limit = i;
	}

	@Override
	public MapCodec<LimitSlotSource> codec() {
		return MAP_CODEC;
	}

	@Override
	protected SlotCollection transform(SlotCollection slotCollection) {
		return slotCollection.limit(this.limit);
	}
}
