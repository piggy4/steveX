package net.minecraft.server.packs.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.JsonOps;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.util.GsonHelper;

public interface ResourceMetadata {
	ResourceMetadata EMPTY = new ResourceMetadata() {
		@Override
		public <T> Optional<T> getSection(MetadataSectionType<T> metadataSectionType) {
			return Optional.empty();
		}
	};
	IoSupplier<ResourceMetadata> EMPTY_SUPPLIER = () -> EMPTY;

	static ResourceMetadata fromJsonStream(InputStream inputStream) throws IOException {
		try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			final JsonObject jsonObject = GsonHelper.parse(bufferedReader);
			return new ResourceMetadata() {
				@Override
				public <T> Optional<T> getSection(MetadataSectionType<T> metadataSectionType) {
					String string = metadataSectionType.name();
					if (jsonObject.has(string)) {
						T object = metadataSectionType.codec().parse(JsonOps.INSTANCE, jsonObject.get(string)).getOrThrow(JsonParseException::new);
						return Optional.of(object);
					} else {
						return Optional.empty();
					}
				}
			};
		}
	}

	<T> Optional<T> getSection(MetadataSectionType<T> metadataSectionType);

	default <T> Optional<MetadataSectionType.WithValue<T>> getTypedSection(MetadataSectionType<T> metadataSectionType) {
		return this.getSection(metadataSectionType).map(metadataSectionType::withValue);
	}

	default List<MetadataSectionType.WithValue<?>> getTypedSections(Collection<MetadataSectionType<?>> collection) {
		return collection.stream().map(this::getTypedSection).flatMap(Optional::stream).collect(Collectors.toUnmodifiableList());
	}
}
