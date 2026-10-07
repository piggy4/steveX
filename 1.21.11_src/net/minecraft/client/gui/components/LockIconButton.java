package net.minecraft.client.gui.components;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public class LockIconButton extends Button {
	private boolean locked;

	public LockIconButton(int i, int j, Button.OnPress onPress) {
		super(i, j, 20, 20, Component.translatable("narrator.button.difficulty_lock"), onPress, DEFAULT_NARRATION);
	}

	@Override
	protected MutableComponent createNarrationMessage() {
		return CommonComponents.joinForNarration(
			super.createNarrationMessage(),
			this.isLocked() ? Component.translatable("narrator.button.difficulty_lock.locked") : Component.translatable("narrator.button.difficulty_lock.unlocked")
		);
	}

	public boolean isLocked() {
		return this.locked;
	}

	public void setLocked(boolean bl) {
		this.locked = bl;
	}

	@Override
	public void renderContents(GuiGraphics guiGraphics, int i, int j, float f) {
		LockIconButton.Icon icon;
		if (!this.active) {
			icon = this.locked ? LockIconButton.Icon.LOCKED_DISABLED : LockIconButton.Icon.UNLOCKED_DISABLED;
		} else if (this.isHoveredOrFocused()) {
			icon = this.locked ? LockIconButton.Icon.LOCKED_HOVER : LockIconButton.Icon.UNLOCKED_HOVER;
		} else {
			icon = this.locked ? LockIconButton.Icon.LOCKED : LockIconButton.Icon.UNLOCKED;
		}

		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon.sprite, this.getX(), this.getY(), this.width, this.height);
	}

	@Environment(EnvType.CLIENT)
	enum Icon {
		LOCKED(Identifier.withDefaultNamespace("widget/locked_button")),
		LOCKED_HOVER(Identifier.withDefaultNamespace("widget/locked_button_highlighted")),
		LOCKED_DISABLED(Identifier.withDefaultNamespace("widget/locked_button_disabled")),
		UNLOCKED(Identifier.withDefaultNamespace("widget/unlocked_button")),
		UNLOCKED_HOVER(Identifier.withDefaultNamespace("widget/unlocked_button_highlighted")),
		UNLOCKED_DISABLED(Identifier.withDefaultNamespace("widget/unlocked_button_disabled"));

		final Identifier sprite;

		Icon(final Identifier identifier) {
			this.sprite = identifier;
		}
	}
}
