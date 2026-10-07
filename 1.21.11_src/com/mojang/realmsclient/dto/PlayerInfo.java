package com.mojang.realmsclient.dto;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.mojang.util.UUIDTypeAdapter;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class PlayerInfo extends ValueObject implements ReflectionBasedSerialization {
	@SerializedName("name")
	public final String name;
	@SerializedName("uuid")
	@JsonAdapter(UUIDTypeAdapter.class)
	public final UUID uuid;
	@SerializedName("operator")
	public boolean operator;
	@SerializedName("accepted")
	public final boolean accepted;
	@SerializedName("online")
	public final boolean online;

	public PlayerInfo(String string, UUID uUID, boolean bl, boolean bl2, boolean bl3) {
		this.name = string;
		this.uuid = uUID;
		this.operator = bl;
		this.accepted = bl2;
		this.online = bl3;
	}
}
