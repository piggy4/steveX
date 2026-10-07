package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class EndCrystalRenderer extends EntityRenderer<EndCrystal, EndCrystalRenderState> {
	private static final Identifier END_CRYSTAL_LOCATION = Identifier.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entityCutoutNoCull(END_CRYSTAL_LOCATION);
	private final EndCrystalModel model;

	public EndCrystalRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.5F;
		this.model = new EndCrystalModel(context.bakeLayer(ModelLayers.END_CRYSTAL));
	}

	public void submit(
		EndCrystalRenderState endCrystalRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		poseStack.pushPose();
		poseStack.scale(2.0F, 2.0F, 2.0F);
		poseStack.translate(0.0F, -0.5F, 0.0F);
		submitNodeCollector.submitModel(
			this.model,
			endCrystalRenderState,
			poseStack,
			RENDER_TYPE,
			endCrystalRenderState.lightCoords,
			OverlayTexture.NO_OVERLAY,
			endCrystalRenderState.outlineColor,
			null
		);
		poseStack.popPose();
		Vec3 vec3 = endCrystalRenderState.beamOffset;
		if (vec3 != null) {
			float f = getY(endCrystalRenderState.ageInTicks);
			float g = (float)vec3.x;
			float h = (float)vec3.y;
			float i = (float)vec3.z;
			poseStack.translate(vec3);
			EnderDragonRenderer.submitCrystalBeams(-g, -h + f, -i, endCrystalRenderState.ageInTicks, poseStack, submitNodeCollector, endCrystalRenderState.lightCoords);
		}

		super.submit(endCrystalRenderState, poseStack, submitNodeCollector, cameraRenderState);
	}

	public static float getY(float f) {
		float g = Mth.sin(f * 0.2F) / 2.0F + 0.5F;
		g = (g * g + g) * 0.4F;
		return g - 1.4F;
	}

	public EndCrystalRenderState createRenderState() {
		return new EndCrystalRenderState();
	}

	public void extractRenderState(EndCrystal endCrystal, EndCrystalRenderState endCrystalRenderState, float f) {
		super.extractRenderState(endCrystal, endCrystalRenderState, f);
		endCrystalRenderState.ageInTicks = endCrystal.time + f;
		endCrystalRenderState.showsBottom = endCrystal.showsBottom();
		BlockPos blockPos = endCrystal.getBeamTarget();
		if (blockPos != null) {
			endCrystalRenderState.beamOffset = Vec3.atCenterOf(blockPos).subtract(endCrystal.getPosition(f));
		} else {
			endCrystalRenderState.beamOffset = null;
		}
	}

	public boolean shouldRender(EndCrystal endCrystal, Frustum frustum, double d, double e, double f) {
		return super.shouldRender(endCrystal, frustum, d, e, f) || endCrystal.getBeamTarget() != null;
	}
}
