package net.minecraft.client.gui.components;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public abstract class AbstractScrollArea extends AbstractWidget {
	public static final int SCROLLBAR_WIDTH = 6;
	private double scrollAmount;
	private static final Identifier SCROLLER_SPRITE = Identifier.withDefaultNamespace("widget/scroller");
	private static final Identifier SCROLLER_BACKGROUND_SPRITE = Identifier.withDefaultNamespace("widget/scroller_background");
	private boolean scrolling;

	public AbstractScrollArea(int i, int j, int k, int l, Component component) {
		super(i, j, k, l, component);
	}

	@Override
	public boolean mouseScrolled(double d, double e, double f, double g) {
		if (!this.visible) {
			return false;
		}

		this.setScrollAmount(this.scrollAmount() - g * this.scrollRate());
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double d, double e) {
		if (this.scrolling) {
			if (mouseButtonEvent.y() < this.getY()) {
				this.setScrollAmount(0.0);
			} else if (mouseButtonEvent.y() > this.getBottom()) {
				this.setScrollAmount(this.maxScrollAmount());
			} else {
				double f = Math.max(1, this.maxScrollAmount());
				int i = this.scrollerHeight();
				double g = Math.max(1.0, f / (this.height - i));
				this.setScrollAmount(this.scrollAmount() + e * g);
			}

			return true;
		} else {
			return super.mouseDragged(mouseButtonEvent, d, e);
		}
	}

	@Override
	public void onRelease(MouseButtonEvent mouseButtonEvent) {
		this.scrolling = false;
	}

	public double scrollAmount() {
		return this.scrollAmount;
	}

	public void setScrollAmount(double d) {
		this.scrollAmount = Mth.clamp(d, 0.0, this.maxScrollAmount());
	}

	public boolean updateScrolling(MouseButtonEvent mouseButtonEvent) {
		this.scrolling = this.scrollbarVisible()
			&& this.isValidClickButton(mouseButtonEvent.buttonInfo())
			&& this.isOverScrollbar(mouseButtonEvent.x(), mouseButtonEvent.y());
		return this.scrolling;
	}

	protected boolean isOverScrollbar(double d, double e) {
		return d >= this.scrollBarX() && d <= this.scrollBarX() + 6 && e >= this.getY() && e < this.getBottom();
	}

	public void refreshScrollAmount() {
		this.setScrollAmount(this.scrollAmount);
	}

	public int maxScrollAmount() {
		return Math.max(0, this.contentHeight() - this.height);
	}

	protected boolean scrollbarVisible() {
		return this.maxScrollAmount() > 0;
	}

	protected int scrollerHeight() {
		return Mth.clamp((int)((float)(this.height * this.height) / this.contentHeight()), 32, this.height - 8);
	}

	protected int scrollBarX() {
		return this.getRight() - 6;
	}

	protected int scrollBarY() {
		return Math.max(this.getY(), (int)this.scrollAmount * (this.height - this.scrollerHeight()) / this.maxScrollAmount() + this.getY());
	}

	protected void renderScrollbar(GuiGraphics guiGraphics, int i, int j) {
		if (this.scrollbarVisible()) {
			int k = this.scrollBarX();
			int l = this.scrollerHeight();
			int m = this.scrollBarY();
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_BACKGROUND_SPRITE, k, this.getY(), 6, this.getHeight());
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_SPRITE, k, m, 6, l);
			if (this.isOverScrollbar(i, j)) {
				guiGraphics.requestCursor(this.scrolling ? CursorTypes.RESIZE_NS : CursorTypes.POINTING_HAND);
			}
		}
	}

	protected abstract int contentHeight();

	protected abstract double scrollRate();
}
