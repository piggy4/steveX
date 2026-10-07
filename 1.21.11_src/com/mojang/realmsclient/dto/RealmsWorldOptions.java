package com.mojang.realmsclient.dto;

import com.google.gson.annotations.SerializedName;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.StringUtil;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class RealmsWorldOptions extends ValueObject implements ReflectionBasedSerialization {
	@SerializedName("spawnProtection")
	public int spawnProtection = 0;
	@SerializedName("forceGameMode")
	public boolean forceGameMode = false;
	@SerializedName("difficulty")
	public int difficulty = 2;
	@SerializedName("gameMode")
	public int gameMode = 0;
	@SerializedName("slotName")
	private String slotName = "";
	@SerializedName("version")
	public String version = "";
	@SerializedName("compatibility")
	public RealmsServer.Compatibility compatibility = RealmsServer.Compatibility.UNVERIFIABLE;
	@SerializedName("worldTemplateId")
	public long templateId = -1L;
	@SerializedName("worldTemplateImage")
	public @Nullable String templateImage = null;
	@Exclude
	public boolean empty;

	private RealmsWorldOptions() {
	}

	public RealmsWorldOptions(int i, int j, int k, boolean bl, String string, String string2, RealmsServer.Compatibility compatibility) {
		this.spawnProtection = i;
		this.difficulty = j;
		this.gameMode = k;
		this.forceGameMode = bl;
		this.slotName = string;
		this.version = string2;
		this.compatibility = compatibility;
	}

	public static RealmsWorldOptions createDefaults() {
		return new RealmsWorldOptions();
	}

	public static RealmsWorldOptions createDefaultsWith(GameType gameType, Difficulty difficulty, boolean bl, String string, String string2) {
		RealmsWorldOptions realmsWorldOptions = createDefaults();
		realmsWorldOptions.difficulty = difficulty.getId();
		realmsWorldOptions.gameMode = gameType.getId();
		realmsWorldOptions.slotName = string2;
		realmsWorldOptions.version = string;
		return realmsWorldOptions;
	}

	public static RealmsWorldOptions createFromSettings(LevelSettings levelSettings, String string) {
		return createDefaultsWith(levelSettings.gameType(), levelSettings.difficulty(), levelSettings.hardcore(), string, levelSettings.levelName());
	}

	public static RealmsWorldOptions createEmptyDefaults() {
		RealmsWorldOptions realmsWorldOptions = createDefaults();
		realmsWorldOptions.setEmpty(true);
		return realmsWorldOptions;
	}

	public void setEmpty(boolean bl) {
		this.empty = bl;
	}

	public static RealmsWorldOptions parse(GuardedSerializer guardedSerializer, String string) {
		RealmsWorldOptions realmsWorldOptions = guardedSerializer.fromJson(string, RealmsWorldOptions.class);
		if (realmsWorldOptions == null) {
			return createDefaults();
		}

		finalize(realmsWorldOptions);
		return realmsWorldOptions;
	}

	private static void finalize(RealmsWorldOptions realmsWorldOptions) {
		if (realmsWorldOptions.slotName == null) {
			realmsWorldOptions.slotName = "";
		}

		if (realmsWorldOptions.version == null) {
			realmsWorldOptions.version = "";
		}

		if (realmsWorldOptions.compatibility == null) {
			realmsWorldOptions.compatibility = RealmsServer.Compatibility.UNVERIFIABLE;
		}
	}

	public String getSlotName(int i) {
		if (StringUtil.isBlank(this.slotName)) {
			return this.empty ? I18n.get("mco.configure.world.slot.empty") : this.getDefaultSlotName(i);
		} else {
			return this.slotName;
		}
	}

	public String getDefaultSlotName(int i) {
		return I18n.get("mco.configure.world.slot", i);
	}

	public RealmsWorldOptions copy() {
		return new RealmsWorldOptions(this.spawnProtection, this.difficulty, this.gameMode, this.forceGameMode, this.slotName, this.version, this.compatibility);
	}
}
