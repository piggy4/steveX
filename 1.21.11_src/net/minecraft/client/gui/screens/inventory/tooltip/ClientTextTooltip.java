package net.minecraft.client.gui.screens.inventory.tooltip;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

@Environment(EnvType.CLIENT)
public class ClientTextTooltip implements ClientTooltipComponent {
	private final FormattedCharSequence text;

	public ClientTextTooltip(FormattedCharSequence formattedCharSequence) {
		this.text = formattedCharSequence;
	}

	@Override
	public int getWidth(Font font) {
		return font.width(this.text);
	}

	@Override
	public int getHeight(Font font) {
		return 10;
	}

	@Override
	public void renderText(GuiGraphics guiGraphics, Font font, int i, int j) {
		guiGraphics.drawString(font, this.text, i, j, -1, true);
	}
}
