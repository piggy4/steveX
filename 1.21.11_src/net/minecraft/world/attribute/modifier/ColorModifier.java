package net.minecraft.world.attribute.modifier;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.LerpFunction;

public interface ColorModifier<Argument> extends AttributeModifier<Integer, Argument> {
	ColorModifier<Integer> ALPHA_BLEND = new ColorModifier<Integer>() {
		public Integer apply(Integer integer, Integer integer2) {
			return ARGB.alphaBlend(integer, integer2);
		}

		@Override
		public Codec<Integer> argumentCodec(EnvironmentAttribute<Integer> environmentAttribute) {
			return ExtraCodecs.STRING_ARGB_COLOR;
		}

		@Override
		public LerpFunction<Integer> argumentKeyframeLerp(EnvironmentAttribute<Integer> environmentAttribute) {
			return LerpFunction.ofColor();
		}
	};
	ColorModifier<Integer> ADD = ARGB::addRgb;
	ColorModifier<Integer> SUBTRACT = ARGB::subtractRgb;
	ColorModifier<Integer> MULTIPLY_RGB = ARGB::multiply;
	ColorModifier<Integer> MULTIPLY_ARGB = ARGB::multiply;
	ColorModifier<ColorModifier.BlendToGray> BLEND_TO_GRAY = new ColorModifier<ColorModifier.BlendToGray>() {
		public Integer apply(Integer integer, ColorModifier.BlendToGray blendToGray) {
			int i = ARGB.scaleRGB(ARGB.greyscale(integer), blendToGray.brightness);
			return ARGB.srgbLerp(blendToGray.factor, integer, i);
		}

		@Override
		public Codec<ColorModifier.BlendToGray> argumentCodec(EnvironmentAttribute<Integer> environmentAttribute) {
			return ColorModifier.BlendToGray.CODEC;
		}

		@Override
		public LerpFunction<ColorModifier.BlendToGray> argumentKeyframeLerp(EnvironmentAttribute<Integer> environmentAttribute) {
			return (f, blendToGray, blendToGray2) -> new ColorModifier.BlendToGray(
				Mth.lerp(f, blendToGray.brightness, blendToGray2.brightness), Mth.lerp(f, blendToGray.factor, blendToGray2.factor)
			);
		}
	};

	@FunctionalInterface
	interface ArgbModifier extends ColorModifier<Integer> {
		@Override
		default Codec<Integer> argumentCodec(EnvironmentAttribute<Integer> environmentAttribute) {
			return Codec.either(ExtraCodecs.STRING_ARGB_COLOR, ExtraCodecs.RGB_COLOR_CODEC)
				.xmap(Either::unwrap, integer -> ARGB.alpha(integer) == 255 ? Either.right(integer) : Either.left(integer));
		}

		@Override
		default LerpFunction<Integer> argumentKeyframeLerp(EnvironmentAttribute<Integer> environmentAttribute) {
			return LerpFunction.ofColor();
		}
	}

	record BlendToGray(float brightness, float factor) {
		public static final Codec<ColorModifier.BlendToGray> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.floatRange(0.0F, 1.0F).fieldOf("brightness").forGetter(ColorModifier.BlendToGray::brightness),
					Codec.floatRange(0.0F, 1.0F).fieldOf("factor").forGetter(ColorModifier.BlendToGray::factor)
				)
				.apply(instance, ColorModifier.BlendToGray::new)
		);
	}

	@FunctionalInterface
	interface RgbModifier extends ColorModifier<Integer> {
		@Override
		default Codec<Integer> argumentCodec(EnvironmentAttribute<Integer> environmentAttribute) {
			return ExtraCodecs.STRING_RGB_COLOR;
		}

		@Override
		default LerpFunction<Integer> argumentKeyframeLerp(EnvironmentAttribute<Integer> environmentAttribute) {
			return LerpFunction.ofColor();
		}
	}
}
