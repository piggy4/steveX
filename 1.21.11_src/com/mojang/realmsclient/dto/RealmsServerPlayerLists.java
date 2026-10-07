package com.mojang.realmsclient.dto;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.LenientJsonParser;
import net.minecraft.world.item.component.ResolvableProfile;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public record RealmsServerPlayerLists(Map<Long, List<ResolvableProfile>> servers) {
	private static final Logger LOGGER = LogUtils.getLogger();

	public static RealmsServerPlayerLists parse(String string) {
		Builder<Long, List<ResolvableProfile>> builder = ImmutableMap.builder();

		try {
			JsonObject jsonObject = GsonHelper.parse(string);
			if (GsonHelper.isArrayNode(jsonObject, "lists")) {
				for (JsonElement jsonElement : jsonObject.getAsJsonArray("lists")) {
					JsonObject jsonObject2 = jsonElement.getAsJsonObject();
					String string2 = JsonUtils.getStringOr("playerList", jsonObject2, null);
					List<ResolvableProfile> list;
					if (string2 != null) {
						JsonElement jsonElement2 = LenientJsonParser.parse(string2);
						if (jsonElement2.isJsonArray()) {
							list = parsePlayers(jsonElement2.getAsJsonArray());
						} else {
							list = Lists.newArrayList();
						}
					} else {
						list = Lists.newArrayList();
					}

					builder.put(JsonUtils.getLongOr("serverId", jsonObject2, -1L), list);
				}
			}
		} catch (Exception exception) {
			LOGGER.error("Could not parse RealmsServerPlayerLists", exception);
		}

		return new RealmsServerPlayerLists(builder.build());
	}

	private static List<ResolvableProfile> parsePlayers(JsonArray jsonArray) {
		List<ResolvableProfile> list = new ArrayList<>(jsonArray.size());

		for (JsonElement jsonElement : jsonArray) {
			if (jsonElement.isJsonObject()) {
				UUID uUID = JsonUtils.getUuidOr("playerId", jsonElement.getAsJsonObject(), null);
				if (uUID != null && !Minecraft.getInstance().isLocalPlayer(uUID)) {
					list.add(ResolvableProfile.createUnresolved(uUID));
				}
			}
		}

		return list;
	}

	public List<ResolvableProfile> getProfileResultsFor(long l) {
		List<ResolvableProfile> list = this.servers.get(l);
		return list != null ? list : List.of();
	}
}
