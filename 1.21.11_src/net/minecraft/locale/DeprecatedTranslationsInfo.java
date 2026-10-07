package net.minecraft.locale;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.minecraft.util.StrictJsonParser;
import org.slf4j.Logger;

public record DeprecatedTranslationsInfo(List<String> removed, Map<String, String> renamed) {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final DeprecatedTranslationsInfo EMPTY = new DeprecatedTranslationsInfo(List.of(), Map.of());
	public static final Codec<DeprecatedTranslationsInfo> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				Codec.STRING.listOf().fieldOf("removed").forGetter(DeprecatedTranslationsInfo::removed),
				Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("renamed").forGetter(DeprecatedTranslationsInfo::renamed)
			)
			.apply(instance, DeprecatedTranslationsInfo::new)
	);

	public static DeprecatedTranslationsInfo loadFromJson(InputStream inputStream) {
		JsonElement jsonElement = StrictJsonParser.parse(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
		return CODEC.parse(JsonOps.INSTANCE, jsonElement).getOrThrow(string -> new IllegalStateException("Failed to parse deprecated language data: " + string));
	}

	public static DeprecatedTranslationsInfo loadFromResource(String string) {
		try (InputStream inputStream = Language.class.getResourceAsStream(string)) {
			if (inputStream != null) {
				return loadFromJson(inputStream);
			}
		} catch (Exception exception) {
			LOGGER.error("Failed to read {}", string, exception);
		}

		return EMPTY;
	}

	public static DeprecatedTranslationsInfo loadFromDefaultResource() {
		return loadFromResource("/assets/minecraft/lang/deprecated.json");
	}

	public void applyToMap(Map<String, String> map) {
		for (String string : this.removed) {
			map.remove(string);
		}

		this.renamed.forEach((stringx, string2) -> {
			String string3 = map.remove(stringx);
			if (string3 == null) {
				LOGGER.warn("Missing translation key for rename: {}", stringx);
				map.remove(string2);
			} else {
				map.put(string2, string3);
			}
		});
	}
}
