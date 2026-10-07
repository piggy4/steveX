package net.minecraft.client.renderer.item.properties.numeric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record UseDuration(boolean remaining) implements RangeSelectItemModelProperty {
	public static final MapCodec<UseDuration> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(Codec.BOOL.optionalFieldOf("remaining", false).forGetter(UseDuration::remaining)).apply(instance, UseDuration::new)
	);

	@Override
	public float get(ItemStack itemStack, @Nullable ClientLevel clientLevel, @Nullable ItemOwner itemOwner, int i) {
		LivingEntity livingEntity = itemOwner == null ? null : itemOwner.asLivingEntity();
		if (livingEntity != null && livingEntity.getUseItem() == itemStack) {
			return this.remaining ? livingEntity.getUseItemRemainingTicks() : useDuration(itemStack, livingEntity);
		} else {
			return 0.0F;
		}
	}

	@Override
	public MapCodec<UseDuration> type() {
		return MAP_CODEC;
	}

	public static int useDuration(ItemStack itemStack, LivingEntity livingEntity) {
		return itemStack.getUseDuration(livingEntity) - livingEntity.getUseItemRemainingTicks();
	}
}
