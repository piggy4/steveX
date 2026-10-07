package net.minecraft.client.gui.screens;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class GenericMessageScreen extends Screen {
	private @Nullable FocusableTextWidget textWidget;

	public GenericMessageScreen(Component component) {
		super(component);
	}

	@Override
	protected void init() {
		this.textWidget = this.addRenderableWidget(FocusableTextWidget.builder(this.title, this.font, 12).textWidth(this.font.width(this.title)).build());
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		if (this.textWidget != null) {
			this.textWidget.setPosition(this.width / 2 - this.textWidget.getWidth() / 2, this.height / 2 - 9 / 2);
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	protected boolean shouldNarrateNavigation() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
		this.renderPanorama(guiGraphics, f);
		this.renderBlurredBackground(guiGraphics);
		this.renderMenuBackground(guiGraphics);
	}
}
