package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public class PlayerItemInHandLayer<S extends AvatarRenderState, M extends EntityModel<S> & ArmedModel & HeadedModel> extends ItemInHandLayer<S, M> {
	private static final float X_ROT_MIN = (float) (-Math.PI / 6);
	private static final float X_ROT_MAX = (float) (Math.PI / 2);

	public PlayerItemInHandLayer(RenderLayerParent<S, M> renderLayerParent) {
		super(renderLayerParent);
	}

	protected void submitArmWithItem(
		S avatarRenderState,
		ItemStackRenderState itemStackRenderState,
		ItemStack itemStack,
		HumanoidArm humanoidArm,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int i
	) {
		if (!itemStackRenderState.isEmpty()) {
			InteractionHand interactionHand = humanoidArm == avatarRenderState.mainArm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
			if (avatarRenderState.isUsingItem
				&& avatarRenderState.useItemHand == interactionHand
				&& avatarRenderState.attackTime < 1.0E-5F
				&& !avatarRenderState.heldOnHead.isEmpty()) {
				this.renderItemHeldToEye(avatarRenderState, humanoidArm, poseStack, submitNodeCollector, i);
			} else {
				super.submitArmWithItem(avatarRenderState, itemStackRenderState, itemStack, humanoidArm, poseStack, submitNodeCollector, i);
			}
		}
	}

	private void renderItemHeldToEye(S avatarRenderState, HumanoidArm humanoidArm, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i) {
		poseStack.pushPose();
		this.getParentModel().root().translateAndRotate(poseStack);
		ModelPart modelPart = this.getParentModel().getHead();
		float f = modelPart.xRot;
		modelPart.xRot = Mth.clamp(modelPart.xRot, (float) (-Math.PI / 6), (float) (Math.PI / 2));
		modelPart.translateAndRotate(poseStack);
		modelPart.xRot = f;
		CustomHeadLayer.translateToHead(poseStack, CustomHeadLayer.Transforms.DEFAULT);
		boolean bl = humanoidArm == HumanoidArm.LEFT;
		poseStack.translate((bl ? -2.5F : 2.5F) / 16.0F, -0.0625F, 0.0F);
		avatarRenderState.heldOnHead.submit(poseStack, submitNodeCollector, i, OverlayTexture.NO_OVERLAY, avatarRenderState.outlineColor);
		poseStack.popPose();
	}
}
