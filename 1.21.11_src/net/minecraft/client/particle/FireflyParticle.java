package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public class FireflyParticle extends SingleQuadParticle {
	private static final float PARTICLE_FADE_OUT_LIGHT_TIME = 0.3F;
	private static final float PARTICLE_FADE_IN_LIGHT_TIME = 0.1F;
	private static final float PARTICLE_FADE_OUT_ALPHA_TIME = 0.5F;
	private static final float PARTICLE_FADE_IN_ALPHA_TIME = 0.3F;
	private static final int PARTICLE_MIN_LIFETIME = 200;
	private static final int PARTICLE_MAX_LIFETIME = 300;

	FireflyParticle(ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, TextureAtlasSprite textureAtlasSprite) {
		super(clientLevel, d, e, f, g, h, i, textureAtlasSprite);
		this.speedUpWhenYMotionIsBlocked = true;
		this.friction = 0.96F;
		this.quadSize *= 0.75F;
		this.yd *= 0.8F;
		this.xd *= 0.8F;
		this.zd *= 0.8F;
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}

	@Override
	public int getLightColor(float f) {
		return (int)(255.0F * getFadeAmount(this.getLifetimeProgress(this.age + f), 0.1F, 0.3F));
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level.getBlockState(BlockPos.containing(this.x, this.y, this.z)).isAir()) {
			this.remove();
		} else {
			this.setAlpha(getFadeAmount(this.getLifetimeProgress(this.age), 0.3F, 0.5F));
			if (this.random.nextFloat() > 0.95F || this.age == 1) {
				this.setParticleSpeed(-0.05F + 0.1F * this.random.nextFloat(), -0.05F + 0.1F * this.random.nextFloat(), -0.05F + 0.1F * this.random.nextFloat());
			}
		}
	}

	private float getLifetimeProgress(float f) {
		return Mth.clamp(f / this.lifetime, 0.0F, 1.0F);
	}

	private static float getFadeAmount(float f, float g, float h) {
		if (f >= 1.0F - g) {
			return (1.0F - f) / g;
		} else {
			return f <= h ? f / h : 1.0F;
		}
	}

	@Environment(EnvType.CLIENT)
	public static class FireflyProvider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprite;

		public FireflyProvider(SpriteSet spriteSet) {
			this.sprite = spriteSet;
		}

		public Particle createParticle(
			SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		) {
			FireflyParticle fireflyParticle = new FireflyParticle(
				clientLevel, d, e, f, 0.5 - randomSource.nextDouble(), randomSource.nextBoolean() ? h : -h, 0.5 - randomSource.nextDouble(), this.sprite.get(randomSource)
			);
			fireflyParticle.setLifetime(randomSource.nextIntBetweenInclusive(200, 300));
			fireflyParticle.scale(1.5F);
			fireflyParticle.setAlpha(0.0F);
			return fireflyParticle;
		}
	}
}
