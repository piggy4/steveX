package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.nio.ByteBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SpriteCoordinateExpander;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

@Environment(EnvType.CLIENT)
public class TextureAtlasSprite implements AutoCloseable {
	private final Identifier atlasLocation;
	private final SpriteContents contents;
	private final int x;
	private final int y;
	private final float u0;
	private final float u1;
	private final float v0;
	private final float v1;
	private final int padding;

	protected TextureAtlasSprite(Identifier identifier, SpriteContents spriteContents, int i, int j, int k, int l, int m) {
		this.atlasLocation = identifier;
		this.contents = spriteContents;
		this.padding = m;
		this.x = k;
		this.y = l;
		this.u0 = (float)(k + m) / i;
		this.u1 = (float)(k + m + spriteContents.width()) / i;
		this.v0 = (float)(l + m) / j;
		this.v1 = (float)(l + m + spriteContents.height()) / j;
	}

	public int getX() {
		return this.x;
	}

	public int getY() {
		return this.y;
	}

	public float getU0() {
		return this.u0;
	}

	public float getU1() {
		return this.u1;
	}

	public SpriteContents contents() {
		return this.contents;
	}

	public SpriteContents.@Nullable AnimationState createAnimationState(GpuBufferSlice gpuBufferSlice, int i) {
		return this.contents.createAnimationState(gpuBufferSlice, i);
	}

	public float getU(float f) {
		float g = this.u1 - this.u0;
		return this.u0 + g * f;
	}

	public float getV0() {
		return this.v0;
	}

	public float getV1() {
		return this.v1;
	}

	public float getV(float f) {
		float g = this.v1 - this.v0;
		return this.v0 + g * f;
	}

	public Identifier atlasLocation() {
		return this.atlasLocation;
	}

	@Override
	public String toString() {
		return "TextureAtlasSprite{contents='" + this.contents + "', u0=" + this.u0 + ", u1=" + this.u1 + ", v0=" + this.v0 + ", v1=" + this.v1 + "}";
	}

	public void uploadFirstFrame(GpuTexture gpuTexture, int i) {
		this.contents.uploadFirstFrame(gpuTexture, i);
	}

	public VertexConsumer wrap(VertexConsumer vertexConsumer) {
		return new SpriteCoordinateExpander(vertexConsumer, this);
	}

	boolean isAnimated() {
		return this.contents.isAnimated();
	}

	public void uploadSpriteUbo(ByteBuffer byteBuffer, int i, int j, int k, int l, int m) {
		for (int n = 0; n <= j; n++) {
			Std140Builder.intoBuffer(MemoryUtil.memSlice(byteBuffer, i + n * m, m))
				.putMat4f(new Matrix4f().ortho2D(0.0F, k >> n, 0.0F, l >> n))
				.putMat4f(
					new Matrix4f()
						.translate(this.x >> n, this.y >> n, 0.0F)
						.scale(this.contents.width() + this.padding * 2 >> n, this.contents.height() + this.padding * 2 >> n, 1.0F)
				)
				.putFloat((float)this.padding / this.contents.width())
				.putFloat((float)this.padding / this.contents.height())
				.putInt(n);
		}
	}

	@Override
	public void close() {
		this.contents.close();
	}
}
