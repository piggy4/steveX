package net.minecraft.tags;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.fabricmc.fabric.api.tag.FabricTagKey;
import net.minecraft.core.Registry;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.tag.FabricTagKey} injected by mod fabric-convention-tags-v2</p>
 */
public record TagKey<T>(ResourceKey<? extends Registry<T>> registry, Identifier location) implements FabricTagKey {
	private static final Interner<TagKey<?>> VALUES = Interners.newWeakInterner();

	@Deprecated
	public TagKey {
	}

	public static <T> Codec<TagKey<T>> codec(ResourceKey<? extends Registry<T>> resourceKey) {
		return Identifier.CODEC.xmap(identifier -> create(resourceKey, identifier), TagKey::location);
	}

	public static <T> Codec<TagKey<T>> hashedCodec(ResourceKey<? extends Registry<T>> resourceKey) {
		return Codec.STRING
			.comapFlatMap(
				string -> string.startsWith("#")
					? Identifier.read(string.substring(1)).map(identifier -> create(resourceKey, identifier))
					: DataResult.error(() -> "Not a tag id"),
				tagKey -> "#" + tagKey.location
			);
	}

	public static <T> StreamCodec<ByteBuf, TagKey<T>> streamCodec(ResourceKey<? extends Registry<T>> resourceKey) {
		return Identifier.STREAM_CODEC.map(identifier -> create(resourceKey, identifier), TagKey::location);
	}

	public static <T> TagKey<T> create(ResourceKey<? extends Registry<T>> resourceKey, Identifier identifier) {
		return (TagKey<T>)VALUES.intern(new TagKey<>(resourceKey, identifier));
	}

	public boolean isFor(ResourceKey<? extends Registry<?>> resourceKey) {
		return this.registry == resourceKey;
	}

	public <E> Optional<TagKey<E>> cast(ResourceKey<? extends Registry<E>> resourceKey) {
		return this.isFor(resourceKey) ? Optional.of((TagKey<E>)this) : Optional.empty();
	}

	@Override
	public String toString() {
		return "TagKey[" + this.registry.identifier() + " / " + this.location + "]";
	}
}
