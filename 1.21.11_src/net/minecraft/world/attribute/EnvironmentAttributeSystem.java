package net.minecraft.world.attribute;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.timeline.Timeline;
import org.jspecify.annotations.Nullable;

public class EnvironmentAttributeSystem implements EnvironmentAttributeReader {
	private final Map<EnvironmentAttribute<?>, EnvironmentAttributeSystem.ValueSampler<?>> attributeSamplers = new Reference2ObjectOpenHashMap<>();

	EnvironmentAttributeSystem(Map<EnvironmentAttribute<?>, List<EnvironmentAttributeLayer<?>>> map) {
		map.forEach(
			(environmentAttribute, list) -> this.attributeSamplers
				.put(
					(EnvironmentAttribute<?>)environmentAttribute,
					this.bakeLayerSampler((EnvironmentAttribute<?>)environmentAttribute, (List<? extends EnvironmentAttributeLayer<?>>)list)
				)
		);
	}

	private <Value> EnvironmentAttributeSystem.ValueSampler<Value> bakeLayerSampler(
		EnvironmentAttribute<Value> environmentAttribute, List<? extends EnvironmentAttributeLayer<?>> list
	) {
		List<EnvironmentAttributeLayer<Value>> list2 = new ArrayList<>((Collection<? extends EnvironmentAttributeLayer<Value>>)list);
		Value object = environmentAttribute.defaultValue();

		while (!list2.isEmpty()) {
			if (!(list2.getFirst() instanceof EnvironmentAttributeLayer.Constant<Value> constant)) {
				break;
			}

			object = constant.applyConstant(object);
			list2.removeFirst();
		}

		boolean bl = list2.stream().anyMatch(environmentAttributeLayer -> environmentAttributeLayer instanceof EnvironmentAttributeLayer.Positional);
		return new EnvironmentAttributeSystem.ValueSampler<>(environmentAttribute, object, List.copyOf(list2), bl);
	}

	public static EnvironmentAttributeSystem.Builder builder() {
		return new EnvironmentAttributeSystem.Builder();
	}

	static void addDefaultLayers(EnvironmentAttributeSystem.Builder builder, Level level) {
		RegistryAccess registryAccess = level.registryAccess();
		BiomeManager biomeManager = level.getBiomeManager();
		LongSupplier longSupplier = level::getDayTime;
		addDimensionLayer(builder, level.dimensionType());
		addBiomeLayer(builder, registryAccess.lookupOrThrow(Registries.BIOME), biomeManager);
		level.dimensionType().timelines().forEach(holder -> builder.addTimelineLayer((Holder<Timeline>)holder, longSupplier));
		if (level.canHaveWeather()) {
			WeatherAttributes.addBuiltinLayers(builder, WeatherAttributes.WeatherAccess.from(level));
		}
	}

	private static void addDimensionLayer(EnvironmentAttributeSystem.Builder builder, DimensionType dimensionType) {
		builder.addConstantLayer(dimensionType.attributes());
	}

	private static void addBiomeLayer(EnvironmentAttributeSystem.Builder builder, HolderLookup<Biome> holderLookup, BiomeManager biomeManager) {
		Stream<EnvironmentAttribute<?>> stream = holderLookup.listElements().flatMap(reference -> reference.value().getAttributes().keySet().stream()).distinct();
		stream.forEach(environmentAttribute -> addBiomeLayerForAttribute(builder, (EnvironmentAttribute<?>)environmentAttribute, biomeManager));
	}

	private static <Value> void addBiomeLayerForAttribute(
		EnvironmentAttributeSystem.Builder builder, EnvironmentAttribute<Value> environmentAttribute, BiomeManager biomeManager
	) {
		builder.addPositionalLayer(environmentAttribute, (object, vec3, spatialAttributeInterpolator) -> {
			if (spatialAttributeInterpolator != null && environmentAttribute.isSpatiallyInterpolated()) {
				return spatialAttributeInterpolator.applyAttributeLayer(environmentAttribute, object);
			}

			Holder<Biome> holder = biomeManager.getNoiseBiomeAtPosition(vec3.x, vec3.y, vec3.z);
			return holder.value().getAttributes().applyModifier(environmentAttribute, object);
		});
	}

	public void invalidateTickCache() {
		this.attributeSamplers.values().forEach(EnvironmentAttributeSystem.ValueSampler::invalidateTickCache);
	}

	private <Value> EnvironmentAttributeSystem.@Nullable ValueSampler<Value> getValueSampler(EnvironmentAttribute<Value> environmentAttribute) {
		return (EnvironmentAttributeSystem.ValueSampler<Value>)this.attributeSamplers.get(environmentAttribute);
	}

	@Override
	public <Value> Value getDimensionValue(EnvironmentAttribute<Value> environmentAttribute) {
		if (SharedConstants.IS_RUNNING_IN_IDE && environmentAttribute.isPositional()) {
			throw new IllegalStateException("Position must always be provided for positional attribute " + environmentAttribute);
		}

		EnvironmentAttributeSystem.ValueSampler<Value> valueSampler = this.getValueSampler(environmentAttribute);
		return valueSampler == null ? environmentAttribute.defaultValue() : valueSampler.getDimensionValue();
	}

	@Override
	public <Value> Value getValue(EnvironmentAttribute<Value> environmentAttribute, Vec3 vec3, @Nullable SpatialAttributeInterpolator spatialAttributeInterpolator) {
		EnvironmentAttributeSystem.ValueSampler<Value> valueSampler = this.getValueSampler(environmentAttribute);
		return valueSampler == null ? environmentAttribute.defaultValue() : valueSampler.getValue(vec3, spatialAttributeInterpolator);
	}

	@VisibleForTesting
	<Value> Value getConstantBaseValue(EnvironmentAttribute<Value> environmentAttribute) {
		EnvironmentAttributeSystem.ValueSampler<Value> valueSampler = this.getValueSampler(environmentAttribute);
		return valueSampler != null ? valueSampler.baseValue : environmentAttribute.defaultValue();
	}

	@VisibleForTesting
	boolean isAffectedByPosition(EnvironmentAttribute<?> environmentAttribute) {
		EnvironmentAttributeSystem.ValueSampler<?> valueSampler = this.getValueSampler(environmentAttribute);
		return valueSampler != null && valueSampler.isAffectedByPosition;
	}

	public static class Builder {
		private final Map<EnvironmentAttribute<?>, List<EnvironmentAttributeLayer<?>>> layersByAttribute = new HashMap<>();

		Builder() {
		}

		public EnvironmentAttributeSystem.Builder addDefaultLayers(Level level) {
			EnvironmentAttributeSystem.addDefaultLayers(this, level);
			return this;
		}

		public EnvironmentAttributeSystem.Builder addConstantLayer(EnvironmentAttributeMap environmentAttributeMap) {
			for (EnvironmentAttribute<?> environmentAttribute : environmentAttributeMap.keySet()) {
				this.addConstantEntry(environmentAttribute, environmentAttributeMap);
			}

			return this;
		}

		private <Value> EnvironmentAttributeSystem.Builder addConstantEntry(
			EnvironmentAttribute<Value> environmentAttribute, EnvironmentAttributeMap environmentAttributeMap
		) {
			EnvironmentAttributeMap.Entry<Value, ?> entry = environmentAttributeMap.get(environmentAttribute);
			if (entry == null) {
				throw new IllegalArgumentException("Missing attribute " + environmentAttribute);
			} else {
				return this.addConstantLayer(environmentAttribute, entry::applyModifier);
			}
		}

		public <Value> EnvironmentAttributeSystem.Builder addConstantLayer(
			EnvironmentAttribute<Value> environmentAttribute, EnvironmentAttributeLayer.Constant<Value> constant
		) {
			return this.addLayer(environmentAttribute, constant);
		}

		public <Value> EnvironmentAttributeSystem.Builder addTimeBasedLayer(
			EnvironmentAttribute<Value> environmentAttribute, EnvironmentAttributeLayer.TimeBased<Value> timeBased
		) {
			return this.addLayer(environmentAttribute, timeBased);
		}

		public <Value> EnvironmentAttributeSystem.Builder addPositionalLayer(
			EnvironmentAttribute<Value> environmentAttribute, EnvironmentAttributeLayer.Positional<Value> positional
		) {
			return this.addLayer(environmentAttribute, positional);
		}

		private <Value> EnvironmentAttributeSystem.Builder addLayer(
			EnvironmentAttribute<Value> environmentAttribute, EnvironmentAttributeLayer<Value> environmentAttributeLayer
		) {
			this.layersByAttribute.computeIfAbsent(environmentAttribute, environmentAttributex -> new ArrayList<>()).add(environmentAttributeLayer);
			return this;
		}

		public EnvironmentAttributeSystem.Builder addTimelineLayer(Holder<Timeline> holder, LongSupplier longSupplier) {
			for (EnvironmentAttribute<?> environmentAttribute : holder.value().attributes()) {
				this.addTimelineLayerForAttribute(holder, environmentAttribute, longSupplier);
			}

			return this;
		}

		private <Value> void addTimelineLayerForAttribute(Holder<Timeline> holder, EnvironmentAttribute<Value> environmentAttribute, LongSupplier longSupplier) {
			this.addTimeBasedLayer(environmentAttribute, holder.value().createTrackSampler(environmentAttribute, longSupplier));
		}

		public EnvironmentAttributeSystem build() {
			return new EnvironmentAttributeSystem(this.layersByAttribute);
		}
	}

	static class ValueSampler<Value> {
		private final EnvironmentAttribute<Value> attribute;
		final Value baseValue;
		private final List<EnvironmentAttributeLayer<Value>> layers;
		final boolean isAffectedByPosition;
		private @Nullable Value cachedTickValue;
		private int cacheTickId;

		ValueSampler(EnvironmentAttribute<Value> environmentAttribute, Value object, List<EnvironmentAttributeLayer<Value>> list, boolean bl) {
			this.attribute = environmentAttribute;
			this.baseValue = object;
			this.layers = list;
			this.isAffectedByPosition = bl;
		}

		public void invalidateTickCache() {
			this.cachedTickValue = null;
			this.cacheTickId++;
		}

		public Value getDimensionValue() {
			if (this.cachedTickValue != null) {
				return this.cachedTickValue;
			}

			Value object = this.computeValueNotPositional();
			this.cachedTickValue = object;
			return object;
		}

		public Value getValue(Vec3 vec3, @Nullable SpatialAttributeInterpolator spatialAttributeInterpolator) {
			return !this.isAffectedByPosition ? this.getDimensionValue() : this.computeValuePositional(vec3, spatialAttributeInterpolator);
		}

		private Value computeValuePositional(Vec3 vec3, @Nullable SpatialAttributeInterpolator spatialAttributeInterpolator) {
			Value object = this.baseValue;

			for (EnvironmentAttributeLayer<Value> environmentAttributeLayer : this.layers) {
				object = (Value)(switch (environmentAttributeLayer) {
					case EnvironmentAttributeLayer.Constant<Value> constant -> (Object)constant.applyConstant(object);
					case EnvironmentAttributeLayer.TimeBased<Value> timeBased -> (Object)timeBased.applyTimeBased(object, this.cacheTickId);
					case EnvironmentAttributeLayer.Positional<Value> positional -> (Object)positional.applyPositional(
						object, Objects.requireNonNull(vec3), spatialAttributeInterpolator
					);
					default -> throw new MatchException(null, null);
				});
			}

			return this.attribute.sanitizeValue(object);
		}

		private Value computeValueNotPositional() {
			Value object = this.baseValue;

			for (EnvironmentAttributeLayer<Value> environmentAttributeLayer : this.layers) {
				object = (Value)(switch (environmentAttributeLayer) {
					case EnvironmentAttributeLayer.Constant<Value> constant -> (Object)constant.applyConstant(object);
					case EnvironmentAttributeLayer.TimeBased<Value> timeBased -> (Object)timeBased.applyTimeBased(object, this.cacheTickId);
					case EnvironmentAttributeLayer.Positional<Value> positional -> (Object)object;
					default -> throw new MatchException(null, null);
				});
			}

			return this.attribute.sanitizeValue(object);
		}
	}
}
