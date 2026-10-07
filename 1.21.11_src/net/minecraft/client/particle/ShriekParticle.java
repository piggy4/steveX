package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ShriekParticleOption;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;

@Environment(EnvType.CLIENT)
public class ShriekParticle extends SingleQuadParticle {
	private static final float MAGICAL_X_ROT = 1.0472F;
	private int delay;

	ShriekParticle(ClientLevel clientLevel, double d, double e, double f, int i, TextureAtlasSprite textureAtlasSprite) {
		super(clientLevel, d, e, f, 0.0, 0.0, 0.0, textureAtlasSprite);
		this.quadSize = 0.85F;
		this.delay = i;
		this.lifetime = 30;
		this.gravity = 0.0F;
		this.xd = 0.0;
		this.yd = 0.1;
		this.zd = 0.0;
	}

	@Override
	public float getQuadSize(float f) {
		return this.quadSize * Mth.clamp((this.age + f) / this.lifetime * 0.75F, 0.0F, 1.0F);
	}

	@Override
	public void extract(QuadParticleRenderState quadParticleRenderState, Camera camera, float f) {
		if (this.delay <= 0) {
			this.alpha = 1.0F - Mth.clamp((this.age + f) / this.lifetime, 0.0F, 1.0F);
			Quaternionf quaternionf = new Quaternionf();
			quaternionf.rotationX(-1.0472F);
			this.extractRotatedQuad(quadParticleRenderState, camera, quaternionf, f);
			quaternionf.rotationYXZ((float) -Math.PI, 1.0472F, 0.0F);
			this.extractRotatedQuad(quadParticleRenderState, camera, quaternionf, f);
		}
	}

	@Override
	public int getLightColor(float f) {
		return 240;
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.TRANSLUCENT;
	}

	@Override
	public void tick() {
		if (this.delay > 0) {
			this.delay--;
		} else {
			super.tick();
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Provider implements ParticleProvider<ShriekParticleOption> {
		private final SpriteSet sprite;

		public Provider(SpriteSet spriteSet) {
			this.sprite = spriteSet;
		}

		public Particle createParticle(
			ShriekParticleOption shriekParticleOption, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource randomSource
		) {
			ShriekParticle shriekParticle = new ShriekParticle(clientLevel, d, e, f, shriekParticleOption.getDelay(), this.sprite.get(randomSource));
			shriekParticle.setAlpha(1.0F);
			return shriekParticle;
		}
	}
}
