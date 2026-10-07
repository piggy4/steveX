package com.mojang.realmsclient.dto;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.LenientJsonParser;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public record Ops(Set<String> ops) {
	private static final Logger LOGGER = LogUtils.getLogger();

	public static Ops parse(String string) {
		Set<String> set = new HashSet<>();

		try {
			JsonObject jsonObject = LenientJsonParser.parse(string).getAsJsonObject();
			JsonElement jsonElement = jsonObject.get("ops");
			if (jsonElement.isJsonArray()) {
				for (JsonElement jsonElement2 : jsonElement.getAsJsonArray()) {
					set.add(jsonElement2.getAsString());
				}
			}
		} catch (Exception exception) {
			LOGGER.error("Could not parse Ops", exception);
		}

		return new Ops(set);
	}
}
