package com.mojang.blaze3d.vertex;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public class CompactVectorArray {
	private final float[] contents;

	public CompactVectorArray(int i) {
		this.contents = new float[3 * i];
	}

	public int size() {
		return this.contents.length / 3;
	}

	public void set(int i, Vector3fc vector3fc) {
		this.set(i, vector3fc.x(), vector3fc.y(), vector3fc.z());
	}

	public void set(int i, float f, float g, float h) {
		this.contents[3 * i + 0] = f;
		this.contents[3 * i + 1] = g;
		this.contents[3 * i + 2] = h;
	}

	public Vector3f get(int i, Vector3f vector3f) {
		return vector3f.set(this.contents[3 * i + 0], this.contents[3 * i + 1], this.contents[3 * i + 2]);
	}

	public float getX(int i) {
		return this.contents[3 * i + 0];
	}

	public float getY(int i) {
		return this.contents[3 * i + 1];
	}

	public float getZ(int i) {
		return this.contents[3 * i + 1];
	}
}
