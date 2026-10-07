package net.minecraft.client.gui.screens.multiplayer;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LoadingDotsWidget;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.SelectableEntry;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.server.LanServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ServerSelectionList extends ObjectSelectionList<ServerSelectionList.Entry> {
	static final Identifier INCOMPATIBLE_SPRITE = Identifier.withDefaultNamespace("server_list/incompatible");
	static final Identifier UNREACHABLE_SPRITE = Identifier.withDefaultNamespace("server_list/unreachable");
	static final Identifier PING_1_SPRITE = Identifier.withDefaultNamespace("server_list/ping_1");
	static final Identifier PING_2_SPRITE = Identifier.withDefaultNamespace("server_list/ping_2");
	static final Identifier PING_3_SPRITE = Identifier.withDefaultNamespace("server_list/ping_3");
	static final Identifier PING_4_SPRITE = Identifier.withDefaultNamespace("server_list/ping_4");
	static final Identifier PING_5_SPRITE = Identifier.withDefaultNamespace("server_list/ping_5");
	static final Identifier PINGING_1_SPRITE = Identifier.withDefaultNamespace("server_list/pinging_1");
	static final Identifier PINGING_2_SPRITE = Identifier.withDefaultNamespace("server_list/pinging_2");
	static final Identifier PINGING_3_SPRITE = Identifier.withDefaultNamespace("server_list/pinging_3");
	static final Identifier PINGING_4_SPRITE = Identifier.withDefaultNamespace("server_list/pinging_4");
	static final Identifier PINGING_5_SPRITE = Identifier.withDefaultNamespace("server_list/pinging_5");
	static final Identifier JOIN_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("server_list/join_highlighted");
	static final Identifier JOIN_SPRITE = Identifier.withDefaultNamespace("server_list/join");
	static final Identifier MOVE_UP_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("server_list/move_up_highlighted");
	static final Identifier MOVE_UP_SPRITE = Identifier.withDefaultNamespace("server_list/move_up");
	static final Identifier MOVE_DOWN_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("server_list/move_down_highlighted");
	static final Identifier MOVE_DOWN_SPRITE = Identifier.withDefaultNamespace("server_list/move_down");
	static final Logger LOGGER = LogUtils.getLogger();
	static final ThreadPoolExecutor THREAD_POOL = new ScheduledThreadPoolExecutor(
		5,
		new ThreadFactoryBuilder()
			.setNameFormat("Server Pinger #%d")
			.setDaemon(true)
			.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER))
			.build()
	);
	static final Component SCANNING_LABEL = Component.translatable("lanServer.scanning");
	static final Component CANT_RESOLVE_TEXT = Component.translatable("multiplayer.status.cannot_resolve").withColor(-65536);
	static final Component CANT_CONNECT_TEXT = Component.translatable("multiplayer.status.cannot_connect").withColor(-65536);
	static final Component INCOMPATIBLE_STATUS = Component.translatable("multiplayer.status.incompatible");
	static final Component NO_CONNECTION_STATUS = Component.translatable("multiplayer.status.no_connection");
	static final Component PINGING_STATUS = Component.translatable("multiplayer.status.pinging");
	static final Component ONLINE_STATUS = Component.translatable("multiplayer.status.online");
	private final JoinMultiplayerScreen screen;
	private final List<ServerSelectionList.OnlineServerEntry> onlineServers = Lists.newArrayList();
	private final ServerSelectionList.Entry lanHeader = new ServerSelectionList.LANHeader();
	private final List<ServerSelectionList.NetworkServerEntry> networkServers = Lists.newArrayList();

	public ServerSelectionList(JoinMultiplayerScreen joinMultiplayerScreen, Minecraft minecraft, int i, int j, int k, int l) {
		super(minecraft, i, j, k, l);
		this.screen = joinMultiplayerScreen;
	}

	private void refreshEntries() {
		ServerSelectionList.Entry entry = this.getSelected();
		List<ServerSelectionList.Entry> list = new ArrayList<>(this.onlineServers);
		list.add(this.lanHeader);
		list.addAll(this.networkServers);
		this.replaceEntries(list);
		if (entry != null) {
			for (ServerSelectionList.Entry entry2 : list) {
				if (entry2.matches(entry)) {
					this.setSelected(entry2);
					break;
				}
			}
		}
	}

	public void setSelected(ServerSelectionList.@Nullable Entry entry) {
		super.setSelected(entry);
		this.screen.onSelectedChange();
	}

	public void updateOnlineServers(ServerList serverList) {
		this.onlineServers.clear();

		for (int i = 0; i < serverList.size(); i++) {
			this.onlineServers.add(new ServerSelectionList.OnlineServerEntry(this.screen, serverList.get(i)));
		}

		this.refreshEntries();
	}

	public void updateNetworkServers(List<LanServer> list) {
		int i = list.size() - this.networkServers.size();
		this.networkServers.clear();

		for (LanServer lanServer : list) {
			this.networkServers.add(new ServerSelectionList.NetworkServerEntry(this.screen, lanServer));
		}

		this.refreshEntries();

		for (int j = this.networkServers.size() - i; j < this.networkServers.size(); j++) {
			ServerSelectionList.NetworkServerEntry networkServerEntry = this.networkServers.get(j);
			int k = j - this.networkServers.size() + this.children().size();
			int l = this.getRowTop(k);
			int m = this.getRowBottom(k);
			if (m >= this.getY() && l <= this.getBottom()) {
				this.minecraft.getNarrator().saySystemQueued(Component.translatable("multiplayer.lan.server_found", networkServerEntry.getServerNarration()));
			}
		}
	}

	@Override
	public int getRowWidth() {
		return 305;
	}

	public void removed() {
	}

	@Environment(EnvType.CLIENT)
	public abstract static class Entry extends ObjectSelectionList.Entry<ServerSelectionList.Entry> implements AutoCloseable {
		@Override
		public void close() {
		}

		abstract boolean matches(ServerSelectionList.Entry entry);

		public abstract void join();
	}

	@Environment(EnvType.CLIENT)
	public static class LANHeader extends ServerSelectionList.Entry {
		private final Minecraft minecraft = Minecraft.getInstance();
		private final LoadingDotsWidget loadingDotsWidget = new LoadingDotsWidget(this.minecraft.font, ServerSelectionList.SCANNING_LABEL);

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			this.loadingDotsWidget.setPosition(this.getContentXMiddle() - this.minecraft.font.width(ServerSelectionList.SCANNING_LABEL) / 2, this.getContentY());
			this.loadingDotsWidget.render(guiGraphics, i, j, f);
		}

		@Override
		public Component getNarration() {
			return ServerSelectionList.SCANNING_LABEL;
		}

		@Override
		boolean matches(ServerSelectionList.Entry entry) {
			return entry instanceof ServerSelectionList.LANHeader;
		}

		@Override
		public void join() {
		}
	}

	@Environment(EnvType.CLIENT)
	public static class NetworkServerEntry extends ServerSelectionList.Entry {
		private static final int ICON_WIDTH = 32;
		private static final Component LAN_SERVER_HEADER = Component.translatable("lanServer.title");
		private static final Component HIDDEN_ADDRESS_TEXT = Component.translatable("selectServer.hiddenAddress");
		private final JoinMultiplayerScreen screen;
		protected final Minecraft minecraft;
		protected final LanServer serverData;

		protected NetworkServerEntry(JoinMultiplayerScreen joinMultiplayerScreen, LanServer lanServer) {
			this.screen = joinMultiplayerScreen;
			this.serverData = lanServer;
			this.minecraft = Minecraft.getInstance();
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			guiGraphics.drawString(this.minecraft.font, LAN_SERVER_HEADER, this.getContentX() + 32 + 3, this.getContentY() + 1, -1);
			guiGraphics.drawString(this.minecraft.font, this.serverData.getMotd(), this.getContentX() + 32 + 3, this.getContentY() + 12, -8355712);
			if (this.minecraft.options.hideServerAddress) {
				guiGraphics.drawString(this.minecraft.font, HIDDEN_ADDRESS_TEXT, this.getContentX() + 32 + 3, this.getContentY() + 12 + 11, -8355712);
			} else {
				guiGraphics.drawString(this.minecraft.font, this.serverData.getAddress(), this.getContentX() + 32 + 3, this.getContentY() + 12 + 11, -8355712);
			}
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
			if (bl) {
				this.join();
			}

			return super.mouseClicked(mouseButtonEvent, bl);
		}

		@Override
		public boolean keyPressed(KeyEvent keyEvent) {
			if (keyEvent.isSelection()) {
				this.join();
				return true;
			} else {
				return super.keyPressed(keyEvent);
			}
		}

		@Override
		public void join() {
			this.screen.join(new ServerData(this.serverData.getMotd(), this.serverData.getAddress(), ServerData.Type.LAN));
		}

		@Override
		public Component getNarration() {
			return Component.translatable("narrator.select", this.getServerNarration());
		}

		public Component getServerNarration() {
			return Component.empty().append(LAN_SERVER_HEADER).append(CommonComponents.SPACE).append(this.serverData.getMotd());
		}

		@Override
		boolean matches(ServerSelectionList.Entry entry) {
			return entry instanceof ServerSelectionList.NetworkServerEntry networkServerEntry && networkServerEntry.serverData == this.serverData;
		}
	}

	@Environment(EnvType.CLIENT)
	public class OnlineServerEntry extends ServerSelectionList.Entry implements SelectableEntry {
		private static final int ICON_SIZE = 32;
		private static final int SPACING = 5;
		private static final int STATUS_ICON_WIDTH = 10;
		private static final int STATUS_ICON_HEIGHT = 8;
		private final JoinMultiplayerScreen screen;
		private final Minecraft minecraft;
		private final ServerData serverData;
		private final FaviconTexture icon;
		private byte @Nullable [] lastIconBytes;
		private @Nullable List<Component> onlinePlayersTooltip;
		private @Nullable Identifier statusIcon;
		private @Nullable Component statusIconTooltip;

		protected OnlineServerEntry(final JoinMultiplayerScreen joinMultiplayerScreen, final ServerData serverData) {
			this.screen = joinMultiplayerScreen;
			this.serverData = serverData;
			this.minecraft = Minecraft.getInstance();
			this.icon = FaviconTexture.forServer(this.minecraft.getTextureManager(), serverData.ip);
			this.refreshStatus();
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			if (this.serverData.state() == ServerData.State.INITIAL) {
				this.serverData.setState(ServerData.State.PINGING);
				this.serverData.motd = CommonComponents.EMPTY;
				this.serverData.status = CommonComponents.EMPTY;
				ServerSelectionList.THREAD_POOL
					.submit(
						() -> {
							try {
								this.screen
									.getPinger()
									.pingServer(
										this.serverData,
										() -> this.minecraft.execute(this::updateServerList),
										() -> {
											this.serverData
												.setState(
													this.serverData.protocol == SharedConstants.getCurrentVersion().protocolVersion() ? ServerData.State.SUCCESSFUL : ServerData.State.INCOMPATIBLE
												);
											this.minecraft.execute(this::refreshStatus);
										},
										EventLoopGroupHolder.remote(this.minecraft.options.useNativeTransport())
									);
							} catch (UnknownHostException unknownHostException) {
								this.serverData.setState(ServerData.State.UNREACHABLE);
								this.serverData.motd = ServerSelectionList.CANT_RESOLVE_TEXT;
								this.minecraft.execute(this::refreshStatus);
							} catch (Exception exception) {
								this.serverData.setState(ServerData.State.UNREACHABLE);
								this.serverData.motd = ServerSelectionList.CANT_CONNECT_TEXT;
								this.minecraft.execute(this::refreshStatus);
							}
						}
					);
			}

			guiGraphics.drawString(this.minecraft.font, this.serverData.name, this.getContentX() + 32 + 3, this.getContentY() + 1, -1);
			List<FormattedCharSequence> list = this.minecraft.font.split(this.serverData.motd, this.getContentWidth() - 32 - 2);

			for (int k = 0; k < Math.min(list.size(), 2); k++) {
				guiGraphics.drawString(this.minecraft.font, list.get(k), this.getContentX() + 32 + 3, this.getContentY() + 12 + 9 * k, -8355712);
			}

			this.drawIcon(guiGraphics, this.getContentX(), this.getContentY(), this.icon.textureLocation());
			int k = ServerSelectionList.this.children().indexOf(this);
			if (this.serverData.state() == ServerData.State.PINGING) {
				int l = (int)(Util.getMillis() / 100L + k * 2 & 7L);
				if (l > 4) {
					l = 8 - l;
				}
				this.statusIcon = switch (l) {
					case 1 -> ServerSelectionList.PINGING_2_SPRITE;
					case 2 -> ServerSelectionList.PINGING_3_SPRITE;
					case 3 -> ServerSelectionList.PINGING_4_SPRITE;
					case 4 -> ServerSelectionList.PINGING_5_SPRITE;
					default -> ServerSelectionList.PINGING_1_SPRITE;
				};
			}

			int l = this.getContentRight() - 10 - 5;
			if (this.statusIcon != null) {
				guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.statusIcon, l, this.getContentY(), 10, 8);
			}

			byte[] bs = this.serverData.getIconBytes();
			if (!Arrays.equals(bs, this.lastIconBytes)) {
				if (this.uploadServerIcon(bs)) {
					this.lastIconBytes = bs;
				} else {
					this.serverData.setIconBytes(null);
					this.updateServerList();
				}
			}

			Component component = this.serverData.state() == ServerData.State.INCOMPATIBLE
				? this.serverData.version.copy().withStyle(ChatFormatting.RED)
				: this.serverData.status;
			int m = this.minecraft.font.width(component);
			int n = l - m - 5;
			guiGraphics.drawString(this.minecraft.font, component, n, this.getContentY() + 1, -8355712);
			if (this.statusIconTooltip != null && i >= l && i <= l + 10 && j >= this.getContentY() && j <= this.getContentY() + 8) {
				guiGraphics.setTooltipForNextFrame(this.statusIconTooltip, i, j);
			} else if (this.onlinePlayersTooltip != null && i >= n && i <= n + m && j >= this.getContentY() && j <= this.getContentY() - 1 + 9) {
				guiGraphics.setTooltipForNextFrame(Lists.transform(this.onlinePlayersTooltip, Component::getVisualOrderText), i, j);
			}

			if (this.minecraft.options.touchscreen().get() || bl) {
				guiGraphics.fill(this.getContentX(), this.getContentY(), this.getContentX() + 32, this.getContentY() + 32, -1601138544);
				int o = i - this.getContentX();
				int p = j - this.getContentY();
				if (this.mouseOverRightHalf(o, p, 32)) {
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ServerSelectionList.JOIN_HIGHLIGHTED_SPRITE, this.getContentX(), this.getContentY(), 32, 32);
					ServerSelectionList.this.handleCursor(guiGraphics);
				} else {
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ServerSelectionList.JOIN_SPRITE, this.getContentX(), this.getContentY(), 32, 32);
				}

				if (k > 0) {
					if (this.mouseOverTopLeftQuarter(o, p, 32)) {
						guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ServerSelectionList.MOVE_UP_HIGHLIGHTED_SPRITE, this.getContentX(), this.getContentY(), 32, 32);
						ServerSelectionList.this.handleCursor(guiGraphics);
					} else {
						guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ServerSelectionList.MOVE_UP_SPRITE, this.getContentX(), this.getContentY(), 32, 32);
					}
				}

				if (k < this.screen.getServers().size() - 1) {
					if (this.mouseOverBottomLeftQuarter(o, p, 32)) {
						guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ServerSelectionList.MOVE_DOWN_HIGHLIGHTED_SPRITE, this.getContentX(), this.getContentY(), 32, 32);
						ServerSelectionList.this.handleCursor(guiGraphics);
					} else {
						guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, ServerSelectionList.MOVE_DOWN_SPRITE, this.getContentX(), this.getContentY(), 32, 32);
					}
				}
			}
		}

		private void refreshStatus() {
			this.onlinePlayersTooltip = null;
			switch (this.serverData.state()) {
				case INITIAL:
				case PINGING:
					this.statusIcon = ServerSelectionList.PING_1_SPRITE;
					this.statusIconTooltip = ServerSelectionList.PINGING_STATUS;
					break;
				case INCOMPATIBLE:
					this.statusIcon = ServerSelectionList.INCOMPATIBLE_SPRITE;
					this.statusIconTooltip = ServerSelectionList.INCOMPATIBLE_STATUS;
					this.onlinePlayersTooltip = this.serverData.playerList;
					break;
				case UNREACHABLE:
					this.statusIcon = ServerSelectionList.UNREACHABLE_SPRITE;
					this.statusIconTooltip = ServerSelectionList.NO_CONNECTION_STATUS;
					break;
				case SUCCESSFUL:
					if (this.serverData.ping < 150L) {
						this.statusIcon = ServerSelectionList.PING_5_SPRITE;
					} else if (this.serverData.ping < 300L) {
						this.statusIcon = ServerSelectionList.PING_4_SPRITE;
					} else if (this.serverData.ping < 600L) {
						this.statusIcon = ServerSelectionList.PING_3_SPRITE;
					} else if (this.serverData.ping < 1000L) {
						this.statusIcon = ServerSelectionList.PING_2_SPRITE;
					} else {
						this.statusIcon = ServerSelectionList.PING_1_SPRITE;
					}

					this.statusIconTooltip = Component.translatable("multiplayer.status.ping", this.serverData.ping);
					this.onlinePlayersTooltip = this.serverData.playerList;
			}
		}

		public void updateServerList() {
			this.screen.getServers().save();
		}

		protected void drawIcon(GuiGraphics guiGraphics, int i, int j, Identifier identifier) {
			guiGraphics.blit(RenderPipelines.GUI_TEXTURED, identifier, i, j, 0.0F, 0.0F, 32, 32, 32, 32);
		}

		private boolean uploadServerIcon(byte @Nullable [] bs) {
			if (bs == null) {
				this.icon.clear();
			} else {
				try {
					this.icon.upload(NativeImage.read(bs));
				} catch (Throwable throwable) {
					ServerSelectionList.LOGGER.error("Invalid icon for server {} ({})", this.serverData.name, this.serverData.ip, throwable);
					return false;
				}
			}

			return true;
		}

		@Override
		public boolean keyPressed(KeyEvent keyEvent) {
			if (keyEvent.isSelection()) {
				this.join();
				return true;
			}

			if (keyEvent.hasShiftDown()) {
				ServerSelectionList serverSelectionList = this.screen.serverSelectionList;
				int i = serverSelectionList.children().indexOf(this);
				if (i == -1) {
					return true;
				}

				if (keyEvent.isDown() && i < this.screen.getServers().size() - 1 || keyEvent.isUp() && i > 0) {
					this.swap(i, keyEvent.isDown() ? i + 1 : i - 1);
					return true;
				}
			}

			return super.keyPressed(keyEvent);
		}

		@Override
		public void join() {
			this.screen.join(this.serverData);
		}

		private void swap(int i, int j) {
			this.screen.getServers().swap(i, j);
			this.screen.serverSelectionList.swap(i, j);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
			int i = (int)mouseButtonEvent.x() - this.getContentX();
			int j = (int)mouseButtonEvent.y() - this.getContentY();
			if (this.mouseOverRightHalf(i, j, 32)) {
				this.join();
				return true;
			}

			int k = this.screen.serverSelectionList.children().indexOf(this);
			if (k > 0 && this.mouseOverTopLeftQuarter(i, j, 32)) {
				this.swap(k, k - 1);
				return true;
			}

			if (k < this.screen.getServers().size() - 1 && this.mouseOverBottomLeftQuarter(i, j, 32)) {
				this.swap(k, k + 1);
				return true;
			}

			if (bl) {
				this.join();
			}

			return super.mouseClicked(mouseButtonEvent, bl);
		}

		public ServerData getServerData() {
			return this.serverData;
		}

		@Override
		public Component getNarration() {
			MutableComponent mutableComponent = Component.empty();
			mutableComponent.append(Component.translatable("narrator.select", this.serverData.name));
			mutableComponent.append(CommonComponents.NARRATION_SEPARATOR);
			switch (this.serverData.state()) {
				case PINGING:
					mutableComponent.append(ServerSelectionList.PINGING_STATUS);
					break;
				case INCOMPATIBLE:
					mutableComponent.append(ServerSelectionList.INCOMPATIBLE_STATUS);
					mutableComponent.append(CommonComponents.NARRATION_SEPARATOR);
					mutableComponent.append(Component.translatable("multiplayer.status.version.narration", this.serverData.version));
					mutableComponent.append(CommonComponents.NARRATION_SEPARATOR);
					mutableComponent.append(Component.translatable("multiplayer.status.motd.narration", this.serverData.motd));
					break;
				case UNREACHABLE:
					mutableComponent.append(ServerSelectionList.NO_CONNECTION_STATUS);
					break;
				default:
					mutableComponent.append(ServerSelectionList.ONLINE_STATUS);
					mutableComponent.append(CommonComponents.NARRATION_SEPARATOR);
					mutableComponent.append(Component.translatable("multiplayer.status.ping.narration", this.serverData.ping));
					mutableComponent.append(CommonComponents.NARRATION_SEPARATOR);
					mutableComponent.append(Component.translatable("multiplayer.status.motd.narration", this.serverData.motd));
					if (this.serverData.players != null) {
						mutableComponent.append(CommonComponents.NARRATION_SEPARATOR);
						mutableComponent.append(
							Component.translatable("multiplayer.status.player_count.narration", this.serverData.players.online(), this.serverData.players.max())
						);
						mutableComponent.append(CommonComponents.NARRATION_SEPARATOR);
						mutableComponent.append(ComponentUtils.formatList(this.serverData.playerList, Component.literal(", ")));
					}
			}

			return mutableComponent;
		}

		@Override
		public void close() {
			this.icon.close();
		}

		@Override
		boolean matches(ServerSelectionList.Entry entry) {
			return entry instanceof ServerSelectionList.OnlineServerEntry onlineServerEntry && onlineServerEntry.serverData == this.serverData;
		}
	}
}
