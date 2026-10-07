package net.minecraft.client.gui.screens.inventory.tooltip;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

@Environment(EnvType.CLIENT)
public interface ClientTooltipComponent {
	static ClientTooltipComponent create(FormattedCharSequence formattedCharSequence) {
		return new ClientTextTooltip(formattedCharSequence);
	}

	static ClientTooltipComponent create(TooltipComponent tooltipComponent) {
		return switch (tooltipComponent) {
			case BundleTooltip bundleTooltip -> new ClientBundleTooltip(bundleTooltip.contents());
			case ClientActivePlayersTooltip.ActivePlayersTooltip activePlayersTooltip -> new ClientActivePlayersTooltip(activePlayersTooltip);
			default -> throw new IllegalArgumentException("Unknown TooltipComponent");
		};
	}

	int getHeight(Font font);

	int getWidth(Font font);

	default boolean showTooltipWithItemInHand() {
		return false;
	}

	default void renderText(GuiGraphics guiGraphics, Font font, int i, int j) {
	}

	default void renderImage(Font font, int i, int j, int k, int l, GuiGraphics guiGraphics) {
	}
}
