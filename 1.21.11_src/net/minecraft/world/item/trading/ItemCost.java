package net.minecraft.world.item.trading;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public record ItemCost(Holder<Item> item, int count, DataComponentExactPredicate components, ItemStack itemStack) {
	public static final Codec<ItemCost> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				Item.CODEC.fieldOf("id").forGetter(ItemCost::item),
				ExtraCodecs.POSITIVE_INT.fieldOf("count").orElse(1).forGetter(ItemCost::count),
				DataComponentExactPredicate.CODEC.optionalFieldOf("components", DataComponentExactPredicate.EMPTY).forGetter(ItemCost::components)
			)
			.apply(instance, ItemCost::new)
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, ItemCost> STREAM_CODEC = StreamCodec.composite(
		Item.STREAM_CODEC, ItemCost::item, ByteBufCodecs.VAR_INT, ItemCost::count, DataComponentExactPredicate.STREAM_CODEC, ItemCost::components, ItemCost::new
	);
	public static final StreamCodec<RegistryFriendlyByteBuf, Optional<ItemCost>> OPTIONAL_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs::optional);

	public ItemCost(ItemLike itemLike) {
		this(itemLike, 1);
	}

	public ItemCost(ItemLike itemLike, int i) {
		this(itemLike.asItem().builtInRegistryHolder(), i, DataComponentExactPredicate.EMPTY);
	}

	public ItemCost(Holder<Item> holder, int i, DataComponentExactPredicate dataComponentExactPredicate) {
		this(holder, i, dataComponentExactPredicate, createStack(holder, i, dataComponentExactPredicate));
	}

	public ItemCost withComponents(UnaryOperator<DataComponentExactPredicate.Builder> unaryOperator) {
		return new ItemCost(this.item, this.count, unaryOperator.apply(DataComponentExactPredicate.builder()).build());
	}

	private static ItemStack createStack(Holder<Item> holder, int i, DataComponentExactPredicate dataComponentExactPredicate) {
		return new ItemStack(holder, i, dataComponentExactPredicate.asPatch());
	}

	public boolean test(ItemStack itemStack) {
		return itemStack.is(this.item) && this.components.test(itemStack);
	}
}
