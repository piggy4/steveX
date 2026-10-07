package net.minecraft.server.players;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.Nullable;

public record NameAndId(UUID id, String name) {
	public static final Codec<NameAndId> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(NameAndId::id), Codec.STRING.fieldOf("name").forGetter(NameAndId::name))
			.apply(instance, NameAndId::new)
	);

	public NameAndId(GameProfile gameProfile) {
		this(gameProfile.id(), gameProfile.name());
	}

	public NameAndId(com.mojang.authlib.yggdrasil.response.NameAndId nameAndId) {
		this(nameAndId.id(), nameAndId.name());
	}

	public static @Nullable NameAndId fromJson(JsonObject jsonObject) {
		if (jsonObject.has("uuid") && jsonObject.has("name")) {
			String string = jsonObject.get("uuid").getAsString();

			UUID uUID;
			try {
				uUID = UUID.fromString(string);
			} catch (Throwable throwable) {
				return null;
			}

			return new NameAndId(uUID, jsonObject.get("name").getAsString());
		} else {
			return null;
		}
	}

	public void appendTo(JsonObject jsonObject) {
		jsonObject.addProperty("uuid", this.id().toString());
		jsonObject.addProperty("name", this.name());
	}

	public static NameAndId createOffline(String string) {
		UUID uUID = UUIDUtil.createOfflinePlayerUUID(string);
		return new NameAndId(uUID, string);
	}
}
