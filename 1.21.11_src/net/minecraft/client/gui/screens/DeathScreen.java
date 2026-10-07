package net.minecraft.client.gui.screens;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DeathScreen extends Screen {
	private static final int TITLE_SCALE = 2;
	private static final Identifier DRAFT_REPORT_SPRITE = Identifier.withDefaultNamespace("icon/draft_report");
	private int delayTicker;
	private final @Nullable Component causeOfDeath;
	private final boolean hardcore;
	private final LocalPlayer player;
	private final Component deathScore;
	private final List<Button> exitButtons = Lists.newArrayList();
	private @Nullable Button exitToTitleButton;

	public DeathScreen(@Nullable Component component, boolean bl, LocalPlayer localPlayer) {
		super(Component.translatable(bl ? "deathScreen.title.hardcore" : "deathScreen.title"));
		this.causeOfDeath = component;
		this.hardcore = bl;
		this.player = localPlayer;
		Component component2 = Component.literal(Integer.toString(localPlayer.getScore())).withStyle(ChatFormatting.YELLOW);
		this.deathScore = Component.translatable("deathScreen.score.value", component2);
	}

	@Override
	protected void init() {
		this.delayTicker = 0;
		this.exitButtons.clear();
		Component component = this.hardcore ? Component.translatable("deathScreen.spectate") : Component.translatable("deathScreen.respawn");
		this.exitButtons.add(this.addRenderableWidget(Button.builder(component, button -> {
			this.player.respawn();
			button.active = false;
		}).bounds(this.width / 2 - 100, this.height / 4 + 72, 200, 20).build()));
		this.exitToTitleButton = this.addRenderableWidget(
			Button.builder(
					Component.translatable("deathScreen.titleScreen"),
					button -> this.minecraft.getReportingContext().draftReportHandled(this.minecraft, this, this::handleExitToTitleScreen, true)
				)
				.bounds(this.width / 2 - 100, this.height / 4 + 96, 200, 20)
				.build()
		);
		this.exitButtons.add(this.exitToTitleButton);
		this.setButtonsActive(false);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	private void handleExitToTitleScreen() {
		if (this.hardcore) {
			this.exitToTitleScreen();
		} else {
			ConfirmScreen confirmScreen = new DeathScreen.TitleConfirmScreen(
				bl -> {
					if (bl) {
						this.exitToTitleScreen();
					} else {
						this.player.respawn();
						this.minecraft.setScreen(null);
					}
				},
				Component.translatable("deathScreen.quit.confirm"),
				CommonComponents.EMPTY,
				Component.translatable("deathScreen.titleScreen"),
				Component.translatable("deathScreen.respawn")
			);
			this.minecraft.setScreen(confirmScreen);
			confirmScreen.setDelay(20);
		}
	}

	private void exitToTitleScreen() {
		if (this.minecraft.level != null) {
			this.minecraft.level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
		}

		this.minecraft.disconnectWithSavingScreen();
		this.minecraft.setScreen(new TitleScreen());
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		this.visitText(guiGraphics.textRenderer(GuiGraphics.HoveredTextEffects.TOOLTIP_AND_CURSOR));
		if (this.exitToTitleButton != null && this.minecraft.getReportingContext().hasDraftReport()) {
			guiGraphics.blitSprite(
				RenderPipelines.GUI_TEXTURED,
				DRAFT_REPORT_SPRITE,
				this.exitToTitleButton.getX() + this.exitToTitleButton.getWidth() - 17,
				this.exitToTitleButton.getY() + 3,
				15,
				15
			);
		}
	}

	private void visitText(ActiveTextCollector activeTextCollector) {
		ActiveTextCollector.Parameters parameters = activeTextCollector.defaultParameters();
		int i = this.width / 2;
		activeTextCollector.defaultParameters(parameters.withScale(2.0F));
		activeTextCollector.accept(TextAlignment.CENTER, i / 2, 30, this.title);
		activeTextCollector.defaultParameters(parameters);
		if (this.causeOfDeath != null) {
			activeTextCollector.accept(TextAlignment.CENTER, i, 85, this.causeOfDeath);
		}

		activeTextCollector.accept(TextAlignment.CENTER, i, 100, this.deathScore);
	}

	@Override
	public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
		renderDeathBackground(guiGraphics, this.width, this.height);
	}

	static void renderDeathBackground(GuiGraphics guiGraphics, int i, int j) {
		guiGraphics.fillGradient(0, 0, i, j, 1615855616, -1602211792);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		ActiveTextCollector.ClickableStyleFinder clickableStyleFinder = new ActiveTextCollector.ClickableStyleFinder(
			this.getFont(), (int)mouseButtonEvent.x(), (int)mouseButtonEvent.y()
		);
		this.visitText(clickableStyleFinder);
		Style style = clickableStyleFinder.result();
		return style != null && style.getClickEvent() instanceof ClickEvent.OpenUrl openUrl
			? clickUrlAction(this.minecraft, this, openUrl.uri())
			: super.mouseClicked(mouseButtonEvent, bl);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isAllowedInPortal() {
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		this.delayTicker++;
		if (this.delayTicker == 20) {
			this.setButtonsActive(true);
		}
	}

	private void setButtonsActive(boolean bl) {
		for (Button button : this.exitButtons) {
			button.active = bl;
		}
	}

	@Environment(EnvType.CLIENT)
	public static class TitleConfirmScreen extends ConfirmScreen {
		public TitleConfirmScreen(BooleanConsumer booleanConsumer, Component component, Component component2, Component component3, Component component4) {
			super(booleanConsumer, component, component2, component3, component4);
		}

		@Override
		public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
			DeathScreen.renderDeathBackground(guiGraphics, this.width, this.height);
		}
	}
}
