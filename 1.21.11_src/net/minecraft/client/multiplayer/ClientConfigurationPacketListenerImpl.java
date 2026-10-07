package net.minecraft.client.multiplayer;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogConnectionAccess;
import net.minecraft.client.gui.screens.multiplayer.CodeOfConductScreen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import net.minecraft.network.protocol.configuration.ClientboundCodeOfConductPacket;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import net.minecraft.network.protocol.configuration.ClientboundResetChatPacket;
import net.minecraft.network.protocol.configuration.ClientboundSelectKnownPacks;
import net.minecraft.network.protocol.configuration.ClientboundUpdateEnabledFeaturesPacket;
import net.minecraft.network.protocol.configuration.ServerboundAcceptCodeOfConductPacket;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.network.protocol.configuration.ServerboundSelectKnownPacks;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ClientConfigurationPacketListenerImpl extends ClientCommonPacketListenerImpl implements ClientConfigurationPacketListener, TickablePacketListener {
	static final Logger LOGGER = LogUtils.getLogger();
	public static final Component DISCONNECTED_MESSAGE = Component.translatable("multiplayer.disconnect.code_of_conduct");
	private final LevelLoadTracker levelLoadTracker;
	private final GameProfile localGameProfile;
	private FeatureFlagSet enabledFeatures;
	private final RegistryAccess.Frozen receivedRegistries;
	private final RegistryDataCollector registryDataCollector = new RegistryDataCollector();
	private @Nullable KnownPacksManager knownPacks;
	protected ChatComponent.@Nullable State chatState;
	private boolean seenCodeOfConduct;

	public ClientConfigurationPacketListenerImpl(Minecraft minecraft, Connection connection, CommonListenerCookie commonListenerCookie) {
		super(minecraft, connection, commonListenerCookie);
		this.levelLoadTracker = commonListenerCookie.levelLoadTracker();
		this.localGameProfile = commonListenerCookie.localGameProfile();
		this.receivedRegistries = commonListenerCookie.receivedRegistries();
		this.enabledFeatures = commonListenerCookie.enabledFeatures();
		this.chatState = commonListenerCookie.chatState();
	}

	@Override
	public boolean isAcceptingMessages() {
		return this.connection.isConnected();
	}

	@Override
	protected void handleCustomPayload(CustomPacketPayload customPacketPayload) {
		this.handleUnknownCustomPayload(customPacketPayload);
	}

	private void handleUnknownCustomPayload(CustomPacketPayload customPacketPayload) {
		LOGGER.warn("Unknown custom packet payload: {}", customPacketPayload.type().id());
	}

	@Override
	public void handleRegistryData(ClientboundRegistryDataPacket clientboundRegistryDataPacket) {
		PacketUtils.ensureRunningOnSameThread(clientboundRegistryDataPacket, this, this.minecraft.packetProcessor());
		this.registryDataCollector.appendContents(clientboundRegistryDataPacket.registry(), clientboundRegistryDataPacket.entries());
	}

	@Override
	public void handleUpdateTags(ClientboundUpdateTagsPacket clientboundUpdateTagsPacket) {
		PacketUtils.ensureRunningOnSameThread(clientboundUpdateTagsPacket, this, this.minecraft.packetProcessor());
		this.registryDataCollector.appendTags(clientboundUpdateTagsPacket.getTags());
	}

	@Override
	public void handleEnabledFeatures(ClientboundUpdateEnabledFeaturesPacket clientboundUpdateEnabledFeaturesPacket) {
		this.enabledFeatures = FeatureFlags.REGISTRY.fromNames(clientboundUpdateEnabledFeaturesPacket.features());
	}

	@Override
	public void handleSelectKnownPacks(ClientboundSelectKnownPacks clientboundSelectKnownPacks) {
		PacketUtils.ensureRunningOnSameThread(clientboundSelectKnownPacks, this, this.minecraft.packetProcessor());
		if (this.knownPacks == null) {
			this.knownPacks = new KnownPacksManager();
		}

		List<KnownPack> list = this.knownPacks.trySelectingPacks(clientboundSelectKnownPacks.knownPacks());
		this.send(new ServerboundSelectKnownPacks(list));
	}

	@Override
	public void handleResetChat(ClientboundResetChatPacket clientboundResetChatPacket) {
		this.chatState = null;
	}

	private <T> T runWithResources(Function<ResourceProvider, T> function) {
		if (this.knownPacks == null) {
			return function.apply(ResourceProvider.EMPTY);
		}

		try (CloseableResourceManager closeableResourceManager = this.knownPacks.createResourceManager()) {
			return function.apply(closeableResourceManager);
		}
	}

	@Override
	public void handleCodeOfConduct(ClientboundCodeOfConductPacket clientboundCodeOfConductPacket) {
		PacketUtils.ensureRunningOnSameThread(clientboundCodeOfConductPacket, this, this.minecraft.packetProcessor());
		if (this.seenCodeOfConduct) {
			throw new IllegalStateException("Server sent duplicate Code of Conduct");
		}

		this.seenCodeOfConduct = true;
		String string = clientboundCodeOfConductPacket.codeOfConduct();
		if (this.serverData != null && this.serverData.hasAcceptedCodeOfConduct(string)) {
			this.send(ServerboundAcceptCodeOfConductPacket.INSTANCE);
		} else {
			Screen screen = this.minecraft.screen;
			this.minecraft.setScreen(new CodeOfConductScreen(this.serverData, screen, string, bl -> {
				if (bl) {
					this.send(ServerboundAcceptCodeOfConductPacket.INSTANCE);
					this.minecraft.setScreen(screen);
				} else {
					this.createDialogAccess().disconnect(DISCONNECTED_MESSAGE);
				}
			}));
		}
	}

	@Override
	public void handleConfigurationFinished(ClientboundFinishConfigurationPacket clientboundFinishConfigurationPacket) {
		PacketUtils.ensureRunningOnSameThread(clientboundFinishConfigurationPacket, this, this.minecraft.packetProcessor());
		RegistryAccess.Frozen frozen = this.runWithResources(
			resourceProvider -> this.registryDataCollector.collectGameRegistries(resourceProvider, this.receivedRegistries, this.connection.isMemoryConnection())
		);
		this.connection
			.setupInboundProtocol(
				GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(frozen)),
				new ClientPacketListener(
					this.minecraft,
					this.connection,
					new CommonListenerCookie(
						this.levelLoadTracker,
						this.localGameProfile,
						this.telemetryManager,
						frozen,
						this.enabledFeatures,
						this.serverBrand,
						this.serverData,
						this.postDisconnectScreen,
						this.serverCookies,
						this.chatState,
						this.customReportDetails,
						this.serverLinks(),
						this.seenPlayers,
						this.seenInsecureChatWarning
					)
				)
			);
		this.connection.send(ServerboundFinishConfigurationPacket.INSTANCE);
		this.connection.setupOutboundProtocol(GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(frozen), new GameProtocols.Context() {
			@Override
			public boolean hasInfiniteMaterials() {
				return true;
			}
		}));
	}

	@Override
	public void tick() {
		this.sendDeferredPackets();
	}

	@Override
	public void onDisconnect(DisconnectionDetails disconnectionDetails) {
		super.onDisconnect(disconnectionDetails);
		this.minecraft.clearDownloadedResourcePacks();
	}

	@Override
	protected DialogConnectionAccess createDialogAccess() {
		return new ClientCommonPacketListenerImpl.CommonDialogAccess() {
			@Override
			public void runCommand(String string, @Nullable Screen screen) {
				ClientConfigurationPacketListenerImpl.LOGGER.warn("Commands are not supported in configuration phase, trying to run '{}'", string);
			}
		};
	}
}
