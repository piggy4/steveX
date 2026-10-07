package net.minecraft.server.dialog.input;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;

public record NumberRangeInput(int width, Component label, String labelFormat, NumberRangeInput.RangeInfo rangeInfo) implements InputControl {
	public static final MapCodec<NumberRangeInput> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				Dialog.WIDTH_CODEC.optionalFieldOf("width", 200).forGetter(NumberRangeInput::width),
				ComponentSerialization.CODEC.fieldOf("label").forGetter(NumberRangeInput::label),
				Codec.STRING.optionalFieldOf("label_format", "options.generic_value").forGetter(NumberRangeInput::labelFormat),
				NumberRangeInput.RangeInfo.MAP_CODEC.forGetter(NumberRangeInput::rangeInfo)
			)
			.apply(instance, NumberRangeInput::new)
	);

	@Override
	public MapCodec<NumberRangeInput> mapCodec() {
		return MAP_CODEC;
	}

	public Component computeLabel(String string) {
		return Component.translatable(this.labelFormat, this.label, string);
	}

	public record RangeInfo(float start, float end, Optional<Float> initial, Optional<Float> step) {
		public static final MapCodec<NumberRangeInput.RangeInfo> MAP_CODEC = RecordCodecBuilder.<NumberRangeInput.RangeInfo>mapCodec(
				instance -> instance.group(
						Codec.FLOAT.fieldOf("start").forGetter(NumberRangeInput.RangeInfo::start),
						Codec.FLOAT.fieldOf("end").forGetter(NumberRangeInput.RangeInfo::end),
						Codec.FLOAT.optionalFieldOf("initial").forGetter(NumberRangeInput.RangeInfo::initial),
						ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("step").forGetter(NumberRangeInput.RangeInfo::step)
					)
					.apply(instance, NumberRangeInput.RangeInfo::new)
			)
			.validate(rangeInfo -> {
				if (rangeInfo.initial.isPresent()) {
					double d = rangeInfo.initial.get().floatValue();
					double e = Math.min(rangeInfo.start, rangeInfo.end);
					double f = Math.max(rangeInfo.start, rangeInfo.end);
					if (d < e || d > f) {
						return DataResult.error(() -> "Initial value " + d + " is outside of range [" + e + ", " + f + "]");
					}
				}

				return DataResult.success(rangeInfo);
			});

		public float computeScaledValue(float f) {
			float g = Mth.lerp(f, this.start, this.end);
			if (this.step.isEmpty()) {
				return g;
			}

			float h = this.step.get();
			float i = this.initialScaledValue();
			float j = g - i;
			int k = Math.round(j / h);
			float l = i + k * h;
			if (!this.isOutOfRange(l)) {
				return l;
			}

			int m = k - Mth.sign(k);
			return i + m * h;
		}

		private boolean isOutOfRange(float f) {
			float g = this.scaledValueToSlider(f);
			return g < 0.0 || g > 1.0;
		}

		private float initialScaledValue() {
			return this.initial.isPresent() ? this.initial.get() : (this.start + this.end) / 2.0F;
		}

		public float initialSliderValue() {
			float f = this.initialScaledValue();
			return this.scaledValueToSlider(f);
		}

		private float scaledValueToSlider(float f) {
			return this.start == this.end ? 0.5F : Mth.inverseLerp(f, this.start, this.end);
		}
	}
}
