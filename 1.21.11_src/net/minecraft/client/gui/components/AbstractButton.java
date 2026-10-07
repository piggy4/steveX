package net.minecraft.client.gui.components;

import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class AbstractButton extends AbstractWidget.WithInactiveMessage {
	protected static final int TEXT_MARGIN = 2;
	private static final WidgetSprites SPRITES = new WidgetSprites(
		Identifier.withDefaultNamespace("widget/button"),
		Identifier.withDefaultNamespace("widget/button_disabled"),
		Identifier.withDefaultNamespace("widget/button_highlighted")
	);
	private @Nullable Supplier<Boolean> overrideRenderHighlightedSprite;

	public AbstractButton(int i, int j, int k, int l, Component component) {
		super(i, j, k, l, component);
	}

	public abstract void onPress(InputWithModifiers inputWithModifiers);

	@Override
	protected final void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
		this.renderContents(guiGraphics, i, j, f);
		this.handleCursor(guiGraphics);
	}

	protected abstract void renderContents(GuiGraphics guiGraphics, int i, int j, float f);

	protected void renderDefaultLabel(ActiveTextCollector activeTextCollector) {
		this.renderScrollingStringOverContents(activeTextCollector, this.getMessage(), 2);
	}

	protected final void renderDefaultSprite(GuiGraphics guiGraphics) {
		guiGraphics.blitSprite(
			RenderPipelines.GUI_TEXTURED,
			SPRITES.get(this.active, this.overrideRenderHighlightedSprite != null ? this.overrideRenderHighlightedSprite.get() : this.isHoveredOrFocused()),
			this.getX(),
			this.getY(),
			this.getWidth(),
			this.getHeight(),
			ARGB.white(this.alpha)
		);
	}

	@Override
	public void onClick(MouseButtonEvent mouseButtonEvent, boolean bl) {
		this.onPress(mouseButtonEvent);
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if (!this.isActive()) {
			return false;
		} else if (keyEvent.isSelection()) {
			this.playDownSound(Minecraft.getInstance().getSoundManager());
			this.onPress(keyEvent);
			return true;
		} else {
			return false;
		}
	}

	public void setOverrideRenderHighlightedSprite(Supplier<Boolean> supplier) {
		this.overrideRenderHighlightedSprite = supplier;
	}
}
