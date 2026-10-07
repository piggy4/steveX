package net.minecraft.client.gui.contextualbar;

import com.mojang.blaze3d.platform.Window;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public interface ContextualBarRenderer {
	int WIDTH = 182;
	int HEIGHT = 5;
	int MARGIN_BOTTOM = 24;
	ContextualBarRenderer EMPTY = new ContextualBarRenderer() {
		@Override
		public void renderBackground(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		}

		@Override
		public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		}
	};

	default int left(Window window) {
		return (window.getGuiScaledWidth() - 182) / 2;
	}

	default int top(Window window) {
		return window.getGuiScaledHeight() - 24 - 5;
	}

	void renderBackground(GuiGraphics guiGraphics, DeltaTracker deltaTracker);

	void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker);

	static void renderExperienceLevel(GuiGraphics guiGraphics, Font font, int i) {
		Component component = Component.translatable("gui.experience.level", i);
		int j = (guiGraphics.guiWidth() - font.width(component)) / 2;
		int k = guiGraphics.guiHeight() - 24 - 9 - 2;
		guiGraphics.drawString(font, component, j + 1, k, -16777216, false);
		guiGraphics.drawString(font, component, j - 1, k, -16777216, false);
		guiGraphics.drawString(font, component, j, k + 1, -16777216, false);
		guiGraphics.drawString(font, component, j, k - 1, -16777216, false);
		guiGraphics.drawString(font, component, j, k, -8323296, false);
	}
}
