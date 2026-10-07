package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.monster.witch.WitchModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.WitchRenderState;

@Environment(EnvType.CLIENT)
public class WitchItemLayer extends CrossedArmsItemLayer<WitchRenderState, WitchModel> {
	public WitchItemLayer(RenderLayerParent<WitchRenderState, WitchModel> renderLayerParent) {
		super(renderLayerParent);
	}

	protected void applyTranslation(WitchRenderState witchRenderState, PoseStack poseStack) {
		if (witchRenderState.isHoldingPotion) {
			this.getParentModel().root().translateAndRotate(poseStack);
			this.getParentModel().translateToHead(poseStack);
			this.getParentModel().getNose().translateAndRotate(poseStack);
			poseStack.translate(0.0625F, 0.25F, 0.0F);
			poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
			poseStack.mulPose(Axis.XP.rotationDegrees(140.0F));
			poseStack.mulPose(Axis.ZP.rotationDegrees(10.0F));
			poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
		} else {
			super.applyTranslation(witchRenderState, poseStack);
		}
	}
}
