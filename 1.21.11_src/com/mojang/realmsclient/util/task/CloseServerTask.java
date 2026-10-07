package com.mojang.realmsclient.util.task;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.exception.RetryCallException;
import com.mojang.realmsclient.gui.screens.configuration.RealmsConfigureWorldScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class CloseServerTask extends LongRunningTask {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Component TITLE = Component.translatable("mco.configure.world.closing");
	private final RealmsServer serverData;
	private final RealmsConfigureWorldScreen configureScreen;

	public CloseServerTask(RealmsServer realmsServer, RealmsConfigureWorldScreen realmsConfigureWorldScreen) {
		this.serverData = realmsServer;
		this.configureScreen = realmsConfigureWorldScreen;
	}

	@Override
	public void run() {
		RealmsClient realmsClient = RealmsClient.getOrCreate();

		for (int i = 0; i < 25; i++) {
			if (this.aborted()) {
				return;
			}

			try {
				boolean bl = realmsClient.close(this.serverData.id);
				if (bl) {
					this.configureScreen.stateChanged();
					this.serverData.state = RealmsServer.State.CLOSED;
					setScreen(this.configureScreen);
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

				LOGGER.error("Failed to close server", exception);
				this.error(exception);
			}
		}
	}

	@Override
	public Component getTitle() {
		return TITLE;
	}
}
