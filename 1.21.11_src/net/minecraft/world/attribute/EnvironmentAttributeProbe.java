package net.minecraft.world.attribute;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class EnvironmentAttributeProbe {
	private final Map<EnvironmentAttribute<?>, EnvironmentAttributeProbe.ValueProbe<?>> valueProbes = new Reference2ObjectOpenHashMap<>();
	private final Function<EnvironmentAttribute<?>, EnvironmentAttributeProbe.ValueProbe<?>> valueProbeFactory = environmentAttribute -> new EnvironmentAttributeProbe.ValueProbe<>(
		environmentAttribute
	);
	@Nullable Level level;
	@Nullable Vec3 position;
	final SpatialAttributeInterpolator biomeInterpolator = new SpatialAttributeInterpolator();

	public void reset() {
		this.level = null;
		this.position = null;
		this.biomeInterpolator.clear();
		this.valueProbes.clear();
	}

	public void tick(Level level, Vec3 vec3) {
		this.level = level;
		this.position = vec3;
		this.valueProbes.values().removeIf(EnvironmentAttributeProbe.ValueProbe::tick);
		this.biomeInterpolator.clear();
		GaussianSampler.sample(
			vec3.scale(0.25), level.getBiomeManager()::getNoiseBiomeAtQuart, (d, holder) -> this.biomeInterpolator.accumulate(d, holder.value().getAttributes())
		);
	}

	public <Value> Value getValue(EnvironmentAttribute<Value> environmentAttribute, float f) {
		EnvironmentAttributeProbe.ValueProbe<Value> valueProbe = (EnvironmentAttributeProbe.ValueProbe<Value>)this.valueProbes
			.computeIfAbsent(environmentAttribute, this.valueProbeFactory);
		return valueProbe.get(environmentAttribute, f);
	}

	class ValueProbe<Value> {
		private Value lastValue;
		private @Nullable Value newValue;

		public ValueProbe(final EnvironmentAttribute<Value> environmentAttribute) {
			Value object = this.getValueFromLevel(environmentAttribute);
			this.lastValue = object;
			this.newValue = object;
		}

		private Value getValueFromLevel(EnvironmentAttribute<Value> environmentAttribute) {
			return EnvironmentAttributeProbe.this.level != null && EnvironmentAttributeProbe.this.position != null
				? EnvironmentAttributeProbe.this.level
					.environmentAttributes()
					.getValue(environmentAttribute, EnvironmentAttributeProbe.this.position, EnvironmentAttributeProbe.this.biomeInterpolator)
				: environmentAttribute.defaultValue();
		}

		public boolean tick() {
			if (this.newValue == null) {
				return true;
			}

			this.lastValue = this.newValue;
			this.newValue = null;
			return false;
		}

		public Value get(EnvironmentAttribute<Value> environmentAttribute, float f) {
			if (this.newValue == null) {
				this.newValue = this.getValueFromLevel(environmentAttribute);
			}

			return environmentAttribute.type().partialTickLerp().apply(f, this.lastValue, this.newValue);
		}
	}
}
