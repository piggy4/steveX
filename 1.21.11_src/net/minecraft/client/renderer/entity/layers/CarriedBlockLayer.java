package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public class CarriedBlockLayer extends RenderLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
	public CarriedBlockLayer(RenderLayerParent<EndermanRenderState, EndermanModel<EndermanRenderState>> renderLayerParent) {
		super(renderLayerParent);
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, EndermanRenderState endermanRenderState, float f, float g) {
		BlockState blockState = endermanRenderState.carriedBlock;
		if (blockState != null) {
			poseStack.pushPose();
			poseStack.translate(0.0F, 0.6875F, -0.75F);
			poseStack.mulPose(Axis.XP.rotationDegrees(20.0F));
			poseStack.mulPose(Axis.YP.rotationDegrees(45.0F));
			poseStack.translate(0.25F, 0.1875F, 0.25F);
			float h = 0.5F;
			poseStack.scale(-0.5F, -0.5F, 0.5F);
			poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
			submitNodeCollector.submitBlock(poseStack, blockState, i, OverlayTexture.NO_OVERLAY, endermanRenderState.outlineColor);
			poseStack.popPose();
		}
	}
}
