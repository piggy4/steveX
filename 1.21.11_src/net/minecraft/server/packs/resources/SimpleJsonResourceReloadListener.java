package net.minecraft.server.packs.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public abstract class SimpleJsonResourceReloadListener<T> extends SimplePreparableReloadListener<Map<Identifier, T>> {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final DynamicOps<JsonElement> ops;
	private final Codec<T> codec;
	private final FileToIdConverter lister;

	protected SimpleJsonResourceReloadListener(HolderLookup.Provider provider, Codec<T> codec, ResourceKey<? extends Registry<T>> resourceKey) {
		this(provider.createSerializationContext(JsonOps.INSTANCE), codec, FileToIdConverter.registry(resourceKey));
	}

	protected SimpleJsonResourceReloadListener(Codec<T> codec, FileToIdConverter fileToIdConverter) {
		this(JsonOps.INSTANCE, codec, fileToIdConverter);
	}

	private SimpleJsonResourceReloadListener(DynamicOps<JsonElement> dynamicOps, Codec<T> codec, FileToIdConverter fileToIdConverter) {
		this.ops = dynamicOps;
		this.codec = codec;
		this.lister = fileToIdConverter;
	}

	protected Map<Identifier, T> prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		Map<Identifier, T> map = new HashMap<>();
		scanDirectory(resourceManager, this.lister, this.ops, this.codec, map);
		return map;
	}

	public static <T> void scanDirectory(
		ResourceManager resourceManager, ResourceKey<? extends Registry<T>> resourceKey, DynamicOps<JsonElement> dynamicOps, Codec<T> codec, Map<Identifier, T> map
	) {
		scanDirectory(resourceManager, FileToIdConverter.registry(resourceKey), dynamicOps, codec, map);
	}

	public static <T> void scanDirectory(
		ResourceManager resourceManager, FileToIdConverter fileToIdConverter, DynamicOps<JsonElement> dynamicOps, Codec<T> codec, Map<Identifier, T> map
	) {
		for (Entry<Identifier, Resource> entry : fileToIdConverter.listMatchingResources(resourceManager).entrySet()) {
			Identifier identifier = entry.getKey();
			Identifier identifier2 = fileToIdConverter.fileToId(identifier);

			try (Reader reader = entry.getValue().openAsReader()) {
				codec.parse(dynamicOps, StrictJsonParser.parse(reader)).ifSuccess(object -> {
					if (map.putIfAbsent(identifier2, (T)object) != null) {
						throw new IllegalStateException("Duplicate data file ignored with ID " + identifier2);
					}
				}).ifError(error -> LOGGER.error("Couldn't parse data file '{}' from '{}': {}", identifier2, identifier, error));
			} catch (JsonParseException | IllegalArgumentException | IOException exception) {
				LOGGER.error("Couldn't parse data file '{}' from '{}'", identifier2, identifier, exception);
			}
		}
	}
}
