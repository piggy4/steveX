package com.mojang.realmsclient.util.task;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.WorldDownload;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.exception.RetryCallException;
import com.mojang.realmsclient.gui.screens.RealmsDownloadLatestWorldScreen;
import com.mojang.realmsclient.gui.screens.RealmsGenericErrorScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class DownloadTask extends LongRunningTask {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Component TITLE = Component.translatable("mco.download.preparing");
	private final long realmId;
	private final int slot;
	private final Screen lastScreen;
	private final String downloadName;

	public DownloadTask(long l, int i, String string, Screen screen) {
		this.realmId = l;
		this.slot = i;
		this.lastScreen = screen;
		this.downloadName = string;
	}

	@Override
	public void run() {
		RealmsClient realmsClient = RealmsClient.getOrCreate();
		int i = 0;

		while (i < 25) {
			try {
				if (this.aborted()) {
					return;
				}

				WorldDownload worldDownload = realmsClient.requestDownloadInfo(this.realmId, this.slot);
				pause(1L);
				if (this.aborted()) {
					return;
				}

				setScreen(new RealmsDownloadLatestWorldScreen(this.lastScreen, worldDownload, this.downloadName, bl -> {}));
				return;
			} catch (RetryCallException retryCallException) {
				if (this.aborted()) {
					return;
				}

				pause(retryCallException.delaySeconds);
				i++;
			} catch (RealmsServiceException realmsServiceException) {
				if (this.aborted()) {
					return;
				}

				LOGGER.error("Couldn't download world data", realmsServiceException);
				setScreen(new RealmsGenericErrorScreen(realmsServiceException, this.lastScreen));
				return;
			} catch (Exception exception) {
				if (this.aborted()) {
					return;
				}

				LOGGER.error("Couldn't download world data", exception);
				this.error(exception);
				return;
			}
		}
	}

	@Override
	public Component getTitle() {
		return TITLE;
	}
}
