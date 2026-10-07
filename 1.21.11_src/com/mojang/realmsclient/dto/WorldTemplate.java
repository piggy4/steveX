package com.mojang.realmsclient.dto;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public record WorldTemplate(
	String id,
	String name,
	String version,
	String author,
	String link,
	@Nullable String image,
	String trailer,
	String recommendedPlayers,
	WorldTemplate.WorldTemplateType type
) {
	private static final Logger LOGGER = LogUtils.getLogger();

	public static @Nullable WorldTemplate parse(JsonObject jsonObject) {
		try {
			String string = JsonUtils.getStringOr("type", jsonObject, null);
			return new WorldTemplate(
				JsonUtils.getStringOr("id", jsonObject, ""),
				JsonUtils.getStringOr("name", jsonObject, ""),
				JsonUtils.getStringOr("version", jsonObject, ""),
				JsonUtils.getStringOr("author", jsonObject, ""),
				JsonUtils.getStringOr("link", jsonObject, ""),
				JsonUtils.getStringOr("image", jsonObject, null),
				JsonUtils.getStringOr("trailer", jsonObject, ""),
				JsonUtils.getStringOr("recommendedPlayers", jsonObject, ""),
				string == null ? WorldTemplate.WorldTemplateType.WORLD_TEMPLATE : WorldTemplate.WorldTemplateType.valueOf(string)
			);
		} catch (Exception exception) {
			LOGGER.error("Could not parse WorldTemplate", exception);
			return null;
		}
	}

	@Environment(EnvType.CLIENT)
	public enum WorldTemplateType {
		WORLD_TEMPLATE,
		MINIGAME,
		ADVENTUREMAP,
		EXPERIENCE,
		INSPIRATION;
	}
}
