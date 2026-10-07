package net.minecraft.client.gui.render.pip;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.state.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class GuiEntityRenderer extends PictureInPictureRenderer<GuiEntityRenderState> {
	private final EntityRenderDispatcher entityRenderDispatcher;

	public GuiEntityRenderer(MultiBufferSource.BufferSource bufferSource, EntityRenderDispatcher entityRenderDispatcher) {
		super(bufferSource);
		this.entityRenderDispatcher = entityRenderDispatcher;
	}

	@Override
	public Class<GuiEntityRenderState> getRenderStateClass() {
		return GuiEntityRenderState.class;
	}

	protected void renderToTexture(GuiEntityRenderState guiEntityRenderState, PoseStack poseStack) {
		Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
		Vector3f vector3f = guiEntityRenderState.translation();
		poseStack.translate(vector3f.x, vector3f.y, vector3f.z);
		poseStack.mulPose(guiEntityRenderState.rotation());
		Quaternionf quaternionf = guiEntityRenderState.overrideCameraAngle();
		FeatureRenderDispatcher featureRenderDispatcher = Minecraft.getInstance().gameRenderer.getFeatureRenderDispatcher();
		CameraRenderState cameraRenderState = new CameraRenderState();
		if (quaternionf != null) {
			cameraRenderState.orientation = quaternionf.conjugate(new Quaternionf()).rotateY((float) Math.PI);
		}

		this.entityRenderDispatcher
			.submit(guiEntityRenderState.renderState(), cameraRenderState, 0.0, 0.0, 0.0, poseStack, featureRenderDispatcher.getSubmitNodeStorage());
		featureRenderDispatcher.renderAllFeatures();
	}

	@Override
	protected float getTranslateY(int i, int j) {
		return i / 2.0F;
	}

	@Override
	protected String getTextureLabel() {
		return "entity";
	}
}
