package com.mojang.realmsclient.gui.screens.configuration;

import com.google.common.collect.Lists;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.dto.WorldTemplate;
import com.mojang.realmsclient.gui.RealmsWorldSlotButton;
import com.mojang.realmsclient.gui.screens.RealmsLongRunningMcoTaskScreen;
import com.mojang.realmsclient.gui.screens.RealmsPopups;
import com.mojang.realmsclient.gui.screens.RealmsResetWorldScreen;
import com.mojang.realmsclient.gui.screens.RealmsSelectWorldTemplateScreen;
import com.mojang.realmsclient.util.task.SwitchMinigameTask;
import com.mojang.realmsclient.util.task.SwitchSlotTask;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
class RealmsWorldsTab extends GridLayoutTab implements RealmsConfigurationTab {
	static final Component TITLE = Component.translatable("mco.configure.worlds.title");
	private final RealmsConfigureWorldScreen configurationScreen;
	private final Minecraft minecraft;
	private RealmsServer serverData;
	private final Button optionsButton;
	private final Button backupButton;
	private final Button resetWorldButton;
	private final List<RealmsWorldSlotButton> slotButtonList = Lists.newArrayList();

	RealmsWorldsTab(RealmsConfigureWorldScreen realmsConfigureWorldScreen, Minecraft minecraft, RealmsServer realmsServer) {
		super(TITLE);
		this.configurationScreen = realmsConfigureWorldScreen;
		this.minecraft = minecraft;
		this.serverData = realmsServer;
		GridLayout.RowHelper rowHelper = this.layout.spacing(20).createRowHelper(1);
		GridLayout.RowHelper rowHelper2 = new GridLayout().spacing(16).createRowHelper(4);
		this.slotButtonList.clear();

		for (int i = 1; i < 5; i++) {
			this.slotButtonList.add(rowHelper2.addChild(this.createSlotButton(i), LayoutSettings.defaults().alignVerticallyBottom()));
		}

		rowHelper.addChild(rowHelper2.getGrid());
		GridLayout.RowHelper rowHelper3 = new GridLayout().spacing(8).createRowHelper(1);
		this.optionsButton = rowHelper3.addChild(
			Button.builder(
					Component.translatable("mco.configure.world.buttons.options"),
					button -> minecraft.setScreen(
						new RealmsSlotOptionsScreen(
							realmsConfigureWorldScreen, realmsServer.slots.get(realmsServer.activeSlot).copy(), realmsServer.worldType, realmsServer.activeSlot
						)
					)
				)
				.bounds(0, 0, 150, 20)
				.build()
		);
		this.backupButton = rowHelper3.addChild(
			Button.builder(
					Component.translatable("mco.configure.world.backup"),
					button -> minecraft.setScreen(new RealmsBackupScreen(realmsConfigureWorldScreen, realmsServer.copy(), realmsServer.activeSlot))
				)
				.bounds(0, 0, 150, 20)
				.build()
		);
		this.resetWorldButton = rowHelper3.addChild(Button.builder(Component.empty(), button -> this.resetButtonPressed()).bounds(0, 0, 150, 20).build());
		rowHelper.addChild(rowHelper3.getGrid(), LayoutSettings.defaults().alignHorizontallyCenter());
		this.backupButton.active = true;
		this.updateData(realmsServer);
	}

	private void resetButtonPressed() {
		if (this.isMinigame()) {
			this.minecraft
				.setScreen(
					new RealmsSelectWorldTemplateScreen(
						Component.translatable("mco.template.title.minigame"), this::templateSelectionCallback, RealmsServer.WorldType.MINIGAME, null
					)
				);
		} else {
			this.minecraft
				.setScreen(
					RealmsResetWorldScreen.forResetSlot(
						this.configurationScreen, this.serverData.copy(), () -> this.minecraft.execute(() -> this.minecraft.setScreen(this.configurationScreen.getNewScreen()))
					)
				);
		}
	}

	private void templateSelectionCallback(@Nullable WorldTemplate worldTemplate) {
		if (worldTemplate != null && WorldTemplate.WorldTemplateType.MINIGAME == worldTemplate.type()) {
			this.configurationScreen.stateChanged();
			RealmsConfigureWorldScreen realmsConfigureWorldScreen = this.configurationScreen.getNewScreen();
			this.minecraft
				.setScreen(
					new RealmsLongRunningMcoTaskScreen(realmsConfigureWorldScreen, new SwitchMinigameTask(this.serverData.id, worldTemplate, realmsConfigureWorldScreen))
				);
		} else {
			this.minecraft.setScreen(this.configurationScreen);
		}
	}

	private boolean isMinigame() {
		return this.serverData.isMinigameActive();
	}

	@Override
	public void onSelected(RealmsServer realmsServer) {
		this.updateData(realmsServer);
	}

	@Override
	public void updateData(RealmsServer realmsServer) {
		this.serverData = realmsServer;
		this.optionsButton.active = !realmsServer.expired && !this.isMinigame();
		this.resetWorldButton.active = !realmsServer.expired;
		if (this.isMinigame()) {
			this.resetWorldButton.setMessage(Component.translatable("mco.configure.world.buttons.switchminigame"));
		} else {
			boolean bl = realmsServer.slots.containsKey(realmsServer.activeSlot) && realmsServer.slots.get(realmsServer.activeSlot).options.empty;
			if (bl) {
				this.resetWorldButton.setMessage(Component.translatable("mco.configure.world.buttons.newworld"));
			} else {
				this.resetWorldButton.setMessage(Component.translatable("mco.configure.world.buttons.resetworld"));
			}
		}

		this.backupButton.active = !this.isMinigame();

		for (RealmsWorldSlotButton realmsWorldSlotButton : this.slotButtonList) {
			RealmsWorldSlotButton.State state = realmsWorldSlotButton.setServerData(realmsServer);
			if (state.activeSlot) {
				realmsWorldSlotButton.setSize(80, 80);
			} else {
				realmsWorldSlotButton.setSize(50, 50);
			}
		}
	}

	private RealmsWorldSlotButton createSlotButton(int i) {
		return new RealmsWorldSlotButton(0, 0, 80, 80, i, this.serverData, button -> {
			RealmsWorldSlotButton.State state = ((RealmsWorldSlotButton)button).getState();
			switch (state.action) {
				case SWITCH_SLOT:
					if (state.minigame) {
						this.switchToMinigame();
					} else if (state.empty) {
						this.switchToEmptySlot(i, this.serverData);
					} else {
						this.switchToFullSlot(i, this.serverData);
					}
				case NOTHING:
					return;
				default:
					throw new IllegalStateException("Unknown action " + state.action);
			}
		});
	}

	private void switchToMinigame() {
		RealmsSelectWorldTemplateScreen realmsSelectWorldTemplateScreen = new RealmsSelectWorldTemplateScreen(
			Component.translatable("mco.template.title.minigame"),
			this::templateSelectionCallback,
			RealmsServer.WorldType.MINIGAME,
			null,
			List.of(
				Component.translatable("mco.minigame.world.info.line1").withColor(-4539718), Component.translatable("mco.minigame.world.info.line2").withColor(-4539718)
			)
		);
		this.minecraft.setScreen(realmsSelectWorldTemplateScreen);
	}

	private void switchToFullSlot(int i, RealmsServer realmsServer) {
		this.minecraft
			.setScreen(
				RealmsPopups.infoPopupScreen(
					this.configurationScreen,
					Component.translatable("mco.configure.world.slot.switch.question.line1"),
					popupScreen -> {
						RealmsConfigureWorldScreen realmsConfigureWorldScreen = this.configurationScreen.getNewScreen();
						this.configurationScreen.stateChanged();
						this.minecraft
							.setScreen(
								new RealmsLongRunningMcoTaskScreen(
									realmsConfigureWorldScreen,
									new SwitchSlotTask(realmsServer.id, i, () -> this.minecraft.execute(() -> this.minecraft.setScreen(realmsConfigureWorldScreen)))
								)
							);
					}
				)
			);
	}

	private void switchToEmptySlot(int i, RealmsServer realmsServer) {
		this.minecraft
			.setScreen(
				RealmsPopups.infoPopupScreen(
					this.configurationScreen,
					Component.translatable("mco.configure.world.slot.switch.question.line1"),
					popupScreen -> {
						this.configurationScreen.stateChanged();
						RealmsResetWorldScreen realmsResetWorldScreen = RealmsResetWorldScreen.forEmptySlot(
							this.configurationScreen, i, realmsServer, () -> this.minecraft.execute(() -> this.minecraft.setScreen(this.configurationScreen.getNewScreen()))
						);
						this.minecraft.setScreen(realmsResetWorldScreen);
					}
				)
			);
	}
}
