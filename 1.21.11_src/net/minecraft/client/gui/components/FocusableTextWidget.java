package net.minecraft.client.gui.components;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

@Environment(EnvType.CLIENT)
public class FocusableTextWidget extends MultiLineTextWidget {
	public static final int DEFAULT_PADDING = 4;
	private final int padding;
	private final int maxWidth;
	private final boolean alwaysShowBorder;
	private final FocusableTextWidget.BackgroundFill backgroundFill;

	FocusableTextWidget(Component component, Font font, int i, int j, FocusableTextWidget.BackgroundFill backgroundFill, boolean bl) {
		super(component, font);
		this.active = true;
		this.padding = i;
		this.maxWidth = j;
		this.alwaysShowBorder = bl;
		this.backgroundFill = backgroundFill;
		this.updateWidth();
		this.updateHeight();
		this.setCentered(true);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
		narrationElementOutput.add(NarratedElementType.TITLE, this.getMessage());
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
		int k = this.alwaysShowBorder && !this.isFocused() ? ARGB.color(this.alpha, -6250336) : ARGB.white(this.alpha);
		switch (this.backgroundFill) {
			case ALWAYS:
				guiGraphics.fill(this.getX() + 1, this.getY(), this.getRight(), this.getBottom(), ARGB.black(this.alpha));
				break;
			case ON_FOCUS:
				if (this.isFocused()) {
					guiGraphics.fill(this.getX() + 1, this.getY(), this.getRight(), this.getBottom(), ARGB.black(this.alpha));
				}
			case NEVER:
		}

		if (this.isFocused() || this.alwaysShowBorder) {
			guiGraphics.renderOutline(this.getX(), this.getY(), this.getWidth(), this.getHeight(), k);
		}

		super.renderWidget(guiGraphics, i, j, f);
	}

	@Override
	protected int getTextX() {
		return this.getX() + this.padding;
	}

	@Override
	protected int getTextY() {
		return super.getTextY() + this.padding;
	}

	@Override
	public MultiLineTextWidget setMaxWidth(int i) {
		return super.setMaxWidth(i - this.padding * 2);
	}

	@Override
	public int getWidth() {
		return this.width;
	}

	@Override
	public int getHeight() {
		return this.height;
	}

	public int getPadding() {
		return this.padding;
	}

	public void updateWidth() {
		if (this.maxWidth != -1) {
			this.setWidth(this.maxWidth);
			this.setMaxWidth(this.maxWidth);
		} else {
			this.setWidth(this.getFont().width(this.getMessage()) + this.padding * 2);
		}
	}

	public void updateHeight() {
		int i = 9 * this.getFont().split(this.getMessage(), super.getWidth()).size();
		this.setHeight(i + this.padding * 2);
	}

	@Override
	public void setMessage(Component component) {
		this.message = component;
		int i;
		if (this.maxWidth != -1) {
			i = this.maxWidth;
		} else {
			i = this.getFont().width(component) + this.padding * 2;
		}

		this.setWidth(i);
		this.updateHeight();
	}

	@Override
	public void playDownSound(SoundManager soundManager) {
	}

	public static FocusableTextWidget.Builder builder(Component component, Font font) {
		return new FocusableTextWidget.Builder(component, font);
	}

	public static FocusableTextWidget.Builder builder(Component component, Font font, int i) {
		return new FocusableTextWidget.Builder(component, font, i);
	}

	@Environment(EnvType.CLIENT)
	public enum BackgroundFill {
		ALWAYS,
		ON_FOCUS,
		NEVER;
	}

	@Environment(EnvType.CLIENT)
	public static class Builder {
		private final Component message;
		private final Font font;
		private final int padding;
		private int maxWidth = -1;
		private boolean alwaysShowBorder = true;
		private FocusableTextWidget.BackgroundFill backgroundFill = FocusableTextWidget.BackgroundFill.ALWAYS;

		Builder(Component component, Font font) {
			this(component, font, 4);
		}

		Builder(Component component, Font font, int i) {
			this.message = component;
			this.font = font;
			this.padding = i;
		}

		public FocusableTextWidget.Builder maxWidth(int i) {
			this.maxWidth = i;
			return this;
		}

		public FocusableTextWidget.Builder textWidth(int i) {
			this.maxWidth = i + this.padding * 2;
			return this;
		}

		public FocusableTextWidget.Builder alwaysShowBorder(boolean bl) {
			this.alwaysShowBorder = bl;
			return this;
		}

		public FocusableTextWidget.Builder backgroundFill(FocusableTextWidget.BackgroundFill backgroundFill) {
			this.backgroundFill = backgroundFill;
			return this;
		}

		public FocusableTextWidget build() {
			return new FocusableTextWidget(this.message, this.font, this.padding, this.maxWidth, this.backgroundFill, this.alwaysShowBorder);
		}
	}
}
