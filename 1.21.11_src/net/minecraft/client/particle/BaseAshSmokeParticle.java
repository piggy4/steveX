package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public abstract class BaseAshSmokeParticle extends SingleQuadParticle {
	private final SpriteSet sprites;

	protected BaseAshSmokeParticle(
		ClientLevel clientLevel,
		double d,
		double e,
		double f,
		float g,
		float h,
		float i,
		double j,
		double k,
		double l,
		float m,
		SpriteSet spriteSet,
		float n,
		int o,
		float p,
		boolean bl
	) {
		super(clientLevel, d, e, f, 0.0, 0.0, 0.0, spriteSet.first());
		this.friction = 0.96F;
		this.gravity = p;
		this.speedUpWhenYMotionIsBlocked = true;
		this.sprites = spriteSet;
		this.xd *= g;
		this.yd *= h;
		this.zd *= i;
		this.xd += j;
		this.yd += k;
		this.zd += l;
		float q = this.random.nextFloat() * n;
		this.rCol = q;
		this.gCol = q;
		this.bCol = q;
		this.quadSize *= 0.75F * m;
		this.lifetime = (int)(o / (this.random.nextFloat() * 0.8 + 0.2) * m);
		this.lifetime = Math.max(this.lifetime, 1);
		this.setSpriteFromAge(spriteSet);
		this.hasPhysics = bl;
	}

	@Override
	public SingleQuadParticle.Layer getLayer() {
		return SingleQuadParticle.Layer.OPAQUE;
	}

	@Override
	public float getQuadSize(float f) {
		return this.quadSize * Mth.clamp((this.age + f) / this.lifetime * 32.0F, 0.0F, 1.0F);
	}

	@Override
	public void tick() {
		super.tick();
		this.setSpriteFromAge(this.sprites);
	}
}
