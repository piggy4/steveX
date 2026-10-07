package net.minecraft.client.model.geom.builders;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record UVPair(float u, float v) {
	@Override
	public String toString() {
		return "(" + this.u + "," + this.v + ")";
	}

	public static long pack(float f, float g) {
		long l = Float.floatToIntBits(f) & 4294967295L;
		long m = Float.floatToIntBits(g) & 4294967295L;
		return l << 32 | m;
	}

	public static float unpackU(long l) {
		int i = (int)(l >> 32);
		return Float.intBitsToFloat(i);
	}

	public static float unpackV(long l) {
		return Float.intBitsToFloat((int)l);
	}
}
