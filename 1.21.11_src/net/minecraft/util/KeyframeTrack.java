package net.minecraft.util;

import com.google.common.collect.Comparators;
import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.attribute.LerpFunction;

public record KeyframeTrack<T>(List<Keyframe<T>> keyframes, EasingType easingType) {
	public KeyframeTrack {
		if (keyframes.isEmpty()) {
			throw new IllegalArgumentException("Track has no keyframes");
		}
	}

	public static <T> MapCodec<KeyframeTrack<T>> mapCodec(Codec<T> codec) {
		Codec<List<Keyframe<T>>> codec2 = Keyframe.codec(codec).listOf().validate(KeyframeTrack::validateKeyframes);
		return RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					codec2.fieldOf("keyframes").forGetter(KeyframeTrack::keyframes),
					EasingType.CODEC.optionalFieldOf("ease", EasingType.LINEAR).forGetter(KeyframeTrack::easingType)
				)
				.apply(instance, KeyframeTrack::new)
		);
	}

	static <T> DataResult<List<Keyframe<T>>> validateKeyframes(List<Keyframe<T>> list) {
		if (list.isEmpty()) {
			return DataResult.error(() -> "Keyframes must not be empty");
		}

		if (!Comparators.isInOrder(list, Comparator.comparingInt(Keyframe::ticks))) {
			return DataResult.error(() -> "Keyframes must be ordered by ticks field");
		}

		if (list.size() > 1) {
			int i = 0;
			int j = list.getLast().ticks();

			for (Keyframe<T> keyframe : list) {
				if (keyframe.ticks() == j) {
					if (++i > 2) {
						return DataResult.error(() -> "More than 2 keyframes on same tick: " + keyframe.ticks());
					}
				} else {
					i = 0;
				}

				j = keyframe.ticks();
			}
		}

		return DataResult.success(list);
	}

	public static DataResult<KeyframeTrack<?>> validatePeriod(KeyframeTrack<?> keyframeTrack, int i) {
		for (Keyframe<?> keyframe : keyframeTrack.keyframes()) {
			int j = keyframe.ticks();
			if (j < 0 || j > i) {
				return DataResult.error(() -> "Keyframe at tick " + keyframe.ticks() + " must be in range [0; " + i + "]");
			}
		}

		return DataResult.success(keyframeTrack);
	}

	public KeyframeTrackSampler<T> bakeSampler(Optional<Integer> optional, LerpFunction<T> lerpFunction) {
		return new KeyframeTrackSampler<>(this, optional, lerpFunction);
	}

	public static class Builder<T> {
		private final ImmutableList.Builder<Keyframe<T>> keyframes = ImmutableList.builder();
		private EasingType easing = EasingType.LINEAR;

		public KeyframeTrack.Builder<T> addKeyframe(int i, T object) {
			this.keyframes.add(new Keyframe<>(i, object));
			return this;
		}

		public KeyframeTrack.Builder<T> setEasing(EasingType easingType) {
			this.easing = easingType;
			return this;
		}

		public KeyframeTrack<T> build() {
			List<Keyframe<T>> list = KeyframeTrack.validateKeyframes(this.keyframes.build()).getOrThrow();
			return new KeyframeTrack<>(list, this.easing);
		}
	}
}
