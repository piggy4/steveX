package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwingAnimationType;

@Environment(EnvType.CLIENT)
public class ArmedEntityRenderState extends LivingEntityRenderState {
	public HumanoidArm mainArm = HumanoidArm.RIGHT;
	public HumanoidModel.ArmPose rightArmPose = HumanoidModel.ArmPose.EMPTY;
	public final ItemStackRenderState rightHandItemState = new ItemStackRenderState();
	public ItemStack rightHandItemStack = ItemStack.EMPTY;
	public HumanoidModel.ArmPose leftArmPose = HumanoidModel.ArmPose.EMPTY;
	public final ItemStackRenderState leftHandItemState = new ItemStackRenderState();
	public ItemStack leftHandItemStack = ItemStack.EMPTY;
	public SwingAnimationType swingAnimationType = SwingAnimationType.WHACK;
	public float attackTime;

	public ItemStackRenderState getMainHandItemState() {
		return this.mainArm == HumanoidArm.RIGHT ? this.rightHandItemState : this.leftHandItemState;
	}

	public ItemStack getMainHandItemStack() {
		return this.mainArm == HumanoidArm.RIGHT ? this.rightHandItemStack : this.leftHandItemStack;
	}

	public ItemStack getUseItemStackForArm(HumanoidArm humanoidArm) {
		return humanoidArm == HumanoidArm.RIGHT ? this.rightHandItemStack : this.leftHandItemStack;
	}

	public float ticksUsingItem(HumanoidArm humanoidArm) {
		return 0.0F;
	}

	public static void extractArmedEntityRenderState(
		LivingEntity livingEntity, ArmedEntityRenderState armedEntityRenderState, ItemModelResolver itemModelResolver, float f
	) {
		armedEntityRenderState.mainArm = livingEntity.getMainArm();
		ItemStack itemStack = livingEntity.getMainHandItem();
		armedEntityRenderState.swingAnimationType = itemStack.getSwingAnimation().type();
		armedEntityRenderState.attackTime = livingEntity.getAttackAnim(f);
		itemModelResolver.updateForLiving(
			armedEntityRenderState.rightHandItemState, livingEntity.getItemHeldByArm(HumanoidArm.RIGHT), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, livingEntity
		);
		itemModelResolver.updateForLiving(
			armedEntityRenderState.leftHandItemState, livingEntity.getItemHeldByArm(HumanoidArm.LEFT), ItemDisplayContext.THIRD_PERSON_LEFT_HAND, livingEntity
		);
		armedEntityRenderState.leftHandItemStack = livingEntity.getItemHeldByArm(HumanoidArm.LEFT).copy();
		armedEntityRenderState.rightHandItemStack = livingEntity.getItemHeldByArm(HumanoidArm.RIGHT).copy();
	}
}
