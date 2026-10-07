package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SculkChargeParticleOptions;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public class SculkChargeParticle extends SingleQuadParticle {
	private final SpriteSet sprites;

	SculkChargeParticle(ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, SpriteSet spriteSet) {
		super(clientLevel, d, e, f, g, h, i, spriteSet.first());
		this.friction = 0.96F;
		this.sprites = spriteSet;
		this.scale(1.5F);
		this.hasPhysics = false;
		this.setSpriteFromAge(spriteSet);
	}

	@Override
	public int getLightColor(float f) {
		return 240;
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}

	@Override
	public void tick() {
		super.tick();
		this.setSpriteFromAge(this.sprites);
	}

	@Environment(EnvType.CLIENT)
	public record Provider(SpriteSet sprite) implements ParticleProvider<SculkChargeParticleOptions> {
		public Particle createParticle(
			SculkChargeParticleOptions sculkChargeParticleOptions,
			ClientLevel clientLevel,
			double d,
			double e,
			double f,
			double g,
			double h,
			double i,
			RandomSource randomSource
		) {
			SculkChargeParticle sculkChargeParticle = new SculkChargeParticle(clientLevel, d, e, f, g, h, i, this.sprite);
			sculkChargeParticle.setAlpha(1.0F);
			sculkChargeParticle.setParticleSpeed(g, h, i);
			sculkChargeParticle.oRoll = sculkChargeParticleOptions.roll();
			sculkChargeParticle.roll = sculkChargeParticleOptions.roll();
			sculkChargeParticle.setLifetime(randomSource.nextInt(12) + 8);
			return sculkChargeParticle;
		}
	}
}
