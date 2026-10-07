package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public class EquineRenderState extends LivingEntityRenderState {
	public ItemStack saddle = ItemStack.EMPTY;
	public ItemStack bodyArmorItem = ItemStack.EMPTY;
	public boolean isRidden;
	public boolean animateTail;
	public float eatAnimation;
	public float standAnimation;
	public float feedingAnimation;
}
