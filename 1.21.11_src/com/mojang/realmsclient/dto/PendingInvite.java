package com.mojang.realmsclient.dto;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import java.time.Instant;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public record PendingInvite(String invitationId, String realmName, String realmOwnerName, UUID realmOwnerUuid, Instant date) {
	private static final Logger LOGGER = LogUtils.getLogger();

	public static @Nullable PendingInvite parse(JsonObject jsonObject) {
		try {
			return new PendingInvite(
				JsonUtils.getStringOr("invitationId", jsonObject, ""),
				JsonUtils.getStringOr("worldName", jsonObject, ""),
				JsonUtils.getStringOr("worldOwnerName", jsonObject, ""),
				JsonUtils.getUuidOr("worldOwnerUuid", jsonObject, Util.NIL_UUID),
				JsonUtils.getDateOr("date", jsonObject)
			);
		} catch (Exception exception) {
			LOGGER.error("Could not parse PendingInvite", exception);
			return null;
		}
	}
}
