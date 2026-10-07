package net.minecraft.client.gui.screens.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.block.entity.CommandBlockEntity;

@Environment(EnvType.CLIENT)
public class CommandBlockEditScreen extends AbstractCommandBlockEditScreen {
	private final CommandBlockEntity autoCommandBlock;
	private CycleButton<CommandBlockEntity.Mode> modeButton;
	private CycleButton<Boolean> conditionalButton;
	private CycleButton<Boolean> autoexecButton;
	private CommandBlockEntity.Mode mode = CommandBlockEntity.Mode.REDSTONE;
	private boolean conditional;
	private boolean autoexec;

	public CommandBlockEditScreen(CommandBlockEntity commandBlockEntity) {
		this.autoCommandBlock = commandBlockEntity;
	}

	@Override
	BaseCommandBlock getCommandBlock() {
		return this.autoCommandBlock.getCommandBlock();
	}

	@Override
	int getPreviousY() {
		return 135;
	}

	@Override
	protected void init() {
		super.init();
		this.enableControls(false);
	}

	@Override
	protected void addExtraControls() {
		this.modeButton = this.addRenderableWidget(
			CycleButton.<CommandBlockEntity.Mode>builder(mode -> {
					return switch (mode) {
						case SEQUENCE -> Component.translatable("advMode.mode.sequence");
						case AUTO -> Component.translatable("advMode.mode.auto");
						case REDSTONE -> Component.translatable("advMode.mode.redstone");
					};
				}, this.mode)
				.withValues(CommandBlockEntity.Mode.values())
				.displayOnlyValue()
				.create(this.width / 2 - 50 - 100 - 4, 165, 100, 20, Component.translatable("advMode.mode"), (cycleButton, mode) -> this.mode = mode)
		);
		this.conditionalButton = this.addRenderableWidget(
			CycleButton.booleanBuilder(Component.translatable("advMode.mode.conditional"), Component.translatable("advMode.mode.unconditional"), this.conditional)
				.displayOnlyValue()
				.create(this.width / 2 - 50, 165, 100, 20, Component.translatable("advMode.type"), (cycleButton, boolean_) -> this.conditional = boolean_)
		);
		this.autoexecButton = this.addRenderableWidget(
			CycleButton.booleanBuilder(Component.translatable("advMode.mode.autoexec.bat"), Component.translatable("advMode.mode.redstoneTriggered"), this.autoexec)
				.displayOnlyValue()
				.create(this.width / 2 + 50 + 4, 165, 100, 20, Component.translatable("advMode.triggering"), (cycleButton, boolean_) -> this.autoexec = boolean_)
		);
	}

	private void enableControls(boolean bl) {
		this.doneButton.active = bl;
		this.outputButton.active = bl;
		this.modeButton.active = bl;
		this.conditionalButton.active = bl;
		this.autoexecButton.active = bl;
	}

	public void updateGui() {
		BaseCommandBlock baseCommandBlock = this.autoCommandBlock.getCommandBlock();
		this.commandEdit.setValue(baseCommandBlock.getCommand());
		boolean bl = baseCommandBlock.isTrackOutput();
		this.mode = this.autoCommandBlock.getMode();
		this.conditional = this.autoCommandBlock.isConditional();
		this.autoexec = this.autoCommandBlock.isAutomatic();
		this.outputButton.setValue(bl);
		this.modeButton.setValue(this.mode);
		this.conditionalButton.setValue(this.conditional);
		this.autoexecButton.setValue(this.autoexec);
		this.updatePreviousOutput(bl);
		this.enableControls(true);
	}

	@Override
	public void resize(int i, int j) {
		super.resize(i, j);
		this.enableControls(true);
	}

	@Override
	protected void populateAndSendPacket() {
		this.minecraft
			.getConnection()
			.send(
				new ServerboundSetCommandBlockPacket(
					this.autoCommandBlock.getBlockPos(),
					this.commandEdit.getValue(),
					this.mode,
					this.autoCommandBlock.getCommandBlock().isTrackOutput(),
					this.conditional,
					this.autoexec
				)
			);
	}
}
