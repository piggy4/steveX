package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.fox.FoxModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.FoxRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

@Environment(EnvType.CLIENT)
public class FoxHeldItemLayer extends RenderLayer<FoxRenderState, FoxModel> {
	public FoxHeldItemLayer(RenderLayerParent<FoxRenderState, FoxModel> renderLayerParent) {
		super(renderLayerParent);
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, FoxRenderState foxRenderState, float f, float g) {
		ItemStackRenderState itemStackRenderState = foxRenderState.heldItem;
		if (!itemStackRenderState.isEmpty()) {
			boolean bl = foxRenderState.isSleeping;
			boolean bl2 = foxRenderState.isBaby;
			poseStack.pushPose();
			poseStack.translate(this.getParentModel().head.x / 16.0F, this.getParentModel().head.y / 16.0F, this.getParentModel().head.z / 16.0F);
			if (bl2) {
				float h = 0.75F;
				poseStack.scale(0.75F, 0.75F, 0.75F);
			}

			poseStack.mulPose(Axis.ZP.rotation(foxRenderState.headRollAngle));
			poseStack.mulPose(Axis.YP.rotationDegrees(f));
			poseStack.mulPose(Axis.XP.rotationDegrees(g));
			if (foxRenderState.isBaby) {
				if (bl) {
					poseStack.translate(0.4F, 0.26F, 0.15F);
				} else {
					poseStack.translate(0.06F, 0.26F, -0.5F);
				}
			} else if (bl) {
				poseStack.translate(0.46F, 0.26F, 0.22F);
			} else {
				poseStack.translate(0.06F, 0.27F, -0.5F);
			}

			poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
			if (bl) {
				poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
			}

			itemStackRenderState.submit(poseStack, submitNodeCollector, i, OverlayTexture.NO_OVERLAY, foxRenderState.outlineColor);
			poseStack.popPose();
		}
	}
}
