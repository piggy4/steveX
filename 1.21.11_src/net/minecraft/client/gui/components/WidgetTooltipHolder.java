package net.minecraft.client.gui.components;

import java.time.Duration;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.tooltip.BelowOrAboveWidgetTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.MenuTooltipPositioner;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class WidgetTooltipHolder {
	private @Nullable Tooltip tooltip;
	private Duration delay = Duration.ZERO;
	private long displayStartTime;
	private boolean wasDisplayed;

	public void setDelay(Duration duration) {
		this.delay = duration;
	}

	public void set(@Nullable Tooltip tooltip) {
		this.tooltip = tooltip;
	}

	public @Nullable Tooltip get() {
		return this.tooltip;
	}

	public void refreshTooltipForNextRenderPass(GuiGraphics guiGraphics, int i, int j, boolean bl, boolean bl2, ScreenRectangle screenRectangle) {
		if (this.tooltip == null) {
			this.wasDisplayed = false;
		} else {
			Minecraft minecraft = Minecraft.getInstance();
			boolean bl3 = bl || bl2 && minecraft.getLastInputType().isKeyboard();
			if (bl3 != this.wasDisplayed) {
				if (bl3) {
					this.displayStartTime = Util.getMillis();
				}

				this.wasDisplayed = bl3;
			}

			if (bl3 && Util.getMillis() - this.displayStartTime > this.delay.toMillis()) {
				guiGraphics.setTooltipForNextFrame(
					minecraft.font, this.tooltip.toCharSequence(minecraft), this.createTooltipPositioner(screenRectangle, bl, bl2), i, j, bl2
				);
			}
		}
	}

	private ClientTooltipPositioner createTooltipPositioner(ScreenRectangle screenRectangle, boolean bl, boolean bl2) {
		return !bl && bl2 && Minecraft.getInstance().getLastInputType().isKeyboard()
			? new BelowOrAboveWidgetTooltipPositioner(screenRectangle)
			: new MenuTooltipPositioner(screenRectangle);
	}

	public void updateNarration(NarrationElementOutput narrationElementOutput) {
		if (this.tooltip != null) {
			this.tooltip.updateNarration(narrationElementOutput);
		}
	}
}
