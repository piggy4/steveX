package net.minecraft.util;

import it.unimi.dsi.fastutil.floats.Float2FloatFunction;
import java.util.function.Function;

public interface BoundedFloatFunction<C> {
	BoundedFloatFunction<Float> IDENTITY = createUnlimited(f -> f);

	float apply(C object);

	float minValue();

	float maxValue();

	static BoundedFloatFunction<Float> createUnlimited(Float2FloatFunction float2FloatFunction) {
		return new BoundedFloatFunction<Float>() {
			public float apply(Float float_) {
				return float2FloatFunction.apply(float_);
			}

			@Override
			public float minValue() {
				return Float.NEGATIVE_INFINITY;
			}

			@Override
			public float maxValue() {
				return Float.POSITIVE_INFINITY;
			}
		};
	}

	default <C2> BoundedFloatFunction<C2> comap(Function<C2, C> function) {
		final BoundedFloatFunction<C> boundedFloatFunction = this;
		return new BoundedFloatFunction<C2>() {
			@Override
			public float apply(C2 object) {
				return boundedFloatFunction.apply(function.apply(object));
			}

			@Override
			public float minValue() {
				return boundedFloatFunction.minValue();
			}

			@Override
			public float maxValue() {
				return boundedFloatFunction.maxValue();
			}
		};
	}
}
