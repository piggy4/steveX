package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.llama.LlamaSpitModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LlamaSpitRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.LlamaSpit;

@Environment(EnvType.CLIENT)
public class LlamaSpitRenderer extends EntityRenderer<LlamaSpit, LlamaSpitRenderState> {
	private static final Identifier LLAMA_SPIT_LOCATION = Identifier.withDefaultNamespace("textures/entity/llama/spit.png");
	private final LlamaSpitModel model;

	public LlamaSpitRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new LlamaSpitModel(context.bakeLayer(ModelLayers.LLAMA_SPIT));
	}

	public void submit(
		LlamaSpitRenderState llamaSpitRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.15F, 0.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(llamaSpitRenderState.yRot - 90.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(llamaSpitRenderState.xRot));
		submitNodeCollector.submitModel(
			this.model,
			llamaSpitRenderState,
			poseStack,
			this.model.renderType(LLAMA_SPIT_LOCATION),
			llamaSpitRenderState.lightCoords,
			OverlayTexture.NO_OVERLAY,
			llamaSpitRenderState.outlineColor,
			null
		);
		poseStack.popPose();
		super.submit(llamaSpitRenderState, poseStack, submitNodeCollector, cameraRenderState);
	}

	public LlamaSpitRenderState createRenderState() {
		return new LlamaSpitRenderState();
	}

	public void extractRenderState(LlamaSpit llamaSpit, LlamaSpitRenderState llamaSpitRenderState, float f) {
		super.extractRenderState(llamaSpit, llamaSpitRenderState, f);
		llamaSpitRenderState.xRot = llamaSpit.getXRot(f);
		llamaSpitRenderState.yRot = llamaSpit.getYRot(f);
	}
}
