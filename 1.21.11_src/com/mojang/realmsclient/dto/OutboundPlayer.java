package com.mojang.realmsclient.dto;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.mojang.util.UUIDTypeAdapter;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class OutboundPlayer implements ReflectionBasedSerialization {
	@SerializedName("name")
	public @Nullable String name;
	@SerializedName("uuid")
	@JsonAdapter(UUIDTypeAdapter.class)
	public @Nullable UUID uuid;
}
