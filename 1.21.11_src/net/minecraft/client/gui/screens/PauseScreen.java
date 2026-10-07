package net.minecraft.client.gui.screens;

import java.net.URI;
import java.util.Optional;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.NowPlayingToast;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerLinks;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.Dialogs;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DialogTags;
import net.minecraft.util.CommonLinks;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class PauseScreen extends Screen {
	private static final Identifier DRAFT_REPORT_SPRITE = Identifier.withDefaultNamespace("icon/draft_report");
	private static final int COLUMNS = 2;
	private static final int MENU_PADDING_TOP = 50;
	private static final int BUTTON_PADDING = 4;
	private static final int BUTTON_WIDTH_FULL = 204;
	private static final int BUTTON_WIDTH_HALF = 98;
	private static final Component RETURN_TO_GAME = Component.translatable("menu.returnToGame");
	private static final Component ADVANCEMENTS = Component.translatable("gui.advancements");
	private static final Component STATS = Component.translatable("gui.stats");
	private static final Component SEND_FEEDBACK = Component.translatable("menu.sendFeedback");
	private static final Component REPORT_BUGS = Component.translatable("menu.reportBugs");
	private static final Component FEEDBACK_SUBSCREEN = Component.translatable("menu.feedback");
	private static final Component OPTIONS = Component.translatable("menu.options");
	private static final Component SHARE_TO_LAN = Component.translatable("menu.shareToLan");
	private static final Component PLAYER_REPORTING = Component.translatable("menu.playerReporting");
	private static final Component GAME = Component.translatable("menu.game");
	private static final Component PAUSED = Component.translatable("menu.paused");
	private static final Tooltip CUSTOM_OPTIONS_TOOLTIP = Tooltip.create(Component.translatable("menu.custom_options.tooltip"));
	private final boolean showPauseMenu;
	private @Nullable Button disconnectButton;

	public PauseScreen(boolean bl) {
		super(bl ? GAME : PAUSED);
		this.showPauseMenu = bl;
	}

	public boolean showsPauseMenu() {
		return this.showPauseMenu;
	}

	@Override
	protected void init() {
		if (this.showPauseMenu) {
			this.createPauseMenu();
		}

		int i = this.font.width(this.title);
		this.addRenderableWidget(new StringWidget(this.width / 2 - i / 2, this.showPauseMenu ? 40 : 10, i, 9, this.title, this.font));
	}

	private void createPauseMenu() {
		GridLayout gridLayout = new GridLayout();
		gridLayout.defaultCellSetting().padding(4, 4, 4, 0);
		GridLayout.RowHelper rowHelper = gridLayout.createRowHelper(2);
		rowHelper.addChild(Button.builder(RETURN_TO_GAME, button -> {
			this.minecraft.setScreen(null);
			this.minecraft.mouseHandler.grabMouse();
		}).width(204).build(), 2, gridLayout.newCellSettings().paddingTop(50));
		rowHelper.addChild(this.openScreenButton(ADVANCEMENTS, () -> new AdvancementsScreen(this.minecraft.player.connection.getAdvancements(), this)));
		rowHelper.addChild(this.openScreenButton(STATS, () -> new StatsScreen(this, this.minecraft.player.getStats())));
		Optional<? extends Holder<Dialog>> optional = this.getCustomAdditions();
		if (optional.isEmpty()) {
			addFeedbackButtons(this, rowHelper);
		} else {
			this.addFeedbackSubscreenAndCustomDialogButtons(this.minecraft, (Holder<Dialog>)optional.get(), rowHelper);
		}

		rowHelper.addChild(this.openScreenButton(OPTIONS, () -> new OptionsScreen(this, this.minecraft.options)));
		if (this.minecraft.hasSingleplayerServer() && !this.minecraft.getSingleplayerServer().isPublished()) {
			rowHelper.addChild(this.openScreenButton(SHARE_TO_LAN, () -> new ShareToLanScreen(this)));
		} else {
			rowHelper.addChild(this.openScreenButton(PLAYER_REPORTING, () -> new SocialInteractionsScreen(this)));
		}

		this.disconnectButton = rowHelper.addChild(
			Button.builder(
					CommonComponents.disconnectButtonLabel(this.minecraft.isLocalServer()),
					button -> {
						button.active = false;
						this.minecraft
							.getReportingContext()
							.draftReportHandled(this.minecraft, this, () -> this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE), true);
					}
				)
				.width(204)
				.build(),
			2
		);
		gridLayout.arrangeElements();
		FrameLayout.alignInRectangle(gridLayout, 0, 0, this.width, this.height, 0.5F, 0.25F);
		gridLayout.visitWidgets(this::addRenderableWidget);
	}

	private Optional<? extends Holder<Dialog>> getCustomAdditions() {
		Registry<Dialog> registry = this.minecraft.player.connection.registryAccess().lookupOrThrow(Registries.DIALOG);
		Optional<? extends HolderSet<Dialog>> optional = registry.get(DialogTags.PAUSE_SCREEN_ADDITIONS);
		if (optional.isPresent()) {
			HolderSet<Dialog> holderSet = (HolderSet<Dialog>)optional.get();
			if (holderSet.size() > 0) {
				if (holderSet.size() == 1) {
					return Optional.of(holderSet.get(0));
				}

				return registry.get(Dialogs.CUSTOM_OPTIONS);
			}
		}

		ServerLinks serverLinks = this.minecraft.player.connection.serverLinks();
		return !serverLinks.isEmpty() ? registry.get(Dialogs.SERVER_LINKS) : Optional.empty();
	}

	static void addFeedbackButtons(Screen screen, GridLayout.RowHelper rowHelper) {
		rowHelper.addChild(
			openLinkButton(screen, SEND_FEEDBACK, SharedConstants.getCurrentVersion().stable() ? CommonLinks.RELEASE_FEEDBACK : CommonLinks.SNAPSHOT_FEEDBACK)
		);
		rowHelper.addChild(openLinkButton(screen, REPORT_BUGS, CommonLinks.SNAPSHOT_BUGS_FEEDBACK)).active = !SharedConstants.getCurrentVersion()
			.dataVersion()
			.isSideSeries();
	}

	private void addFeedbackSubscreenAndCustomDialogButtons(Minecraft minecraft, Holder<Dialog> holder, GridLayout.RowHelper rowHelper) {
		rowHelper.addChild(this.openScreenButton(FEEDBACK_SUBSCREEN, () -> new PauseScreen.FeedbackSubScreen(this)));
		rowHelper.addChild(
			Button.builder(holder.value().common().computeExternalTitle(), button -> minecraft.player.connection.showDialog(holder, this))
				.width(98)
				.tooltip(CUSTOM_OPTIONS_TOOLTIP)
				.build()
		);
	}

	@Override
	public void tick() {
		if (this.rendersNowPlayingToast()) {
			NowPlayingToast.tickMusicNotes();
		}
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		if (this.rendersNowPlayingToast()) {
			NowPlayingToast.renderToast(guiGraphics, this.font);
		}

		if (this.showPauseMenu && this.minecraft.getReportingContext().hasDraftReport() && this.disconnectButton != null) {
			guiGraphics.blitSprite(
				RenderPipelines.GUI_TEXTURED,
				DRAFT_REPORT_SPRITE,
				this.disconnectButton.getX() + this.disconnectButton.getWidth() - 17,
				this.disconnectButton.getY() + 3,
				15,
				15
			);
		}
	}

	@Override
	public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
		if (this.showPauseMenu) {
			super.renderBackground(guiGraphics, i, j, f);
		}
	}

	public boolean rendersNowPlayingToast() {
		Options options = this.minecraft.options;
		return options.musicToast().get().renderInPauseScreen() && options.getFinalSoundSourceVolume(SoundSource.MUSIC) > 0.0F && this.showPauseMenu;
	}

	private Button openScreenButton(Component component, Supplier<Screen> supplier) {
		return Button.builder(component, button -> this.minecraft.setScreen(supplier.get())).width(98).build();
	}

	private static Button openLinkButton(Screen screen, Component component, URI uRI) {
		return Button.builder(component, ConfirmLinkScreen.confirmLink(screen, uRI)).width(98).build();
	}

	@Environment(EnvType.CLIENT)
	static class FeedbackSubScreen extends Screen {
		private static final Component TITLE = Component.translatable("menu.feedback.title");
		public final Screen parent;
		private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

		protected FeedbackSubScreen(Screen screen) {
			super(TITLE);
			this.parent = screen;
		}

		@Override
		protected void init() {
			this.layout.addTitleHeader(TITLE, this.font);
			GridLayout gridLayout = this.layout.addToContents(new GridLayout());
			gridLayout.defaultCellSetting().padding(4, 4, 4, 0);
			GridLayout.RowHelper rowHelper = gridLayout.createRowHelper(2);
			PauseScreen.addFeedbackButtons(this, rowHelper);
			this.layout.addToFooter(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).width(200).build());
			this.layout.visitWidgets(this::addRenderableWidget);
			this.repositionElements();
		}

		@Override
		protected void repositionElements() {
			this.layout.arrangeElements();
		}

		@Override
		public void onClose() {
			this.minecraft.setScreen(this.parent);
		}
	}
}
