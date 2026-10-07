package net.minecraft.data.worldgen;

import net.minecraft.util.BoundedFloatFunction;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.NoiseRouterData;

public class TerrainProvider {
	private static final float DEEP_OCEAN_CONTINENTALNESS = -0.51F;
	private static final float OCEAN_CONTINENTALNESS = -0.4F;
	private static final float PLAINS_CONTINENTALNESS = 0.1F;
	private static final float BEACH_CONTINENTALNESS = -0.15F;
	private static final BoundedFloatFunction<Float> NO_TRANSFORM = BoundedFloatFunction.IDENTITY;
	private static final BoundedFloatFunction<Float> AMPLIFIED_OFFSET = BoundedFloatFunction.createUnlimited(f -> f < 0.0F ? f : f * 2.0F);
	private static final BoundedFloatFunction<Float> AMPLIFIED_FACTOR = BoundedFloatFunction.createUnlimited(f -> 1.25F - 6.25F / (f + 5.0F));
	private static final BoundedFloatFunction<Float> AMPLIFIED_JAGGEDNESS = BoundedFloatFunction.createUnlimited(f -> f * 2.0F);

	public static <C, I extends BoundedFloatFunction<C>> CubicSpline<C, I> overworldOffset(
		I boundedFloatFunction, I boundedFloatFunction2, I boundedFloatFunction3, boolean bl
	) {
		BoundedFloatFunction<Float> boundedFloatFunction4 = bl ? AMPLIFIED_OFFSET : NO_TRANSFORM;
		CubicSpline<C, I> cubicSpline = buildErosionOffsetSpline(
			boundedFloatFunction2, boundedFloatFunction3, -0.15F, 0.0F, 0.0F, 0.1F, 0.0F, -0.03F, false, false, boundedFloatFunction4
		);
		CubicSpline<C, I> cubicSpline2 = buildErosionOffsetSpline(
			boundedFloatFunction2, boundedFloatFunction3, -0.1F, 0.03F, 0.1F, 0.1F, 0.01F, -0.03F, false, false, boundedFloatFunction4
		);
		CubicSpline<C, I> cubicSpline3 = buildErosionOffsetSpline(
			boundedFloatFunction2, boundedFloatFunction3, -0.1F, 0.03F, 0.1F, 0.7F, 0.01F, -0.03F, true, true, boundedFloatFunction4
		);
		CubicSpline<C, I> cubicSpline4 = buildErosionOffsetSpline(
			boundedFloatFunction2, boundedFloatFunction3, -0.05F, 0.03F, 0.1F, 1.0F, 0.01F, 0.01F, true, true, boundedFloatFunction4
		);
		return CubicSpline.<C, I>builder(boundedFloatFunction, boundedFloatFunction4)
			.addPoint(-1.1F, 0.044F)
			.addPoint(-1.02F, -0.2222F)
			.addPoint(-0.51F, -0.2222F)
			.addPoint(-0.44F, -0.12F)
			.addPoint(-0.18F, -0.12F)
			.addPoint(-0.16F, cubicSpline)
			.addPoint(-0.15F, cubicSpline)
			.addPoint(-0.1F, cubicSpline2)
			.addPoint(0.25F, cubicSpline3)
			.addPoint(1.0F, cubicSpline4)
			.build();
	}

	public static <C, I extends BoundedFloatFunction<C>> CubicSpline<C, I> overworldFactor(
		I boundedFloatFunction, I boundedFloatFunction2, I boundedFloatFunction3, I boundedFloatFunction4, boolean bl
	) {
		BoundedFloatFunction<Float> boundedFloatFunction5 = bl ? AMPLIFIED_FACTOR : NO_TRANSFORM;
		return CubicSpline.<C, I>builder(boundedFloatFunction, NO_TRANSFORM)
			.addPoint(-0.19F, 3.95F)
			.addPoint(-0.15F, getErosionFactor(boundedFloatFunction2, boundedFloatFunction3, boundedFloatFunction4, 6.25F, true, NO_TRANSFORM))
			.addPoint(-0.1F, getErosionFactor(boundedFloatFunction2, boundedFloatFunction3, boundedFloatFunction4, 5.47F, true, boundedFloatFunction5))
			.addPoint(0.03F, getErosionFactor(boundedFloatFunction2, boundedFloatFunction3, boundedFloatFunction4, 5.08F, true, boundedFloatFunction5))
			.addPoint(0.06F, getErosionFactor(boundedFloatFunction2, boundedFloatFunction3, boundedFloatFunction4, 4.69F, false, boundedFloatFunction5))
			.build();
	}

	public static <C, I extends BoundedFloatFunction<C>> CubicSpline<C, I> overworldJaggedness(
		I boundedFloatFunction, I boundedFloatFunction2, I boundedFloatFunction3, I boundedFloatFunction4, boolean bl
	) {
		BoundedFloatFunction<Float> boundedFloatFunction5 = bl ? AMPLIFIED_JAGGEDNESS : NO_TRANSFORM;
		float f = 0.65F;
		return CubicSpline.<C, I>builder(boundedFloatFunction, boundedFloatFunction5)
			.addPoint(-0.11F, 0.0F)
			.addPoint(
				0.03F, buildErosionJaggednessSpline(boundedFloatFunction2, boundedFloatFunction3, boundedFloatFunction4, 1.0F, 0.5F, 0.0F, 0.0F, boundedFloatFunction5)
			)
			.addPoint(
				0.65F, buildErosionJaggednessSpline(boundedFloatFunction2, boundedFloatFunction3, boundedFloatFunction4, 1.0F, 1.0F, 1.0F, 0.0F, boundedFloatFunction5)
			)
			.build();
	}

	private static <C, I extends BoundedFloatFunction<C>> CubicSpline<C, I> buildErosionJaggednessSpline(
		I boundedFloatFunction,
		I boundedFloatFunction2,
		I boundedFloatFunction3,
		float f,
		float g,
		float h,
		float i,
		BoundedFloatFunction<Float> boundedFloatFunction4
	) {
		float j = -0.5775F;
		CubicSpline<C, I> cubicSpline = buildRidgeJaggednessSpline(boundedFloatFunction2, boundedFloatFunction3, f, h, boundedFloatFunction4);
		CubicSpline<C, I> cubicSpline2 = buildRidgeJaggednessSpline(boundedFloatFunction2, boundedFloatFunction3, g, i, boundedFloatFunction4);
		return CubicSpline.<C, I>builder(boundedFloatFunction, boundedFloatFunction4)
			.addPoint(-1.0F, cubicSpline)
			.addPoint(-0.78F, cubicSpline2)
			.addPoint(-0.5775F, cubicSpline2)
			.addPoint(-0.375F, 0.0F)
			.build();
	}

	private static <C, I extends BoundedFloatFunction<C>> CubicSpline<C, I> buildRidgeJaggednessSpline(
		I boundedFloatFunction, I boundedFloatFunction2, float f, float g, BoundedFloatFunction<Float> boundedFloatFunction3
	) {
		float h = NoiseRouterData.peaksAndValleys(0.4F);
		float i = NoiseRouterData.peaksAndValleys(0.56666666F);
		float j = (h + i) / 2.0F;
		CubicSpline.Builder<C, I> builder = CubicSpline.builder(boundedFloatFunction2, boundedFloatFunction3);
		builder.addPoint(h, 0.0F);
		if (g > 0.0F) {
			builder.addPoint(j, buildWeirdnessJaggednessSpline(boundedFloatFunction, g, boundedFloatFunction3));
		} else {
			builder.addPoint(j, 0.0F);
		}

		if (f > 0.0F) {
			builder.addPoint(1.0F, buildWeirdnessJaggednessSpline(boundedFloatFunction, f, boundedFloatFunction3));
		} else {
			builder.addPoint(1.0F, 0.0F);
		}

		return builder.build();
	}

	private static <C, I extends BoundedFloatFunction<C>> CubicSpline<C, I> buildWeirdnessJaggednessSpline(
		I boundedFloatFunction, float f, BoundedFloatFunction<Float> boundedFloatFunction2
	) {
		float g = 0.63F * f;
		float h = 0.3F * f;
		return CubicSpline.<C, I>builder(boundedFloatFunction, boundedFloatFunction2).addPoint(-0.01F, g).addPoint(0.01F, h).build();
	}

	private static <C, I extends BoundedFloatFunction<C>> CubicSpline<C, I> getErosionFactor(
		I boundedFloatFunction, I boundedFloatFunction2, I boundedFloatFunction3, float f, boolean bl, BoundedFloatFunction<Float> boundedFloatFunction4
	) {
		CubicSpline<C, I> cubicSpline = CubicSpline.<C, I>builder(boundedFloatFunction2, boundedFloatFunction4).addPoint(-0.2F, 6.3F).addPoint(0.2F, f).build();
		CubicSpline.Builder<C, I> builder = CubicSpline.<C, I>builder(boundedFloatFunction, boundedFloatFunction4)
			.addPoint(-0.6F, cubicSpline)
			.addPoint(-0.5F, CubicSpline.<C, I>builder(boundedFloatFunction2, boundedFloatFunction4).addPoint(-0.05F, 6.3F).addPoint(0.05F, 2.67F).build())
			.addPoint(-0.35F, cubicSpline)
			.addPoint(-0.25F, cubicSpline)
			.addPoint(-0.1F, CubicSpline.<C, I>builder(boundedFloatFunction2, boundedFloatFunction4).addPoint(-0.05F, 2.67F).addPoint(0.05F, 6.3F).build())
			.addPoint(0.03F, cubicSpline);
		if (bl) {
			CubicSpline<C, I> cubicSpline2 = CubicSpline.<C, I>builder(boundedFloatFunction2, boundedFloatFunction4).addPoint(0.0F, f).addPoint(0.1F, 0.625F).build();
			CubicSpline<C, I> cubicSpline3 = CubicSpline.<C, I>builder(boundedFloatFunction3, boundedFloatFunction4)
				.addPoint(-0.9F, f)
				.addPoint(-0.69F, cubicSpline2)
				.build();
			builder.addPoint(0.35F, f).addPoint(0.45F, cubicSpline3).addPoint(0.55F, cubicSpline3).addPoint(0.62F, f);
		} else {
			CubicSpline<C, I> cubicSpline2 = CubicSpline.<C, I>builder(boundedFloatFunction3, boundedFloatFunction4)
				.addPoint(-0.7F, cubicSpline)
				.addPoint(-0.15F, 1.37F)
				.build();
			CubicSpline<C, I> cubicSpline3 = CubicSpline.<C, I>builder(boundedFloatFunction3, boundedFloatFunction4)
				.addPoint(0.45F, cubicSpline)
				.addPoint(0.7F, 1.56F)
				.build();
			builder.addPoint(0.05F, cubicSpline3).addPoint(0.4F, cubicSpline3).addPoint(0.45F, cubicSpline2).addPoint(0.55F, cubicSpline2).addPoint(0.58F, f);
		}

		return builder.build();
	}

	private static float calculateSlope(float f, float g, float h, float i) {
		return (g - f) / (i - h);
	}

	private static <C, I extends BoundedFloatFunction<C>> CubicSpline<C, I> buildMountainRidgeSplineWithPoints(
		I boundedFloatFunction, float f, boolean bl, BoundedFloatFunction<Float> boundedFloatFunction2
	) {
		CubicSpline.Builder<C, I> builder = CubicSpline.builder(boundedFloatFunction, boundedFloatFunction2);
		float g = -0.7F;
		float h = -1.0F;
		float i = mountainContinentalness(-1.0F, f, -0.7F);
		float j = 1.0F;
		float k = mountainContinentalness(1.0F, f, -0.7F);
		float l = calculateMountainRidgeZeroContinentalnessPoint(f);
		float m = -0.65F;
		if (-0.65F < l && l < 1.0F) {
			float n = mountainContinentalness(-0.65F, f, -0.7F);
			float o = -0.75F;
			float p = mountainContinentalness(-0.75F, f, -0.7F);
			float q = calculateSlope(i, p, -1.0F, -0.75F);
			builder.addPoint(-1.0F, i, q);
			builder.addPoint(-0.75F, p);
			builder.addPoint(-0.65F, n);
			float r = mountainContinentalness(l, f, -0.7F);
			float s = calculateSlope(r, k, l, 1.0F);
			float t = 0.01F;
			builder.addPoint(l - 0.01F, r);
			builder.addPoint(l, r, s);
			builder.addPoint(1.0F, k, s);
		} else {
			float n = calculateSlope(i, k, -1.0F, 1.0F);
			if (bl) {
				builder.addPoint(-1.0F, Math.max(0.2F, i));
				builder.addPoint(0.0F, Mth.lerp(0.5F, i, k), n);
			} else {
				builder.addPoint(-1.0F, i, n);
			}

			builder.addPoint(1.0F, k, n);
		}

		return builder.build();
	}

	private static float mountainContinentalness(float f, float g, float h) {
		float i = 1.17F;
		float j = 0.46082947F;
		float k = 1.0F - (1.0F - g) * 0.5F;
		float l = 0.5F * (1.0F - g);
		float m = (f + 1.17F) * 0.46082947F;
		float n = m * k - l;
		return f < h ? Math.max(n, -0.2222F) : Math.max(n, 0.0F);
	}

	private static float calculateMountainRidgeZeroContinentalnessPoint(float f) {
		float g = 1.17F;
		float h = 0.46082947F;
		float i = 1.0F - (1.0F - f) * 0.5F;
		float j = 0.5F * (1.0F - f);
		return j / (0.46082947F * i) - 1.17F;
	}

	public static <C, I extends BoundedFloatFunction<C>> CubicSpline<C, I> buildErosionOffsetSpline(
		I boundedFloatFunction,
		I boundedFloatFunction2,
		float f,
		float g,
		float h,
		float i,
		float j,
		float k,
		boolean bl,
		boolean bl2,
		BoundedFloatFunction<Float> boundedFloatFunction3
	) {
		float l = 0.6F;
		float m = 0.5F;
		float n = 0.5F;
		CubicSpline<C, I> cubicSpline = buildMountainRidgeSplineWithPoints(boundedFloatFunction2, Mth.lerp(i, 0.6F, 1.5F), bl2, boundedFloatFunction3);
		CubicSpline<C, I> cubicSpline2 = buildMountainRidgeSplineWithPoints(boundedFloatFunction2, Mth.lerp(i, 0.6F, 1.0F), bl2, boundedFloatFunction3);
		CubicSpline<C, I> cubicSpline3 = buildMountainRidgeSplineWithPoints(boundedFloatFunction2, i, bl2, boundedFloatFunction3);
		CubicSpline<C, I> cubicSpline4 = ridgeSpline(
			boundedFloatFunction2, f - 0.15F, 0.5F * i, Mth.lerp(0.5F, 0.5F, 0.5F) * i, 0.5F * i, 0.6F * i, 0.5F, boundedFloatFunction3
		);
		CubicSpline<C, I> cubicSpline5 = ridgeSpline(boundedFloatFunction2, f, j * i, g * i, 0.5F * i, 0.6F * i, 0.5F, boundedFloatFunction3);
		CubicSpline<C, I> cubicSpline6 = ridgeSpline(boundedFloatFunction2, f, j, j, g, h, 0.5F, boundedFloatFunction3);
		CubicSpline<C, I> cubicSpline7 = ridgeSpline(boundedFloatFunction2, f, j, j, g, h, 0.5F, boundedFloatFunction3);
		CubicSpline<C, I> cubicSpline8 = CubicSpline.<C, I>builder(boundedFloatFunction2, boundedFloatFunction3)
			.addPoint(-1.0F, f)
			.addPoint(-0.4F, cubicSpline6)
			.addPoint(0.0F, h + 0.07F)
			.build();
		CubicSpline<C, I> cubicSpline9 = ridgeSpline(boundedFloatFunction2, -0.02F, k, k, g, h, 0.0F, boundedFloatFunction3);
		CubicSpline.Builder<C, I> builder = CubicSpline.<C, I>builder(boundedFloatFunction, boundedFloatFunction3)
			.addPoint(-0.85F, cubicSpline)
			.addPoint(-0.7F, cubicSpline2)
			.addPoint(-0.4F, cubicSpline3)
			.addPoint(-0.35F, cubicSpline4)
			.addPoint(-0.1F, cubicSpline5)
			.addPoint(0.2F, cubicSpline6);
		if (bl) {
			builder.addPoint(0.4F, cubicSpline7).addPoint(0.45F, cubicSpline8).addPoint(0.55F, cubicSpline8).addPoint(0.58F, cubicSpline7);
		}

		builder.addPoint(0.7F, cubicSpline9);
		return builder.build();
	}

	private static <C, I extends BoundedFloatFunction<C>> CubicSpline<C, I> ridgeSpline(
		I boundedFloatFunction, float f, float g, float h, float i, float j, float k, BoundedFloatFunction<Float> boundedFloatFunction2
	) {
		float l = Math.max(0.5F * (g - f), k);
		float m = 5.0F * (h - g);
		return CubicSpline.<C, I>builder(boundedFloatFunction, boundedFloatFunction2)
			.addPoint(-1.0F, f, l)
			.addPoint(-0.4F, g, Math.min(l, m))
			.addPoint(0.0F, h, m)
			.addPoint(0.4F, i, 2.0F * (i - h))
			.addPoint(1.0F, j, 0.7F * (j - i))
			.build();
	}
}
