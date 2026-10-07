package net.minecraft.world.attribute;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

public record AmbientParticle(ParticleOptions particle, float probability) {
	public static final Codec<AmbientParticle> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				ParticleTypes.CODEC.fieldOf("particle").forGetter(ambientParticle -> ambientParticle.particle),
				Codec.floatRange(0.0F, 1.0F).fieldOf("probability").forGetter(ambientParticle -> ambientParticle.probability)
			)
			.apply(instance, AmbientParticle::new)
	);

	public boolean canSpawn(RandomSource randomSource) {
		return randomSource.nextFloat() <= this.probability;
	}

	public static List<AmbientParticle> of(ParticleOptions particleOptions, float f) {
		return List.of(new AmbientParticle(particleOptions, f));
	}
}
