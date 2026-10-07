package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public class HappyGhastRenderState extends LivingEntityRenderState {
	public ItemStack bodyItem = ItemStack.EMPTY;
	public boolean isRidden;
	public boolean isLeashHolder;
}
