package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.projectile.ArrowModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

@Environment(EnvType.CLIENT)
public abstract class ArrowRenderer<T extends AbstractArrow, S extends ArrowRenderState> extends EntityRenderer<T, S> {
	private final ArrowModel model;

	public ArrowRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new ArrowModel(context.bakeLayer(ModelLayers.ARROW));
	}

	public void submit(S arrowRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(arrowRenderState.yRot - 90.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(arrowRenderState.xRot));
		submitNodeCollector.submitModel(
			this.model,
			arrowRenderState,
			poseStack,
			RenderTypes.entityCutout(this.getTextureLocation(arrowRenderState)),
			arrowRenderState.lightCoords,
			OverlayTexture.NO_OVERLAY,
			arrowRenderState.outlineColor,
			null
		);
		poseStack.popPose();
		super.submit(arrowRenderState, poseStack, submitNodeCollector, cameraRenderState);
	}

	protected abstract Identifier getTextureLocation(S arrowRenderState);

	public void extractRenderState(T abstractArrow, S arrowRenderState, float f) {
		super.extractRenderState(abstractArrow, arrowRenderState, f);
		arrowRenderState.xRot = abstractArrow.getXRot(f);
		arrowRenderState.yRot = abstractArrow.getYRot(f);
		arrowRenderState.shake = abstractArrow.shakeTime - f;
	}
}
