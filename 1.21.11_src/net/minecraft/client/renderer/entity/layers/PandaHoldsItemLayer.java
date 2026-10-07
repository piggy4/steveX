package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.panda.PandaModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.PandaRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public class PandaHoldsItemLayer extends RenderLayer<PandaRenderState, PandaModel> {
	public PandaHoldsItemLayer(RenderLayerParent<PandaRenderState, PandaModel> renderLayerParent) {
		super(renderLayerParent);
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, PandaRenderState pandaRenderState, float f, float g) {
		ItemStackRenderState itemStackRenderState = pandaRenderState.heldItem;
		if (!itemStackRenderState.isEmpty() && pandaRenderState.isSitting && !pandaRenderState.isScared) {
			float h = -0.6F;
			float j = 1.4F;
			if (pandaRenderState.isEating) {
				h -= 0.2F * Mth.sin(pandaRenderState.ageInTicks * 0.6F) + 0.2F;
				j -= 0.09F * Mth.sin(pandaRenderState.ageInTicks * 0.6F);
			}

			poseStack.pushPose();
			poseStack.translate(0.1F, j, h);
			itemStackRenderState.submit(poseStack, submitNodeCollector, i, OverlayTexture.NO_OVERLAY, pandaRenderState.outlineColor);
			poseStack.popPose();
		}
	}
}
