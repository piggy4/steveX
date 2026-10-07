package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

@Environment(EnvType.CLIENT)
public class PlayerCloudParticle extends SingleQuadParticle {
	private final SpriteSet sprites;

	PlayerCloudParticle(ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, SpriteSet spriteSet) {
		super(clientLevel, d, e, f, 0.0, 0.0, 0.0, spriteSet.first());
		this.friction = 0.96F;
		this.sprites = spriteSet;
		float j = 2.5F;
		this.xd *= 0.1F;
		this.yd *= 0.1F;
		this.zd *= 0.1F;
		this.xd += g;
		this.yd += h;
		this.zd += i;
		float k = 1.0F - this.random.nextFloat() * 0.3F;
		this.rCol = k;
		this.gCol = k;
		this.bCol = k;
		this.quadSize *= 1.875F;
		int l = (int)(8.0 / (this.random.nextFloat() * 0.8 + 0.3));
		this.lifetime = (int)Math.max(l * 2.5F, 1.0F);
		this.hasPhysics = false;
		this.setSpriteFromAge(spriteSet);
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}

	@Override
	public float getQuadSize(float f) {
		return this.quadSize * Mth.clamp((this.age + f) / this.lifetime * 32.0F, 0.0F, 1.0F);
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.removed) {
			this.setSpriteFromAge(this.sprites);
			Player player = this.level.getNearestPlayer(this.x, this.y, this.z, 2.0, false);
			if (player != null) {
				double d = player.getY();
				if (this.y > d) {
					this.y = this.y + (d - this.y) * 0.2;
					this.yd = this.yd + (player.getDeltaMovement().y - this.yd) * 0.2;
					this.setPos(this.x, this.y, this.z);
				}
			}
		}
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
			return new PlayerCloudParticle(clientLevel, d, e, f, g, h, i, this.sprites);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class SneezeProvider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public SneezeProvider(SpriteSet spriteSet) {
			this.sprites = spriteSet;
		}

		public Particle createParticle(
			SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		) {
			PlayerCloudParticle playerCloudParticle = new PlayerCloudParticle(clientLevel, d, e, f, g, h, i, this.sprites);
			playerCloudParticle.setColor(0.22F, 1.0F, 0.53F);
			playerCloudParticle.setAlpha(0.4F);
			return playerCloudParticle;
		}
	}
}
