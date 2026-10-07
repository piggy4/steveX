package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public class BubblePopParticle extends SingleQuadParticle {
	private final SpriteSet sprites;

	BubblePopParticle(ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, SpriteSet spriteSet) {
		super(clientLevel, d, e, f, spriteSet.first());
		this.sprites = spriteSet;
		this.lifetime = 4;
		this.gravity = 0.008F;
		this.xd = g;
		this.yd = h;
		this.zd = i;
		this.setSpriteFromAge(spriteSet);
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		if (this.age++ >= this.lifetime) {
			this.remove();
		} else {
			this.yd = this.yd - this.gravity;
			this.move(this.xd, this.yd, this.zd);
			this.setSpriteFromAge(this.sprites);
		}
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.OPAQUE;
	}

	@Environment(EnvType.CLIENT)
	public static class Provider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public Provider(SpriteSet spriteSet) {
			this.sprites = spriteSet;
		}

		public Particle createParticle(
			SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		) {
			return new BubblePopParticle(clientLevel, d, e, f, g, h, i, this.sprites);
		}
	}
}
