package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public class FlameParticle extends RisingParticle {
	FlameParticle(ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, TextureAtlasSprite textureAtlasSprite) {
		super(clientLevel, d, e, f, g, h, i, textureAtlasSprite);
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.OPAQUE;
	}

	@Override
	public void move(double d, double e, double f) {
		this.setBoundingBox(this.getBoundingBox().move(d, e, f));
		this.setLocationFromBoundingbox();
	}

	@Override
	public float getQuadSize(float f) {
		float g = (this.age + f) / this.lifetime;
		return this.quadSize * (1.0F - g * g * 0.5F);
	}

	@Override
	public int getLightColor(float f) {
		float g = (this.age + f) / this.lifetime;
		g = Mth.clamp(g, 0.0F, 1.0F);
		int i = super.getLightColor(f);
		int j = i & 0xFF;
		int k = i >> 16 & 0xFF;
		j += (int)(g * 15.0F * 16.0F);
		if (j > 240) {
			j = 240;
		}

		return j | k << 16;
	}

	@Environment(EnvType.CLIENT)
	public static class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprite;

		public Provider(SpriteSet spriteSet) {
			this.sprite = spriteSet;
		}

		public Particle createParticle(
			SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		) {
			return new FlameParticle(clientLevel, d, e, f, g, h, i, this.sprite.get(randomSource));
		}
	}

	@Environment(EnvType.CLIENT)
	public static class SmallFlameProvider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprite;

		public SmallFlameProvider(SpriteSet spriteSet) {
			this.sprite = spriteSet;
		}

		public Particle createParticle(
			SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		) {
			FlameParticle flameParticle = new FlameParticle(clientLevel, d, e, f, g, h, i, this.sprite.get(randomSource));
			flameParticle.scale(0.5F);
			return flameParticle;
		}
	}
}
