package net.minecraft.client.renderer.item.properties.conditional;

import com.mojang.serialization.MapCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record ExtendedView() implements ConditionalItemModelProperty {
	public static final MapCodec<ExtendedView> MAP_CODEC = MapCodec.unit(new ExtendedView());

	@Override
	public boolean get(ItemStack itemStack, @Nullable ClientLevel clientLevel, @Nullable LivingEntity livingEntity, int i, ItemDisplayContext itemDisplayContext) {
		return itemDisplayContext == ItemDisplayContext.GUI && Minecraft.getInstance().hasShiftDown();
	}

	@Override
	public MapCodec<ExtendedView> type() {
		return MAP_CODEC;
	}
}
