package net.minecraft.client.particle;

import com.google.common.collect.EvictingQueue;
import java.util.Iterator;
import java.util.Queue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.ParticleGroupRenderState;

@Environment(EnvType.CLIENT)
public abstract class ParticleGroup<P extends Particle> {
	private static final int MAX_PARTICLES = 16384;
	protected final ParticleEngine engine;
	protected final Queue<P> particles = EvictingQueue.create(16384);

	public ParticleGroup(ParticleEngine particleEngine) {
		this.engine = particleEngine;
	}

	public boolean isEmpty() {
		return this.particles.isEmpty();
	}

	public void tickParticles() {
		if (!this.particles.isEmpty()) {
			Iterator<P> iterator = this.particles.iterator();

			while (iterator.hasNext()) {
				P particle = iterator.next();
				this.tickParticle(particle);
				if (!particle.isAlive()) {
					particle.getParticleLimit().ifPresent(particleLimit -> this.engine.updateCount(particleLimit, -1));
					iterator.remove();
				}
			}
		}
	}

	private void tickParticle(Particle particle) {
		try {
			particle.tick();
		} catch (Throwable throwable) {
			CrashReport crashReport = CrashReport.forThrowable(throwable, "Ticking Particle");
			CrashReportCategory crashReportCategory = crashReport.addCategory("Particle being ticked");
			crashReportCategory.setDetail("Particle", particle::toString);
			crashReportCategory.setDetail("Particle Type", particle.getGroup()::toString);
			throw new ReportedException(crashReport);
		}
	}

	public void add(Particle particle) {
		this.particles.add((P)particle);
	}

	public int size() {
		return this.particles.size();
	}

	public abstract ParticleGroupRenderState extractRenderState(Frustum frustum, Camera camera, float f);

	public Queue<P> getAll() {
		return this.particles;
	}
}
