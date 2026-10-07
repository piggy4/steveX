package net.minecraft.client.gui.screens.advancements;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
enum AdvancementTabType {
	ABOVE(
		new AdvancementTabType.Sprites(
			Identifier.withDefaultNamespace("advancements/tab_above_left_selected"),
			Identifier.withDefaultNamespace("advancements/tab_above_middle_selected"),
			Identifier.withDefaultNamespace("advancements/tab_above_right_selected")
		),
		new AdvancementTabType.Sprites(
			Identifier.withDefaultNamespace("advancements/tab_above_left"),
			Identifier.withDefaultNamespace("advancements/tab_above_middle"),
			Identifier.withDefaultNamespace("advancements/tab_above_right")
		),
		28,
		32,
		8
	),
	BELOW(
		new AdvancementTabType.Sprites(
			Identifier.withDefaultNamespace("advancements/tab_below_left_selected"),
			Identifier.withDefaultNamespace("advancements/tab_below_middle_selected"),
			Identifier.withDefaultNamespace("advancements/tab_below_right_selected")
		),
		new AdvancementTabType.Sprites(
			Identifier.withDefaultNamespace("advancements/tab_below_left"),
			Identifier.withDefaultNamespace("advancements/tab_below_middle"),
			Identifier.withDefaultNamespace("advancements/tab_below_right")
		),
		28,
		32,
		8
	),
	LEFT(
		new AdvancementTabType.Sprites(
			Identifier.withDefaultNamespace("advancements/tab_left_top_selected"),
			Identifier.withDefaultNamespace("advancements/tab_left_middle_selected"),
			Identifier.withDefaultNamespace("advancements/tab_left_bottom_selected")
		),
		new AdvancementTabType.Sprites(
			Identifier.withDefaultNamespace("advancements/tab_left_top"),
			Identifier.withDefaultNamespace("advancements/tab_left_middle"),
			Identifier.withDefaultNamespace("advancements/tab_left_bottom")
		),
		32,
		28,
		5
	),
	RIGHT(
		new AdvancementTabType.Sprites(
			Identifier.withDefaultNamespace("advancements/tab_right_top_selected"),
			Identifier.withDefaultNamespace("advancements/tab_right_middle_selected"),
			Identifier.withDefaultNamespace("advancements/tab_right_bottom_selected")
		),
		new AdvancementTabType.Sprites(
			Identifier.withDefaultNamespace("advancements/tab_right_top"),
			Identifier.withDefaultNamespace("advancements/tab_right_middle"),
			Identifier.withDefaultNamespace("advancements/tab_right_bottom")
		),
		32,
		28,
		5
	);

	private final AdvancementTabType.Sprites selectedSprites;
	private final AdvancementTabType.Sprites unselectedSprites;
	private final int width;
	private final int height;
	private final int max;

	AdvancementTabType(final AdvancementTabType.Sprites sprites, final AdvancementTabType.Sprites sprites2, final int j, final int k, final int l) {
		this.selectedSprites = sprites;
		this.unselectedSprites = sprites2;
		this.width = j;
		this.height = k;
		this.max = l;
	}

	public int getWidth() {
		return this.width;
	}

	public int getHeight() {
		return this.height;
	}

	public int getMax() {
		return this.max;
	}

	public void draw(GuiGraphics guiGraphics, int i, int j, boolean bl, int k) {
		AdvancementTabType.Sprites sprites = bl ? this.selectedSprites : this.unselectedSprites;
		Identifier identifier;
		if (k == 0) {
			identifier = sprites.first();
		} else if (k == this.max - 1) {
			identifier = sprites.last();
		} else {
			identifier = sprites.middle();
		}

		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, identifier, i, j, this.width, this.height);
	}

	public void drawIcon(GuiGraphics guiGraphics, int i, int j, int k, ItemStack itemStack) {
		int l = i + this.getX(k);
		int m = j + this.getY(k);
		switch (this) {
			case ABOVE:
				l += 6;
				m += 9;
				break;
			case BELOW:
				l += 6;
				m += 6;
				break;
			case LEFT:
				l += 10;
				m += 5;
				break;
			case RIGHT:
				l += 6;
				m += 5;
		}

		guiGraphics.renderFakeItem(itemStack, l, m);
	}

	public int getX(int i) {
		switch (this) {
			case ABOVE:
				return (this.width + 4) * i;
			case BELOW:
				return (this.width + 4) * i;
			case LEFT:
				return -this.width + 4;
			case RIGHT:
				return 248;
			default:
				throw new UnsupportedOperationException("Don't know what this tab type is!" + this);
		}
	}

	public int getY(int i) {
		switch (this) {
			case ABOVE:
				return -this.height + 4;
			case BELOW:
				return 136;
			case LEFT:
				return this.height * i;
			case RIGHT:
				return this.height * i;
			default:
				throw new UnsupportedOperationException("Don't know what this tab type is!" + this);
		}
	}

	public boolean isMouseOver(int i, int j, int k, double d, double e) {
		int l = i + this.getX(k);
		int m = j + this.getY(k);
		return d > l && d < l + this.width && e > m && e < m + this.height;
	}

	@Environment(EnvType.CLIENT)
	record Sprites(Identifier first, Identifier middle, Identifier last) {
	}
}
