package net.minecraft.client.particle;

import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public class SuspendedParticle extends SingleQuadParticle {
	SuspendedParticle(ClientLevel clientLevel, double d, double e, double f, TextureAtlasSprite textureAtlasSprite) {
		super(clientLevel, d, e - 0.125, f, textureAtlasSprite);
		this.setSize(0.01F, 0.01F);
		this.quadSize = this.quadSize * (this.random.nextFloat() * 0.6F + 0.2F);
		this.lifetime = (int)(16.0 / (this.random.nextFloat() * 0.8 + 0.2));
		this.hasPhysics = false;
		this.friction = 1.0F;
		this.gravity = 0.0F;
	}

	SuspendedParticle(ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, TextureAtlasSprite textureAtlasSprite) {
		super(clientLevel, d, e - 0.125, f, g, h, i, textureAtlasSprite);
		this.setSize(0.01F, 0.01F);
		this.quadSize = this.quadSize * (this.random.nextFloat() * 0.6F + 0.6F);
		this.lifetime = (int)(16.0 / (this.random.nextFloat() * 0.8 + 0.2));
		this.hasPhysics = false;
		this.friction = 1.0F;
		this.gravity = 0.0F;
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.OPAQUE;
	}

	@Environment(EnvType.CLIENT)
	public static class CrimsonSporeProvider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprite;

		public CrimsonSporeProvider(SpriteSet spriteSet) {
			this.sprite = spriteSet;
		}

		public Particle createParticle(
			SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		) {
			double j = randomSource.nextGaussian() * 1.0E-6F;
			double k = randomSource.nextGaussian() * 1.0E-4F;
			double l = randomSource.nextGaussian() * 1.0E-6F;
			SuspendedParticle suspendedParticle = new SuspendedParticle(clientLevel, d, e, f, j, k, l, this.sprite.get(randomSource));
			suspendedParticle.setColor(0.9F, 0.4F, 0.5F);
			return suspendedParticle;
		}
	}

	@Environment(EnvType.CLIENT)
	public static class SporeBlossomAirProvider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprite;

		public SporeBlossomAirProvider(SpriteSet spriteSet) {
			this.sprite = spriteSet;
		}

		public Particle createParticle(
			SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		) {
			SuspendedParticle suspendedParticle = new SuspendedParticle(clientLevel, d, e, f, 0.0, -0.8F, 0.0, this.sprite.get(randomSource)) {
				@Override
				public Optional<ParticleLimit> getParticleLimit() {
					return Optional.of(ParticleLimit.SPORE_BLOSSOM);
				}
			};
			suspendedParticle.lifetime = Mth.randomBetweenInclusive(randomSource, 500, 1000);
			suspendedParticle.gravity = 0.01F;
			suspendedParticle.setColor(0.32F, 0.5F, 0.22F);
			return suspendedParticle;
		}
	}

	@Environment(EnvType.CLIENT)
	public static class UnderwaterProvider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprite;

		public UnderwaterProvider(SpriteSet spriteSet) {
			this.sprite = spriteSet;
		}

		public Particle createParticle(
			SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		) {
			SuspendedParticle suspendedParticle = new SuspendedParticle(clientLevel, d, e, f, this.sprite.get(randomSource));
			suspendedParticle.setColor(0.4F, 0.4F, 0.7F);
			return suspendedParticle;
		}
	}

	@Environment(EnvType.CLIENT)
	public static class WarpedSporeProvider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprite;

		public WarpedSporeProvider(SpriteSet spriteSet) {
			this.sprite = spriteSet;
		}

		public Particle createParticle(
			SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		) {
			double j = randomSource.nextFloat() * -1.9 * randomSource.nextFloat() * 0.1;
			SuspendedParticle suspendedParticle = new SuspendedParticle(clientLevel, d, e, f, 0.0, j, 0.0, this.sprite.get(randomSource));
			suspendedParticle.setColor(0.1F, 0.1F, 0.3F);
			suspendedParticle.setSize(0.001F, 0.001F);
			return suspendedParticle;
		}
	}
}
