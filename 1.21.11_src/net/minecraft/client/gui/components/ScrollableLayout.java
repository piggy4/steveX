package net.minecraft.client.gui.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.CommonComponents;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ScrollableLayout implements Layout {
	private static final int SCROLLBAR_SPACING = 4;
	private static final int SCROLLBAR_RESERVE = 10;
	final Layout content;
	private final ScrollableLayout.Container container;
	private int minWidth;
	private int maxHeight;

	public ScrollableLayout(Minecraft minecraft, Layout layout, int i) {
		this.content = layout;
		this.container = new ScrollableLayout.Container(minecraft, 0, i);
	}

	public void setMinWidth(int i) {
		this.minWidth = i;
		this.container.setWidth(Math.max(this.content.getWidth(), i));
	}

	public void setMaxHeight(int i) {
		this.maxHeight = i;
		this.container.setHeight(Math.min(this.content.getHeight(), i));
		this.container.refreshScrollAmount();
	}

	@Override
	public void arrangeElements() {
		this.content.arrangeElements();
		int i = this.content.getWidth();
		this.container.setWidth(Math.max(i + 20, this.minWidth));
		this.container.setHeight(Math.min(this.content.getHeight(), this.maxHeight));
		this.container.refreshScrollAmount();
	}

	@Override
	public void visitChildren(Consumer<LayoutElement> consumer) {
		consumer.accept(this.container);
	}

	@Override
	public void setX(int i) {
		this.container.setX(i);
	}

	@Override
	public void setY(int i) {
		this.container.setY(i);
	}

	@Override
	public int getX() {
		return this.container.getX();
	}

	@Override
	public int getY() {
		return this.container.getY();
	}

	@Override
	public int getWidth() {
		return this.container.getWidth();
	}

	@Override
	public int getHeight() {
		return this.container.getHeight();
	}

	@Environment(EnvType.CLIENT)
	class Container extends AbstractContainerWidget {
		private final Minecraft minecraft;
		private final List<AbstractWidget> children = new ArrayList<>();

		public Container(final Minecraft minecraft, final int i, final int j) {
			super(0, 0, i, j, CommonComponents.EMPTY);
			this.minecraft = minecraft;
			ScrollableLayout.this.content.visitWidgets(this.children::add);
		}

		@Override
		protected int contentHeight() {
			return ScrollableLayout.this.content.getHeight();
		}

		@Override
		protected double scrollRate() {
			return 10.0;
		}

		@Override
		protected void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
			guiGraphics.enableScissor(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height);

			for (AbstractWidget abstractWidget : this.children) {
				abstractWidget.render(guiGraphics, i, j, f);
			}

			guiGraphics.disableScissor();
			this.renderScrollbar(guiGraphics, i, j);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
		}

		@Override
		public ScreenRectangle getBorderForArrowNavigation(ScreenDirection screenDirection) {
			return new ScreenRectangle(this.getX(), this.getY(), this.width, this.contentHeight());
		}

		@Override
		public void setFocused(@Nullable GuiEventListener guiEventListener) {
			super.setFocused(guiEventListener);
			if (guiEventListener != null && this.minecraft.getLastInputType().isKeyboard()) {
				ScreenRectangle screenRectangle = this.getRectangle();
				ScreenRectangle screenRectangle2 = guiEventListener.getRectangle();
				int i = screenRectangle2.top() - screenRectangle.top();
				int j = screenRectangle2.bottom() - screenRectangle.bottom();
				if (i < 0) {
					this.setScrollAmount(this.scrollAmount() + i - 14.0);
				} else if (j > 0) {
					this.setScrollAmount(this.scrollAmount() + j + 14.0);
				}
			}
		}

		@Override
		public void setX(int i) {
			super.setX(i);
			ScrollableLayout.this.content.setX(i + 10);
		}

		@Override
		public void setY(int i) {
			super.setY(i);
			ScrollableLayout.this.content.setY(i - (int)this.scrollAmount());
		}

		@Override
		public void setScrollAmount(double d) {
			super.setScrollAmount(d);
			ScrollableLayout.this.content.setY(this.getRectangle().top() - (int)this.scrollAmount());
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return this.children;
		}

		@Override
		public Collection<? extends NarratableEntry> getNarratables() {
			return this.children;
		}
	}
}
