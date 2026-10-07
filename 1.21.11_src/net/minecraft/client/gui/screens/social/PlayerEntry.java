package net.minecraft.client.gui.screens.social;

import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.reporting.ReportPlayerScreen;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class PlayerEntry extends ContainerObjectSelectionList.Entry<PlayerEntry> {
	private static final Identifier DRAFT_REPORT_SPRITE = Identifier.withDefaultNamespace("icon/draft_report");
	private static final Duration TOOLTIP_DELAY = Duration.ofMillis(500L);
	private static final WidgetSprites REPORT_BUTTON_SPRITES = new WidgetSprites(
		Identifier.withDefaultNamespace("social_interactions/report_button"),
		Identifier.withDefaultNamespace("social_interactions/report_button_disabled"),
		Identifier.withDefaultNamespace("social_interactions/report_button_highlighted")
	);
	private static final WidgetSprites MUTE_BUTTON_SPRITES = new WidgetSprites(
		Identifier.withDefaultNamespace("social_interactions/mute_button"), Identifier.withDefaultNamespace("social_interactions/mute_button_highlighted")
	);
	private static final WidgetSprites UNMUTE_BUTTON_SPRITES = new WidgetSprites(
		Identifier.withDefaultNamespace("social_interactions/unmute_button"), Identifier.withDefaultNamespace("social_interactions/unmute_button_highlighted")
	);
	private final Minecraft minecraft;
	private final List<AbstractWidget> children;
	private final UUID id;
	private final String playerName;
	private final Supplier<PlayerSkin> skinGetter;
	private boolean isRemoved;
	private boolean hasRecentMessages;
	private final boolean reportingEnabled;
	private boolean hasDraftReport;
	private final boolean chatReportable;
	private @Nullable Button hideButton;
	private @Nullable Button showButton;
	private @Nullable Button reportButton;
	private float tooltipHoverTime;
	private static final Component HIDDEN = Component.translatable("gui.socialInteractions.status_hidden").withStyle(ChatFormatting.ITALIC);
	private static final Component BLOCKED = Component.translatable("gui.socialInteractions.status_blocked").withStyle(ChatFormatting.ITALIC);
	private static final Component OFFLINE = Component.translatable("gui.socialInteractions.status_offline").withStyle(ChatFormatting.ITALIC);
	private static final Component HIDDEN_OFFLINE = Component.translatable("gui.socialInteractions.status_hidden_offline").withStyle(ChatFormatting.ITALIC);
	private static final Component BLOCKED_OFFLINE = Component.translatable("gui.socialInteractions.status_blocked_offline").withStyle(ChatFormatting.ITALIC);
	private static final Component REPORT_DISABLED_TOOLTIP = Component.translatable("gui.socialInteractions.tooltip.report.disabled");
	private static final Component HIDE_TEXT_TOOLTIP = Component.translatable("gui.socialInteractions.tooltip.hide");
	private static final Component SHOW_TEXT_TOOLTIP = Component.translatable("gui.socialInteractions.tooltip.show");
	private static final Component REPORT_PLAYER_TOOLTIP = Component.translatable("gui.socialInteractions.tooltip.report");
	private static final int SKIN_SIZE = 24;
	private static final int PADDING = 4;
	public static final int SKIN_SHADE = ARGB.color(190, 0, 0, 0);
	private static final int CHAT_TOGGLE_ICON_SIZE = 20;
	public static final int BG_FILL = ARGB.color(255, 74, 74, 74);
	public static final int BG_FILL_REMOVED = ARGB.color(255, 48, 48, 48);
	public static final int PLAYERNAME_COLOR = ARGB.color(255, 255, 255, 255);
	public static final int PLAYER_STATUS_COLOR = ARGB.color(140, 255, 255, 255);

	public PlayerEntry(Minecraft minecraft, SocialInteractionsScreen socialInteractionsScreen, UUID uUID, String string, Supplier<PlayerSkin> supplier, boolean bl) {
		this.minecraft = minecraft;
		this.id = uUID;
		this.playerName = string;
		this.skinGetter = supplier;
		ReportingContext reportingContext = minecraft.getReportingContext();
		this.reportingEnabled = reportingContext.sender().isEnabled();
		this.chatReportable = bl;
		this.refreshHasDraftReport(reportingContext);
		Component component = Component.translatable("gui.socialInteractions.narration.hide", string);
		Component component2 = Component.translatable("gui.socialInteractions.narration.show", string);
		PlayerSocialManager playerSocialManager = minecraft.getPlayerSocialManager();
		boolean bl2 = minecraft.getChatStatus().isChatAllowed(minecraft.isLocalServer());
		boolean bl3 = !minecraft.player.getUUID().equals(uUID);
		if (!SharedConstants.DEBUG_SOCIAL_INTERACTIONS && (!bl3 || !bl2 || playerSocialManager.isBlocked(uUID))) {
			this.children = ImmutableList.of();
		} else {
			this.reportButton = new ImageButton(
				0,
				0,
				20,
				20,
				REPORT_BUTTON_SPRITES,
				button -> reportingContext.draftReportHandled(
					minecraft, socialInteractionsScreen, () -> minecraft.setScreen(new ReportPlayerScreen(socialInteractionsScreen, reportingContext, this)), false
				),
				Component.translatable("gui.socialInteractions.report")
			) {
				@Override
				protected MutableComponent createNarrationMessage() {
					return PlayerEntry.this.getEntryNarationMessage(super.createNarrationMessage());
				}
			};
			this.reportButton.active = this.reportingEnabled;
			this.reportButton.setTooltip(this.createReportButtonTooltip());
			this.reportButton.setTooltipDelay(TOOLTIP_DELAY);
			this.hideButton = new ImageButton(0, 0, 20, 20, MUTE_BUTTON_SPRITES, button -> {
				playerSocialManager.hidePlayer(uUID);
				this.onHiddenOrShown(true, Component.translatable("gui.socialInteractions.hidden_in_chat", string));
			}, Component.translatable("gui.socialInteractions.hide")) {
				@Override
				protected MutableComponent createNarrationMessage() {
					return PlayerEntry.this.getEntryNarationMessage(super.createNarrationMessage());
				}
			};
			this.hideButton.setTooltip(Tooltip.create(HIDE_TEXT_TOOLTIP, component));
			this.hideButton.setTooltipDelay(TOOLTIP_DELAY);
			this.showButton = new ImageButton(0, 0, 20, 20, UNMUTE_BUTTON_SPRITES, button -> {
				playerSocialManager.showPlayer(uUID);
				this.onHiddenOrShown(false, Component.translatable("gui.socialInteractions.shown_in_chat", string));
			}, Component.translatable("gui.socialInteractions.show")) {
				@Override
				protected MutableComponent createNarrationMessage() {
					return PlayerEntry.this.getEntryNarationMessage(super.createNarrationMessage());
				}
			};
			this.showButton.setTooltip(Tooltip.create(SHOW_TEXT_TOOLTIP, component2));
			this.showButton.setTooltipDelay(TOOLTIP_DELAY);
			this.children = new ArrayList<>();
			this.children.add(this.hideButton);
			this.children.add(this.reportButton);
			this.updateHideAndShowButton(playerSocialManager.isHidden(this.id));
		}
	}

	public void refreshHasDraftReport(ReportingContext reportingContext) {
		this.hasDraftReport = reportingContext.hasDraftReportFor(this.id);
	}

	private Tooltip createReportButtonTooltip() {
		return !this.reportingEnabled
			? Tooltip.create(REPORT_DISABLED_TOOLTIP)
			: Tooltip.create(REPORT_PLAYER_TOOLTIP, Component.translatable("gui.socialInteractions.narration.report", this.playerName));
	}

	@Override
	public void renderContent(GuiGraphics guiGraphics, int i, int j, boolean bl, float f) {
		int k = this.getContentX() + 4;
		int l = this.getContentY() + (this.getContentHeight() - 24) / 2;
		int m = k + 24 + 4;
		Component component = this.getStatusComponent();
		int n;
		if (component == CommonComponents.EMPTY) {
			guiGraphics.fill(this.getContentX(), this.getContentY(), this.getContentRight(), this.getContentBottom(), BG_FILL);
			n = this.getContentY() + (this.getContentHeight() - 9) / 2;
		} else {
			guiGraphics.fill(this.getContentX(), this.getContentY(), this.getContentRight(), this.getContentBottom(), BG_FILL_REMOVED);
			n = this.getContentY() + (this.getContentHeight() - (9 + 9)) / 2;
			guiGraphics.drawString(this.minecraft.font, component, m, n + 12, PLAYER_STATUS_COLOR);
		}

		PlayerFaceRenderer.draw(guiGraphics, this.skinGetter.get(), k, l, 24);
		guiGraphics.drawString(this.minecraft.font, this.playerName, m, n, PLAYERNAME_COLOR);
		if (this.isRemoved) {
			guiGraphics.fill(k, l, k + 24, l + 24, SKIN_SHADE);
		}

		if (this.hideButton != null && this.showButton != null && this.reportButton != null) {
			float g = this.tooltipHoverTime;
			this.hideButton.setX(this.getContentX() + (this.getContentWidth() - this.hideButton.getWidth() - 4) - 20 - 4);
			this.hideButton.setY(this.getContentY() + (this.getContentHeight() - this.hideButton.getHeight()) / 2);
			this.hideButton.render(guiGraphics, i, j, f);
			this.showButton.setX(this.getContentX() + (this.getContentWidth() - this.showButton.getWidth() - 4) - 20 - 4);
			this.showButton.setY(this.getContentY() + (this.getContentHeight() - this.showButton.getHeight()) / 2);
			this.showButton.render(guiGraphics, i, j, f);
			this.reportButton.setX(this.getContentX() + (this.getContentWidth() - this.showButton.getWidth() - 4));
			this.reportButton.setY(this.getContentY() + (this.getContentHeight() - this.showButton.getHeight()) / 2);
			this.reportButton.render(guiGraphics, i, j, f);
			if (g == this.tooltipHoverTime) {
				this.tooltipHoverTime = 0.0F;
			}
		}

		if (this.hasDraftReport && this.reportButton != null) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, DRAFT_REPORT_SPRITE, this.reportButton.getX() + 5, this.reportButton.getY() + 1, 15, 15);
		}
	}

	@Override
	public List<? extends GuiEventListener> children() {
		return this.children;
	}

	@Override
	public List<? extends NarratableEntry> narratables() {
		return this.children;
	}

	public String getPlayerName() {
		return this.playerName;
	}

	public UUID getPlayerId() {
		return this.id;
	}

	public Supplier<PlayerSkin> getSkinGetter() {
		return this.skinGetter;
	}

	public void setRemoved(boolean bl) {
		this.isRemoved = bl;
	}

	public boolean isRemoved() {
		return this.isRemoved;
	}

	public void setHasRecentMessages(boolean bl) {
		this.hasRecentMessages = bl;
	}

	public boolean hasRecentMessages() {
		return this.hasRecentMessages;
	}

	public boolean isChatReportable() {
		return this.chatReportable;
	}

	private void onHiddenOrShown(boolean bl, Component component) {
		this.updateHideAndShowButton(bl);
		this.minecraft.gui.getChat().addMessage(component);
		this.minecraft.getNarrator().saySystemNow(component);
	}

	private void updateHideAndShowButton(boolean bl) {
		this.showButton.visible = bl;
		this.hideButton.visible = !bl;
		this.children.set(0, bl ? this.showButton : this.hideButton);
	}

	MutableComponent getEntryNarationMessage(MutableComponent mutableComponent) {
		Component component = this.getStatusComponent();
		return component == CommonComponents.EMPTY
			? Component.literal(this.playerName).append(", ").append(mutableComponent)
			: Component.literal(this.playerName).append(", ").append(component).append(", ").append(mutableComponent);
	}

	private Component getStatusComponent() {
		boolean bl = this.minecraft.getPlayerSocialManager().isHidden(this.id);
		boolean bl2 = this.minecraft.getPlayerSocialManager().isBlocked(this.id);
		if (bl2 && this.isRemoved) {
			return BLOCKED_OFFLINE;
		} else if (bl && this.isRemoved) {
			return HIDDEN_OFFLINE;
		} else if (bl2) {
			return BLOCKED;
		} else if (bl) {
			return HIDDEN;
		} else {
			return this.isRemoved ? OFFLINE : CommonComponents.EMPTY;
		}
	}
}
