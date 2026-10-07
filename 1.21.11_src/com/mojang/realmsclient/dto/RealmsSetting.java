package com.mojang.realmsclient.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record RealmsSetting(@SerializedName("name") String name, @SerializedName("value") String value) implements ReflectionBasedSerialization {
	public static RealmsSetting hardcoreSetting(boolean bl) {
		return new RealmsSetting("hardcore", Boolean.toString(bl));
	}

	public static boolean isHardcore(List<RealmsSetting> list) {
		for (RealmsSetting realmsSetting : list) {
			if (realmsSetting.name().equals("hardcore")) {
				return Boolean.parseBoolean(realmsSetting.value());
			}
		}

		return false;
	}
}
