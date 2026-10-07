package com.mojang.realmsclient.dto;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.LenientJsonParser;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public record PendingInvitesList(List<PendingInvite> pendingInvites) {
	private static final Logger LOGGER = LogUtils.getLogger();

	public static PendingInvitesList parse(String string) {
		List<PendingInvite> list = new ArrayList<>();

		try {
			JsonObject jsonObject = LenientJsonParser.parse(string).getAsJsonObject();
			if (jsonObject.get("invites").isJsonArray()) {
				for (JsonElement jsonElement : jsonObject.get("invites").getAsJsonArray()) {
					PendingInvite pendingInvite = PendingInvite.parse(jsonElement.getAsJsonObject());
					if (pendingInvite != null) {
						list.add(pendingInvite);
					}
				}
			}
		} catch (Exception exception) {
			LOGGER.error("Could not parse PendingInvitesList", exception);
		}

		return new PendingInvitesList(list);
	}
}
