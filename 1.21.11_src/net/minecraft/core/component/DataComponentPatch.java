package net.minecraft.core.component;

import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import org.jspecify.annotations.Nullable;

public final class DataComponentPatch {
	public static final DataComponentPatch EMPTY = new DataComponentPatch(Reference2ObjectMaps.emptyMap());
	public static final Codec<DataComponentPatch> CODEC = Codec.dispatchedMap(DataComponentPatch.PatchKey.CODEC, DataComponentPatch.PatchKey::valueCodec)
		.xmap(map -> {
			if (map.isEmpty()) {
				return EMPTY;
			}

			Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2ObjectMap = new Reference2ObjectArrayMap<>(map.size());

			for (Entry<DataComponentPatch.PatchKey, ?> entry : map.entrySet()) {
				DataComponentPatch.PatchKey patchKey = entry.getKey();
				if (patchKey.removed()) {
					reference2ObjectMap.put(patchKey.type(), Optional.empty());
				} else {
					reference2ObjectMap.put(patchKey.type(), Optional.of(entry.getValue()));
				}
			}

			return new DataComponentPatch(reference2ObjectMap);
		}, dataComponentPatch -> {
			Reference2ObjectMap<DataComponentPatch.PatchKey, Object> reference2ObjectMap = new Reference2ObjectArrayMap<>(dataComponentPatch.map.size());

			for (Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(dataComponentPatch.map)) {
				DataComponentType<?> dataComponentType = entry.getKey();
				if (!dataComponentType.isTransient()) {
					Optional<?> optional = entry.getValue();
					if (optional.isPresent()) {
						reference2ObjectMap.put(new DataComponentPatch.PatchKey(dataComponentType, false), optional.get());
					} else {
						reference2ObjectMap.put(new DataComponentPatch.PatchKey(dataComponentType, true), Unit.INSTANCE);
					}
				}
			}

			return reference2ObjectMap;
		});
	public static final StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> STREAM_CODEC = createStreamCodec(new DataComponentPatch.CodecGetter() {
		@Override
		public <T> StreamCodec<RegistryFriendlyByteBuf, T> apply(DataComponentType<T> dataComponentType) {
			return dataComponentType.streamCodec().cast();
		}
	});
	public static final StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> DELIMITED_STREAM_CODEC = createStreamCodec(new DataComponentPatch.CodecGetter() {
		@Override
		public <T> StreamCodec<RegistryFriendlyByteBuf, T> apply(DataComponentType<T> dataComponentType) {
			StreamCodec<RegistryFriendlyByteBuf, T> streamCodec = dataComponentType.streamCodec().cast();
			return streamCodec.apply(ByteBufCodecs.registryFriendlyLengthPrefixed(Integer.MAX_VALUE));
		}
	});
	private static final String REMOVED_PREFIX = "!";
	final Reference2ObjectMap<DataComponentType<?>, Optional<?>> map;

	private static StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> createStreamCodec(DataComponentPatch.CodecGetter codecGetter) {
		return new StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch>() {
			public DataComponentPatch decode(RegistryFriendlyByteBuf registryFriendlyByteBuf) {
				int i = registryFriendlyByteBuf.readVarInt();
				int j = registryFriendlyByteBuf.readVarInt();
				if (i == 0 && j == 0) {
					return DataComponentPatch.EMPTY;
				}

				int k = i + j;
				Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2ObjectMap = new Reference2ObjectArrayMap<>(Math.min(k, 65536));

				for (int l = 0; l < i; l++) {
					DataComponentType<?> dataComponentType = DataComponentType.STREAM_CODEC.decode(registryFriendlyByteBuf);
					Object object = codecGetter.apply(dataComponentType).decode(registryFriendlyByteBuf);
					reference2ObjectMap.put(dataComponentType, Optional.of(object));
				}

				for (int l = 0; l < j; l++) {
					DataComponentType<?> dataComponentType = DataComponentType.STREAM_CODEC.decode(registryFriendlyByteBuf);
					reference2ObjectMap.put(dataComponentType, Optional.empty());
				}

				return new DataComponentPatch(reference2ObjectMap);
			}

			public void encode(RegistryFriendlyByteBuf registryFriendlyByteBuf, DataComponentPatch dataComponentPatch) {
				if (dataComponentPatch.isEmpty()) {
					registryFriendlyByteBuf.writeVarInt(0);
					registryFriendlyByteBuf.writeVarInt(0);
				} else {
					int i = 0;
					int j = 0;

					for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(
						dataComponentPatch.map
					)) {
						if (entry.getValue().isPresent()) {
							i++;
						} else {
							j++;
						}
					}

					registryFriendlyByteBuf.writeVarInt(i);
					registryFriendlyByteBuf.writeVarInt(j);

					for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(
						dataComponentPatch.map
					)) {
						Optional<?> optional = entry.getValue();
						if (optional.isPresent()) {
							DataComponentType<?> dataComponentType = entry.getKey();
							DataComponentType.STREAM_CODEC.encode(registryFriendlyByteBuf, dataComponentType);
							this.encodeComponent(registryFriendlyByteBuf, dataComponentType, optional.get());
						}
					}

					for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(
						dataComponentPatch.map
					)) {
						if (entry.getValue().isEmpty()) {
							DataComponentType<?> dataComponentType2 = entry.getKey();
							DataComponentType.STREAM_CODEC.encode(registryFriendlyByteBuf, dataComponentType2);
						}
					}
				}
			}

			private <T> void encodeComponent(RegistryFriendlyByteBuf registryFriendlyByteBuf, DataComponentType<T> dataComponentType, Object object) {
				codecGetter.apply(dataComponentType).encode(registryFriendlyByteBuf, (T)object);
			}
		};
	}

	DataComponentPatch(Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2ObjectMap) {
		this.map = reference2ObjectMap;
	}

	public static DataComponentPatch.Builder builder() {
		return new DataComponentPatch.Builder();
	}

	public <T> @Nullable Optional<? extends T> get(DataComponentType<? extends T> dataComponentType) {
		return (Optional<? extends T>)this.map.get(dataComponentType);
	}

	public Set<Entry<DataComponentType<?>, Optional<?>>> entrySet() {
		return this.map.entrySet();
	}

	public int size() {
		return this.map.size();
	}

	public DataComponentPatch forget(Predicate<DataComponentType<?>> predicate) {
		if (this.isEmpty()) {
			return EMPTY;
		}

		Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2ObjectMap = new Reference2ObjectArrayMap<>(this.map);
		reference2ObjectMap.keySet().removeIf(predicate);
		return reference2ObjectMap.isEmpty() ? EMPTY : new DataComponentPatch(reference2ObjectMap);
	}

	public boolean isEmpty() {
		return this.map.isEmpty();
	}

	public DataComponentPatch.SplitResult split() {
		if (this.isEmpty()) {
			return DataComponentPatch.SplitResult.EMPTY;
		}

		DataComponentMap.Builder builder = DataComponentMap.builder();
		Set<DataComponentType<?>> set = Sets.newIdentityHashSet();
		this.map.forEach((dataComponentType, optional) -> {
			if (optional.isPresent()) {
				builder.setUnchecked((DataComponentType<?>)dataComponentType, optional.get());
			} else {
				set.add((DataComponentType<?>)dataComponentType);
			}
		});
		return new DataComponentPatch.SplitResult(builder.build(), set);
	}

	@Override
	public boolean equals(Object object) {
		return this == object ? true : object instanceof DataComponentPatch dataComponentPatch && this.map.equals(dataComponentPatch.map);
	}

	@Override
	public int hashCode() {
		return this.map.hashCode();
	}

	@Override
	public String toString() {
		return toString(this.map);
	}

	static String toString(Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2ObjectMap) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append('{');
		boolean bl = true;

		for (Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(reference2ObjectMap)) {
			if (bl) {
				bl = false;
			} else {
				stringBuilder.append(", ");
			}

			Optional<?> optional = entry.getValue();
			if (optional.isPresent()) {
				stringBuilder.append(entry.getKey());
				stringBuilder.append("=>");
				stringBuilder.append(optional.get());
			} else {
				stringBuilder.append("!");
				stringBuilder.append(entry.getKey());
			}
		}

		stringBuilder.append('}');
		return stringBuilder.toString();
	}

	public static class Builder {
		private final Reference2ObjectMap<DataComponentType<?>, Optional<?>> map = new Reference2ObjectArrayMap<>();

		Builder() {
		}

		public <T> DataComponentPatch.Builder set(DataComponentType<T> dataComponentType, T object) {
			this.map.put(dataComponentType, Optional.of(object));
			return this;
		}

		public <T> DataComponentPatch.Builder remove(DataComponentType<T> dataComponentType) {
			this.map.put(dataComponentType, Optional.empty());
			return this;
		}

		public <T> DataComponentPatch.Builder set(TypedDataComponent<T> typedDataComponent) {
			return this.set(typedDataComponent.type(), typedDataComponent.value());
		}

		public DataComponentPatch build() {
			return this.map.isEmpty() ? DataComponentPatch.EMPTY : new DataComponentPatch(this.map);
		}
	}

	@FunctionalInterface
	interface CodecGetter {
		<T> StreamCodec<? super RegistryFriendlyByteBuf, T> apply(DataComponentType<T> dataComponentType);
	}

	record PatchKey(DataComponentType<?> type, boolean removed) {
		public static final Codec<DataComponentPatch.PatchKey> CODEC = Codec.STRING
			.flatXmap(
				string -> {
					boolean bl = string.startsWith("!");
					if (bl) {
						string = string.substring("!".length());
					}

					Identifier identifier = Identifier.tryParse(string);
					DataComponentType<?> dataComponentType = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(identifier);
					if (dataComponentType == null) {
						return DataResult.error(() -> "No component with type: '" + identifier + "'");
					} else {
						return dataComponentType.isTransient()
							? DataResult.error(() -> "'" + identifier + "' is not a persistent component")
							: DataResult.success(new DataComponentPatch.PatchKey(dataComponentType, bl));
					}
				},
				patchKey -> {
					DataComponentType<?> dataComponentType = patchKey.type();
					Identifier identifier = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(dataComponentType);
					return identifier == null
						? DataResult.error(() -> "Unregistered component: " + dataComponentType)
						: DataResult.success(patchKey.removed() ? "!" + identifier : identifier.toString());
				}
			);

		public Codec<?> valueCodec() {
			return this.removed ? Codec.EMPTY.codec() : this.type.codecOrThrow();
		}
	}

	public record SplitResult(DataComponentMap added, Set<DataComponentType<?>> removed) {
		public static final DataComponentPatch.SplitResult EMPTY = new DataComponentPatch.SplitResult(DataComponentMap.EMPTY, Set.of());
	}
}
