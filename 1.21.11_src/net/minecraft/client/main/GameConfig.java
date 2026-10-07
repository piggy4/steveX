package net.minecraft.client.main;

import com.mojang.blaze3d.platform.DisplayData;
import java.io.File;
import java.net.Proxy;
import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.User;
import net.minecraft.client.resources.IndexedAssetSource;
import net.minecraft.util.StringUtil;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class GameConfig {
	public final GameConfig.UserData user;
	public final DisplayData display;
	public final GameConfig.FolderData location;
	public final GameConfig.GameData game;
	public final GameConfig.QuickPlayData quickPlay;

	public GameConfig(
		GameConfig.UserData userData, DisplayData displayData, GameConfig.FolderData folderData, GameConfig.GameData gameData, GameConfig.QuickPlayData quickPlayData
	) {
		this.user = userData;
		this.display = displayData;
		this.location = folderData;
		this.game = gameData;
		this.quickPlay = quickPlayData;
	}

	@Environment(EnvType.CLIENT)
	public static class FolderData {
		public final File gameDirectory;
		public final File resourcePackDirectory;
		public final File assetDirectory;
		public final @Nullable String assetIndex;

		public FolderData(File file, File file2, File file3, @Nullable String string) {
			this.gameDirectory = file;
			this.resourcePackDirectory = file2;
			this.assetDirectory = file3;
			this.assetIndex = string;
		}

		public Path getExternalAssetSource() {
			return this.assetIndex == null ? this.assetDirectory.toPath() : IndexedAssetSource.createIndexFs(this.assetDirectory.toPath(), this.assetIndex);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class GameData {
		public final boolean demo;
		public final String launchVersion;
		public final String versionType;
		public final boolean disableMultiplayer;
		public final boolean disableChat;
		public final boolean captureTracyImages;
		public final boolean renderDebugLabels;
		public final boolean offlineDeveloperMode;

		public GameData(boolean bl, String string, String string2, boolean bl2, boolean bl3, boolean bl4, boolean bl5, boolean bl6) {
			this.demo = bl;
			this.launchVersion = string;
			this.versionType = string2;
			this.disableMultiplayer = bl2;
			this.disableChat = bl3;
			this.captureTracyImages = bl4;
			this.renderDebugLabels = bl5;
			this.offlineDeveloperMode = bl6;
		}
	}

	@Environment(EnvType.CLIENT)
	public record QuickPlayData(@Nullable String logPath, GameConfig.QuickPlayVariant variant) {
		public boolean isEnabled() {
			return this.variant.isEnabled();
		}
	}

	@Environment(EnvType.CLIENT)
	public record QuickPlayDisabled() implements GameConfig.QuickPlayVariant {
		@Override
		public boolean isEnabled() {
			return false;
		}
	}

	@Environment(EnvType.CLIENT)
	public record QuickPlayMultiplayerData(String serverAddress) implements GameConfig.QuickPlayVariant {
		@Override
		public boolean isEnabled() {
			return !StringUtil.isBlank(this.serverAddress);
		}
	}

	@Environment(EnvType.CLIENT)
	public record QuickPlayRealmsData(String realmId) implements GameConfig.QuickPlayVariant {
		@Override
		public boolean isEnabled() {
			return !StringUtil.isBlank(this.realmId);
		}
	}

	@Environment(EnvType.CLIENT)
	public record QuickPlaySinglePlayerData(@Nullable String worldId) implements GameConfig.QuickPlayVariant {
		@Override
		public boolean isEnabled() {
			return true;
		}
	}

	@Environment(EnvType.CLIENT)
	public sealed interface QuickPlayVariant
		permits GameConfig.QuickPlaySinglePlayerData,
		GameConfig.QuickPlayMultiplayerData,
		GameConfig.QuickPlayRealmsData,
		GameConfig.QuickPlayDisabled {
		GameConfig.QuickPlayVariant DISABLED = new GameConfig.QuickPlayDisabled();

		boolean isEnabled();
	}

	@Environment(EnvType.CLIENT)
	public static class UserData {
		public final User user;
		public final Proxy proxy;

		public UserData(User user, Proxy proxy) {
			this.user = user;
			this.proxy = proxy;
		}
	}
}
