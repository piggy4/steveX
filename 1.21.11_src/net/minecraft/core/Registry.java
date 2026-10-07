package net.minecraft.core;

import com.mojang.datafixers.DataFixUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Keyable;
import com.mojang.serialization.Lifecycle;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.fabricmc.fabric.api.event.registry.FabricRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.event.registry.FabricRegistry} injected by mod fabric-registry-sync-v0</p>
 */
public interface Registry<T> extends Keyable, HolderLookup.RegistryLookup<T>, IdMap<T>, FabricRegistry {
	@Override
	ResourceKey<? extends Registry<T>> key();

	default Codec<T> byNameCodec() {
		return this.referenceHolderWithLifecycle().flatComapMap(Holder.Reference::value, object -> this.safeCastToReference(this.wrapAsHolder((T)object)));
	}

	default Codec<Holder<T>> holderByNameCodec() {
		return this.referenceHolderWithLifecycle().flatComapMap(reference -> (Holder<T>)reference, this::safeCastToReference);
	}

	private Codec<Holder.Reference<T>> referenceHolderWithLifecycle() {
		Codec<Holder.Reference<T>> codec = Identifier.CODEC
			.comapFlatMap(
				identifier -> this.get(identifier)
					.map(DataResult::success)
					.orElseGet(() -> DataResult.error(() -> "Unknown registry key in " + this.key() + ": " + identifier)),
				reference -> reference.key().identifier()
			);
		return ExtraCodecs.overrideLifecycle(
			codec, reference -> this.registrationInfo(reference.key()).map(RegistrationInfo::lifecycle).orElse(Lifecycle.experimental())
		);
	}

	private DataResult<Holder.Reference<T>> safeCastToReference(Holder<T> holder) {
		return holder instanceof Holder.Reference<T> reference
			? DataResult.success(reference)
			: DataResult.error(() -> "Unregistered holder in " + this.key() + ": " + holder);
	}

	@Override
	default <U> Stream<U> keys(DynamicOps<U> dynamicOps) {
		return this.keySet().stream().map(identifier -> dynamicOps.createString(identifier.toString()));
	}

	@Nullable Identifier getKey(T object);

	Optional<ResourceKey<T>> getResourceKey(T object);

	@Override
	int getId(@Nullable T object);

	@Nullable T getValue(@Nullable ResourceKey<T> resourceKey);

	@Nullable T getValue(@Nullable Identifier identifier);

	Optional<RegistrationInfo> registrationInfo(ResourceKey<T> resourceKey);

	default Optional<T> getOptional(@Nullable Identifier identifier) {
		return Optional.ofNullable(this.getValue(identifier));
	}

	default Optional<T> getOptional(@Nullable ResourceKey<T> resourceKey) {
		return Optional.ofNullable(this.getValue(resourceKey));
	}

	Optional<Holder.Reference<T>> getAny();

	default T getValueOrThrow(ResourceKey<T> resourceKey) {
		T object = this.getValue(resourceKey);
		if (object == null) {
			throw new IllegalStateException("Missing key in " + this.key() + ": " + resourceKey);
		} else {
			return object;
		}
	}

	Set<Identifier> keySet();

	Set<Entry<ResourceKey<T>, T>> entrySet();

	Set<ResourceKey<T>> registryKeySet();

	Optional<Holder.Reference<T>> getRandom(RandomSource randomSource);

	default Stream<T> stream() {
		return StreamSupport.stream(this.spliterator(), false);
	}

	boolean containsKey(Identifier identifier);

	boolean containsKey(ResourceKey<T> resourceKey);

	static <T> T register(Registry<? super T> registry, String string, T object) {
		return register(registry, Identifier.parse(string), object);
	}

	static <V, T extends V> T register(Registry<V> registry, Identifier identifier, T object) {
		return register(registry, ResourceKey.create(registry.key(), identifier), object);
	}

	static <V, T extends V> T register(Registry<V> registry, ResourceKey<V> resourceKey, T object) {
		((WritableRegistry)registry).register(resourceKey, (V)object, RegistrationInfo.BUILT_IN);
		return object;
	}

	static <R, T extends R> Holder.Reference<T> registerForHolder(Registry<R> registry, ResourceKey<R> resourceKey, T object) {
		return ((WritableRegistry)registry).register(resourceKey, (R)object, RegistrationInfo.BUILT_IN);
	}

	static <R, T extends R> Holder.Reference<T> registerForHolder(Registry<R> registry, Identifier identifier, T object) {
		return registerForHolder(registry, ResourceKey.create(registry.key(), identifier), object);
	}

	Registry<T> freeze();

	Holder.Reference<T> createIntrusiveHolder(T object);

	Optional<Holder.Reference<T>> get(int i);

	Optional<Holder.Reference<T>> get(Identifier identifier);

	Holder<T> wrapAsHolder(T object);

	default Iterable<Holder<T>> getTagOrEmpty(TagKey<T> tagKey) {
		return DataFixUtils.orElse(this.get(tagKey), List.<T>of());
	}

	Stream<HolderSet.Named<T>> getTags();

	default IdMap<Holder<T>> asHolderIdMap() {
		return new IdMap<Holder<T>>() {
			public int getId(Holder<T> holder) {
				return Registry.this.getId(holder.value());
			}

			public @Nullable Holder<T> byId(int i) {
				return (Holder<T>)Registry.this.get(i).orElse(null);
			}

			@Override
			public int size() {
				return Registry.this.size();
			}

			@Override
			public Iterator<Holder<T>> iterator() {
				return Registry.this.listElements().map(reference -> (Holder<T>)reference).iterator();
			}
		};
	}

	Registry.PendingTags<T> prepareTagReload(TagLoader.LoadResult<T> loadResult);

	interface PendingTags<T> {
		ResourceKey<? extends Registry<? extends T>> key();

		HolderLookup.RegistryLookup<T> lookup();

		void apply();

		int size();
	}
}
