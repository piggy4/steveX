package net.minecraft.client.renderer.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.RegistryContextSwapper;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record ClientItem(ItemModel.Unbaked model, ClientItem.Properties properties, @Nullable RegistryContextSwapper registrySwapper) {
	public static final Codec<ClientItem> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(ItemModels.CODEC.fieldOf("model").forGetter(ClientItem::model), ClientItem.Properties.MAP_CODEC.forGetter(ClientItem::properties))
			.apply(instance, ClientItem::new)
	);

	public ClientItem(ItemModel.Unbaked unbaked, ClientItem.Properties properties) {
		this(unbaked, properties, null);
	}

	public ClientItem withRegistrySwapper(RegistryContextSwapper registryContextSwapper) {
		return new ClientItem(this.model, this.properties, registryContextSwapper);
	}

	@Environment(EnvType.CLIENT)
	public record Properties(boolean handAnimationOnSwap, boolean oversizedInGui, float swapAnimationScale) {
		public static final ClientItem.Properties DEFAULT = new ClientItem.Properties(true, false, 1.0F);
		public static final MapCodec<ClientItem.Properties> MAP_CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Codec.BOOL.optionalFieldOf("hand_animation_on_swap", true).forGetter(ClientItem.Properties::handAnimationOnSwap),
					Codec.BOOL.optionalFieldOf("oversized_in_gui", false).forGetter(ClientItem.Properties::oversizedInGui),
					Codec.FLOAT.optionalFieldOf("swap_animation_scale", 1.0F).forGetter(ClientItem.Properties::swapAnimationScale)
				)
				.apply(instance, ClientItem.Properties::new)
		);
	}
}
