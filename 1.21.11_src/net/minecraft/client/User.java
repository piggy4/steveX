package net.minecraft.client;

import com.mojang.util.UndashedUuid;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class User {
	private final String name;
	private final UUID uuid;
	private final String accessToken;
	private final Optional<String> xuid;
	private final Optional<String> clientId;

	public User(String string, UUID uUID, String string2, Optional<String> optional, Optional<String> optional2) {
		this.name = string;
		this.uuid = uUID;
		this.accessToken = string2;
		this.xuid = optional;
		this.clientId = optional2;
	}

	public String getSessionId() {
		return "token:" + this.accessToken + ":" + UndashedUuid.toString(this.uuid);
	}

	public UUID getProfileId() {
		return this.uuid;
	}

	public String getName() {
		return this.name;
	}

	public String getAccessToken() {
		return this.accessToken;
	}

	public Optional<String> getClientId() {
		return this.clientId;
	}

	public Optional<String> getXuid() {
		return this.xuid;
	}
}
