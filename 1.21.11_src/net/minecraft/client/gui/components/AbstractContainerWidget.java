package net.minecraft.client.gui.components;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class AbstractContainerWidget extends AbstractScrollArea implements ContainerEventHandler {
	private @Nullable GuiEventListener focused;
	private boolean isDragging;

	public AbstractContainerWidget(int i, int j, int k, int l, Component component) {
		super(i, j, k, l, component);
	}

	@Override
	public final boolean isDragging() {
		return this.isDragging;
	}

	@Override
	public final void setDragging(boolean bl) {
		this.isDragging = bl;
	}

	@Override
	public @Nullable GuiEventListener getFocused() {
		return this.focused;
	}

	@Override
	public void setFocused(@Nullable GuiEventListener guiEventListener) {
		if (this.focused != null) {
			this.focused.setFocused(false);
		}

		if (guiEventListener != null) {
			guiEventListener.setFocused(true);
		}

		this.focused = guiEventListener;
	}

	@Override
	public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent focusNavigationEvent) {
		return ContainerEventHandler.super.nextFocusPath(focusNavigationEvent);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		boolean bl2 = this.updateScrolling(mouseButtonEvent);
		return ContainerEventHandler.super.mouseClicked(mouseButtonEvent, bl) || bl2;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
		super.mouseReleased(mouseButtonEvent);
		return ContainerEventHandler.super.mouseReleased(mouseButtonEvent);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double d, double e) {
		super.mouseDragged(mouseButtonEvent, d, e);
		return ContainerEventHandler.super.mouseDragged(mouseButtonEvent, d, e);
	}

	@Override
	public boolean isFocused() {
		return ContainerEventHandler.super.isFocused();
	}

	@Override
	public void setFocused(boolean bl) {
		ContainerEventHandler.super.setFocused(bl);
	}
}
