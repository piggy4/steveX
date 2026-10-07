package com.mojang.blaze3d.buffers;

import com.mojang.blaze3d.DontObfuscate;
import java.nio.ByteBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Mth;
import org.joml.Matrix4fc;
import org.joml.Vector2fc;
import org.joml.Vector2ic;
import org.joml.Vector3fc;
import org.joml.Vector3ic;
import org.joml.Vector4fc;
import org.joml.Vector4ic;
import org.lwjgl.system.MemoryStack;

@Environment(EnvType.CLIENT)
@DontObfuscate
public class Std140Builder {
	private final ByteBuffer buffer;
	private final int start;

	private Std140Builder(ByteBuffer byteBuffer) {
		this.buffer = byteBuffer;
		this.start = byteBuffer.position();
	}

	public static Std140Builder intoBuffer(ByteBuffer byteBuffer) {
		return new Std140Builder(byteBuffer);
	}

	public static Std140Builder onStack(MemoryStack memoryStack, int i) {
		return new Std140Builder(memoryStack.malloc(i));
	}

	public ByteBuffer get() {
		return this.buffer.flip();
	}

	public Std140Builder align(int i) {
		int j = this.buffer.position();
		this.buffer.position(this.start + Mth.roundToward(j - this.start, i));
		return this;
	}

	public Std140Builder putFloat(float f) {
		this.align(4);
		this.buffer.putFloat(f);
		return this;
	}

	public Std140Builder putInt(int i) {
		this.align(4);
		this.buffer.putInt(i);
		return this;
	}

	public Std140Builder putVec2(float f, float g) {
		this.align(8);
		this.buffer.putFloat(f);
		this.buffer.putFloat(g);
		return this;
	}

	public Std140Builder putVec2(Vector2fc vector2fc) {
		this.align(8);
		vector2fc.get(this.buffer);
		this.buffer.position(this.buffer.position() + 8);
		return this;
	}

	public Std140Builder putIVec2(int i, int j) {
		this.align(8);
		this.buffer.putInt(i);
		this.buffer.putInt(j);
		return this;
	}

	public Std140Builder putIVec2(Vector2ic vector2ic) {
		this.align(8);
		vector2ic.get(this.buffer);
		this.buffer.position(this.buffer.position() + 8);
		return this;
	}

	public Std140Builder putVec3(float f, float g, float h) {
		this.align(16);
		this.buffer.putFloat(f);
		this.buffer.putFloat(g);
		this.buffer.putFloat(h);
		this.buffer.position(this.buffer.position() + 4);
		return this;
	}

	public Std140Builder putVec3(Vector3fc vector3fc) {
		this.align(16);
		vector3fc.get(this.buffer);
		this.buffer.position(this.buffer.position() + 16);
		return this;
	}

	public Std140Builder putIVec3(int i, int j, int k) {
		this.align(16);
		this.buffer.putInt(i);
		this.buffer.putInt(j);
		this.buffer.putInt(k);
		this.buffer.position(this.buffer.position() + 4);
		return this;
	}

	public Std140Builder putIVec3(Vector3ic vector3ic) {
		this.align(16);
		vector3ic.get(this.buffer);
		this.buffer.position(this.buffer.position() + 16);
		return this;
	}

	public Std140Builder putVec4(float f, float g, float h, float i) {
		this.align(16);
		this.buffer.putFloat(f);
		this.buffer.putFloat(g);
		this.buffer.putFloat(h);
		this.buffer.putFloat(i);
		return this;
	}

	public Std140Builder putVec4(Vector4fc vector4fc) {
		this.align(16);
		vector4fc.get(this.buffer);
		this.buffer.position(this.buffer.position() + 16);
		return this;
	}

	public Std140Builder putIVec4(int i, int j, int k, int l) {
		this.align(16);
		this.buffer.putInt(i);
		this.buffer.putInt(j);
		this.buffer.putInt(k);
		this.buffer.putInt(l);
		return this;
	}

	public Std140Builder putIVec4(Vector4ic vector4ic) {
		this.align(16);
		vector4ic.get(this.buffer);
		this.buffer.position(this.buffer.position() + 16);
		return this;
	}

	public Std140Builder putMat4f(Matrix4fc matrix4fc) {
		this.align(16);
		matrix4fc.get(this.buffer);
		this.buffer.position(this.buffer.position() + 64);
		return this;
	}
}
