package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public class StriderRenderState extends LivingEntityRenderState {
	public ItemStack saddle = ItemStack.EMPTY;
	public boolean isSuffocating;
	public boolean isRidden;
}
