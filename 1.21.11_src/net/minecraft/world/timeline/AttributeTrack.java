package net.minecraft.world.timeline;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.function.LongSupplier;
import net.minecraft.util.KeyframeTrack;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.modifier.AttributeModifier;

public record AttributeTrack<Value, Argument>(AttributeModifier<Value, Argument> modifier, KeyframeTrack<Argument> argumentTrack) {
	public static <Value> Codec<AttributeTrack<Value, ?>> createCodec(EnvironmentAttribute<Value> environmentAttribute) {
		MapCodec<AttributeModifier<Value, ?>> mapCodec = environmentAttribute.type().modifierCodec().optionalFieldOf("modifier", AttributeModifier.override());
		return mapCodec.dispatch(
			AttributeTrack::modifier, Util.memoize(attributeModifier -> createCodecWithModifier(environmentAttribute, (AttributeModifier<Value, ?>)attributeModifier))
		);
	}

	private static <Value, Argument> MapCodec<AttributeTrack<Value, Argument>> createCodecWithModifier(
		EnvironmentAttribute<Value> environmentAttribute, AttributeModifier<Value, Argument> attributeModifier
	) {
		return KeyframeTrack.mapCodec(attributeModifier.argumentCodec(environmentAttribute))
			.xmap(keyframeTrack -> new AttributeTrack<>(attributeModifier, (KeyframeTrack<Argument>)keyframeTrack), AttributeTrack::argumentTrack);
	}

	public AttributeTrackSampler<Value, Argument> bakeSampler(
		EnvironmentAttribute<Value> environmentAttribute, Optional<Integer> optional, LongSupplier longSupplier
	) {
		return new AttributeTrackSampler<>(optional, this.modifier, this.argumentTrack, this.modifier.argumentKeyframeLerp(environmentAttribute), longSupplier);
	}

	public static DataResult<AttributeTrack<?, ?>> validatePeriod(AttributeTrack<?, ?> attributeTrack, int i) {
		return KeyframeTrack.validatePeriod(attributeTrack.argumentTrack(), i).map(keyframeTrack -> attributeTrack);
	}
}
