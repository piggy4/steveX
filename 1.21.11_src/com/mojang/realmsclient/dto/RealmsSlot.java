package com.mojang.realmsclient.dto;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class RealmsSlot implements ReflectionBasedSerialization {
	@SerializedName("slotId")
	public int slotId;
	@SerializedName("options")
	@JsonAdapter(RealmsSlot.RealmsWorldOptionsJsonAdapter.class)
	public RealmsWorldOptions options;
	@SerializedName("settings")
	public List<RealmsSetting> settings;

	public RealmsSlot(int i, RealmsWorldOptions realmsWorldOptions, List<RealmsSetting> list) {
		this.slotId = i;
		this.options = realmsWorldOptions;
		this.settings = list;
	}

	public static RealmsSlot defaults(int i) {
		return new RealmsSlot(i, RealmsWorldOptions.createEmptyDefaults(), List.of(RealmsSetting.hardcoreSetting(false)));
	}

	public RealmsSlot copy() {
		return new RealmsSlot(this.slotId, this.options.copy(), new ArrayList<>(this.settings));
	}

	public boolean isHardcore() {
		return RealmsSetting.isHardcore(this.settings);
	}

	@Environment(EnvType.CLIENT)
	static class RealmsWorldOptionsJsonAdapter extends TypeAdapter<RealmsWorldOptions> {
		private RealmsWorldOptionsJsonAdapter() {
		}

		public void write(JsonWriter jsonWriter, RealmsWorldOptions realmsWorldOptions) throws IOException {
			jsonWriter.jsonValue(new GuardedSerializer().toJson(realmsWorldOptions));
		}

		public RealmsWorldOptions read(JsonReader jsonReader) throws IOException {
			String string = jsonReader.nextString();
			return RealmsWorldOptions.parse(new GuardedSerializer(), string);
		}
	}
}
