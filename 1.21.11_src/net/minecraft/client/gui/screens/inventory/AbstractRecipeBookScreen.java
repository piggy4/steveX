package net.minecraft.client.gui.screens.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.display.RecipeDisplay;

@Environment(EnvType.CLIENT)
public abstract class AbstractRecipeBookScreen<T extends RecipeBookMenu> extends AbstractContainerScreen<T> implements RecipeUpdateListener {
	private final RecipeBookComponent<?> recipeBookComponent;
	private boolean widthTooNarrow;

	public AbstractRecipeBookScreen(T recipeBookMenu, RecipeBookComponent<?> recipeBookComponent, Inventory inventory, Component component) {
		super(recipeBookMenu, inventory, component);
		this.recipeBookComponent = recipeBookComponent;
	}

	@Override
	protected void init() {
		super.init();
		this.widthTooNarrow = this.width < 379;
		this.recipeBookComponent.init(this.width, this.height, this.minecraft, this.widthTooNarrow);
		this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
		this.initButton();
	}

	protected abstract ScreenPosition getRecipeBookButtonPosition();

	private void initButton() {
		ScreenPosition screenPosition = this.getRecipeBookButtonPosition();
		this.addRenderableWidget(new ImageButton(screenPosition.x(), screenPosition.y(), 20, 18, RecipeBookComponent.RECIPE_BUTTON_SPRITES, button -> {
			this.recipeBookComponent.toggleVisibility();
			this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
			ScreenPosition screenPositionx = this.getRecipeBookButtonPosition();
			button.setPosition(screenPositionx.x(), screenPositionx.y());
			this.onRecipeBookButtonClick();
		}));
		this.addWidget(this.recipeBookComponent);
	}

	protected void onRecipeBookButtonClick() {
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		if (this.recipeBookComponent.isVisible() && this.widthTooNarrow) {
			this.renderBackground(guiGraphics, i, j, f);
		} else {
			super.renderContents(guiGraphics, i, j, f);
		}

		guiGraphics.nextStratum();
		this.recipeBookComponent.render(guiGraphics, i, j, f);
		guiGraphics.nextStratum();
		this.renderCarriedItem(guiGraphics, i, j);
		this.renderSnapbackItem(guiGraphics);
		this.renderTooltip(guiGraphics, i, j);
		this.recipeBookComponent.renderTooltip(guiGraphics, i, j, this.hoveredSlot);
	}

	@Override
	protected void renderSlots(GuiGraphics guiGraphics, int i, int j) {
		super.renderSlots(guiGraphics, i, j);
		this.recipeBookComponent.renderGhostRecipe(guiGraphics, this.isBiggerResultSlot());
	}

	protected boolean isBiggerResultSlot() {
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent characterEvent) {
		return this.recipeBookComponent.charTyped(characterEvent) ? true : super.charTyped(characterEvent);
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		return this.recipeBookComponent.keyPressed(keyEvent) ? true : super.keyPressed(keyEvent);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		if (this.recipeBookComponent.mouseClicked(mouseButtonEvent, bl)) {
			this.setFocused(this.recipeBookComponent);
			return true;
		} else {
			return this.widthTooNarrow && this.recipeBookComponent.isVisible() ? true : super.mouseClicked(mouseButtonEvent, bl);
		}
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double d, double e) {
		return this.recipeBookComponent.mouseDragged(mouseButtonEvent, d, e) ? true : super.mouseDragged(mouseButtonEvent, d, e);
	}

	@Override
	protected boolean isHovering(int i, int j, int k, int l, double d, double e) {
		return (!this.widthTooNarrow || !this.recipeBookComponent.isVisible()) && super.isHovering(i, j, k, l, d, e);
	}

	@Override
	protected boolean hasClickedOutside(double d, double e, int i, int j) {
		boolean bl = d < i || e < j || d >= i + this.imageWidth || e >= j + this.imageHeight;
		return this.recipeBookComponent.hasClickedOutside(d, e, this.leftPos, this.topPos, this.imageWidth, this.imageHeight) && bl;
	}

	@Override
	protected void slotClicked(Slot slot, int i, int j, ClickType clickType) {
		super.slotClicked(slot, i, j, clickType);
		this.recipeBookComponent.slotClicked(slot);
	}

	@Override
	public void containerTick() {
		super.containerTick();
		this.recipeBookComponent.tick();
	}

	@Override
	public void recipesUpdated() {
		this.recipeBookComponent.recipesUpdated();
	}

	@Override
	public void fillGhostRecipe(RecipeDisplay recipeDisplay) {
		this.recipeBookComponent.fillGhostRecipe(recipeDisplay);
	}
}
