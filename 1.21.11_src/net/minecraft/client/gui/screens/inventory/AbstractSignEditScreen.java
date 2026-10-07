package net.minecraft.client.gui.screens.inventory;

import java.util.stream.IntStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class AbstractSignEditScreen extends Screen {
	protected final SignBlockEntity sign;
	private SignText text;
	private final String[] messages;
	private final boolean isFrontText;
	protected final WoodType woodType;
	private int frame;
	private int line;
	private @Nullable TextFieldHelper signField;

	public AbstractSignEditScreen(SignBlockEntity signBlockEntity, boolean bl, boolean bl2) {
		this(signBlockEntity, bl, bl2, Component.translatable("sign.edit"));
	}

	public AbstractSignEditScreen(SignBlockEntity signBlockEntity, boolean bl, boolean bl2, Component component) {
		super(component);
		this.sign = signBlockEntity;
		this.text = signBlockEntity.getText(bl);
		this.isFrontText = bl;
		this.woodType = SignBlock.getWoodType(signBlockEntity.getBlockState().getBlock());
		this.messages = IntStream.range(0, 4).mapToObj(i -> this.text.getMessage(i, bl2)).map(Component::getString).toArray(String[]::new);
	}

	@Override
	protected void init() {
		this.addRenderableWidget(
			Button.builder(CommonComponents.GUI_DONE, button -> this.onDone()).bounds(this.width / 2 - 100, this.height / 4 + 144, 200, 20).build()
		);
		this.signField = new TextFieldHelper(
			() -> this.messages[this.line],
			this::setMessage,
			TextFieldHelper.createClipboardGetter(this.minecraft),
			TextFieldHelper.createClipboardSetter(this.minecraft),
			string -> this.minecraft.font.width(string) <= this.sign.getMaxTextLineWidth()
		);
	}

	@Override
	public void tick() {
		this.frame++;
		if (!this.isValid()) {
			this.onDone();
		}
	}

	private boolean isValid() {
		return this.minecraft.player != null && !this.sign.isRemoved() && !this.sign.playerIsTooFarAwayToEdit(this.minecraft.player.getUUID());
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if (keyEvent.isUp()) {
			this.line = this.line - 1 & 3;
			this.signField.setCursorToEnd();
			return true;
		} else if (keyEvent.isDown() || keyEvent.isConfirmation()) {
			this.line = this.line + 1 & 3;
			this.signField.setCursorToEnd();
			return true;
		} else {
			return this.signField.keyPressed(keyEvent) ? true : super.keyPressed(keyEvent);
		}
	}

	@Override
	public boolean charTyped(CharacterEvent characterEvent) {
		this.signField.charTyped(characterEvent);
		return true;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 40, -1);
		this.renderSign(guiGraphics);
	}

	@Override
	public void onClose() {
		this.onDone();
	}

	@Override
	public void removed() {
		ClientPacketListener clientPacketListener = this.minecraft.getConnection();
		if (clientPacketListener != null) {
			clientPacketListener.send(
				new ServerboundSignUpdatePacket(this.sign.getBlockPos(), this.isFrontText, this.messages[0], this.messages[1], this.messages[2], this.messages[3])
			);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	protected abstract void renderSignBackground(GuiGraphics guiGraphics);

	protected abstract Vector3f getSignTextScale();

	protected abstract float getSignYOffset();

	private void renderSign(GuiGraphics guiGraphics) {
		guiGraphics.pose().pushMatrix();
		guiGraphics.pose().translate(this.width / 2.0F, this.getSignYOffset());
		guiGraphics.pose().pushMatrix();
		this.renderSignBackground(guiGraphics);
		guiGraphics.pose().popMatrix();
		this.renderSignText(guiGraphics);
		guiGraphics.pose().popMatrix();
	}

	private void renderSignText(GuiGraphics guiGraphics) {
		Vector3f vector3f = this.getSignTextScale();
		guiGraphics.pose().scale(vector3f.x(), vector3f.y());
		int i = this.text.hasGlowingText() ? this.text.getColor().getTextColor() : AbstractSignRenderer.getDarkColor(this.text);
		boolean bl = this.frame / 6 % 2 == 0;
		int j = this.signField.getCursorPos();
		int k = this.signField.getSelectionPos();
		int l = 4 * this.sign.getTextLineHeight() / 2;
		int m = this.line * this.sign.getTextLineHeight() - l;

		for (int n = 0; n < this.messages.length; n++) {
			String string = this.messages[n];
			if (string != null) {
				if (this.font.isBidirectional()) {
					string = this.font.bidirectionalShaping(string);
				}

				int o = -this.font.width(string) / 2;
				guiGraphics.drawString(this.font, string, o, n * this.sign.getTextLineHeight() - l, i, false);
				if (n == this.line && j >= 0 && bl) {
					int p = this.font.width(string.substring(0, Math.max(Math.min(j, string.length()), 0)));
					int q = p - this.font.width(string) / 2;
					if (j >= string.length()) {
						guiGraphics.drawString(this.font, "_", q, m, i, false);
					}
				}
			}
		}

		for (int n = 0; n < this.messages.length; n++) {
			String string = this.messages[n];
			if (string != null && n == this.line && j >= 0) {
				int o = this.font.width(string.substring(0, Math.max(Math.min(j, string.length()), 0)));
				int p = o - this.font.width(string) / 2;
				if (bl && j < string.length()) {
					guiGraphics.fill(p, m - 1, p + 1, m + this.sign.getTextLineHeight(), ARGB.opaque(i));
				}

				if (k != j) {
					int q = Math.min(j, k);
					int r = Math.max(j, k);
					int s = this.font.width(string.substring(0, q)) - this.font.width(string) / 2;
					int t = this.font.width(string.substring(0, r)) - this.font.width(string) / 2;
					int u = Math.min(s, t);
					int v = Math.max(s, t);
					guiGraphics.textHighlight(u, m, v, m + this.sign.getTextLineHeight(), true);
				}
			}
		}
	}

	private void setMessage(String string) {
		this.messages[this.line] = string;
		this.text = this.text.setMessage(this.line, Component.literal(string));
		this.sign.setText(this.text, this.isFrontText);
	}

	private void onDone() {
		this.minecraft.setScreen(null);
	}
}
