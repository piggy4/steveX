package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public class UndeadRenderState extends HumanoidRenderState {
	@Override
	public ItemStack getUseItemStackForArm(HumanoidArm humanoidArm) {
		return this.getMainHandItemStack();
	}
}
