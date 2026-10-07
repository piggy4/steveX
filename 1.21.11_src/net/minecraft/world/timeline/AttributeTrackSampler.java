package net.minecraft.world.timeline;

import java.util.Optional;
import java.util.function.LongSupplier;
import net.minecraft.util.KeyframeTrack;
import net.minecraft.util.KeyframeTrackSampler;
import net.minecraft.world.attribute.EnvironmentAttributeLayer;
import net.minecraft.world.attribute.LerpFunction;
import net.minecraft.world.attribute.modifier.AttributeModifier;
import org.jspecify.annotations.Nullable;

public class AttributeTrackSampler<Value, Argument> implements EnvironmentAttributeLayer.TimeBased<Value> {
	private final AttributeModifier<Value, Argument> modifier;
	private final KeyframeTrackSampler<Argument> argumentSampler;
	private final LongSupplier dayTimeGetter;
	private int cachedTickId;
	private @Nullable Argument cachedArgument;

	public AttributeTrackSampler(
		Optional<Integer> optional,
		AttributeModifier<Value, Argument> attributeModifier,
		KeyframeTrack<Argument> keyframeTrack,
		LerpFunction<Argument> lerpFunction,
		LongSupplier longSupplier
	) {
		this.modifier = attributeModifier;
		this.dayTimeGetter = longSupplier;
		this.argumentSampler = keyframeTrack.bakeSampler(optional, lerpFunction);
	}

	@Override
	public Value applyTimeBased(Value object, int i) {
		if (this.cachedArgument == null || i != this.cachedTickId) {
			this.cachedTickId = i;
			this.cachedArgument = this.argumentSampler.sample(this.dayTimeGetter.getAsLong());
		}

		return this.modifier.apply(object, this.cachedArgument);
	}
}
