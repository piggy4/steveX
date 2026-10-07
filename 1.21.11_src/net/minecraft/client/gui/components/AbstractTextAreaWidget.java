package net.minecraft.client.gui.components;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public abstract class AbstractTextAreaWidget extends AbstractScrollArea {
	private static final WidgetSprites BACKGROUND_SPRITES = new WidgetSprites(
		Identifier.withDefaultNamespace("widget/text_field"), Identifier.withDefaultNamespace("widget/text_field_highlighted")
	);
	private static final int INNER_PADDING = 4;
	public static final int DEFAULT_TOTAL_PADDING = 8;
	private boolean showBackground = true;
	private boolean showDecorations = true;

	public AbstractTextAreaWidget(int i, int j, int k, int l, Component component) {
		super(i, j, k, l, component);
	}

	public AbstractTextAreaWidget(int i, int j, int k, int l, Component component, boolean bl, boolean bl2) {
		this(i, j, k, l, component);
		this.showBackground = bl;
		this.showDecorations = bl2;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		boolean bl2 = this.updateScrolling(mouseButtonEvent);
		return super.mouseClicked(mouseButtonEvent, bl) || bl2;
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		boolean bl = keyEvent.isUp();
		boolean bl2 = keyEvent.isDown();
		if (bl || bl2) {
			double d = this.scrollAmount();
			this.setScrollAmount(this.scrollAmount() + (bl ? -1 : 1) * this.scrollRate());
			if (d != this.scrollAmount()) {
				return true;
			}
		}

		return super.keyPressed(keyEvent);
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
		if (this.visible) {
			if (this.showBackground) {
				this.renderBackground(guiGraphics);
			}

			guiGraphics.enableScissor(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1);
			guiGraphics.pose().pushMatrix();
			guiGraphics.pose().translate(0.0F, (float)(-this.scrollAmount()));
			this.renderContents(guiGraphics, i, j, f);
			guiGraphics.pose().popMatrix();
			guiGraphics.disableScissor();
			this.renderScrollbar(guiGraphics, i, j);
			if (this.showDecorations) {
				this.renderDecorations(guiGraphics);
			}
		}
	}

	protected void renderDecorations(GuiGraphics guiGraphics) {
	}

	protected int innerPadding() {
		return 4;
	}

	protected int totalInnerPadding() {
		return this.innerPadding() * 2;
	}

	@Override
	public boolean isMouseOver(double d, double e) {
		return this.active && this.visible && d >= this.getX() && e >= this.getY() && d < this.getRight() + 6 && e < this.getBottom();
	}

	@Override
	protected int scrollBarX() {
		return this.getRight();
	}

	@Override
	protected int contentHeight() {
		return this.getInnerHeight() + this.totalInnerPadding();
	}

	protected void renderBackground(GuiGraphics guiGraphics) {
		this.renderBorder(guiGraphics, this.getX(), this.getY(), this.getWidth(), this.getHeight());
	}

	protected void renderBorder(GuiGraphics guiGraphics, int i, int j, int k, int l) {
		Identifier identifier = BACKGROUND_SPRITES.get(this.isActive(), this.isFocused());
		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, identifier, i, j, k, l);
	}

	protected boolean withinContentAreaTopBottom(int i, int j) {
		return j - this.scrollAmount() >= this.getY() && i - this.scrollAmount() <= this.getY() + this.height;
	}

	protected abstract int getInnerHeight();

	protected abstract void renderContents(GuiGraphics guiGraphics, int i, int j, float f);

	protected int getInnerLeft() {
		return this.getX() + this.innerPadding();
	}

	protected int getInnerTop() {
		return this.getY() + this.innerPadding();
	}

	@Override
	public void playDownSound(SoundManager soundManager) {
	}
}
