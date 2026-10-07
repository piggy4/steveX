package com.mojang.realmsclient.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record RealmsConfigurationDto(
	@SerializedName("options") RealmsSlotUpdateDto options,
	@SerializedName("settings") List<RealmsSetting> settings,
	@SerializedName("regionSelectionPreference") @Nullable RegionSelectionPreferenceDto regionSelectionPreference,
	@SerializedName("description") @Nullable RealmsDescriptionDto description
) implements ReflectionBasedSerialization {
}
