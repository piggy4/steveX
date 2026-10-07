package com.mojang.realmsclient.dto;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.LenientJsonParser;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public record BackupList(List<Backup> backups) {
	private static final Logger LOGGER = LogUtils.getLogger();

	public static BackupList parse(String string) {
		List<Backup> list = new ArrayList<>();

		try {
			JsonElement jsonElement = LenientJsonParser.parse(string).getAsJsonObject().get("backups");
			if (jsonElement.isJsonArray()) {
				for (JsonElement jsonElement2 : jsonElement.getAsJsonArray()) {
					Backup backup = Backup.parse(jsonElement2);
					if (backup != null) {
						list.add(backup);
					}
				}
			}
		} catch (Exception exception) {
			LOGGER.error("Could not parse BackupList", exception);
		}

		return new BackupList(list);
	}
}
