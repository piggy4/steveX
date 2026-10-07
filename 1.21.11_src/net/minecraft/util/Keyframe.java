package net.minecraft.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record Keyframe<T>(int ticks, T value) {
	public static <T> Codec<Keyframe<T>> codec(Codec<T> codec) {
		return RecordCodecBuilder.create(
			instance -> instance.group(ExtraCodecs.NON_NEGATIVE_INT.fieldOf("ticks").forGetter(Keyframe::ticks), codec.fieldOf("value").forGetter(Keyframe::value))
				.apply(instance, Keyframe::new)
		);
	}
}
