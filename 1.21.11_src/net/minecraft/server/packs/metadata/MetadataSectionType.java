package net.minecraft.server.packs.metadata;

import com.mojang.serialization.Codec;
import java.util.Optional;

public record MetadataSectionType<T>(String name, Codec<T> codec) {
	public MetadataSectionType.WithValue<T> withValue(T object) {
		return new MetadataSectionType.WithValue<>(this, object);
	}

	public record WithValue<T>(MetadataSectionType<T> type, T value) {
		public <U> Optional<U> unwrapToType(MetadataSectionType<U> metadataSectionType) {
			return metadataSectionType == this.type ? Optional.of(this.value) : Optional.empty();
		}
	}
}
