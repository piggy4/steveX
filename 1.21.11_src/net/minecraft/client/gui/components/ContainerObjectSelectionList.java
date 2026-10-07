package net.minecraft.client.gui.components;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenAxis;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class ContainerObjectSelectionList<E extends ContainerObjectSelectionList.Entry<E>> extends AbstractSelectionList<E> {
	public ContainerObjectSelectionList(Minecraft minecraft, int i, int j, int k, int l) {
		super(minecraft, i, j, k, l);
	}

	@Override
	public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent focusNavigationEvent) {
		if (this.getItemCount() == 0) {
			return null;
		} else if (!(focusNavigationEvent instanceof FocusNavigationEvent.ArrowNavigation arrowNavigation)) {
			return super.nextFocusPath(focusNavigationEvent);
		} else {
			E entry = this.getFocused();
			if (arrowNavigation.direction().getAxis() == ScreenAxis.HORIZONTAL && entry != null) {
				return ComponentPath.path(this, entry.nextFocusPath(focusNavigationEvent));
			}

			int i = -1;
			ScreenDirection screenDirection = arrowNavigation.direction();
			if (entry != null) {
				i = entry.children().indexOf(entry.getFocused());
			}

			if (i == -1) {
				switch (screenDirection) {
					case LEFT:
						i = Integer.MAX_VALUE;
						screenDirection = ScreenDirection.DOWN;
						break;
					case RIGHT:
						i = 0;
						screenDirection = ScreenDirection.DOWN;
						break;
					default:
						i = 0;
				}
			}

			E entry2 = entry;

			ComponentPath componentPath;
			do {
				entry2 = this.nextEntry(screenDirection, entryx -> !entryx.children().isEmpty(), entry2);
				if (entry2 == null) {
					return null;
				}

				componentPath = entry2.focusPathAtIndex(arrowNavigation, i);
			} while (componentPath == null);

			return ComponentPath.path(this, componentPath);
		}
	}

	@Override
	public void setFocused(@Nullable GuiEventListener guiEventListener) {
		if (this.getFocused() != guiEventListener) {
			super.setFocused(guiEventListener);
			if (guiEventListener == null) {
				this.setSelected(null);
			}
		}
	}

	@Override
	public NarratableEntry.NarrationPriority narrationPriority() {
		return this.isFocused() ? NarratableEntry.NarrationPriority.FOCUSED : super.narrationPriority();
	}

	@Override
	protected boolean entriesCanBeSelected() {
		return false;
	}

	@Override
	public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
		if (this.getHovered() instanceof E entry) {
			entry.updateNarration(narrationElementOutput.nest());
			this.narrateListElementPosition(narrationElementOutput, entry);
		} else if (this.getFocused() instanceof E entry2) {
			entry2.updateNarration(narrationElementOutput.nest());
			this.narrateListElementPosition(narrationElementOutput, entry2);
		}
	}

	@Environment(EnvType.CLIENT)
	public abstract static class Entry<E extends ContainerObjectSelectionList.Entry<E>> extends AbstractSelectionList.Entry<E> implements ContainerEventHandler {
		private @Nullable GuiEventListener focused;
		private @Nullable NarratableEntry lastNarratable;
		private boolean dragging;

		@Override
		public boolean isDragging() {
			return this.dragging;
		}

		@Override
		public void setDragging(boolean bl) {
			this.dragging = bl;
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
			return ContainerEventHandler.super.mouseClicked(mouseButtonEvent, bl);
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
		public @Nullable GuiEventListener getFocused() {
			return this.focused;
		}

		public @Nullable ComponentPath focusPathAtIndex(FocusNavigationEvent focusNavigationEvent, int i) {
			if (this.children().isEmpty()) {
				return null;
			}

			ComponentPath componentPath = this.children().get(Math.min(i, this.children().size() - 1)).nextFocusPath(focusNavigationEvent);
			return ComponentPath.path(this, componentPath);
		}

		@Override
		public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent focusNavigationEvent) {
			if (focusNavigationEvent instanceof FocusNavigationEvent.ArrowNavigation arrowNavigation) {
				int i = switch (arrowNavigation.direction()) {
					case LEFT -> -1;
					case RIGHT -> 1;
					case UP, DOWN -> 0;
				};
				if (i == 0) {
					return null;
				}

				int j = Mth.clamp(i + this.children().indexOf(this.getFocused()), 0, this.children().size() - 1);

				for (int k = j; k >= 0 && k < this.children().size(); k += i) {
					GuiEventListener guiEventListener = this.children().get(k);
					ComponentPath componentPath = guiEventListener.nextFocusPath(focusNavigationEvent);
					if (componentPath != null) {
						return ComponentPath.path(this, componentPath);
					}
				}
			}

			return ContainerEventHandler.super.nextFocusPath(focusNavigationEvent);
		}

		public abstract List<? extends NarratableEntry> narratables();

		void updateNarration(NarrationElementOutput narrationElementOutput) {
			List<? extends NarratableEntry> list = this.narratables();
			Screen.NarratableSearchResult narratableSearchResult = Screen.findNarratableWidget(list, this.lastNarratable);
			if (narratableSearchResult != null) {
				if (narratableSearchResult.priority().isTerminal()) {
					this.lastNarratable = narratableSearchResult.entry();
				}

				if (list.size() > 1) {
					narrationElementOutput.add(
						NarratedElementType.POSITION, Component.translatable("narrator.position.object_list", narratableSearchResult.index() + 1, list.size())
					);
				}

				narratableSearchResult.entry().updateNarration(narrationElementOutput.nest());
			}
		}
	}
}
