package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class TrailParticle extends SingleQuadParticle {
	private final Vec3 target;

	TrailParticle(ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, Vec3 vec3, int j, TextureAtlasSprite textureAtlasSprite) {
		super(clientLevel, d, e, f, g, h, i, textureAtlasSprite);
		j = ARGB.scaleRGB(j, 0.875F + this.random.nextFloat() * 0.25F, 0.875F + this.random.nextFloat() * 0.25F, 0.875F + this.random.nextFloat() * 0.25F);
		this.rCol = ARGB.red(j) / 255.0F;
		this.gCol = ARGB.green(j) / 255.0F;
		this.bCol = ARGB.blue(j) / 255.0F;
		this.quadSize = 0.26F;
		this.target = vec3;
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.OPAQUE;
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		if (this.age++ >= this.lifetime) {
			this.remove();
		} else {
			int i = this.lifetime - this.age;
			double d = 1.0 / i;
			this.x = Mth.lerp(d, this.x, this.target.x());
			this.y = Mth.lerp(d, this.y, this.target.y());
			this.z = Mth.lerp(d, this.z, this.target.z());
		}
	}

	@Override
	public int getLightColor(float f) {
		return 15728880;
	}

	@Environment(EnvType.CLIENT)
	public static class Provider implements ParticleProvider<TrailParticleOption> {
		private final SpriteSet sprite;

		public Provider(SpriteSet spriteSet) {
			this.sprite = spriteSet;
		}

		public Particle createParticle(
			TrailParticleOption trailParticleOption, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		) {
			TrailParticle trailParticle = new TrailParticle(
				clientLevel, d, e, f, g, h, i, trailParticleOption.target(), trailParticleOption.color(), this.sprite.get(randomSource)
			);
			trailParticle.setLifetime(trailParticleOption.duration());
			return trailParticle;
		}
	}
}
