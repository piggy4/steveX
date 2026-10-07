package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public class WaterDropParticle extends SingleQuadParticle {
	protected WaterDropParticle(ClientLevel clientLevel, double d, double e, double f, TextureAtlasSprite textureAtlasSprite) {
		super(clientLevel, d, e, f, 0.0, 0.0, 0.0, textureAtlasSprite);
		this.xd *= 0.3F;
		this.yd = this.random.nextFloat() * 0.2F + 0.1F;
		this.zd *= 0.3F;
		this.setSize(0.01F, 0.01F);
		this.gravity = 0.06F;
		this.lifetime = (int)(8.0 / (this.random.nextFloat() * 0.8 + 0.2));
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
		if (this.lifetime-- <= 0) {
			this.remove();
		} else {
			this.yd = this.yd - this.gravity;
			this.move(this.xd, this.yd, this.zd);
			this.xd *= 0.98F;
			this.yd *= 0.98F;
			this.zd *= 0.98F;
			if (this.onGround) {
				if (this.random.nextFloat() < 0.5F) {
					this.remove();
				}

				this.xd *= 0.7F;
				this.zd *= 0.7F;
			}

			BlockPos blockPos = BlockPos.containing(this.x, this.y, this.z);
			double d = Math.max(
				this.level.getBlockState(blockPos).getCollisionShape(this.level, blockPos).max(Direction.Axis.Y, this.x - blockPos.getX(), this.z - blockPos.getZ()),
				this.level.getFluidState(blockPos).getHeight(this.level, blockPos)
			);
			if (d > 0.0 && this.y < blockPos.getY() + d) {
				this.remove();
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
			return new WaterDropParticle(clientLevel, d, e, f, this.sprite.get(randomSource));
		}
	}
}
