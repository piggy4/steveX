package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public class ReversePortalParticle extends PortalParticle {
	ReversePortalParticle(ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, TextureAtlasSprite textureAtlasSprite) {
		super(clientLevel, d, e, f, g, h, i, textureAtlasSprite);
		this.quadSize *= 1.5F;
		this.lifetime = (int)(this.random.nextFloat() * 2.0F) + 60;
	}

	@Override
	public float getQuadSize(float f) {
		float g = 1.0F - (this.age + f) / (this.lifetime * 1.5F);
		return this.quadSize * g;
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
			this.x = this.x + this.xd * f;
			this.y = this.y + this.yd * f;
			this.z = this.z + this.zd * f;
		}
	}

	@Environment(EnvType.CLIENT)
	public static class ReversePortalProvider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprite;

		public ReversePortalProvider(SpriteSet spriteSet) {
			this.sprite = spriteSet;
		}

		public Particle createParticle(
			SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		) {
			return new ReversePortalParticle(clientLevel, d, e, f, g, h, i, this.sprite.get(randomSource));
		}
	}
}
