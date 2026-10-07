package net.minecraft.client.gui.navigation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface FocusNavigationEvent {
	ScreenDirection getVerticalDirectionForInitialFocus();

	@Environment(EnvType.CLIENT)
	record ArrowNavigation(ScreenDirection direction) implements FocusNavigationEvent {
		@Override
		public ScreenDirection getVerticalDirectionForInitialFocus() {
			return this.direction.getAxis() == ScreenAxis.VERTICAL ? this.direction : ScreenDirection.DOWN;
		}
	}

	@Environment(EnvType.CLIENT)
	class InitialFocus implements FocusNavigationEvent {
		@Override
		public ScreenDirection getVerticalDirectionForInitialFocus() {
			return ScreenDirection.DOWN;
		}
	}

	@Environment(EnvType.CLIENT)
	record TabNavigation(boolean forward) implements FocusNavigationEvent {
		@Override
		public ScreenDirection getVerticalDirectionForInitialFocus() {
			return this.forward ? ScreenDirection.DOWN : ScreenDirection.UP;
		}
	}
}
