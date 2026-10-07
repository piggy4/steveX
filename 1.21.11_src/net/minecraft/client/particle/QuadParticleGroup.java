package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.ParticleGroupRenderState;
import net.minecraft.client.renderer.state.QuadParticleRenderState;

@Environment(EnvType.CLIENT)
public class QuadParticleGroup extends ParticleGroup<SingleQuadParticle> {
	private final ParticleRenderType particleType;
	final QuadParticleRenderState particleTypeRenderState = new QuadParticleRenderState();

	public QuadParticleGroup(ParticleEngine particleEngine, ParticleRenderType particleRenderType) {
		super(particleEngine);
		this.particleType = particleRenderType;
	}

	@Override
	public ParticleGroupRenderState extractRenderState(Frustum frustum, Camera camera, float f) {
		for (SingleQuadParticle singleQuadParticle : this.particles) {
			if (frustum.pointInFrustum(singleQuadParticle.x, singleQuadParticle.y, singleQuadParticle.z)) {
				try {
					singleQuadParticle.extract(this.particleTypeRenderState, camera, f);
				} catch (Throwable throwable) {
					CrashReport crashReport = CrashReport.forThrowable(throwable, "Rendering Particle");
					CrashReportCategory crashReportCategory = crashReport.addCategory("Particle being rendered");
					crashReportCategory.setDetail("Particle", singleQuadParticle::toString);
					crashReportCategory.setDetail("Particle Type", this.particleType::toString);
					throw new ReportedException(crashReport);
				}
			}
		}

		return this.particleTypeRenderState;
	}
}
