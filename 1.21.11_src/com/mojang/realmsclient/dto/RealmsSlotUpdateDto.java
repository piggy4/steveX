package com.mojang.realmsclient.dto;

import com.google.gson.annotations.SerializedName;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record RealmsSlotUpdateDto(
	@SerializedName("slotId") int slotId,
	@SerializedName("spawnProtection") int spawnProtection,
	@SerializedName("forceGameMode") boolean forceGameMode,
	@SerializedName("difficulty") int difficulty,
	@SerializedName("gameMode") int gameMode,
	@SerializedName("slotName") String slotName,
	@SerializedName("version") String version,
	@SerializedName("compatibility") RealmsServer.Compatibility compatibility,
	@SerializedName("worldTemplateId") long templateId,
	@SerializedName("worldTemplateImage") @Nullable String templateImage,
	@SerializedName("hardcore") boolean hardcore
) implements ReflectionBasedSerialization {
	public RealmsSlotUpdateDto(int i, RealmsWorldOptions realmsWorldOptions, boolean bl) {
		this(
			i,
			realmsWorldOptions.spawnProtection,
			realmsWorldOptions.forceGameMode,
			realmsWorldOptions.difficulty,
			realmsWorldOptions.gameMode,
			realmsWorldOptions.getSlotName(i),
			realmsWorldOptions.version,
			realmsWorldOptions.compatibility,
			realmsWorldOptions.templateId,
			realmsWorldOptions.templateImage,
			bl
		);
	}
}
