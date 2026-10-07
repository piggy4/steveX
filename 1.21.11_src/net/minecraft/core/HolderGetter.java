package net.minecraft.core;

import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;

public interface HolderGetter<T> {
	Optional<Holder.Reference<T>> get(ResourceKey<T> resourceKey);

	default Holder.Reference<T> getOrThrow(ResourceKey<T> resourceKey) {
		return this.get(resourceKey).orElseThrow(() -> new IllegalStateException("Missing element " + resourceKey));
	}

	Optional<HolderSet.Named<T>> get(TagKey<T> tagKey);

	default HolderSet.Named<T> getOrThrow(TagKey<T> tagKey) {
		return this.get(tagKey).orElseThrow(() -> new IllegalStateException("Missing tag " + tagKey));
	}

	default Optional<Holder<T>> getRandomElementOf(TagKey<T> tagKey, RandomSource randomSource) {
		return this.get(tagKey).flatMap(named -> named.getRandomElement(randomSource));
	}

	interface Provider {
		<T> Optional<? extends HolderGetter<T>> lookup(ResourceKey<? extends Registry<? extends T>> resourceKey);

		default <T> HolderGetter<T> lookupOrThrow(ResourceKey<? extends Registry<? extends T>> resourceKey) {
			return (HolderGetter<T>)this.lookup(resourceKey).orElseThrow(() -> new IllegalStateException("Registry " + resourceKey.identifier() + " not found"));
		}

		default <T> Optional<Holder.Reference<T>> get(ResourceKey<T> resourceKey) {
			return this.lookup(resourceKey.registryKey()).flatMap(holderGetter -> holderGetter.get(resourceKey));
		}

		default <T> Holder.Reference<T> getOrThrow(ResourceKey<T> resourceKey) {
			return this.lookup(resourceKey.registryKey())
				.flatMap(holderGetter -> holderGetter.get(resourceKey))
				.orElseThrow(() -> new IllegalStateException("Missing element " + resourceKey));
		}
	}
}
