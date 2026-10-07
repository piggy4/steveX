package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public class PortalParticle extends SingleQuadParticle {
	private final double xStart;
	private final double yStart;
	private final double zStart;

	protected PortalParticle(ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, TextureAtlasSprite textureAtlasSprite) {
		super(clientLevel, d, e, f, textureAtlasSprite);
		this.xd = g;
		this.yd = h;
		this.zd = i;
		this.x = d;
		this.y = e;
		this.z = f;
		this.xStart = this.x;
		this.yStart = this.y;
		this.zStart = this.z;
		this.quadSize = 0.1F * (this.random.nextFloat() * 0.2F + 0.5F);
		float j = this.random.nextFloat() * 0.6F + 0.4F;
		this.rCol = j * 0.9F;
		this.gCol = j * 0.3F;
		this.bCol = j;
		this.lifetime = (int)(this.random.nextFloat() * 10.0F) + 40;
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
		g = 1.0F - g;
		g *= g;
		g = 1.0F - g;
		return this.quadSize * g;
	}

	@Override
	public int getLightColor(float f) {
		int i = super.getLightColor(f);
		float g = (float)this.age / this.lifetime;
		g *= g;
		g *= g;
		int j = i & 0xFF;
		int k = i >> 16 & 0xFF;
		k += (int)(g * 15.0F * 16.0F);
		if (k > 240) {
			k = 240;
		}

		return j | k << 16;
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		if (this.age++ >= this.lifetime) {
			this.remove();
		} else {
			float f = (float)this.age / this.lifetime;
			float g = f;
			f = -f + f * f * 2.0F;
			f = 1.0F - f;
			this.x = this.xStart + this.xd * f;
			this.y = this.yStart + this.yd * f + (1.0F - g);
			this.z = this.zStart + this.zd * f;
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
			return new PortalParticle(clientLevel, d, e, f, g, h, i, this.sprite.get(randomSource));
		}
	}
}
