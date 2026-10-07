package net.minecraft.client.gui.components;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public class ItemDisplayWidget extends AbstractWidget {
	private final Minecraft minecraft;
	private final int offsetX;
	private final int offsetY;
	private final ItemStack itemStack;
	private final boolean decorations;
	private final boolean tooltip;

	public ItemDisplayWidget(Minecraft minecraft, int i, int j, int k, int l, Component component, ItemStack itemStack, boolean bl, boolean bl2) {
		super(0, 0, k, l, component);
		this.minecraft = minecraft;
		this.offsetX = i;
		this.offsetY = j;
		this.itemStack = itemStack;
		this.decorations = bl;
		this.tooltip = bl2;
	}

	@Override
	protected void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
		guiGraphics.renderItem(this.itemStack, this.getX() + this.offsetX, this.getY() + this.offsetY, 0);
		if (this.decorations) {
			guiGraphics.renderItemDecorations(this.minecraft.font, this.itemStack, this.getX() + this.offsetX, this.getY() + this.offsetY, null);
		}

		if (this.isFocused()) {
			guiGraphics.renderOutline(this.getX(), this.getY(), this.getWidth(), this.getHeight(), -1);
		}

		if (this.tooltip && this.isHoveredOrFocused()) {
			this.renderTooltip(guiGraphics, i, j);
		}
	}

	protected void renderTooltip(GuiGraphics guiGraphics, int i, int j) {
		guiGraphics.setTooltipForNextFrame(this.minecraft.font, this.itemStack, i, j);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
		narrationElementOutput.add(NarratedElementType.TITLE, Component.translatable("narration.item", this.itemStack.getHoverName()));
	}
}
