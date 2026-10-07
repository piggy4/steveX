package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public class HumanoidRenderState extends ArmedEntityRenderState {
	public float swimAmount;
	public float speedValue = 1.0F;
	public float maxCrossbowChargeDuration;
	public float ticksUsingItem;
	public HumanoidArm attackArm = HumanoidArm.RIGHT;
	public InteractionHand useItemHand = InteractionHand.MAIN_HAND;
	public boolean isCrouching;
	public boolean isFallFlying;
	public boolean isVisuallySwimming;
	public boolean isPassenger;
	public boolean isUsingItem;
	public float elytraRotX;
	public float elytraRotY;
	public float elytraRotZ;
	public ItemStack headEquipment = ItemStack.EMPTY;
	public ItemStack chestEquipment = ItemStack.EMPTY;
	public ItemStack legsEquipment = ItemStack.EMPTY;
	public ItemStack feetEquipment = ItemStack.EMPTY;

	@Override
	public float ticksUsingItem(HumanoidArm humanoidArm) {
		return this.isUsingItem && this.useItemHand == InteractionHand.MAIN_HAND == (humanoidArm == this.mainArm) ? this.ticksUsingItem : 0.0F;
	}
}
