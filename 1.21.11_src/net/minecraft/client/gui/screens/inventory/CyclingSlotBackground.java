package net.minecraft.client.gui.screens.inventory;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

@Environment(EnvType.CLIENT)
public class CyclingSlotBackground {
	private static final int ICON_CHANGE_TICK_RATE = 30;
	private static final int ICON_SIZE = 16;
	private static final int ICON_TRANSITION_TICK_DURATION = 4;
	private final int slotIndex;
	private List<Identifier> icons = List.of();
	private int tick;
	private int iconIndex;

	public CyclingSlotBackground(int i) {
		this.slotIndex = i;
	}

	public void tick(List<Identifier> list) {
		if (!this.icons.equals(list)) {
			this.icons = list;
			this.iconIndex = 0;
		}

		if (!this.icons.isEmpty() && ++this.tick % 30 == 0) {
			this.iconIndex = (this.iconIndex + 1) % this.icons.size();
		}
	}

	public void render(AbstractContainerMenu abstractContainerMenu, GuiGraphics guiGraphics, float f, int i, int j) {
		Slot slot = abstractContainerMenu.getSlot(this.slotIndex);
		if (!this.icons.isEmpty() && !slot.hasItem()) {
			boolean bl = this.icons.size() > 1 && this.tick >= 30;
			float g = bl ? this.getIconTransitionTransparency(f) : 1.0F;
			if (g < 1.0F) {
				int k = Math.floorMod(this.iconIndex - 1, this.icons.size());
				this.renderIcon(slot, this.icons.get(k), 1.0F - g, guiGraphics, i, j);
			}

			this.renderIcon(slot, this.icons.get(this.iconIndex), g, guiGraphics, i, j);
		}
	}

	private void renderIcon(Slot slot, Identifier identifier, float f, GuiGraphics guiGraphics, int i, int j) {
		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, identifier, i + slot.x, j + slot.y, 16, 16, ARGB.white(f));
	}

	private float getIconTransitionTransparency(float f) {
		float g = this.tick % 30 + f;
		return Math.min(g, 4.0F) / 4.0F;
	}
}
