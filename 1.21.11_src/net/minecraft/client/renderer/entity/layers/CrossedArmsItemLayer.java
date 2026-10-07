package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.VillagerLikeModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HoldingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

@Environment(EnvType.CLIENT)
public class CrossedArmsItemLayer<S extends HoldingEntityRenderState, M extends EntityModel<S> & VillagerLikeModel> extends RenderLayer<S, M> {
	public CrossedArmsItemLayer(RenderLayerParent<S, M> renderLayerParent) {
		super(renderLayerParent);
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, S holdingEntityRenderState, float f, float g) {
		ItemStackRenderState itemStackRenderState = holdingEntityRenderState.heldItem;
		if (!itemStackRenderState.isEmpty()) {
			poseStack.pushPose();
			this.applyTranslation(holdingEntityRenderState, poseStack);
			itemStackRenderState.submit(poseStack, submitNodeCollector, i, OverlayTexture.NO_OVERLAY, holdingEntityRenderState.outlineColor);
			poseStack.popPose();
		}
	}

	protected void applyTranslation(S holdingEntityRenderState, PoseStack poseStack) {
		this.getParentModel().translateToArms(holdingEntityRenderState, poseStack);
		poseStack.mulPose(Axis.XP.rotation(0.75F));
		poseStack.scale(1.07F, 1.07F, 1.07F);
		poseStack.translate(0.0F, 0.13F, -0.34F);
		poseStack.mulPose(Axis.XP.rotation((float) Math.PI));
	}
}
