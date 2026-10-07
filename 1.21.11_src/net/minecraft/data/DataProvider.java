package net.minecraft.data;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Util;
import org.slf4j.Logger;

public interface DataProvider {
	ToIntFunction<String> FIXED_ORDER_FIELDS = Util.make(new Object2IntOpenHashMap<>(), object2IntOpenHashMap -> {
		object2IntOpenHashMap.put("type", 0);
		object2IntOpenHashMap.put("parent", 1);
		object2IntOpenHashMap.defaultReturnValue(2);
	});
	Comparator<String> KEY_COMPARATOR = Comparator.comparingInt(FIXED_ORDER_FIELDS).thenComparing(string -> (String)string);
	Logger LOGGER = LogUtils.getLogger();

	CompletableFuture<?> run(CachedOutput cachedOutput);

	String getName();

	static <T> CompletableFuture<?> saveAll(CachedOutput cachedOutput, Codec<T> codec, PackOutput.PathProvider pathProvider, Map<Identifier, T> map) {
		return saveAll(cachedOutput, codec, pathProvider::json, map);
	}

	static <T, E> CompletableFuture<?> saveAll(CachedOutput cachedOutput, Codec<E> codec, Function<T, Path> function, Map<T, E> map) {
		return saveAll(cachedOutput, object -> codec.encodeStart(JsonOps.INSTANCE, (E)object).getOrThrow(), function, map);
	}

	static <T, E> CompletableFuture<?> saveAll(CachedOutput cachedOutput, Function<E, JsonElement> function, Function<T, Path> function2, Map<T, E> map) {
		return CompletableFuture.allOf(map.entrySet().stream().map(entry -> {
			Path path = function2.apply(entry.getKey());
			JsonElement jsonElement = function.apply(entry.getValue());
			return saveStable(cachedOutput, jsonElement, path);
		}).toArray(CompletableFuture[]::new));
	}

	static <T> CompletableFuture<?> saveStable(CachedOutput cachedOutput, HolderLookup.Provider provider, Codec<T> codec, T object, Path path) {
		RegistryOps<JsonElement> registryOps = provider.createSerializationContext(JsonOps.INSTANCE);
		return saveStable(cachedOutput, registryOps, codec, object, path);
	}

	static <T> CompletableFuture<?> saveStable(CachedOutput cachedOutput, Codec<T> codec, T object, Path path) {
		return saveStable(cachedOutput, JsonOps.INSTANCE, codec, object, path);
	}

	private static <T> CompletableFuture<?> saveStable(CachedOutput cachedOutput, DynamicOps<JsonElement> dynamicOps, Codec<T> codec, T object, Path path) {
		JsonElement jsonElement = codec.encodeStart(dynamicOps, object).getOrThrow();
		return saveStable(cachedOutput, jsonElement, path);
	}

	static CompletableFuture<?> saveStable(CachedOutput cachedOutput, JsonElement jsonElement, Path path) {
		return CompletableFuture.runAsync(() -> {
			try {
				ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
				HashingOutputStream hashingOutputStream = new HashingOutputStream(Hashing.sha1(), byteArrayOutputStream);

				try (JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(hashingOutputStream, StandardCharsets.UTF_8))) {
					jsonWriter.setSerializeNulls(false);
					jsonWriter.setIndent("  ");
					GsonHelper.writeValue(jsonWriter, jsonElement, KEY_COMPARATOR);
				}

				cachedOutput.writeIfNeeded(path, byteArrayOutputStream.toByteArray(), hashingOutputStream.hash());
			} catch (IOException iOException) {
				LOGGER.error("Failed to save file to {}", path, iOException);
			}
		}, Util.backgroundExecutor().forName("saveStable"));
	}

	@FunctionalInterface
	interface Factory<T extends DataProvider> {
		T create(PackOutput packOutput);
	}
}
