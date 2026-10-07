package net.minecraft.client.gui.screens.recipebook;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class RecipeBookPage {
	public static final int ITEMS_PER_PAGE = 20;
	private static final WidgetSprites PAGE_FORWARD_SPRITES = new WidgetSprites(
		Identifier.withDefaultNamespace("recipe_book/page_forward"), Identifier.withDefaultNamespace("recipe_book/page_forward_highlighted")
	);
	private static final WidgetSprites PAGE_BACKWARD_SPRITES = new WidgetSprites(
		Identifier.withDefaultNamespace("recipe_book/page_backward"), Identifier.withDefaultNamespace("recipe_book/page_backward_highlighted")
	);
	private static final Component NEXT_PAGE_TEXT = Component.translatable("gui.recipebook.next_page");
	private static final Component PREVIOUS_PAGE_TEXT = Component.translatable("gui.recipebook.previous_page");
	private static final int TURN_PAGE_SPRITE_WIDTH = 12;
	private static final int TURN_PAGE_SPRITE_HEIGHT = 17;
	private final List<RecipeButton> buttons = Lists.newArrayListWithCapacity(20);
	private @Nullable RecipeButton hoveredButton;
	private final OverlayRecipeComponent overlay;
	private Minecraft minecraft;
	private final RecipeBookComponent<?> parent;
	private List<RecipeCollection> recipeCollections = ImmutableList.of();
	private @Nullable ImageButton forwardButton;
	private @Nullable ImageButton backButton;
	private int totalPages;
	private int currentPage;
	private ClientRecipeBook recipeBook;
	private @Nullable RecipeDisplayId lastClickedRecipe;
	private @Nullable RecipeCollection lastClickedRecipeCollection;
	private boolean isFiltering;

	public RecipeBookPage(RecipeBookComponent<?> recipeBookComponent, SlotSelectTime slotSelectTime, boolean bl) {
		this.parent = recipeBookComponent;
		this.overlay = new OverlayRecipeComponent(slotSelectTime, bl);

		for (int i = 0; i < 20; i++) {
			this.buttons.add(new RecipeButton(slotSelectTime));
		}
	}

	public void init(Minecraft minecraft, int i, int j) {
		this.minecraft = minecraft;
		this.recipeBook = minecraft.player.getRecipeBook();

		for (int k = 0; k < this.buttons.size(); k++) {
			this.buttons.get(k).setPosition(i + 11 + 25 * (k % 5), j + 31 + 25 * (k / 5));
		}

		this.forwardButton = new ImageButton(i + 93, j + 137, 12, 17, PAGE_FORWARD_SPRITES, button -> this.updateArrowButtons(), NEXT_PAGE_TEXT);
		this.forwardButton.setTooltip(Tooltip.create(NEXT_PAGE_TEXT));
		this.backButton = new ImageButton(i + 38, j + 137, 12, 17, PAGE_BACKWARD_SPRITES, button -> this.updateArrowButtons(), PREVIOUS_PAGE_TEXT);
		this.backButton.setTooltip(Tooltip.create(PREVIOUS_PAGE_TEXT));
	}

	public void updateCollections(List<RecipeCollection> list, boolean bl, boolean bl2) {
		this.recipeCollections = list;
		this.isFiltering = bl2;
		this.totalPages = (int)Math.ceil(list.size() / 20.0);
		if (this.totalPages <= this.currentPage || bl) {
			this.currentPage = 0;
		}

		this.updateButtonsForPage();
	}

	private void updateButtonsForPage() {
		int i = 20 * this.currentPage;
		ContextMap contextMap = SlotDisplayContext.fromLevel(this.minecraft.level);

		for (int j = 0; j < this.buttons.size(); j++) {
			RecipeButton recipeButton = this.buttons.get(j);
			if (i + j < this.recipeCollections.size()) {
				RecipeCollection recipeCollection = this.recipeCollections.get(i + j);
				recipeButton.init(recipeCollection, this.isFiltering, this, contextMap);
				recipeButton.visible = true;
			} else {
				recipeButton.visible = false;
			}
		}

		this.updateArrowButtons();
	}

	private void updateArrowButtons() {
		if (this.forwardButton != null) {
			this.forwardButton.visible = this.totalPages > 1 && this.currentPage < this.totalPages - 1;
		}

		if (this.backButton != null) {
			this.backButton.visible = this.totalPages > 1 && this.currentPage > 0;
		}
	}

	public void render(GuiGraphics guiGraphics, int i, int j, int k, int l, float f) {
		if (this.totalPages > 1) {
			Component component = Component.translatable("gui.recipebook.page", this.currentPage + 1, this.totalPages);
			int m = this.minecraft.font.width(component);
			guiGraphics.drawString(this.minecraft.font, component, i - m / 2 + 73, j + 141, -1);
		}

		this.hoveredButton = null;

		for (RecipeButton recipeButton : this.buttons) {
			recipeButton.render(guiGraphics, k, l, f);
			if (recipeButton.visible && recipeButton.isHoveredOrFocused()) {
				this.hoveredButton = recipeButton;
			}
		}

		if (this.forwardButton != null) {
			this.forwardButton.render(guiGraphics, k, l, f);
		}

		if (this.backButton != null) {
			this.backButton.render(guiGraphics, k, l, f);
		}

		guiGraphics.nextStratum();
		this.overlay.render(guiGraphics, k, l, f);
	}

	public void renderTooltip(GuiGraphics guiGraphics, int i, int j) {
		if (this.minecraft.screen != null && this.hoveredButton != null && !this.overlay.isVisible()) {
			ItemStack itemStack = this.hoveredButton.getDisplayStack();
			Identifier identifier = itemStack.get(DataComponents.TOOLTIP_STYLE);
			guiGraphics.setComponentTooltipForNextFrame(this.minecraft.font, this.hoveredButton.getTooltipText(itemStack), i, j, identifier);
		}
	}

	public @Nullable RecipeDisplayId getLastClickedRecipe() {
		return this.lastClickedRecipe;
	}

	public @Nullable RecipeCollection getLastClickedRecipeCollection() {
		return this.lastClickedRecipeCollection;
	}

	public void setInvisible() {
		this.overlay.setVisible(false);
	}

	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, int i, int j, int k, int l, boolean bl) {
		this.lastClickedRecipe = null;
		this.lastClickedRecipeCollection = null;
		if (this.overlay.isVisible()) {
			if (this.overlay.mouseClicked(mouseButtonEvent, bl)) {
				this.lastClickedRecipe = this.overlay.getLastRecipeClicked();
				this.lastClickedRecipeCollection = this.overlay.getRecipeCollection();
			} else {
				this.overlay.setVisible(false);
			}

			return true;
		} else {
			if (this.forwardButton.mouseClicked(mouseButtonEvent, bl)) {
				this.currentPage++;
				this.updateButtonsForPage();
				return true;
			}

			if (this.backButton.mouseClicked(mouseButtonEvent, bl)) {
				this.currentPage--;
				this.updateButtonsForPage();
				return true;
			}

			ContextMap contextMap = SlotDisplayContext.fromLevel(this.minecraft.level);

			for (RecipeButton recipeButton : this.buttons) {
				if (recipeButton.mouseClicked(mouseButtonEvent, bl)) {
					if (mouseButtonEvent.button() == 0) {
						this.lastClickedRecipe = recipeButton.getCurrentRecipe();
						this.lastClickedRecipeCollection = recipeButton.getCollection();
					} else if (mouseButtonEvent.button() == 1 && !this.overlay.isVisible() && !recipeButton.isOnlyOption()) {
						this.overlay
							.init(
								recipeButton.getCollection(),
								contextMap,
								this.isFiltering,
								recipeButton.getX(),
								recipeButton.getY(),
								i + k / 2,
								j + 13 + l / 2,
								recipeButton.getWidth()
							);
					}

					return true;
				}
			}

			return false;
		}
	}

	public void recipeShown(RecipeDisplayId recipeDisplayId) {
		this.parent.recipeShown(recipeDisplayId);
	}

	public ClientRecipeBook getRecipeBook() {
		return this.recipeBook;
	}

	protected void listButtons(Consumer<AbstractWidget> consumer) {
		this.buttons.forEach(consumer);
	}
}
