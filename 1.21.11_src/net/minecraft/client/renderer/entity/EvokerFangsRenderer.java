package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.effects.EvokerFangsModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EvokerFangsRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.EvokerFangs;

@Environment(EnvType.CLIENT)
public class EvokerFangsRenderer extends EntityRenderer<EvokerFangs, EvokerFangsRenderState> {
	private static final Identifier TEXTURE_LOCATION = Identifier.withDefaultNamespace("textures/entity/illager/evoker_fangs.png");
	private final EvokerFangsModel model;

	public EvokerFangsRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new EvokerFangsModel(context.bakeLayer(ModelLayers.EVOKER_FANGS));
	}

	public void submit(
		EvokerFangsRenderState evokerFangsRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		float f = evokerFangsRenderState.biteProgress;
		if (f != 0.0F) {
			poseStack.pushPose();
			poseStack.mulPose(Axis.YP.rotationDegrees(90.0F - evokerFangsRenderState.yRot));
			poseStack.scale(-1.0F, -1.0F, 1.0F);
			poseStack.translate(0.0F, -1.501F, 0.0F);
			submitNodeCollector.submitModel(
				this.model,
				evokerFangsRenderState,
				poseStack,
				this.model.renderType(TEXTURE_LOCATION),
				evokerFangsRenderState.lightCoords,
				OverlayTexture.NO_OVERLAY,
				evokerFangsRenderState.outlineColor,
				null
			);
			poseStack.popPose();
			super.submit(evokerFangsRenderState, poseStack, submitNodeCollector, cameraRenderState);
		}
	}

	public EvokerFangsRenderState createRenderState() {
		return new EvokerFangsRenderState();
	}

	public void extractRenderState(EvokerFangs evokerFangs, EvokerFangsRenderState evokerFangsRenderState, float f) {
		super.extractRenderState(evokerFangs, evokerFangsRenderState, f);
		evokerFangsRenderState.yRot = evokerFangs.getYRot();
		evokerFangsRenderState.biteProgress = evokerFangs.getAnimationProgress(f);
	}
}
