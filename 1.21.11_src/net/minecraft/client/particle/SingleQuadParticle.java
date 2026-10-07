package net.minecraft.client.particle;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

@Environment(EnvType.CLIENT)
public abstract class SingleQuadParticle extends Particle {
	protected float quadSize;
	protected float rCol = 1.0F;
	protected float gCol = 1.0F;
	protected float bCol = 1.0F;
	protected float alpha = 1.0F;
	protected float roll;
	protected float oRoll;
	protected TextureAtlasSprite sprite;

	protected SingleQuadParticle(ClientLevel clientLevel, double d, double e, double f, TextureAtlasSprite textureAtlasSprite) {
		super(clientLevel, d, e, f);
		this.sprite = textureAtlasSprite;
		this.quadSize = 0.1F * (this.random.nextFloat() * 0.5F + 0.5F) * 2.0F;
	}

	protected SingleQuadParticle(ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, TextureAtlasSprite textureAtlasSprite) {
		super(clientLevel, d, e, f, g, h, i);
		this.sprite = textureAtlasSprite;
		this.quadSize = 0.1F * (this.random.nextFloat() * 0.5F + 0.5F) * 2.0F;
	}

	public SingleQuadParticle.FacingCameraMode getFacingCameraMode() {
		return SingleQuadParticle.FacingCameraMode.LOOKAT_XYZ;
	}

	public void extract(QuadParticleRenderState quadParticleRenderState, Camera camera, float f) {
		Quaternionf quaternionf = new Quaternionf();
		this.getFacingCameraMode().setRotation(quaternionf, camera, f);
		if (this.roll != 0.0F) {
			quaternionf.rotateZ(Mth.lerp(f, this.oRoll, this.roll));
		}

		this.extractRotatedQuad(quadParticleRenderState, camera, quaternionf, f);
	}

	protected void extractRotatedQuad(QuadParticleRenderState quadParticleRenderState, Camera camera, Quaternionf quaternionf, float f) {
		Vec3 vec3 = camera.position();
		float g = (float)(Mth.lerp(f, this.xo, this.x) - vec3.x());
		float h = (float)(Mth.lerp(f, this.yo, this.y) - vec3.y());
		float i = (float)(Mth.lerp(f, this.zo, this.z) - vec3.z());
		this.extractRotatedQuad(quadParticleRenderState, quaternionf, g, h, i, f);
	}

	protected void extractRotatedQuad(QuadParticleRenderState quadParticleRenderState, Quaternionf quaternionf, float f, float g, float h, float i) {
		quadParticleRenderState.add(
			this.getLayer(),
			f,
			g,
			h,
			quaternionf.x,
			quaternionf.y,
			quaternionf.z,
			quaternionf.w,
			this.getQuadSize(i),
			this.getU0(),
			this.getU1(),
			this.getV0(),
			this.getV1(),
			ARGB.colorFromFloat(this.alpha, this.rCol, this.gCol, this.bCol),
			this.getLightColor(i)
		);
	}

	public float getQuadSize(float f) {
		return this.quadSize;
	}

	@Override
	public Particle scale(float f) {
		this.quadSize *= f;
		return super.scale(f);
	}

	@Override
	public ParticleRenderType getGroup() {
		return ParticleRenderType.SINGLE_QUADS;
	}

	public void setSpriteFromAge(SpriteSet spriteSet) {
		if (!this.removed) {
			this.setSprite(spriteSet.get(this.age, this.lifetime));
		}
	}

	protected void setSprite(TextureAtlasSprite textureAtlasSprite) {
		this.sprite = textureAtlasSprite;
	}

	protected float getU0() {
		return this.sprite.getU0();
	}

	protected float getU1() {
		return this.sprite.getU1();
	}

	protected float getV0() {
		return this.sprite.getV0();
	}

	protected float getV1() {
		return this.sprite.getV1();
	}

	protected abstract SingleQuadParticle.Layer getLayer();

	public void setColor(float f, float g, float h) {
		this.rCol = f;
		this.gCol = g;
		this.bCol = h;
	}

	protected void setAlpha(float f) {
		this.alpha = f;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName()
			+ ", Pos ("
			+ this.x
			+ ","
			+ this.y
			+ ","
			+ this.z
			+ "), RGBA ("
			+ this.rCol
			+ ","
			+ this.gCol
			+ ","
			+ this.bCol
			+ ","
			+ this.alpha
			+ "), Age "
			+ this.age;
	}

	@Environment(EnvType.CLIENT)
	public interface FacingCameraMode {
		SingleQuadParticle.FacingCameraMode LOOKAT_XYZ = (quaternionf, camera, f) -> quaternionf.set(camera.rotation());
		SingleQuadParticle.FacingCameraMode LOOKAT_Y = (quaternionf, camera, f) -> quaternionf.set(0.0F, camera.rotation().y, 0.0F, camera.rotation().w);

		void setRotation(Quaternionf quaternionf, Camera camera, float f);
	}

	@Environment(EnvType.CLIENT)
	public record Layer(boolean translucent, Identifier textureAtlasLocation, RenderPipeline pipeline) {
		public static final SingleQuadParticle.Layer TERRAIN = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_BLOCKS, RenderPipelines.TRANSLUCENT_PARTICLE);
		public static final SingleQuadParticle.Layer ITEMS = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_ITEMS, RenderPipelines.TRANSLUCENT_PARTICLE);
		public static final SingleQuadParticle.Layer OPAQUE = new SingleQuadParticle.Layer(false, TextureAtlas.LOCATION_PARTICLES, RenderPipelines.OPAQUE_PARTICLE);
		public static final SingleQuadParticle.Layer TRANSLUCENT = new SingleQuadParticle.Layer(
			true, TextureAtlas.LOCATION_PARTICLES, RenderPipelines.TRANSLUCENT_PARTICLE
		);
	}
}
