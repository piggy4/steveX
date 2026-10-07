package net.minecraft.world.attribute;

import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

public interface LerpFunction<T> {
	static LerpFunction<Float> ofFloat() {
		return Mth::lerp;
	}

	static LerpFunction<Float> ofDegrees(float f) {
		return (g, float_, float2) -> {
			float h = Mth.wrapDegrees(float2 - float_);
			return Math.abs(h) >= f ? float2 : float_ + g * h;
		};
	}

	static <T> LerpFunction<T> ofConstant() {
		return (f, object, object2) -> object;
	}

	static <T> LerpFunction<T> ofStep(float f) {
		return (g, object, object2) -> g >= f ? object2 : object;
	}

	static LerpFunction<Integer> ofColor() {
		return ARGB::srgbLerp;
	}

	T apply(float f, T object, T object2);
}
