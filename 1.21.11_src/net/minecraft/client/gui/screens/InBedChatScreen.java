package net.minecraft.client.gui.screens;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

@Environment(EnvType.CLIENT)
public class InBedChatScreen extends ChatScreen {
	private Button leaveBedButton;

	public InBedChatScreen(String string, boolean bl) {
		super(string, bl);
	}

	@Override
	protected void init() {
		super.init();
		this.leaveBedButton = Button.builder(Component.translatable("multiplayer.stopSleeping"), button -> this.sendWakeUp())
			.bounds(this.width / 2 - 100, this.height - 40, 200, 20)
			.build();
		this.addRenderableWidget(this.leaveBedButton);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		if (!this.minecraft.getChatStatus().isChatAllowed(this.minecraft.isLocalServer())) {
			this.leaveBedButton.render(guiGraphics, i, j, f);
		} else {
			super.render(guiGraphics, i, j, f);
		}
	}

	@Override
	public void onClose() {
		this.sendWakeUp();
	}

	@Override
	public boolean charTyped(CharacterEvent characterEvent) {
		return !this.minecraft.getChatStatus().isChatAllowed(this.minecraft.isLocalServer()) ? true : super.charTyped(characterEvent);
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if (keyEvent.isEscape()) {
			this.sendWakeUp();
		}

		if (!this.minecraft.getChatStatus().isChatAllowed(this.minecraft.isLocalServer())) {
			return true;
		} else if (keyEvent.isConfirmation()) {
			this.handleChatInput(this.input.getValue(), true);
			this.input.setValue("");
			this.minecraft.gui.getChat().resetChatScroll();
			return true;
		} else {
			return super.keyPressed(keyEvent);
		}
	}

	private void sendWakeUp() {
		ClientPacketListener clientPacketListener = this.minecraft.player.connection;
		clientPacketListener.send(new ServerboundPlayerCommandPacket(this.minecraft.player, ServerboundPlayerCommandPacket.Action.STOP_SLEEPING));
	}

	public void onPlayerWokeUp() {
		String string = this.input.getValue();
		if (!this.isDraft && !string.isEmpty()) {
			this.exitReason = ChatScreen.ExitReason.DONE;
			this.minecraft.setScreen(new ChatScreen(string, false));
		} else {
			this.exitReason = ChatScreen.ExitReason.INTERRUPTED;
			this.minecraft.setScreen(null);
		}
	}
}
