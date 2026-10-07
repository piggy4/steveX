package net.minecraft.client.gui.components;

import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class AbstractStringWidget extends AbstractWidget {
	private @Nullable Consumer<Style> componentClickHandler = null;
	private final Font font;

	public AbstractStringWidget(int i, int j, int k, int l, Component component, Font font) {
		super(i, j, k, l, component);
		this.font = font;
	}

	public abstract void visitLines(ActiveTextCollector activeTextCollector);

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
		GuiGraphics.HoveredTextEffects hoveredTextEffects;
		if (this.isHovered()) {
			if (this.componentClickHandler != null) {
				hoveredTextEffects = GuiGraphics.HoveredTextEffects.TOOLTIP_AND_CURSOR;
			} else {
				hoveredTextEffects = GuiGraphics.HoveredTextEffects.TOOLTIP_ONLY;
			}
		} else {
			hoveredTextEffects = GuiGraphics.HoveredTextEffects.NONE;
		}

		this.visitLines(guiGraphics.textRendererForWidget(this, hoveredTextEffects));
	}

	@Override
	public void onClick(MouseButtonEvent mouseButtonEvent, boolean bl) {
		if (this.componentClickHandler != null) {
			ActiveTextCollector.ClickableStyleFinder clickableStyleFinder = new ActiveTextCollector.ClickableStyleFinder(
				this.getFont(), (int)mouseButtonEvent.x(), (int)mouseButtonEvent.y()
			);
			this.visitLines(clickableStyleFinder);
			Style style = clickableStyleFinder.result();
			if (style != null) {
				this.componentClickHandler.accept(style);
				return;
			}
		}

		super.onClick(mouseButtonEvent, bl);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
	}

	protected final Font getFont() {
		return this.font;
	}

	@Override
	public void setMessage(Component component) {
		super.setMessage(component);
		this.setWidth(this.getFont().width(component.getVisualOrderText()));
	}

	public AbstractStringWidget setComponentClickHandler(@Nullable Consumer<Style> consumer) {
		this.componentClickHandler = consumer;
		return this;
	}
}
