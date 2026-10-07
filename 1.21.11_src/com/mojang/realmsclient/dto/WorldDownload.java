package com.mojang.realmsclient.dto;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.LenientJsonParser;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public record WorldDownload(String downloadLink, String resourcePackUrl, String resourcePackHash) {
	private static final Logger LOGGER = LogUtils.getLogger();

	public static WorldDownload parse(String string) {
		JsonObject jsonObject = LenientJsonParser.parse(string).getAsJsonObject();

		try {
			return new WorldDownload(
				JsonUtils.getStringOr("downloadLink", jsonObject, ""),
				JsonUtils.getStringOr("resourcePackUrl", jsonObject, ""),
				JsonUtils.getStringOr("resourcePackHash", jsonObject, "")
			);
		} catch (Exception exception) {
			LOGGER.error("Could not parse WorldDownload", exception);
			return new WorldDownload("", "", "");
		}
	}
}
