package net.minecraft.network;

import com.mojang.datafixers.DataFixUtils;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public interface HashedStack {
	HashedStack EMPTY = new HashedStack() {
		@Override
		public String toString() {
			return "<empty>";
		}

		@Override
		public boolean matches(ItemStack itemStack, HashedPatchMap.HashGenerator hashGenerator) {
			return itemStack.isEmpty();
		}
	};
	StreamCodec<RegistryFriendlyByteBuf, HashedStack> STREAM_CODEC = ByteBufCodecs.optional(HashedStack.ActualItem.STREAM_CODEC)
		.map(
			optional -> DataFixUtils.orElse((Optional<? extends HashedStack>)optional, EMPTY),
			hashedStack -> hashedStack instanceof HashedStack.ActualItem actualItem ? Optional.of(actualItem) : Optional.empty()
		);

	boolean matches(ItemStack itemStack, HashedPatchMap.HashGenerator hashGenerator);

	static HashedStack create(ItemStack itemStack, HashedPatchMap.HashGenerator hashGenerator) {
		return itemStack.isEmpty()
			? EMPTY
			: new HashedStack.ActualItem(itemStack.getItemHolder(), itemStack.getCount(), HashedPatchMap.create(itemStack.getComponentsPatch(), hashGenerator));
	}

	record ActualItem(Holder<Item> item, int count, HashedPatchMap components) implements HashedStack {
		public static final StreamCodec<RegistryFriendlyByteBuf, HashedStack.ActualItem> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.holderRegistry(Registries.ITEM),
			HashedStack.ActualItem::item,
			ByteBufCodecs.VAR_INT,
			HashedStack.ActualItem::count,
			HashedPatchMap.STREAM_CODEC,
			HashedStack.ActualItem::components,
			HashedStack.ActualItem::new
		);

		@Override
		public boolean matches(ItemStack itemStack, HashedPatchMap.HashGenerator hashGenerator) {
			if (this.count != itemStack.getCount()) {
				return false;
			} else {
				return !this.item.equals(itemStack.getItemHolder()) ? false : this.components.matches(itemStack.getComponentsPatch(), hashGenerator);
			}
		}
	}
}
