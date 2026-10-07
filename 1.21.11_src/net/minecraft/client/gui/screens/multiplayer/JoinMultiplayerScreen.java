package net.minecraft.client.gui.screens.multiplayer;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.LanServer;
import net.minecraft.client.server.LanServerDetection;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class JoinMultiplayerScreen extends Screen {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int TOP_ROW_BUTTON_WIDTH = 100;
	private static final int LOWER_ROW_BUTTON_WIDTH = 74;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 33, 60);
	private final ServerStatusPinger pinger = new ServerStatusPinger();
	private final Screen lastScreen;
	protected ServerSelectionList serverSelectionList;
	private ServerList servers;
	private Button editButton;
	private Button selectButton;
	private Button deleteButton;
	private ServerData editingServer;
	private LanServerDetection.LanServerList lanServerList;
	private LanServerDetection.@Nullable LanServerDetector lanServerDetector;

	public JoinMultiplayerScreen(Screen screen) {
		super(Component.translatable("multiplayer.title"));
		this.lastScreen = screen;
	}

	@Override
	protected void init() {
		this.layout.addTitleHeader(this.title, this.font);
		this.servers = new ServerList(this.minecraft);
		this.servers.load();
		this.lanServerList = new LanServerDetection.LanServerList();

		try {
			this.lanServerDetector = new LanServerDetection.LanServerDetector(this.lanServerList);
			this.lanServerDetector.start();
		} catch (Exception exception) {
			LOGGER.warn("Unable to start LAN server detection: {}", exception.getMessage());
		}

		this.serverSelectionList = this.layout
			.addToContents(new ServerSelectionList(this, this.minecraft, this.width, this.layout.getContentHeight(), this.layout.getHeaderHeight(), 36));
		this.serverSelectionList.updateOnlineServers(this.servers);
		LinearLayout linearLayout = this.layout.addToFooter(LinearLayout.vertical().spacing(4));
		linearLayout.defaultCellSetting().alignHorizontallyCenter();
		LinearLayout linearLayout2 = linearLayout.addChild(LinearLayout.horizontal().spacing(4));
		LinearLayout linearLayout3 = linearLayout.addChild(LinearLayout.horizontal().spacing(4));
		this.selectButton = linearLayout2.addChild(Button.builder(Component.translatable("selectServer.select"), button -> {
			ServerSelectionList.Entry entry = this.serverSelectionList.getSelected();
			if (entry != null) {
				entry.join();
			}
		}).width(100).build());
		linearLayout2.addChild(Button.builder(Component.translatable("selectServer.direct"), button -> {
			this.editingServer = new ServerData(I18n.get("selectServer.defaultName"), "", ServerData.Type.OTHER);
			this.minecraft.setScreen(new DirectJoinServerScreen(this, this::directJoinCallback, this.editingServer));
		}).width(100).build());
		linearLayout2.addChild(Button.builder(Component.translatable("selectServer.add"), button -> {
			this.editingServer = new ServerData("", "", ServerData.Type.OTHER);
			this.minecraft.setScreen(new ManageServerScreen(this, Component.translatable("manageServer.add.title"), this::addServerCallback, this.editingServer));
		}).width(100).build());
		this.editButton = linearLayout3.addChild(Button.builder(Component.translatable("selectServer.edit"), button -> {
			ServerSelectionList.Entry entry = this.serverSelectionList.getSelected();
			if (entry instanceof ServerSelectionList.OnlineServerEntry) {
				ServerData serverData = ((ServerSelectionList.OnlineServerEntry)entry).getServerData();
				this.editingServer = new ServerData(serverData.name, serverData.ip, ServerData.Type.OTHER);
				this.editingServer.copyFrom(serverData);
				this.minecraft.setScreen(new ManageServerScreen(this, Component.translatable("manageServer.edit.title"), this::editServerCallback, this.editingServer));
			}
		}).width(74).build());
		this.deleteButton = linearLayout3.addChild(Button.builder(Component.translatable("selectServer.delete"), button -> {
			ServerSelectionList.Entry entry = this.serverSelectionList.getSelected();
			if (entry instanceof ServerSelectionList.OnlineServerEntry) {
				String string = ((ServerSelectionList.OnlineServerEntry)entry).getServerData().name;
				if (string != null) {
					Component component = Component.translatable("selectServer.deleteQuestion");
					Component component2 = Component.translatable("selectServer.deleteWarning", string);
					Component component3 = Component.translatable("selectServer.deleteButton");
					Component component4 = CommonComponents.GUI_CANCEL;
					this.minecraft.setScreen(new ConfirmScreen(this::deleteCallback, component, component2, component3, component4));
				}
			}
		}).width(74).build());
		linearLayout3.addChild(Button.builder(Component.translatable("selectServer.refresh"), button -> this.refreshServerList()).width(74).build());
		linearLayout3.addChild(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).width(74).build());
		this.layout.visitWidgets(guiEventListener -> {
			AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
		});
		this.repositionElements();
		this.onSelectedChange();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
		if (this.serverSelectionList != null) {
			this.serverSelectionList.updateSize(this.width, this.layout);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.lastScreen);
	}

	@Override
	public void tick() {
		super.tick();
		List<LanServer> list = this.lanServerList.takeDirtyServers();
		if (list != null) {
			this.serverSelectionList.updateNetworkServers(list);
		}

		this.pinger.tick();
	}

	@Override
	public void removed() {
		if (this.lanServerDetector != null) {
			this.lanServerDetector.interrupt();
			this.lanServerDetector = null;
		}

		this.pinger.removeAll();
		this.serverSelectionList.removed();
	}

	private void refreshServerList() {
		this.minecraft.setScreen(new JoinMultiplayerScreen(this.lastScreen));
	}

	private void deleteCallback(boolean bl) {
		ServerSelectionList.Entry entry = this.serverSelectionList.getSelected();
		if (bl && entry instanceof ServerSelectionList.OnlineServerEntry) {
			this.servers.remove(((ServerSelectionList.OnlineServerEntry)entry).getServerData());
			this.servers.save();
			this.serverSelectionList.setSelected((ServerSelectionList.Entry)null);
			this.serverSelectionList.updateOnlineServers(this.servers);
		}

		this.minecraft.setScreen(this);
	}

	private void editServerCallback(boolean bl) {
		ServerSelectionList.Entry entry = this.serverSelectionList.getSelected();
		if (bl && entry instanceof ServerSelectionList.OnlineServerEntry) {
			ServerData serverData = ((ServerSelectionList.OnlineServerEntry)entry).getServerData();
			serverData.name = this.editingServer.name;
			serverData.ip = this.editingServer.ip;
			serverData.copyFrom(this.editingServer);
			this.servers.save();
			this.serverSelectionList.updateOnlineServers(this.servers);
		}

		this.minecraft.setScreen(this);
	}

	private void addServerCallback(boolean bl) {
		if (bl) {
			ServerData serverData = this.servers.unhide(this.editingServer.ip);
			if (serverData != null) {
				serverData.copyNameIconFrom(this.editingServer);
				this.servers.save();
			} else {
				this.servers.add(this.editingServer, false);
				this.servers.save();
			}

			this.serverSelectionList.setSelected((ServerSelectionList.Entry)null);
			this.serverSelectionList.updateOnlineServers(this.servers);
		}

		this.minecraft.setScreen(this);
	}

	private void directJoinCallback(boolean bl) {
		if (bl) {
			ServerData serverData = this.servers.get(this.editingServer.ip);
			if (serverData == null) {
				this.servers.add(this.editingServer, true);
				this.servers.save();
				this.join(this.editingServer);
			} else {
				this.join(serverData);
			}
		} else {
			this.minecraft.setScreen(this);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if (super.keyPressed(keyEvent)) {
			return true;
		} else if (keyEvent.key() == 294) {
			this.refreshServerList();
			return true;
		} else {
			return false;
		}
	}

	public void join(ServerData serverData) {
		ConnectScreen.startConnecting(this, this.minecraft, ServerAddress.parseString(serverData.ip), serverData, false, null);
	}

	protected void onSelectedChange() {
		this.selectButton.active = false;
		this.editButton.active = false;
		this.deleteButton.active = false;
		ServerSelectionList.Entry entry = this.serverSelectionList.getSelected();
		if (entry != null && !(entry instanceof ServerSelectionList.LANHeader)) {
			this.selectButton.active = true;
			if (entry instanceof ServerSelectionList.OnlineServerEntry) {
				this.editButton.active = true;
				this.deleteButton.active = true;
			}
		}
	}

	public ServerStatusPinger getPinger() {
		return this.pinger;
	}

	public ServerList getServers() {
		return this.servers;
	}
}
