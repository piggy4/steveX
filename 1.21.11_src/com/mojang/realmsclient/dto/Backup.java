package com.mojang.realmsclient.dto;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class Backup extends ValueObject {
	private static final Logger LOGGER = LogUtils.getLogger();
	public final String backupId;
	public final Instant lastModified;
	public final long size;
	public boolean uploadedVersion;
	public final Map<String, String> metadata;
	public final Map<String, String> changeList = new HashMap<>();

	private Backup(String string, Instant instant, long l, Map<String, String> map) {
		this.backupId = string;
		this.lastModified = instant;
		this.size = l;
		this.metadata = map;
	}

	public ZonedDateTime lastModifiedDate() {
		return ZonedDateTime.ofInstant(this.lastModified, ZoneId.systemDefault());
	}

	public static @Nullable Backup parse(JsonElement jsonElement) {
		JsonObject jsonObject = jsonElement.getAsJsonObject();

		try {
			String string = JsonUtils.getStringOr("backupId", jsonObject, "");
			Instant instant = JsonUtils.getDateOr("lastModifiedDate", jsonObject);
			long l = JsonUtils.getLongOr("size", jsonObject, 0L);
			Map<String, String> map = new HashMap<>();
			if (jsonObject.has("metadata")) {
				JsonObject jsonObject2 = jsonObject.getAsJsonObject("metadata");

				for (Entry<String, JsonElement> entry : jsonObject2.entrySet()) {
					if (!entry.getValue().isJsonNull()) {
						map.put(entry.getKey(), entry.getValue().getAsString());
					}
				}
			}

			return new Backup(string, instant, l, map);
		} catch (Exception exception) {
			LOGGER.error("Could not parse Backup", exception);
			return null;
		}
	}
}
