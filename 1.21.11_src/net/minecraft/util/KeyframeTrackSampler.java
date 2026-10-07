package net.minecraft.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.attribute.LerpFunction;

public class KeyframeTrackSampler<T> {
	private final Optional<Integer> periodTicks;
	private final LerpFunction<T> lerp;
	private final List<KeyframeTrackSampler.Segment<T>> segments;

	KeyframeTrackSampler(KeyframeTrack<T> keyframeTrack, Optional<Integer> optional, LerpFunction<T> lerpFunction) {
		this.periodTicks = optional;
		this.lerp = lerpFunction;
		this.segments = bakeSegments(keyframeTrack, optional);
	}

	private static <T> List<KeyframeTrackSampler.Segment<T>> bakeSegments(KeyframeTrack<T> keyframeTrack, Optional<Integer> optional) {
		List<Keyframe<T>> list = keyframeTrack.keyframes();
		if (list.size() == 1) {
			T object = list.getFirst().value();
			return List.of(new KeyframeTrackSampler.Segment<>(EasingType.CONSTANT, object, 0, object, 0));
		}

		List<KeyframeTrackSampler.Segment<T>> list2 = new ArrayList<>();
		if (optional.isPresent()) {
			Keyframe<T> keyframe = list.getFirst();
			Keyframe<T> keyframe2 = list.getLast();
			list2.add(new KeyframeTrackSampler.Segment<>(keyframeTrack, keyframe2, keyframe2.ticks() - optional.get(), keyframe, keyframe.ticks()));
			addSegmentsFromKeyframes(keyframeTrack, list, list2);
			list2.add(new KeyframeTrackSampler.Segment<>(keyframeTrack, keyframe2, keyframe2.ticks(), keyframe, keyframe.ticks() + optional.get()));
		} else {
			addSegmentsFromKeyframes(keyframeTrack, list, list2);
		}

		return List.copyOf(list2);
	}

	private static <T> void addSegmentsFromKeyframes(KeyframeTrack<T> keyframeTrack, List<Keyframe<T>> list, List<KeyframeTrackSampler.Segment<T>> list2) {
		for (int i = 0; i < list.size() - 1; i++) {
			Keyframe<T> keyframe = list.get(i);
			Keyframe<T> keyframe2 = list.get(i + 1);
			list2.add(new KeyframeTrackSampler.Segment<>(keyframeTrack, keyframe, keyframe.ticks(), keyframe2, keyframe2.ticks()));
		}
	}

	public T sample(long l) {
		long m = this.loopTicks(l);
		KeyframeTrackSampler.Segment<T> segment = this.getSegmentAt(m);
		if (m <= segment.fromTicks) {
			return segment.fromValue;
		}

		if (m >= segment.toTicks) {
			return segment.toValue;
		}

		float f = (float)(m - segment.fromTicks) / (segment.toTicks - segment.fromTicks);
		float g = segment.easing.apply(f);
		return this.lerp.apply(g, segment.fromValue, segment.toValue);
	}

	private KeyframeTrackSampler.Segment<T> getSegmentAt(long l) {
		for (KeyframeTrackSampler.Segment<T> segment : this.segments) {
			if (l < segment.toTicks) {
				return segment;
			}
		}

		return this.segments.getLast();
	}

	private long loopTicks(long l) {
		return this.periodTicks.isPresent() ? Math.floorMod(l, this.periodTicks.get()) : l;
	}

	record Segment<T>(EasingType easing, T fromValue, int fromTicks, T toValue, int toTicks) {

		public Segment(KeyframeTrack<T> keyframeTrack, Keyframe<T> keyframe, int i, Keyframe<T> keyframe2, int j) {
			this(keyframeTrack.easingType(), keyframe.value(), i, keyframe2.value(), j);
		}
	}
}
