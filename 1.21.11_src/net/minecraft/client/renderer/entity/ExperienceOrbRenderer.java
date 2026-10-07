package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ExperienceOrbRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ExperienceOrb;

@Environment(EnvType.CLIENT)
public class ExperienceOrbRenderer extends EntityRenderer<ExperienceOrb, ExperienceOrbRenderState> {
	private static final Identifier EXPERIENCE_ORB_LOCATION = Identifier.withDefaultNamespace("textures/entity/experience_orb.png");
	private static final RenderType RENDER_TYPE = RenderTypes.itemEntityTranslucentCull(EXPERIENCE_ORB_LOCATION);

	public ExperienceOrbRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.15F;
		this.shadowStrength = 0.75F;
	}

	protected int getBlockLightLevel(ExperienceOrb experienceOrb, BlockPos blockPos) {
		return Mth.clamp(super.getBlockLightLevel(experienceOrb, blockPos) + 7, 0, 15);
	}

	public void submit(
		ExperienceOrbRenderState experienceOrbRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		poseStack.pushPose();
		int i = experienceOrbRenderState.icon;
		float f = (i % 4 * 16 + 0) / 64.0F;
		float g = (i % 4 * 16 + 16) / 64.0F;
		float h = (i / 4 * 16 + 0) / 64.0F;
		float j = (i / 4 * 16 + 16) / 64.0F;
		float k = 1.0F;
		float l = 0.5F;
		float m = 0.25F;
		float n = 255.0F;
		float o = experienceOrbRenderState.ageInTicks / 2.0F;
		int p = (int)((Mth.sin(o + 0.0F) + 1.0F) * 0.5F * 255.0F);
		int q = 255;
		int r = (int)((Mth.sin(o + (float) (Math.PI * 4.0 / 3.0)) + 1.0F) * 0.1F * 255.0F);
		poseStack.translate(0.0F, 0.1F, 0.0F);
		poseStack.mulPose(cameraRenderState.orientation);
		float s = 0.3F;
		poseStack.scale(0.3F, 0.3F, 0.3F);
		submitNodeCollector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, vertexConsumer) -> {
			vertex(vertexConsumer, pose, -0.5F, -0.25F, p, 255, r, f, j, experienceOrbRenderState.lightCoords);
			vertex(vertexConsumer, pose, 0.5F, -0.25F, p, 255, r, g, j, experienceOrbRenderState.lightCoords);
			vertex(vertexConsumer, pose, 0.5F, 0.75F, p, 255, r, g, h, experienceOrbRenderState.lightCoords);
			vertex(vertexConsumer, pose, -0.5F, 0.75F, p, 255, r, f, h, experienceOrbRenderState.lightCoords);
		});
		poseStack.popPose();
		super.submit(experienceOrbRenderState, poseStack, submitNodeCollector, cameraRenderState);
	}

	private static void vertex(VertexConsumer vertexConsumer, PoseStack.Pose pose, float f, float g, int i, int j, int k, float h, float l, int m) {
		vertexConsumer.addVertex(pose, f, g, 0.0F)
			.setColor(i, j, k, 128)
			.setUv(h, l)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(m)
			.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	public ExperienceOrbRenderState createRenderState() {
		return new ExperienceOrbRenderState();
	}

	public void extractRenderState(ExperienceOrb experienceOrb, ExperienceOrbRenderState experienceOrbRenderState, float f) {
		super.extractRenderState(experienceOrb, experienceOrbRenderState, f);
		experienceOrbRenderState.icon = experienceOrb.getIcon();
	}
}
