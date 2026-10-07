package net.minecraft.util.datafix;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Optional;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.LenientJsonParser;
import net.minecraft.util.StrictJsonParser;

public class LegacyComponentDataFixUtils {
	private static final String EMPTY_CONTENTS = createTextComponentJson("");

	public static <T> Dynamic<T> createPlainTextComponent(DynamicOps<T> dynamicOps, String string) {
		String string2 = createTextComponentJson(string);
		return new Dynamic<>(dynamicOps, dynamicOps.createString(string2));
	}

	public static <T> Dynamic<T> createEmptyComponent(DynamicOps<T> dynamicOps) {
		return new Dynamic<>(dynamicOps, dynamicOps.createString(EMPTY_CONTENTS));
	}

	public static String createTextComponentJson(String string) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("text", string);
		return GsonHelper.toStableString(jsonObject);
	}

	public static String createTranslatableComponentJson(String string) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("translate", string);
		return GsonHelper.toStableString(jsonObject);
	}

	public static <T> Dynamic<T> createTranslatableComponent(DynamicOps<T> dynamicOps, String string) {
		String string2 = createTranslatableComponentJson(string);
		return new Dynamic<>(dynamicOps, dynamicOps.createString(string2));
	}

	public static String rewriteFromLenient(String string) {
		if (!string.isEmpty() && !string.equals("null")) {
			char c = string.charAt(0);
			char d = string.charAt(string.length() - 1);
			if (c == '"' && d == '"' || c == '{' && d == '}' || c == '[' && d == ']') {
				try {
					JsonElement jsonElement = LenientJsonParser.parse(string);
					if (jsonElement.isJsonPrimitive()) {
						return createTextComponentJson(jsonElement.getAsString());
					}

					return GsonHelper.toStableString(jsonElement);
				} catch (JsonParseException var4) {
				}
			}

			return createTextComponentJson(string);
		} else {
			return EMPTY_CONTENTS;
		}
	}

	public static boolean isStrictlyValidJson(Dynamic<?> dynamic) {
		return dynamic.asString().result().filter(string -> {
			try {
				StrictJsonParser.parse(string);
				return true;
			} catch (JsonParseException jsonParseException) {
				return false;
			}
		}).isPresent();
	}

	public static Optional<String> extractTranslationString(String string) {
		try {
			JsonElement jsonElement = LenientJsonParser.parse(string);
			if (jsonElement.isJsonObject()) {
				JsonObject jsonObject = jsonElement.getAsJsonObject();
				JsonElement jsonElement2 = jsonObject.get("translate");
				if (jsonElement2 != null && jsonElement2.isJsonPrimitive()) {
					return Optional.of(jsonElement2.getAsString());
				}
			}
		} catch (JsonParseException var4) {
		}

		return Optional.empty();
	}
}
