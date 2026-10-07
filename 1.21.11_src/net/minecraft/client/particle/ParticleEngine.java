package net.minecraft.client.particle;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.ParticlesRenderState;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ParticleEngine {
	private static final List<ParticleRenderType> RENDER_ORDER = List.of(
		ParticleRenderType.SINGLE_QUADS, ParticleRenderType.ITEM_PICKUP, ParticleRenderType.ELDER_GUARDIANS
	);
	protected ClientLevel level;
	private final Map<ParticleRenderType, ParticleGroup<?>> particles = Maps.newIdentityHashMap();
	private final Queue<TrackingEmitter> trackingEmitters = Queues.newArrayDeque();
	private final Queue<Particle> particlesToAdd = Queues.newArrayDeque();
	private final Object2IntOpenHashMap<ParticleLimit> trackedParticleCounts = new Object2IntOpenHashMap<>();
	private final ParticleResources resourceManager;
	private final RandomSource random = RandomSource.create();

	public ParticleEngine(ClientLevel clientLevel, ParticleResources particleResources) {
		this.level = clientLevel;
		this.resourceManager = particleResources;
	}

	public void createTrackingEmitter(Entity entity, ParticleOptions particleOptions) {
		this.trackingEmitters.add(new TrackingEmitter(this.level, entity, particleOptions));
	}

	public void createTrackingEmitter(Entity entity, ParticleOptions particleOptions, int i) {
		this.trackingEmitters.add(new TrackingEmitter(this.level, entity, particleOptions, i));
	}

	public @Nullable Particle createParticle(ParticleOptions particleOptions, double d, double e, double f, double g, double h, double i) {
		Particle particle = this.makeParticle(particleOptions, d, e, f, g, h, i);
		if (particle != null) {
			this.add(particle);
			return particle;
		} else {
			return null;
		}
	}

	private <T extends ParticleOptions> @Nullable Particle makeParticle(T particleOptions, double d, double e, double f, double g, double h, double i) {
		ParticleProvider<T> particleProvider = (ParticleProvider<T>)this.resourceManager
			.getProviders()
			.get(BuiltInRegistries.PARTICLE_TYPE.getId(particleOptions.getType()));
		return particleProvider == null ? null : particleProvider.createParticle(particleOptions, this.level, d, e, f, g, h, i, this.random);
	}

	public void add(Particle particle) {
		Optional<ParticleLimit> optional = particle.getParticleLimit();
		if (optional.isPresent()) {
			if (this.hasSpaceInParticleLimit(optional.get())) {
				this.particlesToAdd.add(particle);
				this.updateCount(optional.get(), 1);
			}
		} else {
			this.particlesToAdd.add(particle);
		}
	}

	public void tick() {
		this.particles.forEach((particleRenderType, particleGroup) -> {
			Profiler.get().push(particleRenderType.name());
			particleGroup.tickParticles();
			Profiler.get().pop();
		});
		if (!this.trackingEmitters.isEmpty()) {
			List<TrackingEmitter> list = Lists.newArrayList();

			for (TrackingEmitter trackingEmitter : this.trackingEmitters) {
				trackingEmitter.tick();
				if (!trackingEmitter.isAlive()) {
					list.add(trackingEmitter);
				}
			}

			this.trackingEmitters.removeAll(list);
		}

		Particle particle;
		if (!this.particlesToAdd.isEmpty()) {
			while ((particle = this.particlesToAdd.poll()) != null) {
				this.particles.computeIfAbsent(particle.getGroup(), this::createParticleGroup).add(particle);
			}
		}
	}

	private ParticleGroup<?> createParticleGroup(ParticleRenderType particleRenderType) {
		if (particleRenderType == ParticleRenderType.ITEM_PICKUP) {
			return new ItemPickupParticleGroup(this);
		} else if (particleRenderType == ParticleRenderType.ELDER_GUARDIANS) {
			return new ElderGuardianParticleGroup(this);
		} else {
			return particleRenderType == ParticleRenderType.NO_RENDER ? new NoRenderParticleGroup(this) : new QuadParticleGroup(this, particleRenderType);
		}
	}

	protected void updateCount(ParticleLimit particleLimit, int i) {
		this.trackedParticleCounts.addTo(particleLimit, i);
	}

	public void extract(ParticlesRenderState particlesRenderState, Frustum frustum, Camera camera, float f) {
		for (ParticleRenderType particleRenderType : RENDER_ORDER) {
			ParticleGroup<?> particleGroup = this.particles.get(particleRenderType);
			if (particleGroup != null && !particleGroup.isEmpty()) {
				particlesRenderState.add(particleGroup.extractRenderState(frustum, camera, f));
			}
		}
	}

	public void setLevel(@Nullable ClientLevel clientLevel) {
		this.level = clientLevel;
		this.clearParticles();
		this.trackingEmitters.clear();
	}

	public String countParticles() {
		return String.valueOf(this.particles.values().stream().mapToInt(ParticleGroup::size).sum());
	}

	private boolean hasSpaceInParticleLimit(ParticleLimit particleLimit) {
		return this.trackedParticleCounts.getInt(particleLimit) < particleLimit.limit();
	}

	public void clearParticles() {
		this.particles.clear();
		this.particlesToAdd.clear();
		this.trackingEmitters.clear();
		this.trackedParticleCounts.clear();
	}
}
