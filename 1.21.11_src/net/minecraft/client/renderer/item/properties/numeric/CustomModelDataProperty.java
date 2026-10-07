package net.minecraft.client.renderer.item.properties.numeric;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record CustomModelDataProperty(int index) implements RangeSelectItemModelProperty {
	public static final MapCodec<CustomModelDataProperty> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("index", 0).forGetter(CustomModelDataProperty::index))
			.apply(instance, CustomModelDataProperty::new)
	);

	@Override
	public float get(ItemStack itemStack, @Nullable ClientLevel clientLevel, @Nullable ItemOwner itemOwner, int i) {
		CustomModelData customModelData = itemStack.get(DataComponents.CUSTOM_MODEL_DATA);
		if (customModelData != null) {
			Float float_ = customModelData.getFloat(this.index);
			if (float_ != null) {
				return float_;
			}
		}

		return 0.0F;
	}

	@Override
	public MapCodec<CustomModelDataProperty> type() {
		return MAP_CODEC;
	}
}
