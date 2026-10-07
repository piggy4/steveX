package com.mojang.realmsclient.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.util.UndashedUuid;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class JsonUtils {
	public static <T> T getRequired(String string, JsonObject jsonObject, Function<JsonObject, T> function) {
		JsonElement jsonElement = jsonObject.get(string);
		if (jsonElement == null || jsonElement.isJsonNull()) {
			throw new IllegalStateException("Missing required property: " + string);
		} else if (!jsonElement.isJsonObject()) {
			throw new IllegalStateException("Required property " + string + " was not a JsonObject as espected");
		} else {
			return function.apply(jsonElement.getAsJsonObject());
		}
	}

	public static <T> @Nullable T getOptional(String string, JsonObject jsonObject, Function<JsonObject, T> function) {
		JsonElement jsonElement = jsonObject.get(string);
		if (jsonElement == null || jsonElement.isJsonNull()) {
			return null;
		} else if (!jsonElement.isJsonObject()) {
			throw new IllegalStateException("Required property " + string + " was not a JsonObject as espected");
		} else {
			return function.apply(jsonElement.getAsJsonObject());
		}
	}

	public static String getRequiredString(String string, JsonObject jsonObject) {
		String string2 = getStringOr(string, jsonObject, null);
		if (string2 == null) {
			throw new IllegalStateException("Missing required property: " + string);
		} else {
			return string2;
		}
	}

	@Contract("_,_,!null->!null;_,_,null->_")
	public static @Nullable String getStringOr(String string, JsonObject jsonObject, @Nullable String string2) {
		JsonElement jsonElement = jsonObject.get(string);
		if (jsonElement != null) {
			return jsonElement.isJsonNull() ? string2 : jsonElement.getAsString();
		} else {
			return string2;
		}
	}

	@Contract("_,_,!null->!null;_,_,null->_")
	public static @Nullable UUID getUuidOr(String string, JsonObject jsonObject, @Nullable UUID uUID) {
		String string2 = getStringOr(string, jsonObject, null);
		return string2 == null ? uUID : UndashedUuid.fromStringLenient(string2);
	}

	public static int getIntOr(String string, JsonObject jsonObject, int i) {
		JsonElement jsonElement = jsonObject.get(string);
		if (jsonElement != null) {
			return jsonElement.isJsonNull() ? i : jsonElement.getAsInt();
		} else {
			return i;
		}
	}

	public static long getLongOr(String string, JsonObject jsonObject, long l) {
		JsonElement jsonElement = jsonObject.get(string);
		if (jsonElement != null) {
			return jsonElement.isJsonNull() ? l : jsonElement.getAsLong();
		} else {
			return l;
		}
	}

	public static boolean getBooleanOr(String string, JsonObject jsonObject, boolean bl) {
		JsonElement jsonElement = jsonObject.get(string);
		if (jsonElement != null) {
			return jsonElement.isJsonNull() ? bl : jsonElement.getAsBoolean();
		} else {
			return bl;
		}
	}

	public static Instant getDateOr(String string, JsonObject jsonObject) {
		JsonElement jsonElement = jsonObject.get(string);
		return jsonElement != null ? Instant.ofEpochMilli(Long.parseLong(jsonElement.getAsString())) : Instant.EPOCH;
	}
}
