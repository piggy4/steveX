package net.minecraft.util;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

public interface EasingType {
	ExtraCodecs.LateBoundIdMapper<String, EasingType> SIMPLE_REGISTRY = new ExtraCodecs.LateBoundIdMapper<>();
	Codec<EasingType> CODEC = Codec.either(SIMPLE_REGISTRY.codec(Codec.STRING), EasingType.CubicBezier.CODEC)
		.xmap(Either::unwrap, easingType -> easingType instanceof EasingType.CubicBezier cubicBezier ? Either.right(cubicBezier) : Either.left(easingType));
	EasingType CONSTANT = registerSimple("constant", f -> 0.0F);
	EasingType LINEAR = registerSimple("linear", f -> f);
	EasingType IN_BACK = registerSimple("in_back", Ease::inBack);
	EasingType IN_BOUNCE = registerSimple("in_bounce", Ease::inBounce);
	EasingType IN_CIRC = registerSimple("in_circ", Ease::inCirc);
	EasingType IN_CUBIC = registerSimple("in_cubic", Ease::inCubic);
	EasingType IN_ELASTIC = registerSimple("in_elastic", Ease::inElastic);
	EasingType IN_EXPO = registerSimple("in_expo", Ease::inExpo);
	EasingType IN_QUAD = registerSimple("in_quad", Ease::inQuad);
	EasingType IN_QUART = registerSimple("in_quart", Ease::inQuart);
	EasingType IN_QUINT = registerSimple("in_quint", Ease::inQuint);
	EasingType IN_SINE = registerSimple("in_sine", Ease::inSine);
	EasingType IN_OUT_BACK = registerSimple("in_out_back", Ease::inOutBack);
	EasingType IN_OUT_BOUNCE = registerSimple("in_out_bounce", Ease::inOutBounce);
	EasingType IN_OUT_CIRC = registerSimple("in_out_circ", Ease::inOutCirc);
	EasingType IN_OUT_CUBIC = registerSimple("in_out_cubic", Ease::inOutCubic);
	EasingType IN_OUT_ELASTIC = registerSimple("in_out_elastic", Ease::inOutElastic);
	EasingType IN_OUT_EXPO = registerSimple("in_out_expo", Ease::inOutExpo);
	EasingType IN_OUT_QUAD = registerSimple("in_out_quad", Ease::inOutQuad);
	EasingType IN_OUT_QUART = registerSimple("in_out_quart", Ease::inOutQuart);
	EasingType IN_OUT_QUINT = registerSimple("in_out_quint", Ease::inOutQuint);
	EasingType IN_OUT_SINE = registerSimple("in_out_sine", Ease::inOutSine);
	EasingType OUT_BACK = registerSimple("out_back", Ease::outBack);
	EasingType OUT_BOUNCE = registerSimple("out_bounce", Ease::outBounce);
	EasingType OUT_CIRC = registerSimple("out_circ", Ease::outCirc);
	EasingType OUT_CUBIC = registerSimple("out_cubic", Ease::outCubic);
	EasingType OUT_ELASTIC = registerSimple("out_elastic", Ease::outElastic);
	EasingType OUT_EXPO = registerSimple("out_expo", Ease::outExpo);
	EasingType OUT_QUAD = registerSimple("out_quad", Ease::outQuad);
	EasingType OUT_QUART = registerSimple("out_quart", Ease::outQuart);
	EasingType OUT_QUINT = registerSimple("out_quint", Ease::outQuint);
	EasingType OUT_SINE = registerSimple("out_sine", Ease::outSine);

	static EasingType registerSimple(String string, EasingType easingType) {
		SIMPLE_REGISTRY.put(string, easingType);
		return easingType;
	}

	static EasingType cubicBezier(float f, float g, float h, float i) {
		return new EasingType.CubicBezier(new EasingType.CubicBezierControls(f, g, h, i));
	}

	static EasingType symmetricCubicBezier(float f, float g) {
		return cubicBezier(f, g, 1.0F - f, 1.0F - g);
	}

	float apply(float f);

	final class CubicBezier implements EasingType {
		public static final Codec<EasingType.CubicBezier> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(EasingType.CubicBezierControls.CODEC.fieldOf("cubic_bezier").forGetter(cubicBezier -> cubicBezier.controls))
				.apply(instance, EasingType.CubicBezier::new)
		);
		private static final int NEWTON_RAPHSON_ITERATIONS = 4;
		private final EasingType.CubicBezierControls controls;
		private final EasingType.CubicBezier.CubicCurve xCurve;
		private final EasingType.CubicBezier.CubicCurve yCurve;

		public CubicBezier(EasingType.CubicBezierControls cubicBezierControls) {
			this.controls = cubicBezierControls;
			this.xCurve = curveFromControls(cubicBezierControls.x1, cubicBezierControls.x2);
			this.yCurve = curveFromControls(cubicBezierControls.y1, cubicBezierControls.y2);
		}

		private static EasingType.CubicBezier.CubicCurve curveFromControls(float f, float g) {
			return new EasingType.CubicBezier.CubicCurve(3.0F * f - 3.0F * g + 1.0F, -6.0F * f + 3.0F * g, 3.0F * f);
		}

		@Override
		public float apply(float f) {
			float g = f;

			for (int i = 0; i < 4; i++) {
				float h = this.xCurve.sampleGradient(g);
				if (h < 1.0E-5F) {
					break;
				}

				float j = this.xCurve.sample(g) - f;
				g -= j / h;
			}

			return this.yCurve.sample(g);
		}

		@Override
		public boolean equals(Object object) {
			return object instanceof EasingType.CubicBezier cubicBezier && this.controls.equals(cubicBezier.controls);
		}

		@Override
		public int hashCode() {
			return this.controls.hashCode();
		}

		@Override
		public String toString() {
			return "CubicBezier(" + this.controls.x1 + ", " + this.controls.y1 + ", " + this.controls.x2 + ", " + this.controls.y2 + ")";
		}

		record CubicCurve(float a, float b, float c) {
			public float sample(float f) {
				return ((this.a * f + this.b) * f + this.c) * f;
			}

			public float sampleGradient(float f) {
				return (3.0F * this.a * f + 2.0F * this.b) * f + this.c;
			}
		}
	}

	record CubicBezierControls(float x1, float y1, float x2, float y2) {
		public static final Codec<EasingType.CubicBezierControls> CODEC = Codec.FLOAT
			.listOf(4, 4)
			.xmap(
				list -> new EasingType.CubicBezierControls(list.get(0), list.get(1), list.get(2), list.get(3)),
				cubicBezierControls -> List.of(cubicBezierControls.x1, cubicBezierControls.y1, cubicBezierControls.x2, cubicBezierControls.y2)
			)
			.validate(EasingType.CubicBezierControls::validate);

		private DataResult<EasingType.CubicBezierControls> validate() {
			if (this.x1 < 0.0F || this.x1 > 1.0F) {
				return DataResult.error(() -> "x1 must be in range [0; 1]");
			} else {
				return !(this.x2 < 0.0F) && !(this.x2 > 1.0F) ? DataResult.success(this) : DataResult.error(() -> "x2 must be in range [0; 1]");
			}
		}
	}
}
