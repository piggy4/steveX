package net.minecraft.client.gui.screens.inventory;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

@Environment(EnvType.CLIENT)
public class StonecutterScreen extends AbstractContainerScreen<StonecutterMenu> {
	private static final Identifier SCROLLER_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/scroller");
	private static final Identifier SCROLLER_DISABLED_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/scroller_disabled");
	private static final Identifier RECIPE_SELECTED_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/recipe_selected");
	private static final Identifier RECIPE_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/recipe_highlighted");
	private static final Identifier RECIPE_SPRITE = Identifier.withDefaultNamespace("container/stonecutter/recipe");
	private static final Identifier BG_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/stonecutter.png");
	private static final int SCROLLER_WIDTH = 12;
	private static final int SCROLLER_HEIGHT = 15;
	private static final int RECIPES_COLUMNS = 4;
	private static final int RECIPES_ROWS = 3;
	private static final int RECIPES_IMAGE_SIZE_WIDTH = 16;
	private static final int RECIPES_IMAGE_SIZE_HEIGHT = 18;
	private static final int SCROLLER_FULL_HEIGHT = 54;
	private static final int RECIPES_X = 52;
	private static final int RECIPES_Y = 14;
	private float scrollOffs;
	private boolean scrolling;
	private int startIndex;
	private boolean displayRecipes;

	public StonecutterScreen(StonecutterMenu stonecutterMenu, Inventory inventory, Component component) {
		super(stonecutterMenu, inventory, component);
		stonecutterMenu.registerUpdateListener(this::containerChanged);
		this.titleLabelY--;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		super.render(guiGraphics, i, j, f);
		this.renderTooltip(guiGraphics, i, j);
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float f, int i, int j) {
		int k = this.leftPos;
		int l = this.topPos;
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, BG_LOCATION, k, l, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
		int m = (int)(41.0F * this.scrollOffs);
		Identifier identifier = this.isScrollBarActive() ? SCROLLER_SPRITE : SCROLLER_DISABLED_SPRITE;
		int n = k + 119;
		int o = l + 15 + m;
		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, identifier, n, o, 12, 15);
		if (i >= n && i < n + 12 && j >= o && j < o + 15) {
			guiGraphics.requestCursor(this.scrolling ? CursorTypes.RESIZE_NS : CursorTypes.POINTING_HAND);
		}

		int p = this.leftPos + 52;
		int q = this.topPos + 14;
		int r = this.startIndex + 12;
		this.renderButtons(guiGraphics, i, j, p, q, r);
		this.renderRecipes(guiGraphics, p, q, r);
	}

	@Override
	protected void renderTooltip(GuiGraphics guiGraphics, int i, int j) {
		super.renderTooltip(guiGraphics, i, j);
		if (this.displayRecipes) {
			int k = this.leftPos + 52;
			int l = this.topPos + 14;
			int m = this.startIndex + 12;
			SelectableRecipe.SingleInputSet<StonecutterRecipe> singleInputSet = this.menu.getVisibleRecipes();

			for (int n = this.startIndex; n < m && n < singleInputSet.size(); n++) {
				int o = n - this.startIndex;
				int p = k + o % 4 * 16;
				int q = l + o / 4 * 18 + 2;
				if (i >= p && i < p + 16 && j >= q && j < q + 18) {
					ContextMap contextMap = SlotDisplayContext.fromLevel(this.minecraft.level);
					SlotDisplay slotDisplay = singleInputSet.entries().get(n).recipe().optionDisplay();
					guiGraphics.setTooltipForNextFrame(this.font, slotDisplay.resolveForFirstStack(contextMap), i, j);
				}
			}
		}
	}

	private void renderButtons(GuiGraphics guiGraphics, int i, int j, int k, int l, int m) {
		for (int n = this.startIndex; n < m && n < this.menu.getNumberOfVisibleRecipes(); n++) {
			int o = n - this.startIndex;
			int p = k + o % 4 * 16;
			int q = o / 4;
			int r = l + q * 18 + 2;
			Identifier identifier;
			if (n == this.menu.getSelectedRecipeIndex()) {
				identifier = RECIPE_SELECTED_SPRITE;
			} else if (i >= p && j >= r && i < p + 16 && j < r + 18) {
				identifier = RECIPE_HIGHLIGHTED_SPRITE;
			} else {
				identifier = RECIPE_SPRITE;
			}

			int s = r - 1;
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, identifier, p, s, 16, 18);
			if (i >= p && j >= s && i < p + 16 && j < s + 18) {
				guiGraphics.requestCursor(CursorTypes.POINTING_HAND);
			}
		}
	}

	private void renderRecipes(GuiGraphics guiGraphics, int i, int j, int k) {
		SelectableRecipe.SingleInputSet<StonecutterRecipe> singleInputSet = this.menu.getVisibleRecipes();
		ContextMap contextMap = SlotDisplayContext.fromLevel(this.minecraft.level);

		for (int l = this.startIndex; l < k && l < singleInputSet.size(); l++) {
			int m = l - this.startIndex;
			int n = i + m % 4 * 16;
			int o = m / 4;
			int p = j + o * 18 + 2;
			SlotDisplay slotDisplay = singleInputSet.entries().get(l).recipe().optionDisplay();
			guiGraphics.renderItem(slotDisplay.resolveForFirstStack(contextMap), n, p);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		if (this.displayRecipes) {
			int i = this.leftPos + 52;
			int j = this.topPos + 14;
			int k = this.startIndex + 12;

			for (int l = this.startIndex; l < k; l++) {
				int m = l - this.startIndex;
				double d = mouseButtonEvent.x() - (i + m % 4 * 16);
				double e = mouseButtonEvent.y() - (j + m / 4 * 18);
				if (d >= 0.0 && e >= 0.0 && d < 16.0 && e < 18.0 && this.menu.clickMenuButton(this.minecraft.player, l)) {
					Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
					this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, l);
					return true;
				}
			}

			i = this.leftPos + 119;
			j = this.topPos + 9;
			if (mouseButtonEvent.x() >= i && mouseButtonEvent.x() < i + 12 && mouseButtonEvent.y() >= j && mouseButtonEvent.y() < j + 54) {
				this.scrolling = true;
			}
		}

		return super.mouseClicked(mouseButtonEvent, bl);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double d, double e) {
		if (this.scrolling && this.isScrollBarActive()) {
			int i = this.topPos + 14;
			int j = i + 54;
			this.scrollOffs = ((float)mouseButtonEvent.y() - i - 7.5F) / (j - i - 15.0F);
			this.scrollOffs = Mth.clamp(this.scrollOffs, 0.0F, 1.0F);
			this.startIndex = (int)(this.scrollOffs * this.getOffscreenRows() + 0.5) * 4;
			return true;
		} else {
			return super.mouseDragged(mouseButtonEvent, d, e);
		}
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
		this.scrolling = false;
		return super.mouseReleased(mouseButtonEvent);
	}

	@Override
	public boolean mouseScrolled(double d, double e, double f, double g) {
		if (super.mouseScrolled(d, e, f, g)) {
			return true;
		}

		if (this.isScrollBarActive()) {
			int i = this.getOffscreenRows();
			float h = (float)g / i;
			this.scrollOffs = Mth.clamp(this.scrollOffs - h, 0.0F, 1.0F);
			this.startIndex = (int)(this.scrollOffs * i + 0.5) * 4;
		}

		return true;
	}

	private boolean isScrollBarActive() {
		return this.displayRecipes && this.menu.getNumberOfVisibleRecipes() > 12;
	}

	protected int getOffscreenRows() {
		return (this.menu.getNumberOfVisibleRecipes() + 4 - 1) / 4 - 3;
	}

	private void containerChanged() {
		this.displayRecipes = this.menu.hasInputItem();
		if (!this.displayRecipes) {
			this.scrollOffs = 0.0F;
			this.startIndex = 0;
		}
	}
}
