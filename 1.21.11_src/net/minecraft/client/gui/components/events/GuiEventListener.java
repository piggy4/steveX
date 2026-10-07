package net.minecraft.client.gui.components.events;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.components.TabOrderedElement;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface GuiEventListener extends TabOrderedElement {
	default void mouseMoved(double d, double e) {
	}

	default boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		return false;
	}

	default boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
		return false;
	}

	default boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double d, double e) {
		return false;
	}

	default boolean mouseScrolled(double d, double e, double f, double g) {
		return false;
	}

	default boolean keyPressed(KeyEvent keyEvent) {
		return false;
	}

	default boolean keyReleased(KeyEvent keyEvent) {
		return false;
	}

	default boolean charTyped(CharacterEvent characterEvent) {
		return false;
	}

	default @Nullable ComponentPath nextFocusPath(FocusNavigationEvent focusNavigationEvent) {
		return null;
	}

	default boolean isMouseOver(double d, double e) {
		return false;
	}

	void setFocused(boolean bl);

	boolean isFocused();

	default boolean shouldTakeFocusAfterInteraction() {
		return true;
	}

	default @Nullable ComponentPath getCurrentFocusPath() {
		return this.isFocused() ? ComponentPath.leaf(this) : null;
	}

	default ScreenRectangle getRectangle() {
		return ScreenRectangle.empty();
	}

	default ScreenRectangle getBorderForArrowNavigation(ScreenDirection screenDirection) {
		return this.getRectangle().getBorder(screenDirection);
	}
}
