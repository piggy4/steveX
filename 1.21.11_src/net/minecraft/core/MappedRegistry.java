package net.minecraft.core;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class MappedRegistry<T> implements WritableRegistry<T> {
	private final ResourceKey<? extends Registry<T>> key;
	private final ObjectList<Holder.Reference<T>> byId = new ObjectArrayList<>(256);
	private final Reference2IntMap<T> toId = Util.make(
		new Reference2IntOpenHashMap<>(), reference2IntOpenHashMap -> reference2IntOpenHashMap.defaultReturnValue(-1)
	);
	private final Map<Identifier, Holder.Reference<T>> byLocation = new HashMap<>();
	private final Map<ResourceKey<T>, Holder.Reference<T>> byKey = new HashMap<>();
	private final Map<T, Holder.Reference<T>> byValue = new IdentityHashMap<>();
	private final Map<ResourceKey<T>, RegistrationInfo> registrationInfos = new IdentityHashMap<>();
	private Lifecycle registryLifecycle;
	private final Map<TagKey<T>, HolderSet.Named<T>> frozenTags = new IdentityHashMap<>();
	MappedRegistry.TagSet<T> allTags = MappedRegistry.TagSet.unbound();
	private boolean frozen;
	private @Nullable Map<T, Holder.Reference<T>> unregisteredIntrusiveHolders;

	@Override
	public Stream<HolderSet.Named<T>> listTags() {
		return this.getTags();
	}

	public MappedRegistry(ResourceKey<? extends Registry<T>> resourceKey, Lifecycle lifecycle) {
		this(resourceKey, lifecycle, false);
	}

	public MappedRegistry(ResourceKey<? extends Registry<T>> resourceKey, Lifecycle lifecycle, boolean bl) {
		this.key = resourceKey;
		this.registryLifecycle = lifecycle;
		if (bl) {
			this.unregisteredIntrusiveHolders = new IdentityHashMap<>();
		}
	}

	@Override
	public ResourceKey<? extends Registry<T>> key() {
		return this.key;
	}

	@Override
	public String toString() {
		return "Registry[" + this.key + " (" + this.registryLifecycle + ")]";
	}

	private void validateWrite() {
		if (this.frozen) {
			throw new IllegalStateException("Registry is already frozen");
		}
	}

	private void validateWrite(ResourceKey<T> resourceKey) {
		if (this.frozen) {
			throw new IllegalStateException("Registry is already frozen (trying to add key " + resourceKey + ")");
		}
	}

	@Override
	public Holder.Reference<T> register(ResourceKey<T> resourceKey, T object, RegistrationInfo registrationInfo) {
		this.validateWrite(resourceKey);
		Objects.requireNonNull(resourceKey);
		Objects.requireNonNull(object);
		if (this.byLocation.containsKey(resourceKey.identifier())) {
			throw (IllegalStateException)Util.pauseInIde(new IllegalStateException("Adding duplicate key '" + resourceKey + "' to registry"));
		}

		if (this.byValue.containsKey(object)) {
			throw (IllegalStateException)Util.pauseInIde(new IllegalStateException("Adding duplicate value '" + object + "' to registry"));
		}

		Holder.Reference<T> reference;
		if (this.unregisteredIntrusiveHolders != null) {
			reference = this.unregisteredIntrusiveHolders.remove(object);
			if (reference == null) {
				throw new AssertionError("Missing intrusive holder for " + resourceKey + ":" + object);
			}

			reference.bindKey(resourceKey);
		} else {
			reference = this.byKey.computeIfAbsent(resourceKey, resourceKeyx -> Holder.Reference.createStandAlone(this, resourceKeyx));
		}

		this.byKey.put(resourceKey, reference);
		this.byLocation.put(resourceKey.identifier(), reference);
		this.byValue.put(object, reference);
		int i = this.byId.size();
		this.byId.add(reference);
		this.toId.put(object, i);
		this.registrationInfos.put(resourceKey, registrationInfo);
		this.registryLifecycle = this.registryLifecycle.add(registrationInfo.lifecycle());
		return reference;
	}

	@Override
	public @Nullable Identifier getKey(T object) {
		Holder.Reference<T> reference = this.byValue.get(object);
		return reference != null ? reference.key().identifier() : null;
	}

	@Override
	public Optional<ResourceKey<T>> getResourceKey(T object) {
		return Optional.ofNullable(this.byValue.get(object)).map(Holder.Reference::key);
	}

	@Override
	public int getId(@Nullable T object) {
		return this.toId.getInt(object);
	}

	@Override
	public @Nullable T getValue(@Nullable ResourceKey<T> resourceKey) {
		return getValueFromNullable(this.byKey.get(resourceKey));
	}

	@Override
	public @Nullable T byId(int i) {
		return i >= 0 && i < this.byId.size() ? this.byId.get(i).value() : null;
	}

	@Override
	public Optional<Holder.Reference<T>> get(int i) {
		return i >= 0 && i < this.byId.size() ? Optional.ofNullable(this.byId.get(i)) : Optional.empty();
	}

	@Override
	public Optional<Holder.Reference<T>> get(Identifier identifier) {
		return Optional.ofNullable(this.byLocation.get(identifier));
	}

	@Override
	public Optional<Holder.Reference<T>> get(ResourceKey<T> resourceKey) {
		return Optional.ofNullable(this.byKey.get(resourceKey));
	}

	@Override
	public Optional<Holder.Reference<T>> getAny() {
		return this.byId.isEmpty() ? Optional.empty() : Optional.of(this.byId.getFirst());
	}

	@Override
	public Holder<T> wrapAsHolder(T object) {
		Holder.Reference<T> reference = this.byValue.get(object);
		return reference != null ? reference : Holder.direct(object);
	}

	Holder.Reference<T> getOrCreateHolderOrThrow(ResourceKey<T> resourceKey) {
		return this.byKey.computeIfAbsent(resourceKey, resourceKeyx -> {
			if (this.unregisteredIntrusiveHolders != null) {
				throw new IllegalStateException("This registry can't create new holders without value");
			}

			this.validateWrite(resourceKeyx);
			return Holder.Reference.createStandAlone(this, resourceKeyx);
		});
	}

	@Override
	public int size() {
		return this.byKey.size();
	}

	@Override
	public Optional<RegistrationInfo> registrationInfo(ResourceKey<T> resourceKey) {
		return Optional.ofNullable(this.registrationInfos.get(resourceKey));
	}

	@Override
	public Lifecycle registryLifecycle() {
		return this.registryLifecycle;
	}

	@Override
	public Iterator<T> iterator() {
		return Iterators.transform(this.byId.iterator(), Holder::value);
	}

	@Override
	public @Nullable T getValue(@Nullable Identifier identifier) {
		Holder.Reference<T> reference = this.byLocation.get(identifier);
		return getValueFromNullable(reference);
	}

	private static <T> @Nullable T getValueFromNullable(Holder.@Nullable Reference<T> reference) {
		return reference != null ? reference.value() : null;
	}

	@Override
	public Set<Identifier> keySet() {
		return Collections.unmodifiableSet(this.byLocation.keySet());
	}

	@Override
	public Set<ResourceKey<T>> registryKeySet() {
		return Collections.unmodifiableSet(this.byKey.keySet());
	}

	@Override
	public Set<Entry<ResourceKey<T>, T>> entrySet() {
		return Collections.unmodifiableSet(Util.mapValuesLazy(this.byKey, Holder::value).entrySet());
	}

	@Override
	public Stream<Holder.Reference<T>> listElements() {
		return this.byId.stream();
	}

	@Override
	public Stream<HolderSet.Named<T>> getTags() {
		return this.allTags.getTags();
	}

	HolderSet.Named<T> getOrCreateTagForRegistration(TagKey<T> tagKey) {
		return this.frozenTags.computeIfAbsent(tagKey, this::createTag);
	}

	private HolderSet.Named<T> createTag(TagKey<T> tagKey) {
		return new HolderSet.Named<>(this, tagKey);
	}

	@Override
	public boolean isEmpty() {
		return this.byKey.isEmpty();
	}

	@Override
	public Optional<Holder.Reference<T>> getRandom(RandomSource randomSource) {
		return Util.getRandomSafe(this.byId, randomSource);
	}

	@Override
	public boolean containsKey(Identifier identifier) {
		return this.byLocation.containsKey(identifier);
	}

	@Override
	public boolean containsKey(ResourceKey<T> resourceKey) {
		return this.byKey.containsKey(resourceKey);
	}

	@Override
	public Registry<T> freeze() {
		if (this.frozen) {
			return this;
		}

		this.frozen = true;
		this.byValue.forEach((object, reference) -> reference.bindValue((T)object));
		List<Identifier> list = this.byKey
			.entrySet()
			.stream()
			.filter(entry -> !entry.getValue().isBound())
			.map(entry -> entry.getKey().identifier())
			.sorted()
			.toList();
		if (!list.isEmpty()) {
			throw new IllegalStateException("Unbound values in registry " + this.key() + ": " + list);
		}

		if (this.unregisteredIntrusiveHolders != null) {
			if (!this.unregisteredIntrusiveHolders.isEmpty()) {
				throw new IllegalStateException("Some intrusive holders were not registered: " + this.unregisteredIntrusiveHolders.values());
			}

			this.unregisteredIntrusiveHolders = null;
		}

		if (this.allTags.isBound()) {
			throw new IllegalStateException("Tags already present before freezing");
		}

		List<Identifier> list2 = this.frozenTags
			.entrySet()
			.stream()
			.filter(entry -> !entry.getValue().isBound())
			.map(entry -> entry.getKey().location())
			.sorted()
			.toList();
		if (!list2.isEmpty()) {
			throw new IllegalStateException("Unbound tags in registry " + this.key() + ": " + list2);
		}

		this.allTags = MappedRegistry.TagSet.fromMap(this.frozenTags);
		this.refreshTagsInHolders();
		return this;
	}

	@Override
	public Holder.Reference<T> createIntrusiveHolder(T object) {
		if (this.unregisteredIntrusiveHolders == null) {
			throw new IllegalStateException("This registry can't create intrusive holders");
		}

		this.validateWrite();
		return this.unregisteredIntrusiveHolders.computeIfAbsent(object, objectx -> Holder.Reference.createIntrusive(this, (T)objectx));
	}

	@Override
	public Optional<HolderSet.Named<T>> get(TagKey<T> tagKey) {
		return this.allTags.get(tagKey);
	}

	private Holder.Reference<T> validateAndUnwrapTagElement(TagKey<T> tagKey, Holder<T> holder) {
		if (!holder.canSerializeIn(this)) {
			throw new IllegalStateException("Can't create named set " + tagKey + " containing value " + holder + " from outside registry " + this);
		} else if (holder instanceof Holder.Reference<T> reference) {
			return reference;
		} else {
			throw new IllegalStateException("Found direct holder " + holder + " value in tag " + tagKey);
		}
	}

	@Override
	public void bindTag(TagKey<T> tagKey, List<Holder<T>> list) {
		this.validateWrite();
		this.getOrCreateTagForRegistration(tagKey).bind(list);
	}

	void refreshTagsInHolders() {
		Map<Holder.Reference<T>, List<TagKey<T>>> map = new IdentityHashMap<>();
		this.byKey.values().forEach(reference -> map.put((Holder.Reference<T>)reference, new ArrayList<>()));
		this.allTags.forEach((tagKey, named) -> {
			for (Holder<T> holder : named) {
				Holder.Reference<T> reference = this.validateAndUnwrapTagElement((TagKey<T>)tagKey, holder);
				map.get(reference).add((TagKey<T>)tagKey);
			}
		});
		map.forEach(Holder.Reference::bindTags);
	}

	public void bindAllTagsToEmpty() {
		this.validateWrite();
		this.frozenTags.values().forEach(named -> named.bind(List.of()));
	}

	@Override
	public HolderGetter<T> createRegistrationLookup() {
		this.validateWrite();
		return new HolderGetter<T>() {
			@Override
			public Optional<Holder.Reference<T>> get(ResourceKey<T> resourceKey) {
				return Optional.of(this.getOrThrow(resourceKey));
			}

			@Override
			public Holder.Reference<T> getOrThrow(ResourceKey<T> resourceKey) {
				return MappedRegistry.this.getOrCreateHolderOrThrow(resourceKey);
			}

			@Override
			public Optional<HolderSet.Named<T>> get(TagKey<T> tagKey) {
				return Optional.of(this.getOrThrow(tagKey));
			}

			@Override
			public HolderSet.Named<T> getOrThrow(TagKey<T> tagKey) {
				return MappedRegistry.this.getOrCreateTagForRegistration(tagKey);
			}
		};
	}

	@Override
	public Registry.PendingTags<T> prepareTagReload(TagLoader.LoadResult<T> loadResult) {
		if (!this.frozen) {
			throw new IllegalStateException("Invalid method used for tag loading");
		}

		Builder<TagKey<T>, HolderSet.Named<T>> builder = ImmutableMap.builder();
		final Map<TagKey<T>, List<Holder<T>>> map = new HashMap<>();
		loadResult.tags().forEach((tagKey, list) -> {
			HolderSet.Named<T> named = this.frozenTags.get(tagKey);
			if (named == null) {
				named = this.createTag((TagKey<T>)tagKey);
			}

			builder.put((TagKey<T>)tagKey, named);
			map.put((TagKey<T>)tagKey, List.copyOf(list));
		});
		final ImmutableMap<TagKey<T>, HolderSet.Named<T>> immutableMap = builder.build();
		final HolderLookup.RegistryLookup<T> registryLookup = new HolderLookup.RegistryLookup.Delegate<T>() {
			@Override
			public HolderLookup.RegistryLookup<T> parent() {
				return MappedRegistry.this;
			}

			@Override
			public Optional<HolderSet.Named<T>> get(TagKey<T> tagKey) {
				return Optional.ofNullable(immutableMap.get(tagKey));
			}

			@Override
			public Stream<HolderSet.Named<T>> listTags() {
				return immutableMap.values().stream();
			}
		};
		return new Registry.PendingTags<T>() {
			@Override
			public ResourceKey<? extends Registry<? extends T>> key() {
				return MappedRegistry.this.key();
			}

			@Override
			public int size() {
				return map.size();
			}

			@Override
			public HolderLookup.RegistryLookup<T> lookup() {
				return registryLookup;
			}

			@Override
			public void apply() {
				immutableMap.forEach((tagKey, named) -> {
					List<Holder<T>> list = map.getOrDefault(tagKey, List.of());
					named.bind(list);
				});
				MappedRegistry.this.allTags = MappedRegistry.TagSet.fromMap(immutableMap);
				MappedRegistry.this.refreshTagsInHolders();
			}
		};
	}

	interface TagSet<T> {
		static <T> MappedRegistry.TagSet<T> unbound() {
			return new MappedRegistry.TagSet<T>() {
				@Override
				public boolean isBound() {
					return false;
				}

				@Override
				public Optional<HolderSet.Named<T>> get(TagKey<T> tagKey) {
					throw new IllegalStateException("Tags not bound, trying to access " + tagKey);
				}

				@Override
				public void forEach(BiConsumer<? super TagKey<T>, ? super HolderSet.Named<T>> biConsumer) {
					throw new IllegalStateException("Tags not bound");
				}

				@Override
				public Stream<HolderSet.Named<T>> getTags() {
					throw new IllegalStateException("Tags not bound");
				}
			};
		}

		static <T> MappedRegistry.TagSet<T> fromMap(Map<TagKey<T>, HolderSet.Named<T>> map) {
			return new MappedRegistry.TagSet<T>() {
				@Override
				public boolean isBound() {
					return true;
				}

				@Override
				public Optional<HolderSet.Named<T>> get(TagKey<T> tagKey) {
					return Optional.ofNullable(map.get(tagKey));
				}

				@Override
				public void forEach(BiConsumer<? super TagKey<T>, ? super HolderSet.Named<T>> biConsumer) {
					map.forEach(biConsumer);
				}

				@Override
				public Stream<HolderSet.Named<T>> getTags() {
					return map.values().stream();
				}
			};
		}

		boolean isBound();

		Optional<HolderSet.Named<T>> get(TagKey<T> tagKey);

		void forEach(BiConsumer<? super TagKey<T>, ? super HolderSet.Named<T>> biConsumer);

		Stream<HolderSet.Named<T>> getTags();
	}
}
