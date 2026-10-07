package net.minecraft.client.renderer.item.properties.numeric;

import com.mojang.serialization.MapCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class CrossbowPull implements RangeSelectItemModelProperty {
	public static final MapCodec<CrossbowPull> MAP_CODEC = MapCodec.unit(new CrossbowPull());

	@Override
	public float get(ItemStack itemStack, @Nullable ClientLevel clientLevel, @Nullable ItemOwner itemOwner, int i) {
		LivingEntity livingEntity = itemOwner == null ? null : itemOwner.asLivingEntity();
		if (livingEntity == null) {
			return 0.0F;
		}

		if (CrossbowItem.isCharged(itemStack)) {
			return 0.0F;
		}

		int j = CrossbowItem.getChargeDuration(itemStack, livingEntity);
		return (float)UseDuration.useDuration(itemStack, livingEntity) / j;
	}

	@Override
	public MapCodec<CrossbowPull> type() {
		return MAP_CODEC;
	}
}
