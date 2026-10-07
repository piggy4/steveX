package net.minecraft.world.flag;

import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

public class FeatureFlagRegistry {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final FeatureFlagUniverse universe;
	private final Map<Identifier, FeatureFlag> names;
	private final FeatureFlagSet allFlags;

	FeatureFlagRegistry(FeatureFlagUniverse featureFlagUniverse, FeatureFlagSet featureFlagSet, Map<Identifier, FeatureFlag> map) {
		this.universe = featureFlagUniverse;
		this.names = map;
		this.allFlags = featureFlagSet;
	}

	public boolean isSubset(FeatureFlagSet featureFlagSet) {
		return featureFlagSet.isSubsetOf(this.allFlags);
	}

	public FeatureFlagSet allFlags() {
		return this.allFlags;
	}

	public FeatureFlagSet fromNames(Iterable<Identifier> iterable) {
		return this.fromNames(iterable, identifier -> LOGGER.warn("Unknown feature flag: {}", identifier));
	}

	public FeatureFlagSet subset(FeatureFlag... featureFlags) {
		return FeatureFlagSet.create(this.universe, Arrays.asList(featureFlags));
	}

	public FeatureFlagSet fromNames(Iterable<Identifier> iterable, Consumer<Identifier> consumer) {
		Set<FeatureFlag> set = Sets.newIdentityHashSet();

		for (Identifier identifier : iterable) {
			FeatureFlag featureFlag = this.names.get(identifier);
			if (featureFlag == null) {
				consumer.accept(identifier);
			} else {
				set.add(featureFlag);
			}
		}

		return FeatureFlagSet.create(this.universe, set);
	}

	public Set<Identifier> toNames(FeatureFlagSet featureFlagSet) {
		Set<Identifier> set = new HashSet<>();
		this.names.forEach((identifier, featureFlag) -> {
			if (featureFlagSet.contains(featureFlag)) {
				set.add(identifier);
			}
		});
		return set;
	}

	public Codec<FeatureFlagSet> codec() {
		return Identifier.CODEC.listOf().comapFlatMap(list -> {
			Set<Identifier> set = new HashSet<>();
			FeatureFlagSet featureFlagSet = this.fromNames(list, set::add);
			return !set.isEmpty() ? DataResult.error(() -> "Unknown feature ids: " + set, featureFlagSet) : DataResult.success(featureFlagSet);
		}, featureFlagSet -> List.copyOf(this.toNames(featureFlagSet)));
	}

	public static class Builder {
		private final FeatureFlagUniverse universe;
		private int id;
		private final Map<Identifier, FeatureFlag> flags = new LinkedHashMap<>();

		public Builder(String string) {
			this.universe = new FeatureFlagUniverse(string);
		}

		public FeatureFlag createVanilla(String string) {
			return this.create(Identifier.withDefaultNamespace(string));
		}

		public FeatureFlag create(Identifier identifier) {
			if (this.id >= 64) {
				throw new IllegalStateException("Too many feature flags");
			} else {
				FeatureFlag featureFlag = new FeatureFlag(this.universe, this.id++);
				FeatureFlag featureFlag2 = this.flags.put(identifier, featureFlag);
				if (featureFlag2 != null) {
					throw new IllegalStateException("Duplicate feature flag " + identifier);
				} else {
					return featureFlag;
				}
			}
		}

		public FeatureFlagRegistry build() {
			FeatureFlagSet featureFlagSet = FeatureFlagSet.create(this.universe, this.flags.values());
			return new FeatureFlagRegistry(this.universe, featureFlagSet, Map.copyOf(this.flags));
		}
	}
}
