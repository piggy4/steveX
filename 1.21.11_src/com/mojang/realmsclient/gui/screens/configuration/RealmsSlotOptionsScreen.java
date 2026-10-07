package com.mojang.realmsclient.gui.screens.configuration;

import com.google.common.collect.ImmutableList;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.dto.RealmsSlot;
import com.mojang.realmsclient.dto.RealmsWorldOptions;
import com.mojang.realmsclient.gui.screens.RealmsPopups;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.realms.RealmsLabel;
import net.minecraft.realms.RealmsScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;

@Environment(EnvType.CLIENT)
public class RealmsSlotOptionsScreen extends RealmsScreen {
	private static final int DEFAULT_DIFFICULTY = 2;
	public static final List<Difficulty> DIFFICULTIES = ImmutableList.of(Difficulty.PEACEFUL, Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD);
	private static final int DEFAULT_GAME_MODE = 0;
	public static final List<GameType> GAME_MODES = ImmutableList.of(GameType.SURVIVAL, GameType.CREATIVE, GameType.ADVENTURE);
	private static final Component NAME_LABEL = Component.translatable("mco.configure.world.edit.slot.name");
	static final Component SPAWN_PROTECTION_TEXT = Component.translatable("mco.configure.world.spawnProtection");
	private EditBox nameEdit;
	protected final RealmsConfigureWorldScreen parentScreen;
	private int column1X;
	private int columnWidth;
	private final RealmsSlot slot;
	private final RealmsServer.WorldType worldType;
	private Difficulty difficulty;
	private GameType gameMode;
	private final String defaultSlotName;
	private String worldName;
	int spawnProtection;
	private boolean forceGameMode;
	RealmsSlotOptionsScreen.SettingsSlider spawnProtectionButton;

	public RealmsSlotOptionsScreen(RealmsConfigureWorldScreen realmsConfigureWorldScreen, RealmsSlot realmsSlot, RealmsServer.WorldType worldType, int i) {
		super(Component.translatable("mco.configure.world.buttons.options"));
		this.parentScreen = realmsConfigureWorldScreen;
		this.slot = realmsSlot;
		this.worldType = worldType;
		this.difficulty = findByIndex(DIFFICULTIES, realmsSlot.options.difficulty, 2);
		this.gameMode = findByIndex(GAME_MODES, realmsSlot.options.gameMode, 0);
		this.defaultSlotName = realmsSlot.options.getDefaultSlotName(i);
		this.setWorldName(realmsSlot.options.getSlotName(i));
		if (worldType == RealmsServer.WorldType.NORMAL) {
			this.spawnProtection = realmsSlot.options.spawnProtection;
			this.forceGameMode = realmsSlot.options.forceGameMode;
		} else {
			this.spawnProtection = 0;
			this.forceGameMode = false;
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parentScreen);
	}

	private static <T> T findByIndex(List<T> list, int i, int j) {
		try {
			return list.get(i);
		} catch (IndexOutOfBoundsException indexOutOfBoundsException) {
			return list.get(j);
		}
	}

	private static <T> int findIndex(List<T> list, T object, int i) {
		int j = list.indexOf(object);
		return j == -1 ? i : j;
	}

	@Override
	public void init() {
		this.columnWidth = 170;
		this.column1X = this.width / 2 - this.columnWidth;
		int i = this.width / 2 + 10;
		if (this.worldType != RealmsServer.WorldType.NORMAL) {
			Component component;
			if (this.worldType == RealmsServer.WorldType.ADVENTUREMAP) {
				component = Component.translatable("mco.configure.world.edit.subscreen.adventuremap");
			} else if (this.worldType == RealmsServer.WorldType.INSPIRATION) {
				component = Component.translatable("mco.configure.world.edit.subscreen.inspiration");
			} else {
				component = Component.translatable("mco.configure.world.edit.subscreen.experience");
			}

			this.addLabel(new RealmsLabel(component, this.width / 2, 26, -65536));
		}

		this.nameEdit = this.addWidget(
			new EditBox(this.minecraft.font, this.column1X, row(1), this.columnWidth, 20, null, Component.translatable("mco.configure.world.edit.slot.name"))
		);
		this.nameEdit.setValue(this.worldName);
		this.nameEdit.setResponder(this::setWorldName);
		CycleButton<Difficulty> cycleButton = this.addRenderableWidget(
			CycleButton.builder(Difficulty::getDisplayName, this.difficulty)
				.withValues(DIFFICULTIES)
				.create(i, row(1), this.columnWidth, 20, Component.translatable("options.difficulty"), (cycleButtonx, difficulty) -> this.difficulty = difficulty)
		);
		CycleButton<GameType> cycleButton2 = this.addRenderableWidget(
			CycleButton.builder(GameType::getShortDisplayName, this.gameMode)
				.withValues(GAME_MODES)
				.create(this.column1X, row(3), this.columnWidth, 20, Component.translatable("selectWorld.gameMode"), (cycleButtonx, gameType) -> this.gameMode = gameType)
		);
		CycleButton<Boolean> cycleButton3 = this.addRenderableWidget(
			CycleButton.onOffBuilder(this.forceGameMode)
				.create(
					i, row(3), this.columnWidth, 20, Component.translatable("mco.configure.world.forceGameMode"), (cycleButtonx, boolean_) -> this.forceGameMode = boolean_
				)
		);
		this.spawnProtectionButton = this.addRenderableWidget(
			new RealmsSlotOptionsScreen.SettingsSlider(this.column1X, row(5), this.columnWidth, this.spawnProtection, 0.0F, 16.0F)
		);
		if (this.worldType != RealmsServer.WorldType.NORMAL) {
			this.spawnProtectionButton.active = false;
			cycleButton3.active = false;
		}

		if (this.slot.isHardcore()) {
			cycleButton.active = false;
			cycleButton2.active = false;
			cycleButton3.active = false;
		}

		this.addRenderableWidget(
			Button.builder(Component.translatable("mco.configure.world.buttons.done"), button -> this.saveSettings())
				.bounds(this.column1X, row(13), this.columnWidth, 20)
				.build()
		);
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).bounds(i, row(13), this.columnWidth, 20).build());
	}

	private CycleButton.OnValueChange<Boolean> confirmDangerousOption(Component component, Consumer<Boolean> consumer) {
		return (cycleButton, boolean_) -> {
			if (boolean_) {
				consumer.accept(true);
			} else {
				this.minecraft.setScreen(RealmsPopups.warningPopupScreen(this, component, popupScreen -> {
					consumer.accept(false);
					popupScreen.onClose();
				}));
			}
		};
	}

	@Override
	public Component getNarrationMessage() {
		return CommonComponents.joinForNarration(this.getTitle(), this.createLabelNarration());
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 17, -1);
		guiGraphics.drawString(this.font, NAME_LABEL, this.column1X + this.columnWidth / 2 - this.font.width(NAME_LABEL) / 2, row(0) - 5, -1);
		this.nameEdit.render(guiGraphics, i, j, f);
	}

	private void setWorldName(String string) {
		if (string.equals(this.defaultSlotName)) {
			this.worldName = "";
		} else {
			this.worldName = string;
		}
	}

	private void saveSettings() {
		int i = findIndex(DIFFICULTIES, this.difficulty, 2);
		int j = findIndex(GAME_MODES, this.gameMode, 0);
		if (this.worldType != RealmsServer.WorldType.ADVENTUREMAP
			&& this.worldType != RealmsServer.WorldType.EXPERIENCE
			&& this.worldType != RealmsServer.WorldType.INSPIRATION) {
			this.parentScreen
				.saveSlotSettings(
					new RealmsSlot(
						this.slot.slotId,
						new RealmsWorldOptions(this.spawnProtection, i, j, this.forceGameMode, this.worldName, this.slot.options.version, this.slot.options.compatibility),
						this.slot.settings
					)
				);
		} else {
			this.parentScreen
				.saveSlotSettings(
					new RealmsSlot(
						this.slot.slotId,
						new RealmsWorldOptions(
							this.slot.options.spawnProtection, i, j, this.slot.options.forceGameMode, this.worldName, this.slot.options.version, this.slot.options.compatibility
						),
						this.slot.settings
					)
				);
		}
	}

	@Environment(EnvType.CLIENT)
	class SettingsSlider extends AbstractSliderButton {
		private final double minValue;
		private final double maxValue;

		public SettingsSlider(final int i, final int j, final int k, final int l, final float f, final float g) {
			super(i, j, k, 20, CommonComponents.EMPTY, 0.0);
			this.minValue = f;
			this.maxValue = g;
			this.value = (Mth.clamp(l, f, g) - f) / (g - f);
			this.updateMessage();
		}

		@Override
		public void applyValue() {
			if (RealmsSlotOptionsScreen.this.spawnProtectionButton.active) {
				RealmsSlotOptionsScreen.this.spawnProtection = (int)Mth.lerp(Mth.clamp(this.value, 0.0, 1.0), this.minValue, this.maxValue);
			}
		}

		@Override
		protected void updateMessage() {
			this.setMessage(
				CommonComponents.optionNameValue(
					RealmsSlotOptionsScreen.SPAWN_PROTECTION_TEXT,
					RealmsSlotOptionsScreen.this.spawnProtection == 0
						? CommonComponents.OPTION_OFF
						: Component.literal(String.valueOf(RealmsSlotOptionsScreen.this.spawnProtection))
				)
			);
		}
	}
}
