package net.minecraft.client.quickplay;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.RealmsMainScreen;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.dto.RealmsServerList;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.gui.screens.RealmsLongRunningMcoTaskScreen;
import com.mojang.realmsclient.util.task.GetServerDetailsTask;
import java.util.List;
import java.util.concurrent.ExecutionException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class QuickPlay {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final Component ERROR_TITLE = Component.translatable("quickplay.error.title");
	private static final Component INVALID_IDENTIFIER = Component.translatable("quickplay.error.invalid_identifier");
	private static final Component REALM_CONNECT = Component.translatable("quickplay.error.realm_connect");
	private static final Component REALM_PERMISSION = Component.translatable("quickplay.error.realm_permission");
	private static final Component TO_TITLE = Component.translatable("gui.toTitle");
	private static final Component TO_WORLD_LIST = Component.translatable("gui.toWorld");
	private static final Component TO_REALMS_LIST = Component.translatable("gui.toRealms");

	public static void connect(Minecraft minecraft, GameConfig.QuickPlayVariant quickPlayVariant, RealmsClient realmsClient) {
		if (!quickPlayVariant.isEnabled()) {
			LOGGER.error("Quick play disabled");
			minecraft.setScreen(new TitleScreen());
		} else {
			switch (quickPlayVariant) {
				case GameConfig.QuickPlayMultiplayerData quickPlayMultiplayerData:
					joinMultiplayerWorld(minecraft, quickPlayMultiplayerData.serverAddress());
					break;
				case GameConfig.QuickPlayRealmsData quickPlayRealmsData:
					joinRealmsWorld(minecraft, realmsClient, quickPlayRealmsData.realmId());
					break;
				case GameConfig.QuickPlaySinglePlayerData quickPlaySinglePlayerData:
					String string = quickPlaySinglePlayerData.worldId();
					if (StringUtil.isBlank(string)) {
						string = getLatestSingleplayerWorld(minecraft.getLevelSource());
					}

					joinSingleplayerWorld(minecraft, string);
					break;
				case GameConfig.QuickPlayDisabled quickPlayDisabled:
					LOGGER.error("Quick play disabled");
					minecraft.setScreen(new TitleScreen());
					break;
				default:
					throw new MatchException(null, null);
			}
		}
	}

	private static @Nullable String getLatestSingleplayerWorld(LevelStorageSource levelStorageSource) {
		try {
			List<LevelSummary> list = levelStorageSource.loadLevelSummaries(levelStorageSource.findLevelCandidates()).get();
			if (list.isEmpty()) {
				LOGGER.warn("no latest singleplayer world found");
				return null;
			} else {
				return list.getFirst().getLevelId();
			}
		} catch (InterruptedException | ExecutionException exception) {
			LOGGER.error("failed to load singleplayer world summaries", exception);
			return null;
		}
	}

	private static void joinSingleplayerWorld(Minecraft minecraft, @Nullable String string) {
		if (!StringUtil.isBlank(string) && minecraft.getLevelSource().levelExists(string)) {
			minecraft.createWorldOpenFlows().openWorld(string, () -> minecraft.setScreen(new TitleScreen()));
		} else {
			Screen screen = new SelectWorldScreen(new TitleScreen());
			minecraft.setScreen(new DisconnectedScreen(screen, ERROR_TITLE, INVALID_IDENTIFIER, TO_WORLD_LIST));
		}
	}

	private static void joinMultiplayerWorld(Minecraft minecraft, String string) {
		ServerList serverList = new ServerList(minecraft);
		serverList.load();
		ServerData serverData = serverList.get(string);
		if (serverData == null) {
			serverData = new ServerData(I18n.get("selectServer.defaultName"), string, ServerData.Type.OTHER);
			serverList.add(serverData, true);
			serverList.save();
		}

		ServerAddress serverAddress = ServerAddress.parseString(string);
		ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), minecraft, serverAddress, serverData, true, null);
	}

	private static void joinRealmsWorld(Minecraft minecraft, RealmsClient realmsClient, String string) {
		long l;
		RealmsServerList realmsServerList;
		try {
			l = Long.parseLong(string);
			realmsServerList = realmsClient.listRealms();
		} catch (NumberFormatException numberFormatException) {
			Screen screen = new RealmsMainScreen(new TitleScreen());
			minecraft.setScreen(new DisconnectedScreen(screen, ERROR_TITLE, INVALID_IDENTIFIER, TO_REALMS_LIST));
			return;
		} catch (RealmsServiceException realmsServiceException) {
			Screen screen = new TitleScreen();
			minecraft.setScreen(new DisconnectedScreen(screen, ERROR_TITLE, REALM_CONNECT, TO_TITLE));
			return;
		}

		RealmsServer realmsServer = realmsServerList.servers().stream().filter(realmsServerx -> realmsServerx.id == l).findFirst().orElse(null);
		if (realmsServer == null) {
			Screen screen = new RealmsMainScreen(new TitleScreen());
			minecraft.setScreen(new DisconnectedScreen(screen, ERROR_TITLE, REALM_PERMISSION, TO_REALMS_LIST));
		} else {
			TitleScreen titleScreen = new TitleScreen();
			minecraft.setScreen(new RealmsLongRunningMcoTaskScreen(titleScreen, new GetServerDetailsTask(titleScreen, realmsServer)));
		}
	}
}
