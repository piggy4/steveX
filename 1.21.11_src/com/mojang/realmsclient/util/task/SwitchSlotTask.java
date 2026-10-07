package com.mojang.realmsclient.util.task;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.exception.RetryCallException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class SwitchSlotTask extends LongRunningTask {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Component TITLE = Component.translatable("mco.minigame.world.slot.screen.title");
	private final long realmId;
	private final int slot;
	private final Runnable callback;

	public SwitchSlotTask(long l, int i, Runnable runnable) {
		this.realmId = l;
		this.slot = i;
		this.callback = runnable;
	}

	@Override
	public void run() {
		RealmsClient realmsClient = RealmsClient.getOrCreate();

		for (int i = 0; i < 25; i++) {
			try {
				if (this.aborted()) {
					return;
				}

				if (realmsClient.switchSlot(this.realmId, this.slot)) {
					this.callback.run();
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

				LOGGER.error("Couldn't switch world!");
				this.error(exception);
			}
		}
	}

	@Override
	public Component getTitle() {
		return TITLE;
	}
}
