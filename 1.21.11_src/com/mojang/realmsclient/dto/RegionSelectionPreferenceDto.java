package com.mojang.realmsclient.dto;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class RegionSelectionPreferenceDto implements ReflectionBasedSerialization {
	public static final RegionSelectionPreferenceDto DEFAULT = new RegionSelectionPreferenceDto(RegionSelectionPreference.AUTOMATIC_OWNER, null);
	@SerializedName("regionSelectionPreference")
	@JsonAdapter(RegionSelectionPreference.RegionSelectionPreferenceJsonAdapter.class)
	public final RegionSelectionPreference regionSelectionPreference;
	@SerializedName("preferredRegion")
	@JsonAdapter(RealmsRegion.RealmsRegionJsonAdapter.class)
	public @Nullable RealmsRegion preferredRegion;

	public RegionSelectionPreferenceDto(RegionSelectionPreference regionSelectionPreference, @Nullable RealmsRegion realmsRegion) {
		this.regionSelectionPreference = regionSelectionPreference;
		this.preferredRegion = realmsRegion;
	}

	public RegionSelectionPreferenceDto copy() {
		return new RegionSelectionPreferenceDto(this.regionSelectionPreference, this.preferredRegion);
	}
}
