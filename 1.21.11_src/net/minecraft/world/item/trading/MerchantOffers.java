package net.minecraft.world.item.trading;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class MerchantOffers extends ArrayList<MerchantOffer> {
	public static final Codec<MerchantOffers> CODEC = MerchantOffer.CODEC
		.listOf()
		.optionalFieldOf("Recipes", List.of())
		.xmap(MerchantOffers::new, Function.identity())
		.codec();
	public static final StreamCodec<RegistryFriendlyByteBuf, MerchantOffers> STREAM_CODEC = MerchantOffer.STREAM_CODEC
		.apply(ByteBufCodecs.collection(MerchantOffers::new));

	public MerchantOffers() {
	}

	private MerchantOffers(int i) {
		super(i);
	}

	private MerchantOffers(Collection<MerchantOffer> collection) {
		super(collection);
	}

	public @Nullable MerchantOffer getRecipeFor(ItemStack itemStack, ItemStack itemStack2, int i) {
		if (i > 0 && i < this.size()) {
			MerchantOffer merchantOffer = this.get(i);
			return merchantOffer.satisfiedBy(itemStack, itemStack2) ? merchantOffer : null;
		}

		for (int j = 0; j < this.size(); j++) {
			MerchantOffer merchantOffer2 = this.get(j);
			if (merchantOffer2.satisfiedBy(itemStack, itemStack2)) {
				return merchantOffer2;
			}
		}

		return null;
	}

	public MerchantOffers copy() {
		MerchantOffers merchantOffers = new MerchantOffers(this.size());

		for (MerchantOffer merchantOffer : this) {
			merchantOffers.add(merchantOffer.copy());
		}

		return merchantOffers;
	}
}
