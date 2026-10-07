package com.mojang.realmsclient.dto;

import com.google.gson.annotations.SerializedName;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record RegionDataDto(@SerializedName("regionName") RealmsRegion region, @SerializedName("serviceQuality") ServiceQuality serviceQuality)
	implements ReflectionBasedSerialization {
}
