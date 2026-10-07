package net.minecraft.resources;

import com.google.common.collect.MapMaker;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;

public class ResourceKey<T> {
	private static final ConcurrentMap<ResourceKey.InternKey, ResourceKey<?>> VALUES = new MapMaker().weakValues().makeMap();
	private final Identifier registryName;
	private final Identifier identifier;

	public static <T> Codec<ResourceKey<T>> codec(ResourceKey<? extends Registry<T>> resourceKey) {
		return Identifier.CODEC.xmap(identifier -> create(resourceKey, identifier), ResourceKey::identifier);
	}

	public static <T> StreamCodec<ByteBuf, ResourceKey<T>> streamCodec(ResourceKey<? extends Registry<T>> resourceKey) {
		return Identifier.STREAM_CODEC.map(identifier -> create(resourceKey, identifier), ResourceKey::identifier);
	}

	public static <T> ResourceKey<T> create(ResourceKey<? extends Registry<T>> resourceKey, Identifier identifier) {
		return create(resourceKey.identifier, identifier);
	}

	public static <T> ResourceKey<Registry<T>> createRegistryKey(Identifier identifier) {
		return create(Registries.ROOT_REGISTRY_NAME, identifier);
	}

	private static <T> ResourceKey<T> create(Identifier identifier, Identifier identifier2) {
		return (ResourceKey<T>)VALUES.computeIfAbsent(
			new ResourceKey.InternKey(identifier, identifier2), internKey -> new ResourceKey(internKey.registry, internKey.identifier)
		);
	}

	private ResourceKey(Identifier identifier, Identifier identifier2) {
		this.registryName = identifier;
		this.identifier = identifier2;
	}

	@Override
	public String toString() {
		return "ResourceKey[" + this.registryName + " / " + this.identifier + "]";
	}

	public boolean isFor(ResourceKey<? extends Registry<?>> resourceKey) {
		return this.registryName.equals(resourceKey.identifier());
	}

	public <E> Optional<ResourceKey<E>> cast(ResourceKey<? extends Registry<E>> resourceKey) {
		return this.isFor(resourceKey) ? Optional.of((ResourceKey<E>)this) : Optional.empty();
	}

	public Identifier identifier() {
		return this.identifier;
	}

	public Identifier registry() {
		return this.registryName;
	}

	public ResourceKey<Registry<T>> registryKey() {
		return createRegistryKey(this.registryName);
	}

	record InternKey(Identifier registry, Identifier identifier) {
	}
}
