package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public class LavaParticle extends SingleQuadParticle {
	LavaParticle(ClientLevel clientLevel, double d, double e, double f, TextureAtlasSprite textureAtlasSprite) {
		super(clientLevel, d, e, f, 0.0, 0.0, 0.0, textureAtlasSprite);
		this.gravity = 0.75F;
		this.friction = 0.999F;
		this.xd *= 0.8F;
		this.yd *= 0.8F;
		this.zd *= 0.8F;
		this.yd = this.random.nextFloat() * 0.4F + 0.05F;
		this.quadSize = this.quadSize * (this.random.nextFloat() * 2.0F + 0.2F);
		this.lifetime = (int)(16.0 / (this.random.nextFloat() * 0.8 + 0.2));
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.OPAQUE;
	}

	@Override
	public int getLightColor(float f) {
		int i = super.getLightColor(f);
		int j = 240;
		int k = i >> 16 & 0xFF;
		return 240 | k << 16;
	}

	@Override
	public float getQuadSize(float f) {
		float g = (this.age + f) / this.lifetime;
		return this.quadSize * (1.0F - g * g);
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.removed) {
			float f = (float)this.age / this.lifetime;
			if (this.random.nextFloat() > f) {
				this.level.addParticle(ParticleTypes.SMOKE, this.x, this.y, this.z, this.xd, this.yd, this.zd);
			}
		}
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
			return new LavaParticle(clientLevel, d, e, f, this.sprite.get(randomSource));
		}
	}
}
