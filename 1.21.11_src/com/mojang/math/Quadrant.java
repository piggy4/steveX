package com.mojang.math;

import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.util.Mth;

public enum Quadrant {
	R0(0, OctahedralGroup.IDENTITY, OctahedralGroup.IDENTITY, OctahedralGroup.IDENTITY),
	R90(1, OctahedralGroup.BLOCK_ROT_X_90, OctahedralGroup.BLOCK_ROT_Y_90, OctahedralGroup.BLOCK_ROT_Z_90),
	R180(2, OctahedralGroup.BLOCK_ROT_X_180, OctahedralGroup.BLOCK_ROT_Y_180, OctahedralGroup.BLOCK_ROT_Z_180),
	R270(3, OctahedralGroup.BLOCK_ROT_X_270, OctahedralGroup.BLOCK_ROT_Y_270, OctahedralGroup.BLOCK_ROT_Z_270);

	public static final Codec<Quadrant> CODEC = Codec.INT.comapFlatMap(integer -> {
		return switch (Mth.positiveModulo(integer, 360)) {
			case 0 -> DataResult.success(R0);
			case 90 -> DataResult.success(R90);
			case 180 -> DataResult.success(R180);
			case 270 -> DataResult.success(R270);
			default -> DataResult.error(() -> "Invalid rotation " + integer + " found, only 0/90/180/270 allowed");
		};
	}, quadrant -> {
		return switch (quadrant) {
			case R0 -> 0;
			case R90 -> 90;
			case R180 -> 180;
			case R270 -> 270;
		};
	});
	public final int shift;
	public final OctahedralGroup rotationX;
	public final OctahedralGroup rotationY;
	public final OctahedralGroup rotationZ;

	Quadrant(final int j, final OctahedralGroup octahedralGroup, final OctahedralGroup octahedralGroup2, final OctahedralGroup octahedralGroup3) {
		this.shift = j;
		this.rotationX = octahedralGroup;
		this.rotationY = octahedralGroup2;
		this.rotationZ = octahedralGroup3;
	}

	@Deprecated
	public static Quadrant parseJson(int i) {
		return switch (Mth.positiveModulo(i, 360)) {
			case 0 -> R0;
			case 90 -> R90;
			case 180 -> R180;
			case 270 -> R270;
			default -> throw new JsonParseException("Invalid rotation " + i + " found, only 0/90/180/270 allowed");
		};
	}

	public static OctahedralGroup fromXYAngles(Quadrant quadrant, Quadrant quadrant2) {
		return quadrant2.rotationY.compose(quadrant.rotationX);
	}

	public static OctahedralGroup fromXYZAngles(Quadrant quadrant, Quadrant quadrant2, Quadrant quadrant3) {
		return quadrant3.rotationZ.compose(quadrant2.rotationY.compose(quadrant.rotationX));
	}

	public int rotateVertexIndex(int i) {
		return (i + this.shift) % 4;
	}
}
