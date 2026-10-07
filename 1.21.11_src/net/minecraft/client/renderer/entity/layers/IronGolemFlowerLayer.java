package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.golem.IronGolemModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;

@Environment(EnvType.CLIENT)
public class IronGolemFlowerLayer extends RenderLayer<IronGolemRenderState, IronGolemModel> {
	public IronGolemFlowerLayer(RenderLayerParent<IronGolemRenderState, IronGolemModel> renderLayerParent) {
		super(renderLayerParent);
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, IronGolemRenderState ironGolemRenderState, float f, float g) {
		if (ironGolemRenderState.offerFlowerTick != 0) {
			poseStack.pushPose();
			ModelPart modelPart = this.getParentModel().getFlowerHoldingArm();
			modelPart.translateAndRotate(poseStack);
			poseStack.translate(-1.1875F, 1.0625F, -0.9375F);
			poseStack.translate(0.5F, 0.5F, 0.5F);
			float h = 0.5F;
			poseStack.scale(0.5F, 0.5F, 0.5F);
			poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
			poseStack.translate(-0.5F, -0.5F, -0.5F);
			submitNodeCollector.submitBlock(poseStack, Blocks.POPPY.defaultBlockState(), i, OverlayTexture.NO_OVERLAY, ironGolemRenderState.outlineColor);
			poseStack.popPose();
		}
	}
}
