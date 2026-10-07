package net.minecraft.client.color.item;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record CustomModelDataSource(int index, int defaultColor) implements ItemTintSource {
	public static final MapCodec<CustomModelDataSource> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("index", 0).forGetter(CustomModelDataSource::index),
				ExtraCodecs.RGB_COLOR_CODEC.fieldOf("default").forGetter(CustomModelDataSource::defaultColor)
			)
			.apply(instance, CustomModelDataSource::new)
	);

	@Override
	public int calculate(ItemStack itemStack, @Nullable ClientLevel clientLevel, @Nullable LivingEntity livingEntity) {
		CustomModelData customModelData = itemStack.get(DataComponents.CUSTOM_MODEL_DATA);
		if (customModelData != null) {
			Integer integer = customModelData.getColor(this.index);
			if (integer != null) {
				return ARGB.opaque(integer);
			}
		}

		return ARGB.opaque(this.defaultColor);
	}

	@Override
	public MapCodec<CustomModelDataSource> type() {
		return MAP_CODEC;
	}
}
