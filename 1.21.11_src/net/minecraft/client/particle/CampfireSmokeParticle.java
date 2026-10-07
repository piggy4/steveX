package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public class CampfireSmokeParticle extends SingleQuadParticle {
	CampfireSmokeParticle(ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, boolean bl, TextureAtlasSprite textureAtlasSprite) {
		super(clientLevel, d, e, f, textureAtlasSprite);
		this.scale(3.0F);
		this.setSize(0.25F, 0.25F);
		if (bl) {
			this.lifetime = this.random.nextInt(50) + 280;
		} else {
			this.lifetime = this.random.nextInt(50) + 80;
		}

		this.gravity = 3.0E-6F;
		this.xd = g;
		this.yd = h + this.random.nextFloat() / 500.0F;
		this.zd = i;
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		if (this.age++ < this.lifetime && !(this.alpha <= 0.0F)) {
			this.xd = this.xd + this.random.nextFloat() / 5000.0F * (this.random.nextBoolean() ? 1 : -1);
			this.zd = this.zd + this.random.nextFloat() / 5000.0F * (this.random.nextBoolean() ? 1 : -1);
			this.yd = this.yd - this.gravity;
			this.move(this.xd, this.yd, this.zd);
			if (this.age >= this.lifetime - 60 && this.alpha > 0.01F) {
				this.alpha -= 0.015F;
			}
		} else {
			this.remove();
		}
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}

	@Environment(EnvType.CLIENT)
	public static class CosyProvider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public CosyProvider(SpriteSet spriteSet) {
			this.sprites = spriteSet;
		}

		public Particle createParticle(
			SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		) {
			CampfireSmokeParticle campfireSmokeParticle = new CampfireSmokeParticle(clientLevel, d, e, f, g, h, i, false, this.sprites.get(randomSource));
			campfireSmokeParticle.setAlpha(0.9F);
			return campfireSmokeParticle;
		}
	}

	@Environment(EnvType.CLIENT)
	public static class SignalProvider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public SignalProvider(SpriteSet spriteSet) {
			this.sprites = spriteSet;
		}

		public Particle createParticle(
			SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		) {
			CampfireSmokeParticle campfireSmokeParticle = new CampfireSmokeParticle(clientLevel, d, e, f, g, h, i, true, this.sprites.get(randomSource));
			campfireSmokeParticle.setAlpha(0.95F);
			return campfireSmokeParticle;
		}
	}
}
