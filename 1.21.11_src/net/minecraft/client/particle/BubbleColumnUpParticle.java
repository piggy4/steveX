package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public class BubbleColumnUpParticle extends SingleQuadParticle {
	BubbleColumnUpParticle(ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, TextureAtlasSprite textureAtlasSprite) {
		super(clientLevel, d, e, f, textureAtlasSprite);
		this.gravity = -0.125F;
		this.friction = 0.85F;
		this.setSize(0.02F, 0.02F);
		this.quadSize = this.quadSize * (this.random.nextFloat() * 0.6F + 0.2F);
		this.xd = g * 0.2F + (this.random.nextFloat() * 2.0F - 1.0F) * 0.02F;
		this.yd = h * 0.2F + (this.random.nextFloat() * 2.0F - 1.0F) * 0.02F;
		this.zd = i * 0.2F + (this.random.nextFloat() * 2.0F - 1.0F) * 0.02F;
		this.lifetime = (int)(40.0 / (this.random.nextFloat() * 0.8 + 0.2));
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.removed && !this.level.getFluidState(BlockPos.containing(this.x, this.y, this.z)).is(FluidTags.WATER)) {
			this.remove();
		}
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.OPAQUE;
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
			return new BubbleColumnUpParticle(clientLevel, d, e, f, g, h, i, this.sprite.get(randomSource));
		}
	}
}
