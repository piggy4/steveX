package net.minecraft.client.renderer.item.properties.select;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.PrimitiveCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record ItemBlockState(String property) implements SelectItemModelProperty<String> {
	public static final PrimitiveCodec<String> VALUE_CODEC = Codec.STRING;
	public static final SelectItemModelProperty.Type<ItemBlockState, String> TYPE = SelectItemModelProperty.Type.create(
		RecordCodecBuilder.mapCodec(
			instance -> instance.group(Codec.STRING.fieldOf("block_state_property").forGetter(ItemBlockState::property)).apply(instance, ItemBlockState::new)
		),
		VALUE_CODEC
	);

	public @Nullable String get(
		ItemStack itemStack, @Nullable ClientLevel clientLevel, @Nullable LivingEntity livingEntity, int i, ItemDisplayContext itemDisplayContext
	) {
		BlockItemStateProperties blockItemStateProperties = itemStack.get(DataComponents.BLOCK_STATE);
		return blockItemStateProperties == null ? null : blockItemStateProperties.properties().get(this.property);
	}

	@Override
	public SelectItemModelProperty.Type<ItemBlockState, String> type() {
		return TYPE;
	}

	@Override
	public Codec<String> valueCodec() {
		return VALUE_CODEC;
	}
}
