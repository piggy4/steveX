package com.mojang.realmsclient.dto;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public enum RegionSelectionPreference {
	AUTOMATIC_PLAYER(0, "realms.configuration.region_preference.automatic_player"),
	AUTOMATIC_OWNER(1, "realms.configuration.region_preference.automatic_owner"),
	MANUAL(2, "");

	public static final RegionSelectionPreference DEFAULT_SELECTION = AUTOMATIC_PLAYER;
	public final int id;
	public final String translationKey;

	RegionSelectionPreference(final int j, final String string2) {
		this.id = j;
		this.translationKey = string2;
	}

	@Environment(EnvType.CLIENT)
	public static class RegionSelectionPreferenceJsonAdapter extends TypeAdapter<RegionSelectionPreference> {
		private static final Logger LOGGER = LogUtils.getLogger();

		public void write(JsonWriter jsonWriter, RegionSelectionPreference regionSelectionPreference) throws IOException {
			jsonWriter.value(regionSelectionPreference.id);
		}

		public RegionSelectionPreference read(JsonReader jsonReader) throws IOException {
			int i = jsonReader.nextInt();

			for (RegionSelectionPreference regionSelectionPreference : RegionSelectionPreference.values()) {
				if (regionSelectionPreference.id == i) {
					return regionSelectionPreference;
				}
			}

			LOGGER.warn("Unsupported RegionSelectionPreference {}", i);
			return RegionSelectionPreference.DEFAULT_SELECTION;
		}
	}
}
