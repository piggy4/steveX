package net.minecraft.client.multiplayer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.dialog.DialogConnectionAccess;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.client.gui.screens.dialog.DialogScreens;
import net.minecraft.client.gui.screens.dialog.WaitingForResponseScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.core.Holder;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.ServerboundPacketListener;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundCustomReportDetailsPacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ClientboundServerLinksPacket;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.network.protocol.common.ClientboundStoreCookiePacket;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public abstract class ClientCommonPacketListenerImpl implements ClientCommonPacketListener {
	private static final Component GENERIC_DISCONNECT_MESSAGE = Component.translatable("disconnect.lost");
	private static final Logger LOGGER = LogUtils.getLogger();
	protected final Minecraft minecraft;
	protected final Connection connection;
	protected final @Nullable ServerData serverData;
	protected @Nullable String serverBrand;
	protected final WorldSessionTelemetryManager telemetryManager;
	protected final @Nullable Screen postDisconnectScreen;
	protected boolean isTransferring;
	private final List<ClientCommonPacketListenerImpl.DeferredPacket> deferredPackets = new ArrayList<>();
	protected final Map<Identifier, byte[]> serverCookies;
	protected Map<String, String> customReportDetails;
	private ServerLinks serverLinks;
	protected final Map<UUID, PlayerInfo> seenPlayers;
	protected boolean seenInsecureChatWarning;

	protected ClientCommonPacketListenerImpl(Minecraft minecraft, Connection connection, CommonListenerCookie commonListenerCookie) {
		this.minecraft = minecraft;
		this.connection = connection;
		this.serverData = commonListenerCookie.serverData();
		this.serverBrand = commonListenerCookie.serverBrand();
		this.telemetryManager = commonListenerCookie.telemetryManager();
		this.postDisconnectScreen = commonListenerCookie.postDisconnectScreen();
		this.serverCookies = commonListenerCookie.serverCookies();
		this.customReportDetails = commonListenerCookie.customReportDetails();
		this.serverLinks = commonListenerCookie.serverLinks();
		this.seenPlayers = new HashMap<>(commonListenerCookie.seenPlayers());
		this.seenInsecureChatWarning = commonListenerCookie.seenInsecureChatWarning();
	}

	public ServerLinks serverLinks() {
		return this.serverLinks;
	}

	@Override
	public void onPacketError(Packet packet, Exception exception) {
		LOGGER.error("Failed to handle packet {}, disconnecting", packet, exception);
		Optional<Path> optional = this.storeDisconnectionReport(packet, exception);
		Optional<URI> optional2 = this.serverLinks.findKnownType(ServerLinks.KnownLinkType.BUG_REPORT).map(ServerLinks.Entry::link);
		this.connection.disconnect(new DisconnectionDetails(Component.translatable("disconnect.packetError"), optional, optional2));
	}

	@Override
	public DisconnectionDetails createDisconnectionInfo(Component component, Throwable throwable) {
		Optional<Path> optional = this.storeDisconnectionReport(null, throwable);
		Optional<URI> optional2 = this.serverLinks.findKnownType(ServerLinks.KnownLinkType.BUG_REPORT).map(ServerLinks.Entry::link);
		return new DisconnectionDetails(component, optional, optional2);
	}

	private Optional<Path> storeDisconnectionReport(@Nullable Packet packet, Throwable throwable) {
		CrashReport crashReport = CrashReport.forThrowable(throwable, "Packet handling error");
		PacketUtils.fillCrashReport(crashReport, this, packet);
		Path path = this.minecraft.gameDirectory.toPath().resolve("debug");
		Path path2 = path.resolve("disconnect-" + Util.getFilenameFormattedDateTime() + "-client.txt");
		Optional<ServerLinks.Entry> optional = this.serverLinks.findKnownType(ServerLinks.KnownLinkType.BUG_REPORT);
		List<String> list = optional.<List<String>>map(entry -> List.of("Server bug reporting link: " + entry.link())).orElse(List.of());
		return crashReport.saveToFile(path2, ReportType.NETWORK_PROTOCOL_ERROR, list) ? Optional.of(path2) : Optional.empty();
	}

	@Override
	public boolean shouldHandleMessage(Packet<?> packet) {
		return ClientCommonPacketListener.super.shouldHandleMessage(packet)
			? true
			: this.isTransferring && (packet instanceof ClientboundStoreCookiePacket || packet instanceof ClientboundTransferPacket);
	}

	@Override
	public void handleKeepAlive(ClientboundKeepAlivePacket clientboundKeepAlivePacket) {
		this.sendWhen(new ServerboundKeepAlivePacket(clientboundKeepAlivePacket.getId()), () -> !RenderSystem.isFrozenAtPollEvents(), Duration.ofMinutes(1L));
	}

	@Override
	public void handlePing(ClientboundPingPacket clientboundPingPacket) {
		PacketUtils.ensureRunningOnSameThread(clientboundPingPacket, this, this.minecraft.packetProcessor());
		this.send(new ServerboundPongPacket(clientboundPingPacket.getId()));
	}

	@Override
	public void handleCustomPayload(ClientboundCustomPayloadPacket clientboundCustomPayloadPacket) {
		CustomPacketPayload customPacketPayload = clientboundCustomPayloadPacket.payload();
		if (!(customPacketPayload instanceof DiscardedPayload)) {
			PacketUtils.ensureRunningOnSameThread(clientboundCustomPayloadPacket, this, this.minecraft.packetProcessor());
			if (customPacketPayload instanceof BrandPayload brandPayload) {
				this.serverBrand = brandPayload.brand();
				this.telemetryManager.onServerBrandReceived(brandPayload.brand());
			} else {
				this.handleCustomPayload(customPacketPayload);
			}
		}
	}

	protected abstract void handleCustomPayload(CustomPacketPayload customPacketPayload);

	@Override
	public void handleResourcePackPush(ClientboundResourcePackPushPacket clientboundResourcePackPushPacket) {
		PacketUtils.ensureRunningOnSameThread(clientboundResourcePackPushPacket, this, this.minecraft.packetProcessor());
		UUID uUID = clientboundResourcePackPushPacket.id();
		URL uRL = parseResourcePackUrl(clientboundResourcePackPushPacket.url());
		if (uRL == null) {
			this.connection.send(new ServerboundResourcePackPacket(uUID, ServerboundResourcePackPacket.Action.INVALID_URL));
		} else {
			String string = clientboundResourcePackPushPacket.hash();
			boolean bl = clientboundResourcePackPushPacket.required();
			ServerData.ServerPackStatus serverPackStatus = this.serverData != null ? this.serverData.getResourcePackStatus() : ServerData.ServerPackStatus.PROMPT;
			if (serverPackStatus != ServerData.ServerPackStatus.PROMPT && (!bl || serverPackStatus != ServerData.ServerPackStatus.DISABLED)) {
				this.minecraft.getDownloadedPackSource().pushPack(uUID, uRL, string);
			} else {
				this.minecraft.setScreen(this.addOrUpdatePackPrompt(uUID, uRL, string, bl, clientboundResourcePackPushPacket.prompt().orElse(null)));
			}
		}
	}

	@Override
	public void handleResourcePackPop(ClientboundResourcePackPopPacket clientboundResourcePackPopPacket) {
		PacketUtils.ensureRunningOnSameThread(clientboundResourcePackPopPacket, this, this.minecraft.packetProcessor());
		clientboundResourcePackPopPacket.id()
			.ifPresentOrElse(uUID -> this.minecraft.getDownloadedPackSource().popPack(uUID), () -> this.minecraft.getDownloadedPackSource().popAll());
	}

	static Component preparePackPrompt(Component component, @Nullable Component component2) {
		return component2 == null ? component : Component.translatable("multiplayer.texturePrompt.serverPrompt", component, component2);
	}

	private static @Nullable URL parseResourcePackUrl(String string) {
		try {
			URL uRL = new URL(string);
			String string2 = uRL.getProtocol();
			return !"http".equals(string2) && !"https".equals(string2) ? null : uRL;
		} catch (MalformedURLException malformedURLException) {
			return null;
		}
	}

	@Override
	public void handleRequestCookie(ClientboundCookieRequestPacket clientboundCookieRequestPacket) {
		PacketUtils.ensureRunningOnSameThread(clientboundCookieRequestPacket, this, this.minecraft.packetProcessor());
		this.connection.send(new ServerboundCookieResponsePacket(clientboundCookieRequestPacket.key(), this.serverCookies.get(clientboundCookieRequestPacket.key())));
	}

	@Override
	public void handleStoreCookie(ClientboundStoreCookiePacket clientboundStoreCookiePacket) {
		PacketUtils.ensureRunningOnSameThread(clientboundStoreCookiePacket, this, this.minecraft.packetProcessor());
		this.serverCookies.put(clientboundStoreCookiePacket.key(), clientboundStoreCookiePacket.payload());
	}

	@Override
	public void handleCustomReportDetails(ClientboundCustomReportDetailsPacket clientboundCustomReportDetailsPacket) {
		PacketUtils.ensureRunningOnSameThread(clientboundCustomReportDetailsPacket, this, this.minecraft.packetProcessor());
		this.customReportDetails = clientboundCustomReportDetailsPacket.details();
	}

	@Override
	public void handleServerLinks(ClientboundServerLinksPacket clientboundServerLinksPacket) {
		PacketUtils.ensureRunningOnSameThread(clientboundServerLinksPacket, this, this.minecraft.packetProcessor());
		List<ServerLinks.UntrustedEntry> list = clientboundServerLinksPacket.links();
		Builder<ServerLinks.Entry> builder = ImmutableList.builderWithExpectedSize(list.size());

		for (ServerLinks.UntrustedEntry untrustedEntry : list) {
			try {
				URI uRI = Util.parseAndValidateUntrustedUri(untrustedEntry.link());
				builder.add(new ServerLinks.Entry(untrustedEntry.type(), uRI));
			} catch (Exception exception) {
				LOGGER.warn("Received invalid link for type {}:{}", untrustedEntry.type(), untrustedEntry.link(), exception);
			}
		}

		this.serverLinks = new ServerLinks(builder.build());
	}

	@Override
	public void handleShowDialog(ClientboundShowDialogPacket clientboundShowDialogPacket) {
		PacketUtils.ensureRunningOnSameThread(clientboundShowDialogPacket, this, this.minecraft.packetProcessor());
		this.showDialog(clientboundShowDialogPacket.dialog(), this.minecraft.screen);
	}

	protected abstract DialogConnectionAccess createDialogAccess();

	public void showDialog(Holder<Dialog> holder, @Nullable Screen screen) {
		this.showDialog(holder, this.createDialogAccess(), screen);
	}

	protected void showDialog(Holder<Dialog> holder, DialogConnectionAccess dialogConnectionAccess, @Nullable Screen screen) {
		if (screen instanceof DialogScreen.WarningScreen warningScreen) {
			Screen screen2 = warningScreen.returnScreen();
			Screen screen3 = screen2 instanceof DialogScreen<?> dialogScreen ? dialogScreen.previousScreen() : screen2;
			DialogScreen<?> dialogScreen = DialogScreens.createFromData(holder.value(), screen3, dialogConnectionAccess);
			if (dialogScreen != null) {
				warningScreen.updateReturnScreen(dialogScreen);
			} else {
				LOGGER.warn("Failed to show dialog for data {}", holder);
			}
		} else {
			Screen screen4;
			if (screen instanceof DialogScreen<?> dialogScreen2) {
				screen4 = dialogScreen2.previousScreen();
			} else if (screen instanceof WaitingForResponseScreen waitingForResponseScreen) {
				screen4 = waitingForResponseScreen.previousScreen();
			} else {
				screen4 = screen;
			}

			Screen screen2 = DialogScreens.createFromData(holder.value(), screen4, dialogConnectionAccess);
			if (screen2 != null) {
				this.minecraft.setScreen(screen2);
			} else {
				LOGGER.warn("Failed to show dialog for data {}", holder);
			}
		}
	}

	@Override
	public void handleClearDialog(ClientboundClearDialogPacket clientboundClearDialogPacket) {
		PacketUtils.ensureRunningOnSameThread(clientboundClearDialogPacket, this, this.minecraft.packetProcessor());
		this.clearDialog();
	}

	public void clearDialog() {
		if (this.minecraft.screen instanceof DialogScreen.WarningScreen warningScreen) {
			if (warningScreen.returnScreen() instanceof DialogScreen<?> dialogScreen) {
				warningScreen.updateReturnScreen(dialogScreen.previousScreen());
			}
		} else if (this.minecraft.screen instanceof DialogScreen<?> dialogScreen2) {
			this.minecraft.setScreen(dialogScreen2.previousScreen());
		}
	}

	@Override
	public void handleTransfer(ClientboundTransferPacket clientboundTransferPacket) {
		this.isTransferring = true;
		PacketUtils.ensureRunningOnSameThread(clientboundTransferPacket, this, this.minecraft.packetProcessor());
		if (this.serverData == null) {
			throw new IllegalStateException("Cannot transfer to server from singleplayer");
		}

		this.connection.disconnect(Component.translatable("disconnect.transfer"));
		this.connection.setReadOnly();
		this.connection.handleDisconnection();
		ServerAddress serverAddress = new ServerAddress(clientboundTransferPacket.host(), clientboundTransferPacket.port());
		ConnectScreen.startConnecting(
			Objects.requireNonNullElseGet(this.postDisconnectScreen, TitleScreen::new),
			this.minecraft,
			serverAddress,
			this.serverData,
			false,
			new TransferState(this.serverCookies, this.seenPlayers, this.seenInsecureChatWarning)
		);
	}

	@Override
	public void handleDisconnect(ClientboundDisconnectPacket clientboundDisconnectPacket) {
		this.connection.disconnect(clientboundDisconnectPacket.reason());
	}

	protected void sendDeferredPackets() {
		Iterator<ClientCommonPacketListenerImpl.DeferredPacket> iterator = this.deferredPackets.iterator();

		while (iterator.hasNext()) {
			ClientCommonPacketListenerImpl.DeferredPacket deferredPacket = iterator.next();
			if (deferredPacket.sendCondition().getAsBoolean()) {
				this.send(deferredPacket.packet);
				iterator.remove();
			} else if (deferredPacket.expirationTime() <= Util.getMillis()) {
				iterator.remove();
			}
		}
	}

	public void send(Packet<?> packet) {
		this.connection.send(packet);
	}

	@Override
	public void onDisconnect(DisconnectionDetails disconnectionDetails) {
		this.telemetryManager.onDisconnect();
		this.minecraft.disconnect(this.createDisconnectScreen(disconnectionDetails), this.isTransferring);
		LOGGER.warn("Client disconnected with reason: {}", disconnectionDetails.reason().getString());
	}

	@Override
	public void fillListenerSpecificCrashDetails(CrashReport crashReport, CrashReportCategory crashReportCategory) {
		crashReportCategory.setDetail("Is Local", () -> String.valueOf(this.connection.isMemoryConnection()));
		crashReportCategory.setDetail("Server type", () -> this.serverData != null ? this.serverData.type().toString() : "<none>");
		crashReportCategory.setDetail("Server brand", () -> this.serverBrand);
		if (!this.customReportDetails.isEmpty()) {
			CrashReportCategory crashReportCategory2 = crashReport.addCategory("Custom Server Details");
			this.customReportDetails.forEach(crashReportCategory2::setDetail);
		}
	}

	protected Screen createDisconnectScreen(DisconnectionDetails disconnectionDetails) {
		Screen screen = Objects.requireNonNullElseGet(
			this.postDisconnectScreen, () -> this.serverData != null ? new JoinMultiplayerScreen(new TitleScreen()) : new TitleScreen()
		);
		return this.serverData != null && this.serverData.isRealm()
			? new DisconnectedScreen(screen, GENERIC_DISCONNECT_MESSAGE, disconnectionDetails, CommonComponents.GUI_BACK)
			: new DisconnectedScreen(screen, GENERIC_DISCONNECT_MESSAGE, disconnectionDetails);
	}

	public @Nullable String serverBrand() {
		return this.serverBrand;
	}

	private void sendWhen(Packet<? extends ServerboundPacketListener> packet, BooleanSupplier booleanSupplier, Duration duration) {
		if (booleanSupplier.getAsBoolean()) {
			this.send(packet);
		} else {
			this.deferredPackets.add(new ClientCommonPacketListenerImpl.DeferredPacket(packet, booleanSupplier, Util.getMillis() + duration.toMillis()));
		}
	}

	private Screen addOrUpdatePackPrompt(UUID uUID, URL uRL, String string, boolean bl, @Nullable Component component) {
		Screen screen = this.minecraft.screen;
		return screen instanceof ClientCommonPacketListenerImpl.PackConfirmScreen packConfirmScreen
			? packConfirmScreen.update(this.minecraft, uUID, uRL, string, bl, component)
			: new ClientCommonPacketListenerImpl.PackConfirmScreen(
				this.minecraft, screen, List.of(new ClientCommonPacketListenerImpl.PackConfirmScreen.PendingRequest(uUID, uRL, string)), bl, component
			);
	}

	@Environment(EnvType.CLIENT)
	protected abstract class CommonDialogAccess implements DialogConnectionAccess {
		@Override
		public void disconnect(Component component) {
			ClientCommonPacketListenerImpl.this.connection.disconnect(component);
			ClientCommonPacketListenerImpl.this.connection.handleDisconnection();
		}

		@Override
		public void openDialog(Holder<Dialog> holder, @Nullable Screen screen) {
			ClientCommonPacketListenerImpl.this.showDialog(holder, this, screen);
		}

		@Override
		public void sendCustomAction(Identifier identifier, Optional<Tag> optional) {
			ClientCommonPacketListenerImpl.this.send(new ServerboundCustomClickActionPacket(identifier, optional));
		}

		@Override
		public ServerLinks serverLinks() {
			return ClientCommonPacketListenerImpl.this.serverLinks();
		}
	}

	@Environment(EnvType.CLIENT)
	record DeferredPacket(Packet<? extends ServerboundPacketListener> packet, BooleanSupplier sendCondition, long expirationTime) {
	}

	@Environment(EnvType.CLIENT)
	class PackConfirmScreen extends ConfirmScreen {
		private final List<ClientCommonPacketListenerImpl.PackConfirmScreen.PendingRequest> requests;
		private final @Nullable Screen parentScreen;

		PackConfirmScreen(
			final Minecraft minecraft,
			final @Nullable Screen screen,
			final List<ClientCommonPacketListenerImpl.PackConfirmScreen.PendingRequest> list,
			final boolean bl,
			final @Nullable Component component
		) {
			super(
				bl2 -> {
					minecraft.setScreen(screen);
					DownloadedPackSource downloadedPackSource = minecraft.getDownloadedPackSource();
					if (bl2) {
						if (ClientCommonPacketListenerImpl.this.serverData != null) {
							ClientCommonPacketListenerImpl.this.serverData.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
						}

						downloadedPackSource.allowServerPacks();
					} else {
						downloadedPackSource.rejectServerPacks();
						if (bl) {
							ClientCommonPacketListenerImpl.this.connection.disconnect(Component.translatable("multiplayer.requiredTexturePrompt.disconnect"));
						} else if (ClientCommonPacketListenerImpl.this.serverData != null) {
							ClientCommonPacketListenerImpl.this.serverData.setResourcePackStatus(ServerData.ServerPackStatus.DISABLED);
						}
					}

					for (ClientCommonPacketListenerImpl.PackConfirmScreen.PendingRequest pendingRequest : list) {
						downloadedPackSource.pushPack(pendingRequest.id, pendingRequest.url, pendingRequest.hash);
					}

					if (ClientCommonPacketListenerImpl.this.serverData != null) {
						ServerList.saveSingleServer(ClientCommonPacketListenerImpl.this.serverData);
					}
				},
				bl ? Component.translatable("multiplayer.requiredTexturePrompt.line1") : Component.translatable("multiplayer.texturePrompt.line1"),
				ClientCommonPacketListenerImpl.preparePackPrompt(
					bl
						? Component.translatable("multiplayer.requiredTexturePrompt.line2").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
						: Component.translatable("multiplayer.texturePrompt.line2"),
					component
				),
				bl ? CommonComponents.GUI_PROCEED : CommonComponents.GUI_YES,
				bl ? CommonComponents.GUI_DISCONNECT : CommonComponents.GUI_NO
			);
			this.requests = list;
			this.parentScreen = screen;
		}

		public ClientCommonPacketListenerImpl.PackConfirmScreen update(
			Minecraft minecraft, UUID uUID, URL uRL, String string, boolean bl, @Nullable Component component
		) {
			List<ClientCommonPacketListenerImpl.PackConfirmScreen.PendingRequest> list = ImmutableList.<ClientCommonPacketListenerImpl.PackConfirmScreen.PendingRequest>builderWithExpectedSize(
					this.requests.size() + 1
				)
				.addAll(this.requests)
				.add(new ClientCommonPacketListenerImpl.PackConfirmScreen.PendingRequest(uUID, uRL, string))
				.build();
			return ClientCommonPacketListenerImpl.this.new PackConfirmScreen(minecraft, this.parentScreen, list, bl, component);
		}

		@Environment(EnvType.CLIENT)
		record PendingRequest(UUID id, URL url, String hash) {
		}
	}
}
