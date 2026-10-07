package net.minecraft.client.gui.components.events;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class AbstractContainerEventHandler implements ContainerEventHandler {
	private @Nullable GuiEventListener focused;
	private boolean isDragging;

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
		if (this.focused != guiEventListener) {
			if (this.focused != null) {
				this.focused.setFocused(false);
			}

			if (guiEventListener != null) {
				guiEventListener.setFocused(true);
			}

			this.focused = guiEventListener;
		}
	}
}
