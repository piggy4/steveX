package net.minecraft.client.resources.metadata.animation;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.ExtraCodecs;

@Environment(EnvType.CLIENT)
public record AnimationFrame(int index, Optional<Integer> time) {
	public static final Codec<AnimationFrame> FULL_CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				ExtraCodecs.NON_NEGATIVE_INT.fieldOf("index").forGetter(AnimationFrame::index),
				ExtraCodecs.POSITIVE_INT.optionalFieldOf("time").forGetter(AnimationFrame::time)
			)
			.apply(instance, AnimationFrame::new)
	);
	public static final Codec<AnimationFrame> CODEC = Codec.either(ExtraCodecs.NON_NEGATIVE_INT, FULL_CODEC)
		.xmap(
			either -> either.map(AnimationFrame::new, animationFrame -> (AnimationFrame)animationFrame),
			animationFrame -> animationFrame.time.isPresent() ? Either.right(animationFrame) : Either.left(animationFrame.index)
		);

	public AnimationFrame(int i) {
		this(i, Optional.empty());
	}

	public int timeOr(int i) {
		return this.time.orElse(i);
	}
}
