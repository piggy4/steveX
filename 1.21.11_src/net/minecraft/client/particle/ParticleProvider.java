package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface ParticleProvider<T extends ParticleOptions> {
	@Nullable Particle createParticle(
		T particleOptions, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
	);

	@Environment(EnvType.CLIENT)
	interface Sprite<T extends ParticleOptions> {
		@Nullable SingleQuadParticle createParticle(
			T particleOptions, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		);
	}
}
