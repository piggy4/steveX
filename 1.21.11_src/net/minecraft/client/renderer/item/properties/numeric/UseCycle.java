package net.minecraft.client.renderer.item.properties.numeric;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record UseCycle(float period) implements RangeSelectItemModelProperty {
	public static final MapCodec<UseCycle> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("period", 1.0F).forGetter(UseCycle::period)).apply(instance, UseCycle::new)
	);

	@Override
	public float get(ItemStack itemStack, @Nullable ClientLevel clientLevel, @Nullable ItemOwner itemOwner, int i) {
		LivingEntity livingEntity = itemOwner == null ? null : itemOwner.asLivingEntity();
		return livingEntity != null && livingEntity.getUseItem() == itemStack ? livingEntity.getUseItemRemainingTicks() % this.period : 0.0F;
	}

	@Override
	public MapCodec<UseCycle> type() {
		return MAP_CODEC;
	}
}
