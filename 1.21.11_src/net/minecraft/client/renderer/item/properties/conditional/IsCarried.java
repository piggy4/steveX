package net.minecraft.client.renderer.item.properties.conditional;

import com.mojang.serialization.MapCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record IsCarried() implements ConditionalItemModelProperty {
	public static final MapCodec<IsCarried> MAP_CODEC = MapCodec.unit(new IsCarried());

	@Override
	public boolean get(ItemStack itemStack, @Nullable ClientLevel clientLevel, @Nullable LivingEntity livingEntity, int i, ItemDisplayContext itemDisplayContext) {
		return livingEntity instanceof LocalPlayer localPlayer && localPlayer.containerMenu.getCarried() == itemStack;
	}

	@Override
	public MapCodec<IsCarried> type() {
		return MAP_CODEC;
	}
}
