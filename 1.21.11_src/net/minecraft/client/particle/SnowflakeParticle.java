package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public class SnowflakeParticle extends SingleQuadParticle {
	private final SpriteSet sprites;

	protected SnowflakeParticle(ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, SpriteSet spriteSet) {
		super(clientLevel, d, e, f, spriteSet.first());
		this.gravity = 0.225F;
		this.friction = 1.0F;
		this.sprites = spriteSet;
		this.xd = g + (this.random.nextFloat() * 2.0F - 1.0F) * 0.05F;
		this.yd = h + (this.random.nextFloat() * 2.0F - 1.0F) * 0.05F;
		this.zd = i + (this.random.nextFloat() * 2.0F - 1.0F) * 0.05F;
		this.quadSize = 0.1F * (this.random.nextFloat() * this.random.nextFloat() * 1.0F + 1.0F);
		this.lifetime = (int)(16.0 / (this.random.nextFloat() * 0.8 + 0.2)) + 2;
		this.setSpriteFromAge(spriteSet);
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.OPAQUE;
	}

	@Override
	public void tick() {
		super.tick();
		this.setSpriteFromAge(this.sprites);
		this.xd *= 0.95F;
		this.yd *= 0.9F;
		this.zd *= 0.95F;
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
			SnowflakeParticle snowflakeParticle = new SnowflakeParticle(clientLevel, d, e, f, g, h, i, this.sprites);
			snowflakeParticle.setColor(0.923F, 0.964F, 0.999F);
			return snowflakeParticle;
		}
	}
}
