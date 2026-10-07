package com.mojang.realmsclient.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record PingResult(@SerializedName("pingResults") List<RegionPingResult> pingResults, @SerializedName("worldIds") List<Long> realmIds)
	implements ReflectionBasedSerialization {
}
