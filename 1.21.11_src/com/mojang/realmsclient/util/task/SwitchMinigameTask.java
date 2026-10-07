package com.mojang.realmsclient.util.task;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.WorldTemplate;
import com.mojang.realmsclient.exception.RetryCallException;
import com.mojang.realmsclient.gui.screens.configuration.RealmsConfigureWorldScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class SwitchMinigameTask extends LongRunningTask {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Component TITLE = Component.translatable("mco.minigame.world.starting.screen.title");
	private final long realmId;
	private final WorldTemplate worldTemplate;
	private final RealmsConfigureWorldScreen nextScreen;

	public SwitchMinigameTask(long l, WorldTemplate worldTemplate, RealmsConfigureWorldScreen realmsConfigureWorldScreen) {
		this.realmId = l;
		this.worldTemplate = worldTemplate;
		this.nextScreen = realmsConfigureWorldScreen;
	}

	@Override
	public void run() {
		RealmsClient realmsClient = RealmsClient.getOrCreate();

		for (int i = 0; i < 25; i++) {
			try {
				if (this.aborted()) {
					return;
				}

				if (realmsClient.putIntoMinigameMode(this.realmId, this.worldTemplate.id())) {
					setScreen(this.nextScreen);
					break;
				}
			} catch (RetryCallException retryCallException) {
				if (this.aborted()) {
					return;
				}

				pause(retryCallException.delaySeconds);
			} catch (Exception exception) {
				if (this.aborted()) {
					return;
				}

				LOGGER.error("Couldn't start mini game!");
				this.error(exception);
			}
		}
	}

	@Override
	public Component getTitle() {
		return TITLE;
	}
}
