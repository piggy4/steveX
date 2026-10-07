package net.minecraft.client.renderer.item.properties.conditional;

import com.mojang.serialization.MapCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record FishingRodCast() implements ConditionalItemModelProperty {
	public static final MapCodec<FishingRodCast> MAP_CODEC = MapCodec.unit(new FishingRodCast());

	@Override
	public boolean get(ItemStack itemStack, @Nullable ClientLevel clientLevel, @Nullable LivingEntity livingEntity, int i, ItemDisplayContext itemDisplayContext) {
		if (livingEntity instanceof Player player && player.fishing != null) {
			HumanoidArm humanoidArm = FishingHookRenderer.getHoldingArm(player);
			return livingEntity.getItemHeldByArm(humanoidArm) == itemStack;
		} else {
			return false;
		}
	}

	@Override
	public MapCodec<FishingRodCast> type() {
		return MAP_CODEC;
	}
}
