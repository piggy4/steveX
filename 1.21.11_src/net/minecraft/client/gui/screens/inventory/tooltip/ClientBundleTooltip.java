package net.minecraft.client.gui.screens.inventory.tooltip;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.apache.commons.lang3.math.Fraction;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ClientBundleTooltip implements ClientTooltipComponent {
	private static final Identifier PROGRESSBAR_BORDER_SPRITE = Identifier.withDefaultNamespace("container/bundle/bundle_progressbar_border");
	private static final Identifier PROGRESSBAR_FILL_SPRITE = Identifier.withDefaultNamespace("container/bundle/bundle_progressbar_fill");
	private static final Identifier PROGRESSBAR_FULL_SPRITE = Identifier.withDefaultNamespace("container/bundle/bundle_progressbar_full");
	private static final Identifier SLOT_HIGHLIGHT_BACK_SPRITE = Identifier.withDefaultNamespace("container/bundle/slot_highlight_back");
	private static final Identifier SLOT_HIGHLIGHT_FRONT_SPRITE = Identifier.withDefaultNamespace("container/bundle/slot_highlight_front");
	private static final Identifier SLOT_BACKGROUND_SPRITE = Identifier.withDefaultNamespace("container/bundle/slot_background");
	private static final int SLOT_MARGIN = 4;
	private static final int SLOT_SIZE = 24;
	private static final int GRID_WIDTH = 96;
	private static final int PROGRESSBAR_HEIGHT = 13;
	private static final int PROGRESSBAR_WIDTH = 96;
	private static final int PROGRESSBAR_BORDER = 1;
	private static final int PROGRESSBAR_FILL_MAX = 94;
	private static final int PROGRESSBAR_MARGIN_Y = 4;
	private static final Component BUNDLE_FULL_TEXT = Component.translatable("item.minecraft.bundle.full");
	private static final Component BUNDLE_EMPTY_TEXT = Component.translatable("item.minecraft.bundle.empty");
	private static final Component BUNDLE_EMPTY_DESCRIPTION = Component.translatable("item.minecraft.bundle.empty.description");
	private final BundleContents contents;

	public ClientBundleTooltip(BundleContents bundleContents) {
		this.contents = bundleContents;
	}

	@Override
	public int getHeight(Font font) {
		return this.contents.isEmpty() ? getEmptyBundleBackgroundHeight(font) : this.backgroundHeight();
	}

	@Override
	public int getWidth(Font font) {
		return 96;
	}

	@Override
	public boolean showTooltipWithItemInHand() {
		return true;
	}

	private static int getEmptyBundleBackgroundHeight(Font font) {
		return getEmptyBundleDescriptionTextHeight(font) + 13 + 8;
	}

	private int backgroundHeight() {
		return this.itemGridHeight() + 13 + 8;
	}

	private int itemGridHeight() {
		return this.gridSizeY() * 24;
	}

	private int getContentXOffset(int i) {
		return (i - 96) / 2;
	}

	private int gridSizeY() {
		return Mth.positiveCeilDiv(this.slotCount(), 4);
	}

	private int slotCount() {
		return Math.min(12, this.contents.size());
	}

	@Override
	public void renderImage(Font font, int i, int j, int k, int l, GuiGraphics guiGraphics) {
		if (this.contents.isEmpty()) {
			this.renderEmptyBundleTooltip(font, i, j, k, l, guiGraphics);
		} else {
			this.renderBundleWithItemsTooltip(font, i, j, k, l, guiGraphics);
		}
	}

	private void renderEmptyBundleTooltip(Font font, int i, int j, int k, int l, GuiGraphics guiGraphics) {
		drawEmptyBundleDescriptionText(i + this.getContentXOffset(k), j, font, guiGraphics);
		this.drawProgressbar(i + this.getContentXOffset(k), j + getEmptyBundleDescriptionTextHeight(font) + 4, font, guiGraphics);
	}

	private void renderBundleWithItemsTooltip(Font font, int i, int j, int k, int l, GuiGraphics guiGraphics) {
		boolean bl = this.contents.size() > 12;
		List<ItemStack> list = this.getShownItems(this.contents.getNumberOfItemsToShow());
		int m = i + this.getContentXOffset(k) + 96;
		int n = j + this.gridSizeY() * 24;
		int o = 1;

		for (int p = 1; p <= this.gridSizeY(); p++) {
			for (int q = 1; q <= 4; q++) {
				int r = m - q * 24;
				int s = n - p * 24;
				if (shouldRenderSurplusText(bl, q, p)) {
					renderCount(r, s, this.getAmountOfHiddenItems(list), font, guiGraphics);
				} else if (shouldRenderItemSlot(list, o)) {
					this.renderSlot(o, r, s, list, o, font, guiGraphics);
					o++;
				}
			}
		}

		this.drawSelectedItemTooltip(font, guiGraphics, i, j, k);
		this.drawProgressbar(i + this.getContentXOffset(k), j + this.itemGridHeight() + 4, font, guiGraphics);
	}

	private List<ItemStack> getShownItems(int i) {
		int j = Math.min(this.contents.size(), i);
		return this.contents.itemCopyStream().toList().subList(0, j);
	}

	private static boolean shouldRenderSurplusText(boolean bl, int i, int j) {
		return bl && i * j == 1;
	}

	private static boolean shouldRenderItemSlot(List<ItemStack> list, int i) {
		return list.size() >= i;
	}

	private int getAmountOfHiddenItems(List<ItemStack> list) {
		return this.contents.itemCopyStream().skip(list.size()).mapToInt(ItemStack::getCount).sum();
	}

	private void renderSlot(int i, int j, int k, List<ItemStack> list, int l, Font font, GuiGraphics guiGraphics) {
		int m = list.size() - i;
		boolean bl = m == this.contents.getSelectedItem();
		ItemStack itemStack = list.get(m);
		if (bl) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_BACK_SPRITE, j, k, 24, 24);
		} else {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_BACKGROUND_SPRITE, j, k, 24, 24);
		}

		guiGraphics.renderItem(itemStack, j + 4, k + 4, l);
		guiGraphics.renderItemDecorations(font, itemStack, j + 4, k + 4);
		if (bl) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_FRONT_SPRITE, j, k, 24, 24);
		}
	}

	private static void renderCount(int i, int j, int k, Font font, GuiGraphics guiGraphics) {
		guiGraphics.drawCenteredString(font, "+" + k, i + 12, j + 10, -1);
	}

	private void drawSelectedItemTooltip(Font font, GuiGraphics guiGraphics, int i, int j, int k) {
		if (this.contents.hasSelectedItem()) {
			ItemStack itemStack = this.contents.getItemUnsafe(this.contents.getSelectedItem());
			Component component = itemStack.getStyledHoverName();
			int l = font.width(component.getVisualOrderText());
			int m = i + k / 2 - 12;
			ClientTooltipComponent clientTooltipComponent = ClientTooltipComponent.create(component.getVisualOrderText());
			guiGraphics.renderTooltip(
				font, List.of(clientTooltipComponent), m - l / 2, j - 15, DefaultTooltipPositioner.INSTANCE, itemStack.get(DataComponents.TOOLTIP_STYLE)
			);
		}
	}

	private void drawProgressbar(int i, int j, Font font, GuiGraphics guiGraphics) {
		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.getProgressBarTexture(), i + 1, j, this.getProgressBarFill(), 13);
		guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESSBAR_BORDER_SPRITE, i, j, 96, 13);
		Component component = this.getProgressBarFillText();
		if (component != null) {
			guiGraphics.drawCenteredString(font, component, i + 48, j + 3, -1);
		}
	}

	private static void drawEmptyBundleDescriptionText(int i, int j, Font font, GuiGraphics guiGraphics) {
		guiGraphics.drawWordWrap(font, BUNDLE_EMPTY_DESCRIPTION, i, j, 96, -5592406);
	}

	private static int getEmptyBundleDescriptionTextHeight(Font font) {
		return font.split(BUNDLE_EMPTY_DESCRIPTION, 96).size() * 9;
	}

	private int getProgressBarFill() {
		return Mth.clamp(Mth.mulAndTruncate(this.contents.weight(), 94), 0, 94);
	}

	private Identifier getProgressBarTexture() {
		return this.contents.weight().compareTo(Fraction.ONE) >= 0 ? PROGRESSBAR_FULL_SPRITE : PROGRESSBAR_FILL_SPRITE;
	}

	private @Nullable Component getProgressBarFillText() {
		if (this.contents.isEmpty()) {
			return BUNDLE_EMPTY_TEXT;
		} else {
			return this.contents.weight().compareTo(Fraction.ONE) >= 0 ? BUNDLE_FULL_TEXT : null;
		}
	}
}
