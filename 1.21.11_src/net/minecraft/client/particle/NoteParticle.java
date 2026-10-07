package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public class NoteParticle extends SingleQuadParticle {
	NoteParticle(ClientLevel clientLevel, double d, double e, double f, double g, TextureAtlasSprite textureAtlasSprite) {
		super(clientLevel, d, e, f, 0.0, 0.0, 0.0, textureAtlasSprite);
		this.friction = 0.66F;
		this.speedUpWhenYMotionIsBlocked = true;
		this.xd *= 0.01F;
		this.yd *= 0.01F;
		this.zd *= 0.01F;
		this.yd += 0.2;
		this.rCol = Math.max(0.0F, Mth.sin(((float)g + 0.0F) * (float) (Math.PI * 2)) * 0.65F + 0.35F);
		this.gCol = Math.max(0.0F, Mth.sin(((float)g + 0.33333334F) * (float) (Math.PI * 2)) * 0.65F + 0.35F);
		this.bCol = Math.max(0.0F, Mth.sin(((float)g + 0.6666667F) * (float) (Math.PI * 2)) * 0.65F + 0.35F);
		this.quadSize *= 1.5F;
		this.lifetime = 6;
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.OPAQUE;
	}

	@Override
	public float getQuadSize(float f) {
		return this.quadSize * Mth.clamp((this.age + f) / this.lifetime * 32.0F, 0.0F, 1.0F);
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
			return new NoteParticle(clientLevel, d, e, f, g, this.sprite.get(randomSource));
		}
	}
}
