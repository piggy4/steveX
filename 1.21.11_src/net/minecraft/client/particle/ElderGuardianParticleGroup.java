package net.minecraft.client.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.state.ParticleGroupRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;

@Environment(EnvType.CLIENT)
public class ElderGuardianParticleGroup extends ParticleGroup<ElderGuardianParticle> {
	public ElderGuardianParticleGroup(ParticleEngine particleEngine) {
		super(particleEngine);
	}

	@Override
	public ParticleGroupRenderState extractRenderState(Frustum frustum, Camera camera, float f) {
		return new ElderGuardianParticleGroup.State(
			this.particles
				.stream()
				.map(elderGuardianParticle -> ElderGuardianParticleGroup.ElderGuardianParticleRenderState.fromParticle(elderGuardianParticle, camera, f))
				.toList()
		);
	}

	@Environment(EnvType.CLIENT)
	record ElderGuardianParticleRenderState(Model<Unit> model, PoseStack poseStack, RenderType renderType, int color) {

		public static ElderGuardianParticleGroup.ElderGuardianParticleRenderState fromParticle(ElderGuardianParticle elderGuardianParticle, Camera camera, float f) {
			float g = (elderGuardianParticle.age + f) / elderGuardianParticle.lifetime;
			float h = 0.05F + 0.5F * Mth.sin(g * (float) Math.PI);
			int i = ARGB.colorFromFloat(h, 1.0F, 1.0F, 1.0F);
			PoseStack poseStack = new PoseStack();
			poseStack.pushPose();
			poseStack.mulPose(camera.rotation());
			poseStack.mulPose(Axis.XP.rotationDegrees(60.0F - 150.0F * g));
			float j = 0.42553192F;
			poseStack.scale(0.42553192F, -0.42553192F, -0.42553192F);
			poseStack.translate(0.0F, -0.56F, 3.5F);
			return new ElderGuardianParticleGroup.ElderGuardianParticleRenderState(elderGuardianParticle.model, poseStack, elderGuardianParticle.renderType, i);
		}
	}

	@Environment(EnvType.CLIENT)
	record State(List<ElderGuardianParticleGroup.ElderGuardianParticleRenderState> states) implements ParticleGroupRenderState {
		@Override
		public void submit(SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
			for (ElderGuardianParticleGroup.ElderGuardianParticleRenderState elderGuardianParticleRenderState : this.states) {
				submitNodeCollector.submitModel(
					elderGuardianParticleRenderState.model,
					Unit.INSTANCE,
					elderGuardianParticleRenderState.poseStack,
					elderGuardianParticleRenderState.renderType,
					15728880,
					OverlayTexture.NO_OVERLAY,
					elderGuardianParticleRenderState.color,
					null,
					0,
					null
				);
			}
		}
	}
}
