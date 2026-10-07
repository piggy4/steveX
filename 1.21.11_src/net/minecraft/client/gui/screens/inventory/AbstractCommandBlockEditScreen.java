package net.minecraft.client.gui.screens.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.BaseCommandBlock;

@Environment(EnvType.CLIENT)
public abstract class AbstractCommandBlockEditScreen extends Screen {
	private static final Component SET_COMMAND_LABEL = Component.translatable("advMode.setCommand");
	private static final Component COMMAND_LABEL = Component.translatable("advMode.command");
	private static final Component PREVIOUS_OUTPUT_LABEL = Component.translatable("advMode.previousOutput");
	protected EditBox commandEdit;
	protected EditBox previousEdit;
	protected Button doneButton;
	protected Button cancelButton;
	protected CycleButton<Boolean> outputButton;
	CommandSuggestions commandSuggestions;

	public AbstractCommandBlockEditScreen() {
		super(GameNarrator.NO_TITLE);
	}

	@Override
	public void tick() {
		if (!this.getCommandBlock().isValid()) {
			this.onClose();
		}
	}

	abstract BaseCommandBlock getCommandBlock();

	abstract int getPreviousY();

	@Override
	protected void init() {
		boolean bl = this.getCommandBlock().isTrackOutput();
		this.commandEdit = new EditBox(this.font, this.width / 2 - 150, 50, 300, 20, Component.translatable("advMode.command")) {
			@Override
			protected MutableComponent createNarrationMessage() {
				return super.createNarrationMessage().append(AbstractCommandBlockEditScreen.this.commandSuggestions.getNarrationMessage());
			}
		};
		this.commandEdit.setMaxLength(32500);
		this.commandEdit.setResponder(this::onEdited);
		this.addWidget(this.commandEdit);
		this.previousEdit = new EditBox(this.font, this.width / 2 - 150, this.getPreviousY(), 276, 20, Component.translatable("advMode.previousOutput"));
		this.previousEdit.setMaxLength(32500);
		this.previousEdit.setEditable(false);
		this.previousEdit.setValue("-");
		this.addWidget(this.previousEdit);
		this.outputButton = this.addRenderableWidget(
			CycleButton.booleanBuilder(Component.literal("O"), Component.literal("X"), bl)
				.displayOnlyValue()
				.create(this.width / 2 + 150 - 20, this.getPreviousY(), 20, 20, Component.translatable("advMode.trackOutput"), (cycleButton, boolean_) -> {
					BaseCommandBlock baseCommandBlock = this.getCommandBlock();
					baseCommandBlock.setTrackOutput(boolean_);
					this.updatePreviousOutput(boolean_);
				})
		);
		this.addExtraControls();
		this.doneButton = this.addRenderableWidget(
			Button.builder(CommonComponents.GUI_DONE, button -> this.onDone()).bounds(this.width / 2 - 4 - 150, this.height / 4 + 120 + 12, 150, 20).build()
		);
		this.cancelButton = this.addRenderableWidget(
			Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).bounds(this.width / 2 + 4, this.height / 4 + 120 + 12, 150, 20).build()
		);
		this.commandSuggestions = new CommandSuggestions(this.minecraft, this, this.commandEdit, this.font, true, true, 0, 7, false, Integer.MIN_VALUE);
		this.commandSuggestions.setAllowSuggestions(true);
		this.commandSuggestions.updateCommandInfo();
		this.updatePreviousOutput(bl);
	}

	protected void addExtraControls() {
	}

	@Override
	protected void setInitialFocus() {
		this.setInitialFocus(this.commandEdit);
	}

	@Override
	protected Component getUsageNarration() {
		return this.commandSuggestions.isVisible() ? this.commandSuggestions.getUsageNarration() : super.getUsageNarration();
	}

	@Override
	public void resize(int i, int j) {
		String string = this.commandEdit.getValue();
		this.init(i, j);
		this.commandEdit.setValue(string);
		this.commandSuggestions.updateCommandInfo();
	}

	protected void updatePreviousOutput(boolean bl) {
		this.previousEdit.setValue(bl ? this.getCommandBlock().getLastOutput().getString() : "-");
	}

	protected void onDone() {
		this.populateAndSendPacket();
		BaseCommandBlock baseCommandBlock = this.getCommandBlock();
		if (!baseCommandBlock.isTrackOutput()) {
			baseCommandBlock.setLastOutput(null);
		}

		this.minecraft.setScreen(null);
	}

	protected abstract void populateAndSendPacket();

	private void onEdited(String string) {
		this.commandSuggestions.updateCommandInfo();
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if (this.commandSuggestions.keyPressed(keyEvent)) {
			return true;
		} else if (super.keyPressed(keyEvent)) {
			return true;
		} else if (keyEvent.isConfirmation()) {
			this.onDone();
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean mouseScrolled(double d, double e, double f, double g) {
		return this.commandSuggestions.mouseScrolled(g) ? true : super.mouseScrolled(d, e, f, g);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		return this.commandSuggestions.mouseClicked(mouseButtonEvent) ? true : super.mouseClicked(mouseButtonEvent, bl);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		guiGraphics.drawCenteredString(this.font, SET_COMMAND_LABEL, this.width / 2, 20, -1);
		guiGraphics.drawString(this.font, COMMAND_LABEL, this.width / 2 - 150 + 1, 40, -6250336);
		this.commandEdit.render(guiGraphics, i, j, f);
		int k = 75;
		if (!this.previousEdit.getValue().isEmpty()) {
			k += 5 * 9 + 1 + this.getPreviousY() - 135;
			guiGraphics.drawString(this.font, PREVIOUS_OUTPUT_LABEL, this.width / 2 - 150 + 1, k + 4, -6250336);
			this.previousEdit.render(guiGraphics, i, j, f);
		}

		this.commandSuggestions.render(guiGraphics, i, j);
	}
}
