package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public class SculkChargePopParticle extends SingleQuadParticle {
	private final SpriteSet sprites;

	SculkChargePopParticle(ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, SpriteSet spriteSet) {
		super(clientLevel, d, e, f, g, h, i, spriteSet.first());
		this.friction = 0.96F;
		this.sprites = spriteSet;
		this.scale(1.0F);
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
	public record Provider(SpriteSet sprite) implements ParticleProvider<SimpleParticleType> {
		public Particle createParticle(
			SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		) {
			SculkChargePopParticle sculkChargePopParticle = new SculkChargePopParticle(clientLevel, d, e, f, g, h, i, this.sprite);
			sculkChargePopParticle.setAlpha(1.0F);
			sculkChargePopParticle.setParticleSpeed(g, h, i);
			sculkChargePopParticle.setLifetime(randomSource.nextInt(4) + 6);
			return sculkChargePopParticle;
		}
	}
}
