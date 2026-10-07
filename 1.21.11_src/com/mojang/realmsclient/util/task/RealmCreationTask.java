package com.mojang.realmsclient.util.task;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.exception.RealmsServiceException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class RealmCreationTask extends LongRunningTask {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Component TITLE = Component.translatable("mco.create.world.wait");
	private final String name;
	private final String motd;
	private final long realmId;

	public RealmCreationTask(long l, String string, String string2) {
		this.realmId = l;
		this.name = string;
		this.motd = string2;
	}

	@Override
	public void run() {
		RealmsClient realmsClient = RealmsClient.getOrCreate();

		try {
			realmsClient.initializeRealm(this.realmId, this.name, this.motd);
		} catch (RealmsServiceException realmsServiceException) {
			LOGGER.error("Couldn't create world", realmsServiceException);
			this.error(realmsServiceException);
		} catch (Exception exception) {
			LOGGER.error("Could not create world", exception);
			this.error(exception);
		}
	}

	@Override
	public Component getTitle() {
		return TITLE;
	}
}
