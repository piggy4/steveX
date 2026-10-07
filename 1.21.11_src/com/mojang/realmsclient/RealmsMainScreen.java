package com.mojang.realmsclient;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.RateLimiter;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.Ping;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.PingResult;
import com.mojang.realmsclient.dto.RealmsNotification;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.dto.RealmsServerPlayerLists;
import com.mojang.realmsclient.dto.RegionPingResult;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.gui.RealmsDataFetcher;
import com.mojang.realmsclient.gui.RealmsServerList;
import com.mojang.realmsclient.gui.screens.AddRealmPopupScreen;
import com.mojang.realmsclient.gui.screens.RealmsCreateRealmScreen;
import com.mojang.realmsclient.gui.screens.RealmsGenericErrorScreen;
import com.mojang.realmsclient.gui.screens.RealmsLongRunningMcoTaskScreen;
import com.mojang.realmsclient.gui.screens.RealmsPendingInvitesScreen;
import com.mojang.realmsclient.gui.screens.RealmsPopups;
import com.mojang.realmsclient.gui.screens.configuration.RealmsConfigureWorldScreen;
import com.mojang.realmsclient.gui.task.DataFetcher;
import com.mojang.realmsclient.util.RealmsPersistence;
import com.mojang.realmsclient.util.RealmsUtil;
import com.mojang.realmsclient.util.task.GetServerDetailsTask;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.ImageWidget;
import net.minecraft.client.gui.components.LoadingDotsWidget;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.PopupScreen;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.WidgetTooltipHolder;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientActivePlayersTooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.realms.RealmsScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.CommonLinks;
import net.minecraft.util.Util;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.GameType;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class RealmsMainScreen extends RealmsScreen {
	static final Identifier INFO_SPRITE = Identifier.withDefaultNamespace("icon/info");
	static final Identifier NEW_REALM_SPRITE = Identifier.withDefaultNamespace("icon/new_realm");
	static final Identifier EXPIRED_SPRITE = Identifier.withDefaultNamespace("realm_status/expired");
	static final Identifier EXPIRES_SOON_SPRITE = Identifier.withDefaultNamespace("realm_status/expires_soon");
	static final Identifier OPEN_SPRITE = Identifier.withDefaultNamespace("realm_status/open");
	static final Identifier CLOSED_SPRITE = Identifier.withDefaultNamespace("realm_status/closed");
	private static final Identifier INVITE_SPRITE = Identifier.withDefaultNamespace("icon/invite");
	private static final Identifier NEWS_SPRITE = Identifier.withDefaultNamespace("icon/news");
	public static final Identifier HARDCORE_MODE_SPRITE = Identifier.withDefaultNamespace("hud/heart/hardcore_full");
	static final Logger LOGGER = LogUtils.getLogger();
	private static final Identifier NO_REALMS_LOCATION = Identifier.withDefaultNamespace("textures/gui/realms/no_realms.png");
	private static final Component TITLE = Component.translatable("menu.online");
	private static final Component LOADING_TEXT = Component.translatable("mco.selectServer.loading");
	static final Component SERVER_UNITIALIZED_TEXT = Component.translatable("mco.selectServer.uninitialized");
	static final Component SUBSCRIPTION_EXPIRED_TEXT = Component.translatable("mco.selectServer.expiredList");
	private static final Component SUBSCRIPTION_RENEW_TEXT = Component.translatable("mco.selectServer.expiredRenew");
	static final Component TRIAL_EXPIRED_TEXT = Component.translatable("mco.selectServer.expiredTrial");
	private static final Component PLAY_TEXT = Component.translatable("mco.selectServer.play");
	private static final Component LEAVE_SERVER_TEXT = Component.translatable("mco.selectServer.leave");
	private static final Component CONFIGURE_SERVER_TEXT = Component.translatable("mco.selectServer.configure");
	static final Component SERVER_EXPIRED_TOOLTIP = Component.translatable("mco.selectServer.expired");
	static final Component SERVER_EXPIRES_SOON_TOOLTIP = Component.translatable("mco.selectServer.expires.soon");
	static final Component SERVER_EXPIRES_IN_DAY_TOOLTIP = Component.translatable("mco.selectServer.expires.day");
	static final Component SERVER_OPEN_TOOLTIP = Component.translatable("mco.selectServer.open");
	static final Component SERVER_CLOSED_TOOLTIP = Component.translatable("mco.selectServer.closed");
	static final Component UNITIALIZED_WORLD_NARRATION = Component.translatable("gui.narrate.button", SERVER_UNITIALIZED_TEXT);
	private static final Component NO_REALMS_TEXT = Component.translatable("mco.selectServer.noRealms");
	private static final Component NO_PENDING_INVITES = Component.translatable("mco.invites.nopending");
	private static final Component PENDING_INVITES = Component.translatable("mco.invites.pending");
	private static final Component INCOMPATIBLE_POPUP_TITLE = Component.translatable("mco.compatibility.incompatible.popup.title");
	private static final Component INCOMPATIBLE_RELEASE_TYPE_POPUP_MESSAGE = Component.translatable("mco.compatibility.incompatible.releaseType.popup.message");
	private static final int BUTTON_WIDTH = 100;
	private static final int BUTTON_COLUMNS = 3;
	private static final int BUTTON_SPACING = 4;
	private static final int CONTENT_WIDTH = 308;
	private static final int LOGO_PADDING = 5;
	private static final int HEADER_HEIGHT = 44;
	private static final int FOOTER_PADDING = 11;
	private static final int NEW_REALM_SPRITE_WIDTH = 40;
	private static final int NEW_REALM_SPRITE_HEIGHT = 20;
	private static final boolean SNAPSHOT = !SharedConstants.getCurrentVersion().stable();
	private static boolean snapshotToggle = SNAPSHOT;
	private final CompletableFuture<RealmsAvailability.Result> availability = RealmsAvailability.get();
	private DataFetcher.@Nullable Subscription dataSubscription;
	private final Set<UUID> handledSeenNotifications = new HashSet<>();
	private static boolean regionsPinged;
	private final RateLimiter inviteNarrationLimiter;
	private final Screen lastScreen;
	private Button playButton;
	private Button backButton;
	private Button renewButton;
	private Button configureButton;
	private Button leaveButton;
	RealmsMainScreen.RealmSelectionList realmSelectionList;
	RealmsServerList serverList;
	List<RealmsServer> availableSnapshotServers = List.of();
	RealmsServerPlayerLists onlinePlayersPerRealm = new RealmsServerPlayerLists(Map.of());
	private volatile boolean trialsAvailable;
	private volatile @Nullable String newsLink;
	final List<RealmsNotification> notifications = new ArrayList<>();
	private Button addRealmButton;
	private RealmsMainScreen.NotificationButton pendingInvitesButton;
	private RealmsMainScreen.NotificationButton newsButton;
	private RealmsMainScreen.LayoutState activeLayoutState;
	private @Nullable HeaderAndFooterLayout layout;

	public RealmsMainScreen(Screen screen) {
		super(TITLE);
		this.lastScreen = screen;
		this.inviteNarrationLimiter = RateLimiter.create(0.016666668F);
	}

	@Override
	public void init() {
		this.serverList = new RealmsServerList(this.minecraft);
		this.realmSelectionList = new RealmsMainScreen.RealmSelectionList();
		Component component = Component.translatable("mco.invites.title");
		this.pendingInvitesButton = new RealmsMainScreen.NotificationButton(
			component, INVITE_SPRITE, button -> this.minecraft.setScreen(new RealmsPendingInvitesScreen(this, component)), null
		);
		Component component2 = Component.translatable("mco.news");
		this.newsButton = new RealmsMainScreen.NotificationButton(component2, NEWS_SPRITE, button -> {
			String string = this.newsLink;
			if (string != null) {
				ConfirmLinkScreen.confirmLinkNow(this, string);
				if (this.newsButton.notificationCount() != 0) {
					RealmsPersistence.RealmsPersistenceData realmsPersistenceData = RealmsPersistence.readFile();
					realmsPersistenceData.hasUnreadNews = false;
					RealmsPersistence.writeFile(realmsPersistenceData);
					this.newsButton.setNotificationCount(0);
				}
			}
		}, component2);
		this.playButton = Button.builder(PLAY_TEXT, button -> play(this.getSelectedServer(), this)).width(100).build();
		this.configureButton = Button.builder(CONFIGURE_SERVER_TEXT, button -> this.configureClicked(this.getSelectedServer())).width(100).build();
		this.renewButton = Button.builder(SUBSCRIPTION_RENEW_TEXT, button -> this.onRenew(this.getSelectedServer())).width(100).build();
		this.leaveButton = Button.builder(LEAVE_SERVER_TEXT, button -> this.leaveClicked(this.getSelectedServer())).width(100).build();
		this.addRealmButton = Button.builder(Component.translatable("mco.selectServer.purchase"), button -> this.openTrialAvailablePopup()).size(100, 20).build();
		this.backButton = Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).width(100).build();
		if (RealmsClient.ENVIRONMENT == RealmsClient.Environment.STAGE) {
			this.addRenderableWidget(
				CycleButton.booleanBuilder(Component.literal("Snapshot"), Component.literal("Release"), snapshotToggle)
					.create(5, 5, 100, 20, Component.literal("Realm"), (cycleButton, boolean_) -> {
						snapshotToggle = boolean_;
						this.availableSnapshotServers = List.of();
						this.debugRefreshDataFetchers();
					})
			);
		}

		this.updateLayout(RealmsMainScreen.LayoutState.LOADING);
		this.updateButtonStates();
		this.availability.thenAcceptAsync(result -> {
			Screen screen = result.createErrorScreen(this.lastScreen);
			if (screen == null) {
				this.dataSubscription = this.initDataFetcher(this.minecraft.realmsDataFetcher());
			} else {
				this.minecraft.setScreen(screen);
			}
		}, this.screenExecutor);
	}

	public static boolean isSnapshot() {
		return SNAPSHOT && snapshotToggle;
	}

	@Override
	protected void repositionElements() {
		if (this.layout != null) {
			this.realmSelectionList.updateSize(this.width, this.layout);
			this.layout.arrangeElements();
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.lastScreen);
	}

	private void updateLayout() {
		if (this.serverList.isEmpty() && this.availableSnapshotServers.isEmpty() && this.notifications.isEmpty()) {
			this.updateLayout(RealmsMainScreen.LayoutState.NO_REALMS);
		} else {
			this.updateLayout(RealmsMainScreen.LayoutState.LIST);
		}
	}

	private void updateLayout(RealmsMainScreen.LayoutState layoutState) {
		if (this.activeLayoutState != layoutState) {
			if (this.layout != null) {
				this.layout.visitWidgets(guiEventListener -> this.removeWidget(guiEventListener));
			}

			this.layout = this.createLayout(layoutState);
			this.activeLayoutState = layoutState;
			this.layout.visitWidgets(guiEventListener -> {
				AbstractWidget var10000 = this.addRenderableWidget(guiEventListener);
			});
			this.repositionElements();
		}
	}

	private HeaderAndFooterLayout createLayout(RealmsMainScreen.LayoutState layoutState) {
		HeaderAndFooterLayout headerAndFooterLayout = new HeaderAndFooterLayout(this);
		headerAndFooterLayout.setHeaderHeight(44);
		headerAndFooterLayout.addToHeader(this.createHeader());
		Layout layout = this.createFooter(layoutState);
		layout.arrangeElements();
		headerAndFooterLayout.setFooterHeight(layout.getHeight() + 22);
		headerAndFooterLayout.addToFooter(layout);
		switch (layoutState) {
			case LOADING:
				headerAndFooterLayout.addToContents(new LoadingDotsWidget(this.font, LOADING_TEXT));
				break;
			case NO_REALMS:
				headerAndFooterLayout.addToContents(this.createNoRealmsContent());
				break;
			case LIST:
				headerAndFooterLayout.addToContents(this.realmSelectionList);
		}

		return headerAndFooterLayout;
	}

	private Layout createHeader() {
		int i = 90;
		LinearLayout linearLayout = LinearLayout.horizontal().spacing(4);
		linearLayout.defaultCellSetting().alignVerticallyMiddle();
		linearLayout.addChild(this.pendingInvitesButton);
		linearLayout.addChild(this.newsButton);
		LinearLayout linearLayout2 = LinearLayout.horizontal();
		linearLayout2.defaultCellSetting().alignVerticallyMiddle();
		linearLayout2.addChild(SpacerElement.width(90));
		linearLayout2.addChild(realmsLogo(), LayoutSettings::alignHorizontallyCenter);
		linearLayout2.addChild(new FrameLayout(90, 44)).addChild(linearLayout, LayoutSettings::alignHorizontallyRight);
		return linearLayout2;
	}

	private Layout createFooter(RealmsMainScreen.LayoutState layoutState) {
		GridLayout gridLayout = new GridLayout().spacing(4);
		GridLayout.RowHelper rowHelper = gridLayout.createRowHelper(3);
		if (layoutState == RealmsMainScreen.LayoutState.LIST) {
			rowHelper.addChild(this.playButton);
			rowHelper.addChild(this.configureButton);
			rowHelper.addChild(this.renewButton);
			rowHelper.addChild(this.leaveButton);
		}

		rowHelper.addChild(this.addRealmButton);
		rowHelper.addChild(this.backButton);
		return gridLayout;
	}

	private LinearLayout createNoRealmsContent() {
		LinearLayout linearLayout = LinearLayout.vertical().spacing(8);
		linearLayout.defaultCellSetting().alignHorizontallyCenter();
		linearLayout.addChild(ImageWidget.texture(130, 64, NO_REALMS_LOCATION, 130, 64));
		linearLayout.addChild(
			FocusableTextWidget.builder(NO_REALMS_TEXT, this.font)
				.maxWidth(308)
				.alwaysShowBorder(false)
				.backgroundFill(FocusableTextWidget.BackgroundFill.ON_FOCUS)
				.build()
		);
		return linearLayout;
	}

	void updateButtonStates() {
		RealmsServer realmsServer = this.getSelectedServer();
		boolean bl = realmsServer != null;
		this.addRealmButton.active = this.activeLayoutState != RealmsMainScreen.LayoutState.LOADING;
		this.playButton.active = bl && realmsServer.shouldPlayButtonBeActive();
		if (!this.playButton.active && bl && realmsServer.state == RealmsServer.State.CLOSED) {
			this.playButton.setTooltip(Tooltip.create(RealmsServer.WORLD_CLOSED_COMPONENT));
		}

		this.renewButton.active = bl && this.shouldRenewButtonBeActive(realmsServer);
		this.leaveButton.active = bl && this.shouldLeaveButtonBeActive(realmsServer);
		this.configureButton.active = bl && this.shouldConfigureButtonBeActive(realmsServer);
	}

	private boolean shouldRenewButtonBeActive(RealmsServer realmsServer) {
		return realmsServer.expired && isSelfOwnedServer(realmsServer);
	}

	private boolean shouldConfigureButtonBeActive(RealmsServer realmsServer) {
		return isSelfOwnedServer(realmsServer) && realmsServer.state != RealmsServer.State.UNINITIALIZED;
	}

	private boolean shouldLeaveButtonBeActive(RealmsServer realmsServer) {
		return !isSelfOwnedServer(realmsServer);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.dataSubscription != null) {
			this.dataSubscription.tick();
		}
	}

	public static void refreshPendingInvites() {
		Minecraft.getInstance().realmsDataFetcher().pendingInvitesTask.reset();
	}

	public static void refreshServerList() {
		Minecraft.getInstance().realmsDataFetcher().serverListUpdateTask.reset();
	}

	private void debugRefreshDataFetchers() {
		for (DataFetcher.Task<?> task : this.minecraft.realmsDataFetcher().getTasks()) {
			task.reset();
		}
	}

	private DataFetcher.Subscription initDataFetcher(RealmsDataFetcher realmsDataFetcher) {
		DataFetcher.Subscription subscription = realmsDataFetcher.dataFetcher.createSubscription();
		subscription.subscribe(realmsDataFetcher.serverListUpdateTask, serverListData -> {
			this.serverList.updateServersList(serverListData.serverList());
			this.availableSnapshotServers = serverListData.availableSnapshotServers();
			this.refreshListAndLayout();
			boolean bl = false;

			for (RealmsServer realmsServer : this.serverList) {
				if (this.isSelfOwnedNonExpiredServer(realmsServer)) {
					bl = true;
				}
			}

			if (!regionsPinged && bl) {
				regionsPinged = true;
				this.pingRegions();
			}
		});
		callRealmsClient(RealmsClient::getNotifications, list -> {
			this.notifications.clear();
			this.notifications.addAll(list);

			for (RealmsNotification realmsNotification : list) {
				if (realmsNotification instanceof RealmsNotification.InfoPopup infoPopup) {
					PopupScreen popupScreen = infoPopup.buildScreen(this, this::dismissNotification);
					if (popupScreen != null) {
						this.minecraft.setScreen(popupScreen);
						this.markNotificationsAsSeen(List.of(realmsNotification));
						break;
					}
				}
			}

			if (!this.notifications.isEmpty() && this.activeLayoutState != RealmsMainScreen.LayoutState.LOADING) {
				this.refreshListAndLayout();
			}
		});
		subscription.subscribe(realmsDataFetcher.pendingInvitesTask, integer -> {
			this.pendingInvitesButton.setNotificationCount(integer);
			this.pendingInvitesButton.setTooltip(integer == 0 ? Tooltip.create(NO_PENDING_INVITES) : Tooltip.create(PENDING_INVITES));
			if (integer > 0 && this.inviteNarrationLimiter.tryAcquire(1)) {
				this.minecraft.getNarrator().saySystemNow(Component.translatable("mco.configure.world.invite.narration", integer));
			}
		});
		subscription.subscribe(realmsDataFetcher.trialAvailabilityTask, boolean_ -> this.trialsAvailable = boolean_);
		subscription.subscribe(realmsDataFetcher.onlinePlayersTask, realmsServerPlayerLists -> this.onlinePlayersPerRealm = realmsServerPlayerLists);
		subscription.subscribe(realmsDataFetcher.newsTask, realmsNews -> {
			realmsDataFetcher.newsManager.updateUnreadNews(realmsNews);
			this.newsLink = realmsDataFetcher.newsManager.newsLink();
			this.newsButton.setNotificationCount(realmsDataFetcher.newsManager.hasUnreadNews() ? Integer.MAX_VALUE : 0);
		});
		return subscription;
	}

	void markNotificationsAsSeen(Collection<RealmsNotification> collection) {
		List<UUID> list = new ArrayList<>(collection.size());

		for (RealmsNotification realmsNotification : collection) {
			if (!realmsNotification.seen() && !this.handledSeenNotifications.contains(realmsNotification.uuid())) {
				list.add(realmsNotification.uuid());
			}
		}

		if (!list.isEmpty()) {
			callRealmsClient(realmsClient -> {
				realmsClient.notificationsSeen(list);
				return null;
			}, object -> this.handledSeenNotifications.addAll(list));
		}
	}

	private static <T> void callRealmsClient(RealmsMainScreen.RealmsCall<T> realmsCall, Consumer<T> consumer) {
		Minecraft minecraft = Minecraft.getInstance();
		CompletableFuture.<T>supplyAsync(() -> {
			try {
				return realmsCall.request(RealmsClient.getOrCreate(minecraft));
			} catch (RealmsServiceException realmsServiceException) {
				throw new RuntimeException(realmsServiceException);
			}
		}).thenAcceptAsync(consumer, minecraft).exceptionally(throwable -> {
			LOGGER.error("Failed to execute call to Realms Service", throwable);
			return null;
		});
	}

	private void refreshListAndLayout() {
		this.realmSelectionList.refreshEntries(this);
		this.updateLayout();
		this.updateButtonStates();
	}

	private void pingRegions() {
		new Thread(() -> {
			List<RegionPingResult> list = Ping.pingAllRegions();
			RealmsClient realmsClient = RealmsClient.getOrCreate();
			PingResult pingResult = new PingResult(list, this.getOwnedNonExpiredRealmIds());

			try {
				realmsClient.sendPingResults(pingResult);
			} catch (Throwable throwable) {
				LOGGER.warn("Could not send ping result to Realms: ", throwable);
			}
		}).start();
	}

	private List<Long> getOwnedNonExpiredRealmIds() {
		List<Long> list = Lists.newArrayList();

		for (RealmsServer realmsServer : this.serverList) {
			if (this.isSelfOwnedNonExpiredServer(realmsServer)) {
				list.add(realmsServer.id);
			}
		}

		return list;
	}

	private void onRenew(@Nullable RealmsServer realmsServer) {
		if (realmsServer != null) {
			String string = CommonLinks.extendRealms(realmsServer.remoteSubscriptionId, this.minecraft.getUser().getProfileId(), realmsServer.expiredTrial);
			this.minecraft.setScreen(new ConfirmLinkScreen(bl -> {
				if (bl) {
					Util.getPlatform().openUri(string);
				} else {
					this.minecraft.setScreen(this);
				}
			}, string, true));
		}
	}

	private void configureClicked(@Nullable RealmsServer realmsServer) {
		if (realmsServer != null && this.minecraft.isLocalPlayer(realmsServer.ownerUUID)) {
			this.minecraft.setScreen(new RealmsConfigureWorldScreen(this, realmsServer.id));
		}
	}

	private void leaveClicked(@Nullable RealmsServer realmsServer) {
		if (realmsServer != null && !this.minecraft.isLocalPlayer(realmsServer.ownerUUID)) {
			Component component = Component.translatable("mco.configure.world.leave.question.line1");
			this.minecraft.setScreen(RealmsPopups.infoPopupScreen(this, component, popupScreen -> this.leaveServer(realmsServer)));
		}
	}

	private @Nullable RealmsServer getSelectedServer() {
		return this.realmSelectionList.getSelected() instanceof RealmsMainScreen.ServerEntry serverEntry ? serverEntry.getServer() : null;
	}

	private void leaveServer(RealmsServer realmsServer) {
		(new Thread("Realms-leave-server") {
				@Override
				public void run() {
					try {
						RealmsClient realmsClient = RealmsClient.getOrCreate();
						realmsClient.uninviteMyselfFrom(realmsServer.id);
						RealmsMainScreen.this.minecraft.execute(RealmsMainScreen::refreshServerList);
					} catch (RealmsServiceException realmsServiceException) {
						RealmsMainScreen.LOGGER.error("Couldn't configure world", realmsServiceException);
						RealmsMainScreen.this.minecraft
							.execute(() -> RealmsMainScreen.this.minecraft.setScreen(new RealmsGenericErrorScreen(realmsServiceException, RealmsMainScreen.this)));
					}
				}
			})
			.start();
		this.minecraft.setScreen(this);
	}

	void dismissNotification(UUID uUID) {
		callRealmsClient(realmsClient -> {
			realmsClient.notificationsDismiss(List.of(uUID));
			return null;
		}, object -> {
			this.notifications.removeIf(realmsNotification -> realmsNotification.dismissable() && uUID.equals(realmsNotification.uuid()));
			this.refreshListAndLayout();
		});
	}

	public void resetScreen() {
		this.realmSelectionList.setSelected((RealmsMainScreen.Entry)null);
		refreshServerList();
	}

	@Override
	public Component getNarrationMessage() {
		return switch (this.activeLayoutState) {
			case LOADING -> CommonComponents.joinForNarration(super.getNarrationMessage(), LOADING_TEXT);
			case NO_REALMS -> CommonComponents.joinForNarration(super.getNarrationMessage(), NO_REALMS_TEXT);
			case LIST -> super.getNarrationMessage();
		};
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		if (isSnapshot()) {
			guiGraphics.drawString(this.font, "Minecraft " + SharedConstants.getCurrentVersion().name(), 2, this.height - 10, -1);
		}

		if (this.trialsAvailable && this.addRealmButton.active) {
			AddRealmPopupScreen.renderDiamond(guiGraphics, this.addRealmButton);
		}

		switch (RealmsClient.ENVIRONMENT) {
			case STAGE:
				this.renderEnvironment(guiGraphics, "STAGE!", -256);
				break;
			case LOCAL:
				this.renderEnvironment(guiGraphics, "LOCAL!", -8388737);
		}
	}

	private void openTrialAvailablePopup() {
		this.minecraft.setScreen(new AddRealmPopupScreen(this, this.trialsAvailable));
	}

	public static void play(@Nullable RealmsServer realmsServer, Screen screen) {
		play(realmsServer, screen, false);
	}

	public static void play(@Nullable RealmsServer realmsServer, Screen screen, boolean bl) {
		if (realmsServer != null) {
			if (!isSnapshot() || bl || realmsServer.isMinigameActive()) {
				Minecraft.getInstance().setScreen(new RealmsLongRunningMcoTaskScreen(screen, new GetServerDetailsTask(screen, realmsServer)));
				return;
			}

			switch (realmsServer.compatibility) {
				case COMPATIBLE:
					Minecraft.getInstance().setScreen(new RealmsLongRunningMcoTaskScreen(screen, new GetServerDetailsTask(screen, realmsServer)));
					break;
				case UNVERIFIABLE:
					confirmToPlay(
						realmsServer,
						screen,
						Component.translatable("mco.compatibility.unverifiable.title").withColor(-171),
						Component.translatable("mco.compatibility.unverifiable.message"),
						CommonComponents.GUI_CONTINUE
					);
					break;
				case NEEDS_DOWNGRADE:
					confirmToPlay(
						realmsServer,
						screen,
						Component.translatable("selectWorld.backupQuestion.downgrade").withColor(-2142128),
						Component.translatable(
							"mco.compatibility.downgrade.description",
							Component.literal(realmsServer.activeVersion).withColor(-171),
							Component.literal(SharedConstants.getCurrentVersion().name()).withColor(-171)
						),
						Component.translatable("mco.compatibility.downgrade")
					);
					break;
				case NEEDS_UPGRADE:
					upgradeRealmAndPlay(realmsServer, screen);
					break;
				case INCOMPATIBLE:
					Minecraft.getInstance()
						.setScreen(
							new PopupScreen.Builder(screen, INCOMPATIBLE_POPUP_TITLE)
								.setMessage(
									Component.translatable(
										"mco.compatibility.incompatible.series.popup.message",
										Component.literal(realmsServer.activeVersion).withColor(-171),
										Component.literal(SharedConstants.getCurrentVersion().name()).withColor(-171)
									)
								)
								.addButton(CommonComponents.GUI_BACK, PopupScreen::onClose)
								.build()
						);
					break;
				case RELEASE_TYPE_INCOMPATIBLE:
					Minecraft.getInstance()
						.setScreen(
							new PopupScreen.Builder(screen, INCOMPATIBLE_POPUP_TITLE)
								.setMessage(INCOMPATIBLE_RELEASE_TYPE_POPUP_MESSAGE)
								.addButton(CommonComponents.GUI_BACK, PopupScreen::onClose)
								.build()
						);
			}
		}
	}

	private static void confirmToPlay(RealmsServer realmsServer, Screen screen, Component component, Component component2, Component component3) {
		Minecraft.getInstance().setScreen(new PopupScreen.Builder(screen, component).setMessage(component2).addButton(component3, popupScreen -> {
			Minecraft.getInstance().setScreen(new RealmsLongRunningMcoTaskScreen(screen, new GetServerDetailsTask(screen, realmsServer)));
			refreshServerList();
		}).addButton(CommonComponents.GUI_CANCEL, PopupScreen::onClose).build());
	}

	private static void upgradeRealmAndPlay(RealmsServer realmsServer, Screen screen) {
		Component component = Component.translatable("mco.compatibility.upgrade.title").withColor(-171);
		Component component2 = Component.translatable("mco.compatibility.upgrade");
		Component component3 = Component.literal(realmsServer.activeVersion).withColor(-171);
		Component component4 = Component.literal(SharedConstants.getCurrentVersion().name()).withColor(-171);
		Component component5 = isSelfOwnedServer(realmsServer)
			? Component.translatable("mco.compatibility.upgrade.description", component3, component4)
			: Component.translatable("mco.compatibility.upgrade.friend.description", component3, component4);
		confirmToPlay(realmsServer, screen, component, component5, component2);
	}

	public static Component getVersionComponent(String string, boolean bl) {
		return getVersionComponent(string, bl ? -8355712 : -2142128);
	}

	public static Component getVersionComponent(String string, int i) {
		return StringUtils.isBlank(string) ? CommonComponents.EMPTY : Component.literal(string).withColor(i);
	}

	public static Component getGameModeComponent(int i, boolean bl) {
		return bl ? Component.translatable("gameMode.hardcore").withColor(-65536) : GameType.byId(i).getLongDisplayName();
	}

	static boolean isSelfOwnedServer(RealmsServer realmsServer) {
		return Minecraft.getInstance().isLocalPlayer(realmsServer.ownerUUID);
	}

	private boolean isSelfOwnedNonExpiredServer(RealmsServer realmsServer) {
		return isSelfOwnedServer(realmsServer) && !realmsServer.expired;
	}

	private void renderEnvironment(GuiGraphics guiGraphics, String string, int i) {
		guiGraphics.pose().pushMatrix();
		guiGraphics.pose().translate(this.width / 2 - 25, 20.0F);
		guiGraphics.pose().rotate((float) (-Math.PI / 9));
		guiGraphics.pose().scale(1.5F, 1.5F);
		guiGraphics.drawString(this.font, string, 0, 0, i);
		guiGraphics.pose().popMatrix();
	}

	@Environment(EnvType.CLIENT)
	class AvailableSnapshotEntry extends RealmsMainScreen.Entry {
		private static final Component START_SNAPSHOT_REALM = Component.translatable("mco.snapshot.start");
		private static final int TEXT_PADDING = 5;
		private final WidgetTooltipHolder tooltip = new WidgetTooltipHolder();
		private final RealmsServer parent;

		public AvailableSnapshotEntry(final RealmsServer realmsServer) {
			this.parent = realmsServer;
			this.tooltip.set(Tooltip.create(Component.translatable("mco.snapshot.tooltip")));
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, RealmsMainScreen.NEW_REALM_SPRITE, this.getContentX() - 5, this.getContentYMiddle() - 10, 40, 20);
			int k = this.getContentYMiddle() - 9 / 2;
			guiGraphics.drawString(RealmsMainScreen.this.font, START_SNAPSHOT_REALM, this.getContentX() + 40 - 2, k - 5, -8388737);
			guiGraphics.drawString(
				RealmsMainScreen.this.font,
				Component.translatable("mco.snapshot.description", Objects.requireNonNullElse(this.parent.name, "unknown server")),
				this.getContentX() + 40 - 2,
				k + 5,
				-8355712
			);
			this.tooltip
				.refreshTooltipForNextRenderPass(
					guiGraphics, i, j, bl, this.isFocused(), new ScreenRectangle(this.getContentX(), this.getContentY(), this.getContentWidth(), this.getContentHeight())
				);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
			this.addSnapshotRealm();
			return true;
		}

		@Override
		public boolean keyPressed(KeyEvent keyEvent) {
			if (keyEvent.isSelection()) {
				this.addSnapshotRealm();
				return false;
			} else {
				return super.keyPressed(keyEvent);
			}
		}

		private void addSnapshotRealm() {
			RealmsMainScreen.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			RealmsMainScreen.this.minecraft
				.setScreen(
					new PopupScreen.Builder(RealmsMainScreen.this, Component.translatable("mco.snapshot.createSnapshotPopup.title"))
						.setMessage(Component.translatable("mco.snapshot.createSnapshotPopup.text"))
						.addButton(
							Component.translatable("mco.selectServer.create"),
							popupScreen -> RealmsMainScreen.this.minecraft.setScreen(new RealmsCreateRealmScreen(RealmsMainScreen.this, this.parent, true))
						)
						.addButton(CommonComponents.GUI_CANCEL, PopupScreen::onClose)
						.build()
				);
		}

		@Override
		public Component getNarration() {
			return Component.translatable(
				"gui.narrate.button",
				CommonComponents.joinForNarration(
					START_SNAPSHOT_REALM, Component.translatable("mco.snapshot.description", Objects.requireNonNullElse(this.parent.name, "unknown server"))
				)
			);
		}
	}

	@Environment(EnvType.CLIENT)
	static class CrossButton extends ImageButton {
		private static final WidgetSprites SPRITES = new WidgetSprites(
			Identifier.withDefaultNamespace("widget/cross_button"), Identifier.withDefaultNamespace("widget/cross_button_highlighted")
		);

		protected CrossButton(Button.OnPress onPress, Component component) {
			super(0, 0, 14, 14, SPRITES, onPress);
			this.setTooltip(Tooltip.create(component));
		}
	}

	@Environment(EnvType.CLIENT)
	abstract class Entry extends ObjectSelectionList.Entry<RealmsMainScreen.Entry> {
		protected static final int STATUS_LIGHT_WIDTH = 10;
		private static final int STATUS_LIGHT_HEIGHT = 28;
		protected static final int PADDING_X = 7;
		protected static final int PADDING_Y = 2;

		protected void renderStatusLights(RealmsServer realmsServer, GuiGraphics guiGraphics, int i, int j, int k, int l) {
			int m = i - 10 - 7;
			int n = j + 2;
			if (realmsServer.expired) {
				this.drawRealmStatus(guiGraphics, m, n, k, l, RealmsMainScreen.EXPIRED_SPRITE, () -> RealmsMainScreen.SERVER_EXPIRED_TOOLTIP);
			} else if (realmsServer.state == RealmsServer.State.CLOSED) {
				this.drawRealmStatus(guiGraphics, m, n, k, l, RealmsMainScreen.CLOSED_SPRITE, () -> RealmsMainScreen.SERVER_CLOSED_TOOLTIP);
			} else if (RealmsMainScreen.isSelfOwnedServer(realmsServer) && realmsServer.daysLeft < 7) {
				this.drawRealmStatus(
					guiGraphics,
					m,
					n,
					k,
					l,
					RealmsMainScreen.EXPIRES_SOON_SPRITE,
					() -> {
						if (realmsServer.daysLeft <= 0) {
							return RealmsMainScreen.SERVER_EXPIRES_SOON_TOOLTIP;
						} else {
							return realmsServer.daysLeft == 1
								? RealmsMainScreen.SERVER_EXPIRES_IN_DAY_TOOLTIP
								: Component.translatable("mco.selectServer.expires.days", realmsServer.daysLeft);
						}
					}
				);
			} else if (realmsServer.state == RealmsServer.State.OPEN) {
				this.drawRealmStatus(guiGraphics, m, n, k, l, RealmsMainScreen.OPEN_SPRITE, () -> RealmsMainScreen.SERVER_OPEN_TOOLTIP);
			}
		}

		private void drawRealmStatus(GuiGraphics guiGraphics, int i, int j, int k, int l, Identifier identifier, Supplier<Component> supplier) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, identifier, i, j, 10, 28);
			if (RealmsMainScreen.this.realmSelectionList.isMouseOver(k, l) && k >= i && k <= i + 10 && l >= j && l <= j + 28) {
				guiGraphics.setTooltipForNextFrame(supplier.get(), k, l);
			}
		}

		protected void renderFirstLine(GuiGraphics guiGraphics, int i, int j, int k, int l, RealmsServer realmsServer) {
			int m = this.textX(j);
			int n = this.firstLineY(i);
			Component component = RealmsMainScreen.getVersionComponent(realmsServer.activeVersion, realmsServer.isCompatible());
			int o = this.versionTextX(j, k, component);
			this.renderClampedString(guiGraphics, realmsServer.getName(), m, n, o, l);
			if (component != CommonComponents.EMPTY && !realmsServer.isMinigameActive()) {
				guiGraphics.drawString(RealmsMainScreen.this.font, component, o, n, -8355712);
			}
		}

		protected void renderSecondLine(GuiGraphics guiGraphics, int i, int j, int k, RealmsServer realmsServer) {
			int l = this.textX(j);
			int m = this.firstLineY(i);
			int n = this.secondLineY(m);
			String string = realmsServer.getMinigameName();
			boolean bl = realmsServer.isMinigameActive();
			if (bl && string != null) {
				Component component = Component.literal(string).withStyle(ChatFormatting.GRAY);
				guiGraphics.drawString(RealmsMainScreen.this.font, Component.translatable("mco.selectServer.minigameName", component).withColor(-171), l, n, -1);
			} else {
				int o = this.renderGameMode(realmsServer, guiGraphics, j, k, m);
				this.renderClampedString(guiGraphics, realmsServer.getDescription(), l, this.secondLineY(m), o, -8355712);
			}
		}

		protected void renderThirdLine(GuiGraphics guiGraphics, int i, int j, RealmsServer realmsServer) {
			int k = this.textX(j);
			int l = this.firstLineY(i);
			int m = this.thirdLineY(l);
			if (!RealmsMainScreen.isSelfOwnedServer(realmsServer)) {
				guiGraphics.drawString(RealmsMainScreen.this.font, realmsServer.owner, k, this.thirdLineY(l), -8355712);
			} else if (realmsServer.expired) {
				Component component = realmsServer.expiredTrial ? RealmsMainScreen.TRIAL_EXPIRED_TEXT : RealmsMainScreen.SUBSCRIPTION_EXPIRED_TEXT;
				guiGraphics.drawString(RealmsMainScreen.this.font, component, k, m, -2142128);
			}
		}

		protected void renderClampedString(GuiGraphics guiGraphics, @Nullable String string, int i, int j, int k, int l) {
			if (string != null) {
				int m = k - i;
				if (RealmsMainScreen.this.font.width(string) > m) {
					String string2 = RealmsMainScreen.this.font.plainSubstrByWidth(string, m - RealmsMainScreen.this.font.width("... "));
					guiGraphics.drawString(RealmsMainScreen.this.font, string2 + "...", i, j, l);
				} else {
					guiGraphics.drawString(RealmsMainScreen.this.font, string, i, j, l);
				}
			}
		}

		protected int versionTextX(int i, int j, Component component) {
			return i + j - RealmsMainScreen.this.font.width(component) - 20;
		}

		protected int gameModeTextX(int i, int j, Component component) {
			return i + j - RealmsMainScreen.this.font.width(component) - 20;
		}

		protected int renderGameMode(RealmsServer realmsServer, GuiGraphics guiGraphics, int i, int j, int k) {
			boolean bl = realmsServer.isHardcore;
			int l = realmsServer.gameMode;
			int m = i;
			if (GameType.isValidId(l)) {
				Component component = RealmsMainScreen.getGameModeComponent(l, bl);
				m = this.gameModeTextX(i, j, component);
				guiGraphics.drawString(RealmsMainScreen.this.font, component, m, this.secondLineY(k), -8355712);
			}

			if (bl) {
				m -= 10;
				guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, RealmsMainScreen.HARDCORE_MODE_SPRITE, m, this.secondLineY(k), 8, 8);
			}

			return m;
		}

		protected int firstLineY(int i) {
			return i + 1;
		}

		protected int lineHeight() {
			return 2 + 9;
		}

		protected int textX(int i) {
			return i + 36 + 2;
		}

		protected int secondLineY(int i) {
			return i + this.lineHeight();
		}

		protected int thirdLineY(int i) {
			return i + this.lineHeight() * 2;
		}
	}

	@Environment(EnvType.CLIENT)
	enum LayoutState {
		LOADING,
		NO_REALMS,
		LIST;
	}

	@Environment(EnvType.CLIENT)
	static class NotificationButton extends SpriteIconButton.CenteredIcon {
		private static final Identifier[] NOTIFICATION_ICONS = new Identifier[]{
			Identifier.withDefaultNamespace("notification/1"),
			Identifier.withDefaultNamespace("notification/2"),
			Identifier.withDefaultNamespace("notification/3"),
			Identifier.withDefaultNamespace("notification/4"),
			Identifier.withDefaultNamespace("notification/5"),
			Identifier.withDefaultNamespace("notification/more")
		};
		private static final int UNKNOWN_COUNT = Integer.MAX_VALUE;
		private static final int SIZE = 20;
		private static final int SPRITE_SIZE = 14;
		private int notificationCount;

		public NotificationButton(Component component, Identifier identifier, Button.OnPress onPress, @Nullable Component component2) {
			super(20, 20, component, 14, 14, new WidgetSprites(identifier), onPress, component2, null);
		}

		int notificationCount() {
			return this.notificationCount;
		}

		public void setNotificationCount(int i) {
			this.notificationCount = i;
		}

		@Override
		public void renderContents(GuiGraphics guiGraphics, int i, int j, float f) {
			super.renderContents(guiGraphics, i, j, f);
			if (this.active && this.notificationCount != 0) {
				this.drawNotificationCounter(guiGraphics);
			}
		}

		private void drawNotificationCounter(GuiGraphics guiGraphics) {
			guiGraphics.blitSprite(
				RenderPipelines.GUI_TEXTURED, NOTIFICATION_ICONS[Math.min(this.notificationCount, 6) - 1], this.getX() + this.getWidth() - 5, this.getY() - 3, 8, 8
			);
		}
	}

	@Environment(EnvType.CLIENT)
	class NotificationMessageEntry extends RealmsMainScreen.Entry {
		private static final int SIDE_MARGINS = 40;
		public static final int PADDING = 7;
		public static final int HEIGHT_WITHOUT_TEXT = 38;
		private final Component text;
		private final List<AbstractWidget> children = new ArrayList<>();
		private final RealmsMainScreen.@Nullable CrossButton dismissButton;
		private final MultiLineTextWidget textWidget;
		private final GridLayout gridLayout;
		private final FrameLayout textFrame;
		private final Button button;
		private int lastEntryWidth = -1;

		public NotificationMessageEntry(final RealmsMainScreen realmsMainScreen2, final int i, final Component component, final RealmsNotification.VisitUrl visitUrl) {
			this.text = component;
			this.gridLayout = new GridLayout();
			this.gridLayout.addChild(ImageWidget.sprite(20, 20, RealmsMainScreen.INFO_SPRITE), 0, 0, this.gridLayout.newCellSettings().padding(7, 7, 0, 0));
			this.gridLayout.addChild(SpacerElement.width(40), 0, 0);
			this.textFrame = this.gridLayout.addChild(new FrameLayout(0, i), 0, 1, this.gridLayout.newCellSettings().paddingTop(7));
			this.textWidget = this.textFrame
				.addChild(
					new MultiLineTextWidget(component, RealmsMainScreen.this.font).setCentered(true),
					this.textFrame.newChildLayoutSettings().alignHorizontallyCenter().alignVerticallyTop()
				);
			this.gridLayout.addChild(SpacerElement.width(40), 0, 2);
			if (visitUrl.dismissable()) {
				this.dismissButton = this.gridLayout
					.addChild(
						new RealmsMainScreen.CrossButton(button -> RealmsMainScreen.this.dismissNotification(visitUrl.uuid()), Component.translatable("mco.notification.dismiss")),
						0,
						2,
						this.gridLayout.newCellSettings().alignHorizontallyRight().padding(0, 7, 7, 0)
					);
			} else {
				this.dismissButton = null;
			}

			this.button = this.gridLayout
				.addChild(visitUrl.buildOpenLinkButton(realmsMainScreen2), 1, 1, this.gridLayout.newCellSettings().alignHorizontallyCenter().padding(4));
			this.button.setOverrideRenderHighlightedSprite(() -> this.isFocused());
			this.gridLayout.visitWidgets(this.children::add);
		}

		@Override
		public boolean keyPressed(KeyEvent keyEvent) {
			if (this.button.keyPressed(keyEvent)) {
				return true;
			} else {
				return this.dismissButton != null && this.dismissButton.keyPressed(keyEvent) ? true : super.keyPressed(keyEvent);
			}
		}

		private void updateEntryWidth() {
			int i = this.getWidth();
			if (this.lastEntryWidth != i) {
				this.refreshLayout(i);
				this.lastEntryWidth = i;
			}
		}

		private void refreshLayout(int i) {
			int j = textWidth(i);
			this.textFrame.setMinWidth(j);
			this.textWidget.setMaxWidth(j);
			this.gridLayout.arrangeElements();
		}

		public static int textWidth(int i) {
			return i - 80;
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			this.gridLayout.setPosition(this.getContentX(), this.getContentY());
			this.updateEntryWidth();
			this.children.forEach(abstractWidget -> abstractWidget.render(guiGraphics, i, j, f));
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
			if (this.dismissButton != null && this.dismissButton.mouseClicked(mouseButtonEvent, bl)) {
				return true;
			} else {
				return this.button.mouseClicked(mouseButtonEvent, bl) ? true : super.mouseClicked(mouseButtonEvent, bl);
			}
		}

		public Component getText() {
			return this.text;
		}

		@Override
		public Component getNarration() {
			return this.getText();
		}
	}

	@Environment(EnvType.CLIENT)
	class ParentEntry extends RealmsMainScreen.Entry {
		private final RealmsServer server;
		private final WidgetTooltipHolder tooltip = new WidgetTooltipHolder();

		public ParentEntry(final RealmsServer realmsServer) {
			this.server = realmsServer;
			if (!realmsServer.expired) {
				this.tooltip.set(Tooltip.create(Component.translatable("mco.snapshot.parent.tooltip")));
			}
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			this.renderStatusLights(this.server, guiGraphics, this.getContentRight(), this.getContentY(), i, j);
			RealmsUtil.renderPlayerFace(guiGraphics, this.getContentX(), this.getContentY(), 32, this.server.ownerUUID);
			this.renderFirstLine(guiGraphics, this.getContentY(), this.getContentX(), this.getContentWidth(), -8355712, this.server);
			this.renderSecondLine(guiGraphics, this.getContentY(), this.getContentX(), this.getContentWidth(), this.server);
			this.renderThirdLine(guiGraphics, this.getContentY(), this.getContentX(), this.server);
			this.tooltip
				.refreshTooltipForNextRenderPass(
					guiGraphics, i, j, bl, this.isFocused(), new ScreenRectangle(this.getContentX(), this.getContentY(), this.getContentWidth(), this.getContentHeight())
				);
		}

		@Override
		public Component getNarration() {
			return Component.literal(Objects.requireNonNullElse(this.server.name, "unknown server"));
		}
	}

	@Environment(EnvType.CLIENT)
	class RealmSelectionList extends ObjectSelectionList<RealmsMainScreen.Entry> {
		public RealmSelectionList() {
			super(Minecraft.getInstance(), RealmsMainScreen.this.width, RealmsMainScreen.this.height, 0, 36);
		}

		public void setSelected(RealmsMainScreen.@Nullable Entry entry) {
			super.setSelected(entry);
			RealmsMainScreen.this.updateButtonStates();
		}

		@Override
		public int getRowWidth() {
			return 300;
		}

		void refreshEntries(RealmsMainScreen realmsMainScreen) {
			RealmsMainScreen.Entry entry = this.getSelected();
			this.clearEntries();

			for (RealmsNotification realmsNotification : RealmsMainScreen.this.notifications) {
				if (realmsNotification instanceof RealmsNotification.VisitUrl visitUrl) {
					this.addEntriesForNotification(visitUrl, realmsMainScreen, entry);
					RealmsMainScreen.this.markNotificationsAsSeen(List.of(realmsNotification));
					break;
				}
			}

			this.refreshServerEntries(entry);
		}

		private void addEntriesForNotification(RealmsNotification.VisitUrl visitUrl, RealmsMainScreen realmsMainScreen, RealmsMainScreen.@Nullable Entry entry) {
			Component component = visitUrl.getMessage();
			int i = RealmsMainScreen.this.font.wordWrapHeight(component, RealmsMainScreen.NotificationMessageEntry.textWidth(this.getRowWidth()));
			RealmsMainScreen.NotificationMessageEntry notificationMessageEntry = RealmsMainScreen.this.new NotificationMessageEntry(
				realmsMainScreen, i, component, visitUrl
			);
			this.addEntry(notificationMessageEntry, 38 + i);
			if (entry instanceof RealmsMainScreen.NotificationMessageEntry notificationMessageEntry2 && notificationMessageEntry2.getText().equals(component)) {
				this.setSelected(notificationMessageEntry);
			}
		}

		private void refreshServerEntries(RealmsMainScreen.@Nullable Entry entry) {
			for (RealmsServer realmsServer : RealmsMainScreen.this.availableSnapshotServers) {
				this.addEntry(RealmsMainScreen.this.new AvailableSnapshotEntry(realmsServer));
			}

			for (RealmsServer realmsServer : RealmsMainScreen.this.serverList) {
				RealmsMainScreen.Entry entry2;
				if (RealmsMainScreen.isSnapshot() && !realmsServer.isSnapshotRealm()) {
					if (realmsServer.state == RealmsServer.State.UNINITIALIZED) {
						continue;
					}

					entry2 = RealmsMainScreen.this.new ParentEntry(realmsServer);
				} else {
					entry2 = RealmsMainScreen.this.new ServerEntry(realmsServer);
				}

				this.addEntry(entry2);
				if (entry instanceof RealmsMainScreen.ServerEntry serverEntry && serverEntry.serverData.id == realmsServer.id) {
					this.setSelected(entry2);
				}
			}
		}
	}

	@Environment(EnvType.CLIENT)
	interface RealmsCall<T> {
		T request(RealmsClient realmsClient) throws RealmsServiceException;
	}

	@Environment(EnvType.CLIENT)
	class ServerEntry extends RealmsMainScreen.Entry {
		private static final Component ONLINE_PLAYERS_TOOLTIP_HEADER = Component.translatable("mco.onlinePlayers");
		private static final int PLAYERS_ONLINE_SPRITE_SIZE = 9;
		private static final int PLAYERS_ONLINE_SPRITE_SEPARATION = 3;
		private static final int SKIN_HEAD_LARGE_WIDTH = 36;
		final RealmsServer serverData;
		private final WidgetTooltipHolder tooltip = new WidgetTooltipHolder();

		public ServerEntry(final RealmsServer realmsServer) {
			this.serverData = realmsServer;
			boolean bl = RealmsMainScreen.isSelfOwnedServer(realmsServer);
			if (RealmsMainScreen.isSnapshot() && bl && realmsServer.isSnapshotRealm()) {
				this.tooltip.set(Tooltip.create(Component.translatable("mco.snapshot.paired", realmsServer.parentWorldName)));
			} else if (!bl && realmsServer.needsDowngrade()) {
				this.tooltip.set(Tooltip.create(Component.translatable("mco.snapshot.friendsRealm.downgrade", realmsServer.activeVersion)));
			}
		}

		@Override
		public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
			if (this.serverData.state == RealmsServer.State.UNINITIALIZED) {
				guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, RealmsMainScreen.NEW_REALM_SPRITE, this.getContentX() - 5, this.getContentYMiddle() - 10, 40, 20);
				int k = this.getContentYMiddle() - 9 / 2;
				guiGraphics.drawString(RealmsMainScreen.this.font, RealmsMainScreen.SERVER_UNITIALIZED_TEXT, this.getContentX() + 40 - 2, k, -8388737);
			} else {
				RealmsUtil.renderPlayerFace(guiGraphics, this.getContentX(), this.getContentY(), 32, this.serverData.ownerUUID);
				this.renderFirstLine(guiGraphics, this.getContentY(), this.getContentX(), this.getContentWidth(), -1, this.serverData);
				this.renderSecondLine(guiGraphics, this.getContentY(), this.getContentX(), this.getContentWidth(), this.serverData);
				this.renderThirdLine(guiGraphics, this.getContentY(), this.getContentX(), this.serverData);
				this.renderStatusLights(this.serverData, guiGraphics, this.getContentRight(), this.getContentY(), i, j);
				boolean bl2 = this.renderOnlinePlayers(guiGraphics, this.getContentY(), this.getContentX(), this.getContentWidth(), this.getContentHeight(), i, j, f);
				if (!bl2) {
					this.tooltip
						.refreshTooltipForNextRenderPass(
							guiGraphics, i, j, bl, this.isFocused(), new ScreenRectangle(this.getContentX(), this.getContentY(), this.getContentWidth(), this.getContentHeight())
						);
				}
			}
		}

		private boolean renderOnlinePlayers(GuiGraphics guiGraphics, int i, int j, int k, int l, int m, int n, float f) {
			List<ResolvableProfile> list = RealmsMainScreen.this.onlinePlayersPerRealm.getProfileResultsFor(this.serverData.id);
			int o = list.size();
			if (o > 0) {
				int p = j + k - 21;
				int q = i + l - 9 - 2;
				int r = 9 * o + 3 * (o - 1);
				int s = p - r;
				List<PlayerSkinRenderCache.RenderInfo> list2;
				if (m >= s && m <= p && n >= q && n <= q + 9) {
					list2 = new ArrayList<>(o);
				} else {
					list2 = null;
				}

				PlayerSkinRenderCache playerSkinRenderCache = RealmsMainScreen.this.minecraft.playerSkinRenderCache();

				for (int t = 0; t < list.size(); t++) {
					ResolvableProfile resolvableProfile = list.get(t);
					PlayerSkinRenderCache.RenderInfo renderInfo = playerSkinRenderCache.getOrDefault(resolvableProfile);
					int u = s + 12 * t;
					PlayerFaceRenderer.draw(guiGraphics, renderInfo.playerSkin(), u, q, 9);
					if (list2 != null) {
						list2.add(renderInfo);
					}
				}

				if (list2 != null) {
					guiGraphics.setTooltipForNextFrame(
						RealmsMainScreen.this.font, List.of(ONLINE_PLAYERS_TOOLTIP_HEADER), Optional.of(new ClientActivePlayersTooltip.ActivePlayersTooltip(list2)), m, n
					);
					return true;
				}
			}

			return false;
		}

		private void playRealm() {
			RealmsMainScreen.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			RealmsMainScreen.play(this.serverData, RealmsMainScreen.this);
		}

		private void createUnitializedRealm() {
			RealmsMainScreen.this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			RealmsCreateRealmScreen realmsCreateRealmScreen = new RealmsCreateRealmScreen(RealmsMainScreen.this, this.serverData, this.serverData.isSnapshotRealm());
			RealmsMainScreen.this.minecraft.setScreen(realmsCreateRealmScreen);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
			if (this.serverData.state == RealmsServer.State.UNINITIALIZED) {
				this.createUnitializedRealm();
			} else if (this.serverData.shouldPlayButtonBeActive() && bl && this.isFocused()) {
				this.playRealm();
			}

			return true;
		}

		@Override
		public boolean keyPressed(KeyEvent keyEvent) {
			if (keyEvent.isSelection()) {
				if (this.serverData.state == RealmsServer.State.UNINITIALIZED) {
					this.createUnitializedRealm();
					return true;
				}

				if (this.serverData.shouldPlayButtonBeActive()) {
					this.playRealm();
					return true;
				}
			}

			return super.keyPressed(keyEvent);
		}

		@Override
		public Component getNarration() {
			return this.serverData.state == RealmsServer.State.UNINITIALIZED
				? RealmsMainScreen.UNITIALIZED_WORLD_NARRATION
				: Component.translatable("narrator.select", Objects.requireNonNullElse(this.serverData.name, "unknown server"));
		}

		public RealmsServer getServer() {
			return this.serverData;
		}
	}
}
