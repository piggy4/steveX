package net.minecraft.realms;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.dto.RealmsServer;
import java.net.InetSocketAddress;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.quickplay.QuickPlayLog;
import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class RealmsConnect {
	static final Logger LOGGER = LogUtils.getLogger();
	final Screen onlineScreen;
	volatile boolean aborted;
	@Nullable Connection connection;

	public RealmsConnect(Screen screen) {
		this.onlineScreen = screen;
	}

	public void connect(RealmsServer realmsServer, ServerAddress serverAddress) {
		final Minecraft minecraft = Minecraft.getInstance();
		minecraft.prepareForMultiplayer();
		minecraft.getNarrator().saySystemNow(Component.translatable("mco.connect.success"));
		final String string = serverAddress.getHost();
		final int i = serverAddress.getPort();
		(new Thread("Realms-connect-task") {
				@Override
				public void run() {
					InetSocketAddress inetSocketAddress = null;

					try {
						inetSocketAddress = new InetSocketAddress(string, i);
						if (RealmsConnect.this.aborted) {
							return;
						}

						RealmsConnect.this.connection = Connection.connectToServer(
							inetSocketAddress, EventLoopGroupHolder.remote(minecraft.options.useNativeTransport()), minecraft.getDebugOverlay().getBandwidthLogger()
						);
						if (RealmsConnect.this.aborted) {
							return;
						}

						ClientHandshakePacketListenerImpl clientHandshakePacketListenerImpl = new ClientHandshakePacketListenerImpl(
							RealmsConnect.this.connection,
							minecraft,
							realmsServer.toServerData(string),
							RealmsConnect.this.onlineScreen,
							false,
							null,
							component -> {},
							new LevelLoadTracker(),
							null
						);
						if (realmsServer.isMinigameActive()) {
							clientHandshakePacketListenerImpl.setMinigameName(realmsServer.minigameName);
						}

						if (RealmsConnect.this.aborted) {
							return;
						}

						RealmsConnect.this.connection.initiateServerboundPlayConnection(string, i, clientHandshakePacketListenerImpl);
						if (RealmsConnect.this.aborted) {
							return;
						}

						RealmsConnect.this.connection.send(new ServerboundHelloPacket(minecraft.getUser().getName(), minecraft.getUser().getProfileId()));
						minecraft.updateReportEnvironment(ReportEnvironment.realm(realmsServer));
						minecraft.quickPlayLog()
							.setWorldData(QuickPlayLog.Type.REALMS, String.valueOf(realmsServer.id), Objects.requireNonNullElse(realmsServer.name, "unknown"));
						minecraft.getDownloadedPackSource().configureForServerControl(RealmsConnect.this.connection, ServerPackManager.PackPromptStatus.ALLOWED);
					} catch (Exception exception) {
						minecraft.getDownloadedPackSource().cleanupAfterDisconnect();
						if (RealmsConnect.this.aborted) {
							return;
						}

						RealmsConnect.LOGGER.error("Couldn't connect to world", exception);
						String stringx = exception.toString();
						if (inetSocketAddress != null) {
							String string2 = inetSocketAddress + ":" + i;
							stringx = stringx.replaceAll(string2, "");
						}

						DisconnectedScreen disconnectedScreen = new DisconnectedScreen(
							RealmsConnect.this.onlineScreen,
							Component.translatable("mco.connect.failed"),
							Component.translatable("disconnect.genericReason", stringx),
							CommonComponents.GUI_BACK
						);
						minecraft.execute(() -> minecraft.setScreen(disconnectedScreen));
					}
				}
			})
			.start();
	}

	public void abort() {
		this.aborted = true;
		if (this.connection != null && this.connection.isConnected()) {
			this.connection.disconnect(Component.translatable("disconnect.genericReason"));
			this.connection.handleDisconnection();
		}
	}

	public void tick() {
		if (this.connection != null) {
			if (this.connection.isConnected()) {
				this.connection.tick();
			} else {
				this.connection.handleDisconnection();
			}
		}
	}
}
