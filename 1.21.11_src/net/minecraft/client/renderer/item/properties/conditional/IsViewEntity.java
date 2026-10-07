package net.minecraft.client.renderer.item.properties.conditional;

import com.mojang.serialization.MapCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record IsViewEntity() implements ConditionalItemModelProperty {
	public static final MapCodec<IsViewEntity> MAP_CODEC = MapCodec.unit(new IsViewEntity());

	@Override
	public boolean get(ItemStack itemStack, @Nullable ClientLevel clientLevel, @Nullable LivingEntity livingEntity, int i, ItemDisplayContext itemDisplayContext) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.getCameraEntity();
		return entity != null ? livingEntity == entity : livingEntity == minecraft.player;
	}

	@Override
	public MapCodec<IsViewEntity> type() {
		return MAP_CODEC;
	}
}
