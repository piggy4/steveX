package net.minecraft.client.gui.screens.inventory;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

@Environment(EnvType.CLIENT)
public class MerchantScreen extends AbstractContainerScreen<MerchantMenu> {
	private static final Identifier OUT_OF_STOCK_SPRITE = Identifier.withDefaultNamespace("container/villager/out_of_stock");
	private static final Identifier EXPERIENCE_BAR_BACKGROUND_SPRITE = Identifier.withDefaultNamespace("container/villager/experience_bar_background");
	private static final Identifier EXPERIENCE_BAR_CURRENT_SPRITE = Identifier.withDefaultNamespace("container/villager/experience_bar_current");
	private static final Identifier EXPERIENCE_BAR_RESULT_SPRITE = Identifier.withDefaultNamespace("container/villager/experience_bar_result");
	private static final Identifier SCROLLER_SPRITE = Identifier.withDefaultNamespace("container/villager/scroller");
	private static final Identifier SCROLLER_DISABLED_SPRITE = Identifier.withDefaultNamespace("container/villager/scroller_disabled");
	private static final Identifier TRADE_ARROW_OUT_OF_STOCK_SPRITE = Identifier.withDefaultNamespace("container/villager/trade_arrow_out_of_stock");
	private static final Identifier TRADE_ARROW_SPRITE = Identifier.withDefaultNamespace("container/villager/trade_arrow");
	private static final Identifier DISCOUNT_STRIKETHRUOGH_SPRITE = Identifier.withDefaultNamespace("container/villager/discount_strikethrough");
	private static final Identifier VILLAGER_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/villager.png");
	private static final int TEXTURE_WIDTH = 512;
	private static final int TEXTURE_HEIGHT = 256;
	private static final int MERCHANT_MENU_PART_X = 99;
	private static final int PROGRESS_BAR_X = 136;
	private static final int PROGRESS_BAR_Y = 16;
	private static final int SELL_ITEM_1_X = 5;
	private static final int SELL_ITEM_2_X = 35;
	private static final int BUY_ITEM_X = 68;
	private static final int LABEL_Y = 6;
	private static final int NUMBER_OF_OFFER_BUTTONS = 7;
	private static final int TRADE_BUTTON_X = 5;
	private static final int TRADE_BUTTON_HEIGHT = 20;
	private static final int TRADE_BUTTON_WIDTH = 88;
	private static final int SCROLLER_HEIGHT = 27;
	private static final int SCROLLER_WIDTH = 6;
	private static final int SCROLL_BAR_HEIGHT = 139;
	private static final int SCROLL_BAR_TOP_POS_Y = 18;
	private static final int SCROLL_BAR_START_X = 94;
	private static final Component TRADES_LABEL = Component.translatable("merchant.trades");
	private static final Component DEPRECATED_TOOLTIP = Component.translatable("merchant.deprecated");
	private int shopItem;
	private final MerchantScreen.TradeOfferButton[] tradeOfferButtons = new MerchantScreen.TradeOfferButton[7];
	int scrollOff;
	private boolean isDragging;

	public MerchantScreen(MerchantMenu merchantMenu, Inventory inventory, Component component) {
		super(merchantMenu, inventory, component);
		this.imageWidth = 276;
		this.inventoryLabelX = 107;
	}

	private void postButtonClick() {
		this.menu.setSelectionHint(this.shopItem);
		this.menu.tryMoveItems(this.shopItem);
		this.minecraft.getConnection().send(new ServerboundSelectTradePacket(this.shopItem));
	}

	@Override
	protected void init() {
		super.init();
		int i = (this.width - this.imageWidth) / 2;
		int j = (this.height - this.imageHeight) / 2;
		int k = j + 16 + 2;

		for (int l = 0; l < 7; l++) {
			this.tradeOfferButtons[l] = this.addRenderableWidget(new MerchantScreen.TradeOfferButton(i + 5, k, l, button -> {
				if (button instanceof MerchantScreen.TradeOfferButton) {
					this.shopItem = ((MerchantScreen.TradeOfferButton)button).getIndex() + this.scrollOff;
					this.postButtonClick();
				}
			}));
			k += 20;
		}
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int i, int j) {
		int k = this.menu.getTraderLevel();
		if (k > 0 && k <= 5 && this.menu.showProgressBar()) {
			Component component = Component.translatable("merchant.title", this.title, Component.translatable("merchant.level." + k));
			int l = this.font.width(component);
			int m = 49 + this.imageWidth / 2 - l / 2;
			guiGraphics.drawString(this.font, component, m, 6, -12566464, false);
		} else {
			guiGraphics.drawString(this.font, this.title, 49 + this.imageWidth / 2 - this.font.width(this.title) / 2, 6, -12566464, false);
		}

		guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, -12566464, false);
		int n = this.font.width(TRADES_LABEL);
		guiGraphics.drawString(this.font, TRADES_LABEL, 5 - n / 2 + 48, 6, -12566464, false);
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float f, int i, int j) {
		int k = (this.width - this.imageWidth) / 2;
		int l = (this.height - this.imageHeight) / 2;
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, VILLAGER_LOCATION, k, l, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 512, 256);
		MerchantOffers merchantOffers = this.menu.getOffers();
		if (!merchantOffers.isEmpty()) {
			int m = this.shopItem;
			if (m < 0 || m >= merchantOffers.size()) {
				return;
			}

			MerchantOffer merchantOffer = merchantOffers.get(m);
			if (merchantOffer.isOutOfStock()) {
				guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, OUT_OF_STOCK_SPRITE, this.leftPos + 83 + 99, this.topPos + 35, 28, 21);
			}
		}
	}

	private void renderProgressBar(GuiGraphics guiGraphics, int i, int j, MerchantOffer merchantOffer) {
		int k = this.menu.getTraderLevel();
		int l = this.menu.getTraderXp();
		if (k < 5) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BAR_BACKGROUND_SPRITE, i + 136, j + 16, 102, 5);
			int m = VillagerData.getMinXpPerLevel(k);
			if (l >= m && VillagerData.canLevelUp(k)) {
				int n = 102;
				float f = 102.0F / (VillagerData.getMaxXpPerLevel(k) - m);
				int o = Math.min(Mth.floor(f * (l - m)), 102);
				guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BAR_CURRENT_SPRITE, 102, 5, 0, 0, i + 136, j + 16, o, 5);
				int p = this.menu.getFutureTraderXp();
				if (p > 0) {
					int q = Math.min(Mth.floor(p * f), 102 - o);
					guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, EXPERIENCE_BAR_RESULT_SPRITE, 102, 5, o, 0, i + 136 + o, j + 16, q, 5);
				}
			}
		}
	}

	private void renderScroller(GuiGraphics guiGraphics, int i, int j, int k, int l, MerchantOffers merchantOffers) {
		int m = merchantOffers.size() + 1 - 7;
		if (m > 1) {
			int n = 139 - (27 + (m - 1) * 139 / m);
			int o = 1 + n / m + 139 / m;
			int p = 113;
			int q = Math.min(113, this.scrollOff * o);
			if (this.scrollOff == m - 1) {
				q = 113;
			}

			int r = i + 94;
			int s = j + 18 + q;
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_SPRITE, r, s, 6, 27);
			if (k >= r && k < i + 94 + 6 && l >= s && l <= s + 27) {
				guiGraphics.requestCursor(this.isDragging ? CursorTypes.RESIZE_NS : CursorTypes.POINTING_HAND);
			}
		} else {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_DISABLED_SPRITE, i + 94, j + 18, 6, 27);
		}
	}

	@Override
	public void renderContents(GuiGraphics guiGraphics, int i, int j, float f) {
		super.renderContents(guiGraphics, i, j, f);
		MerchantOffers merchantOffers = this.menu.getOffers();
		if (!merchantOffers.isEmpty()) {
			int k = (this.width - this.imageWidth) / 2;
			int l = (this.height - this.imageHeight) / 2;
			int m = l + 16 + 1;
			int n = k + 5 + 5;
			this.renderScroller(guiGraphics, k, l, i, j, merchantOffers);
			int o = 0;

			for (MerchantOffer merchantOffer : merchantOffers) {
				if (!this.canScroll(merchantOffers.size()) || o >= this.scrollOff && o < 7 + this.scrollOff) {
					ItemStack itemStack = merchantOffer.getBaseCostA();
					ItemStack itemStack2 = merchantOffer.getCostA();
					ItemStack itemStack3 = merchantOffer.getCostB();
					ItemStack itemStack4 = merchantOffer.getResult();
					int p = m + 2;
					this.renderAndDecorateCostA(guiGraphics, itemStack2, itemStack, n, p);
					if (!itemStack3.isEmpty()) {
						guiGraphics.renderFakeItem(itemStack3, k + 5 + 35, p);
						guiGraphics.renderItemDecorations(this.font, itemStack3, k + 5 + 35, p);
					}

					this.renderButtonArrows(guiGraphics, merchantOffer, k, p);
					guiGraphics.renderFakeItem(itemStack4, k + 5 + 68, p);
					guiGraphics.renderItemDecorations(this.font, itemStack4, k + 5 + 68, p);
					m += 20;
					o++;
				} else {
					o++;
				}
			}

			int q = this.shopItem;
			MerchantOffer merchantOffer = merchantOffers.get(q);
			if (this.menu.showProgressBar()) {
				this.renderProgressBar(guiGraphics, k, l, merchantOffer);
			}

			if (merchantOffer.isOutOfStock() && this.isHovering(186, 35, 22, 21, i, j) && this.menu.canRestock()) {
				guiGraphics.setTooltipForNextFrame(this.font, DEPRECATED_TOOLTIP, i, j);
			}

			for (MerchantScreen.TradeOfferButton tradeOfferButton : this.tradeOfferButtons) {
				if (tradeOfferButton.isHoveredOrFocused()) {
					tradeOfferButton.renderToolTip(guiGraphics, i, j);
				}

				tradeOfferButton.visible = tradeOfferButton.index < this.menu.getOffers().size();
			}
		}

		this.renderTooltip(guiGraphics, i, j);
	}

	private void renderButtonArrows(GuiGraphics guiGraphics, MerchantOffer merchantOffer, int i, int j) {
		if (merchantOffer.isOutOfStock()) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, TRADE_ARROW_OUT_OF_STOCK_SPRITE, i + 5 + 35 + 20, j + 3, 10, 9);
		} else {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, TRADE_ARROW_SPRITE, i + 5 + 35 + 20, j + 3, 10, 9);
		}
	}

	private void renderAndDecorateCostA(GuiGraphics guiGraphics, ItemStack itemStack, ItemStack itemStack2, int i, int j) {
		guiGraphics.renderFakeItem(itemStack, i, j);
		if (itemStack2.getCount() == itemStack.getCount()) {
			guiGraphics.renderItemDecorations(this.font, itemStack, i, j);
		} else {
			guiGraphics.renderItemDecorations(this.font, itemStack2, i, j, itemStack2.getCount() == 1 ? "1" : null);
			guiGraphics.renderItemDecorations(this.font, itemStack, i + 14, j, itemStack.getCount() == 1 ? "1" : null);
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, DISCOUNT_STRIKETHRUOGH_SPRITE, i + 7, j + 12, 9, 2);
		}
	}

	private boolean canScroll(int i) {
		return i > 7;
	}

	@Override
	public boolean mouseScrolled(double d, double e, double f, double g) {
		if (super.mouseScrolled(d, e, f, g)) {
			return true;
		}

		int i = this.menu.getOffers().size();
		if (this.canScroll(i)) {
			int j = i - 7;
			this.scrollOff = Mth.clamp((int)(this.scrollOff - g), 0, j);
		}

		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double d, double e) {
		int i = this.menu.getOffers().size();
		if (this.isDragging) {
			int j = this.topPos + 18;
			int k = j + 139;
			int l = i - 7;
			float f = ((float)mouseButtonEvent.y() - j - 13.5F) / (k - j - 27.0F);
			f = f * l + 0.5F;
			this.scrollOff = Mth.clamp((int)f, 0, l);
			return true;
		} else {
			return super.mouseDragged(mouseButtonEvent, d, e);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		int i = (this.width - this.imageWidth) / 2;
		int j = (this.height - this.imageHeight) / 2;
		if (this.canScroll(this.menu.getOffers().size())
			&& mouseButtonEvent.x() > i + 94
			&& mouseButtonEvent.x() < i + 94 + 6
			&& mouseButtonEvent.y() > j + 18
			&& mouseButtonEvent.y() <= j + 18 + 139 + 1) {
			this.isDragging = true;
		}

		return super.mouseClicked(mouseButtonEvent, bl);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
		this.isDragging = false;
		return super.mouseReleased(mouseButtonEvent);
	}

	@Environment(EnvType.CLIENT)
	class TradeOfferButton extends Button.Plain {
		final int index;

		public TradeOfferButton(final int i, final int j, final int k, final Button.OnPress onPress) {
			super(i, j, 88, 20, CommonComponents.EMPTY, onPress, DEFAULT_NARRATION);
			this.index = k;
			this.visible = false;
		}

		public int getIndex() {
			return this.index;
		}

		public void renderToolTip(GuiGraphics guiGraphics, int i, int j) {
			if (this.isHovered && MerchantScreen.this.menu.getOffers().size() > this.index + MerchantScreen.this.scrollOff) {
				if (i < this.getX() + 20) {
					ItemStack itemStack = MerchantScreen.this.menu.getOffers().get(this.index + MerchantScreen.this.scrollOff).getCostA();
					guiGraphics.setTooltipForNextFrame(MerchantScreen.this.font, itemStack, i, j);
				} else if (i < this.getX() + 50 && i > this.getX() + 30) {
					ItemStack itemStack = MerchantScreen.this.menu.getOffers().get(this.index + MerchantScreen.this.scrollOff).getCostB();
					if (!itemStack.isEmpty()) {
						guiGraphics.setTooltipForNextFrame(MerchantScreen.this.font, itemStack, i, j);
					}
				} else if (i > this.getX() + 65) {
					ItemStack itemStack = MerchantScreen.this.menu.getOffers().get(this.index + MerchantScreen.this.scrollOff).getResult();
					guiGraphics.setTooltipForNextFrame(MerchantScreen.this.font, itemStack, i, j);
				}
			}
		}
	}
}
