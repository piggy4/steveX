package net.minecraft.world.attribute.modifier;

import com.mojang.serialization.Codec;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.LerpFunction;

public interface FloatModifier<Argument> extends AttributeModifier<Float, Argument> {
	FloatModifier<FloatWithAlpha> ALPHA_BLEND = new FloatModifier<FloatWithAlpha>() {
		public Float apply(Float float_, FloatWithAlpha floatWithAlpha) {
			return Mth.lerp(floatWithAlpha.alpha(), float_, floatWithAlpha.value());
		}

		@Override
		public Codec<FloatWithAlpha> argumentCodec(EnvironmentAttribute<Float> environmentAttribute) {
			return FloatWithAlpha.CODEC;
		}

		@Override
		public LerpFunction<FloatWithAlpha> argumentKeyframeLerp(EnvironmentAttribute<Float> environmentAttribute) {
			return (f, floatWithAlpha, floatWithAlpha2) -> new FloatWithAlpha(
				Mth.lerp(f, floatWithAlpha.value(), floatWithAlpha2.value()), Mth.lerp(f, floatWithAlpha.alpha(), floatWithAlpha2.alpha())
			);
		}
	};
	FloatModifier<Float> ADD = Float::sum;
	FloatModifier<Float> SUBTRACT = (FloatModifier.Simple)(float_, float2) -> float_ - float2;
	FloatModifier<Float> MULTIPLY = (FloatModifier.Simple)(float_, float2) -> float_ * float2;
	FloatModifier<Float> MINIMUM = Math::min;
	FloatModifier<Float> MAXIMUM = Math::max;

	@FunctionalInterface
	interface Simple extends FloatModifier<Float> {
		@Override
		default Codec<Float> argumentCodec(EnvironmentAttribute<Float> environmentAttribute) {
			return Codec.FLOAT;
		}

		@Override
		default LerpFunction<Float> argumentKeyframeLerp(EnvironmentAttribute<Float> environmentAttribute) {
			return LerpFunction.ofFloat();
		}
	}
}
