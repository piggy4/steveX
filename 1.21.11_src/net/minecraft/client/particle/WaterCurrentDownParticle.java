package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public class WaterCurrentDownParticle extends SingleQuadParticle {
	private float angle;

	WaterCurrentDownParticle(ClientLevel clientLevel, double d, double e, double f, TextureAtlasSprite textureAtlasSprite) {
		super(clientLevel, d, e, f, textureAtlasSprite);
		this.lifetime = (int)(this.random.nextFloat() * 60.0F) + 30;
		this.hasPhysics = false;
		this.xd = 0.0;
		this.yd = -0.05;
		this.zd = 0.0;
		this.setSize(0.02F, 0.02F);
		this.quadSize = this.quadSize * (this.random.nextFloat() * 0.6F + 0.2F);
		this.gravity = 0.002F;
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
			float f = 0.6F;
			this.xd = this.xd + 0.6F * Mth.cos(this.angle);
			this.zd = this.zd + 0.6F * Mth.sin(this.angle);
			this.xd *= 0.07;
			this.zd *= 0.07;
			this.move(this.xd, this.yd, this.zd);
			if (!this.level.getFluidState(BlockPos.containing(this.x, this.y, this.z)).is(FluidTags.WATER) || this.onGround) {
				this.remove();
			}

			this.angle += 0.08F;
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
			return new WaterCurrentDownParticle(clientLevel, d, e, f, this.sprite.get(randomSource));
		}
	}
}
