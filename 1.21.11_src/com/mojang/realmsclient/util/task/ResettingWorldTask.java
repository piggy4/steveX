package com.mojang.realmsclient.util.task;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.exception.RetryCallException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public abstract class ResettingWorldTask extends LongRunningTask {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final long serverId;
	private final Component title;
	private final Runnable callback;

	public ResettingWorldTask(long l, Component component, Runnable runnable) {
		this.serverId = l;
		this.title = component;
		this.callback = runnable;
	}

	protected abstract void sendResetRequest(RealmsClient realmsClient, long l) throws RealmsServiceException;

	@Override
	public void run() {
		RealmsClient realmsClient = RealmsClient.getOrCreate();
		int i = 0;

		while (i < 25) {
			try {
				if (this.aborted()) {
					return;
				}

				this.sendResetRequest(realmsClient, this.serverId);
				if (this.aborted()) {
					return;
				}

				this.callback.run();
				return;
			} catch (RetryCallException retryCallException) {
				if (this.aborted()) {
					return;
				}

				pause(retryCallException.delaySeconds);
				i++;
			} catch (Exception exception) {
				if (this.aborted()) {
					return;
				}

				LOGGER.error("Couldn't reset world");
				this.error(exception);
				return;
			}
		}
	}

	@Override
	public Component getTitle() {
		return this.title;
	}
}
