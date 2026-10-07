package net.minecraft.client.gui.screens.inventory;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.BundleMouseActions;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.ItemSlotMouseAction;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2i;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class AbstractContainerScreen<T extends AbstractContainerMenu> extends Screen implements MenuAccess<T> {
	public static final Identifier INVENTORY_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/inventory.png");
	private static final Identifier SLOT_HIGHLIGHT_BACK_SPRITE = Identifier.withDefaultNamespace("container/slot_highlight_back");
	private static final Identifier SLOT_HIGHLIGHT_FRONT_SPRITE = Identifier.withDefaultNamespace("container/slot_highlight_front");
	protected static final int BACKGROUND_TEXTURE_WIDTH = 256;
	protected static final int BACKGROUND_TEXTURE_HEIGHT = 256;
	private static final float SNAPBACK_SPEED = 100.0F;
	private static final int QUICKDROP_DELAY = 500;
	protected int imageWidth = 176;
	protected int imageHeight = 166;
	protected int titleLabelX;
	protected int titleLabelY;
	protected int inventoryLabelX;
	protected int inventoryLabelY;
	private final List<ItemSlotMouseAction> itemSlotMouseActions;
	protected final T menu;
	protected final Component playerInventoryTitle;
	protected @Nullable Slot hoveredSlot;
	private @Nullable Slot clickedSlot;
	private @Nullable Slot quickdropSlot;
	private @Nullable Slot lastClickSlot;
	private AbstractContainerScreen.@Nullable SnapbackData snapbackData;
	protected int leftPos;
	protected int topPos;
	private boolean isSplittingStack;
	private ItemStack draggingItem = ItemStack.EMPTY;
	private long quickdropTime;
	protected final Set<Slot> quickCraftSlots = Sets.newHashSet();
	protected boolean isQuickCrafting;
	private int quickCraftingType;
	@MouseButtonInfo.MouseButton
	private int quickCraftingButton;
	private boolean skipNextRelease;
	private int quickCraftingRemainder;
	private boolean doubleclick;
	private ItemStack lastQuickMoved = ItemStack.EMPTY;

	public AbstractContainerScreen(T abstractContainerMenu, Inventory inventory, Component component) {
		super(component);
		this.menu = abstractContainerMenu;
		this.playerInventoryTitle = inventory.getDisplayName();
		this.skipNextRelease = true;
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = this.imageHeight - 94;
		this.itemSlotMouseActions = new ArrayList<>();
	}

	@Override
	protected void init() {
		this.leftPos = (this.width - this.imageWidth) / 2;
		this.topPos = (this.height - this.imageHeight) / 2;
		this.itemSlotMouseActions.clear();
		this.addItemSlotMouseAction(new BundleMouseActions(this.minecraft));
	}

	protected void addItemSlotMouseAction(ItemSlotMouseAction itemSlotMouseAction) {
		this.itemSlotMouseActions.add(itemSlotMouseAction);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int i, int j, float f) {
		this.renderContents(guiGraphics, i, j, f);
		this.renderCarriedItem(guiGraphics, i, j);
		this.renderSnapbackItem(guiGraphics);
	}

	public void renderContents(GuiGraphics guiGraphics, int i, int j, float f) {
		int k = this.leftPos;
		int l = this.topPos;
		super.render(guiGraphics, i, j, f);
		guiGraphics.pose().pushMatrix();
		guiGraphics.pose().translate(k, l);
		this.renderLabels(guiGraphics, i, j);
		Slot slot = this.hoveredSlot;
		this.hoveredSlot = this.getHoveredSlot(i, j);
		this.renderSlotHighlightBack(guiGraphics);
		this.renderSlots(guiGraphics, i, j);
		this.renderSlotHighlightFront(guiGraphics);
		if (slot != null && slot != this.hoveredSlot) {
			this.onStopHovering(slot);
		}

		guiGraphics.pose().popMatrix();
	}

	public void renderCarriedItem(GuiGraphics guiGraphics, int i, int j) {
		ItemStack itemStack = this.draggingItem.isEmpty() ? this.menu.getCarried() : this.draggingItem;
		if (!itemStack.isEmpty()) {
			int k = 8;
			int l = this.draggingItem.isEmpty() ? 8 : 16;
			String string = null;
			if (!this.draggingItem.isEmpty() && this.isSplittingStack) {
				itemStack = itemStack.copyWithCount(Mth.ceil(itemStack.getCount() / 2.0F));
			} else if (this.isQuickCrafting && this.quickCraftSlots.size() > 1) {
				itemStack = itemStack.copyWithCount(this.quickCraftingRemainder);
				if (itemStack.isEmpty()) {
					string = ChatFormatting.YELLOW + "0";
				}
			}

			guiGraphics.nextStratum();
			this.renderFloatingItem(guiGraphics, itemStack, i - 8, j - l, string);
		}
	}

	public void renderSnapbackItem(GuiGraphics guiGraphics) {
		if (this.snapbackData != null) {
			float f = Mth.clamp((float)(Util.getMillis() - this.snapbackData.time) / 100.0F, 0.0F, 1.0F);
			int i = this.snapbackData.end.x - this.snapbackData.start.x;
			int j = this.snapbackData.end.y - this.snapbackData.start.y;
			int k = this.snapbackData.start.x + (int)(i * f);
			int l = this.snapbackData.start.y + (int)(j * f);
			guiGraphics.nextStratum();
			this.renderFloatingItem(guiGraphics, this.snapbackData.item, k, l, null);
			if (f >= 1.0F) {
				this.snapbackData = null;
			}
		}
	}

	protected void renderSlots(GuiGraphics guiGraphics, int i, int j) {
		for (Slot slot : this.menu.slots) {
			if (slot.isActive()) {
				this.renderSlot(guiGraphics, slot, i, j);
			}
		}
	}

	@Override
	public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
		super.renderBackground(guiGraphics, i, j, f);
		this.renderBg(guiGraphics, f, i, j);
	}

	@Override
	public boolean mouseScrolled(double d, double e, double f, double g) {
		if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
			for (ItemSlotMouseAction itemSlotMouseAction : this.itemSlotMouseActions) {
				if (itemSlotMouseAction.matches(this.hoveredSlot) && itemSlotMouseAction.onMouseScrolled(f, g, this.hoveredSlot.index, this.hoveredSlot.getItem())) {
					return true;
				}
			}
		}

		return false;
	}

	private void renderSlotHighlightBack(GuiGraphics guiGraphics) {
		if (this.hoveredSlot != null && this.hoveredSlot.isHighlightable()) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_BACK_SPRITE, this.hoveredSlot.x - 4, this.hoveredSlot.y - 4, 24, 24);
		}
	}

	private void renderSlotHighlightFront(GuiGraphics guiGraphics) {
		if (this.hoveredSlot != null && this.hoveredSlot.isHighlightable()) {
			guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_FRONT_SPRITE, this.hoveredSlot.x - 4, this.hoveredSlot.y - 4, 24, 24);
		}
	}

	protected void renderTooltip(GuiGraphics guiGraphics, int i, int j) {
		if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
			ItemStack itemStack = this.hoveredSlot.getItem();
			if (this.menu.getCarried().isEmpty() || this.showTooltipWithItemInHand(itemStack)) {
				guiGraphics.setTooltipForNextFrame(
					this.font, this.getTooltipFromContainerItem(itemStack), itemStack.getTooltipImage(), i, j, itemStack.get(DataComponents.TOOLTIP_STYLE)
				);
			}
		}
	}

	private boolean showTooltipWithItemInHand(ItemStack itemStack) {
		return itemStack.getTooltipImage().map(ClientTooltipComponent::create).map(ClientTooltipComponent::showTooltipWithItemInHand).orElse(false);
	}

	protected List<Component> getTooltipFromContainerItem(ItemStack itemStack) {
		return getTooltipFromItem(this.minecraft, itemStack);
	}

	private void renderFloatingItem(GuiGraphics guiGraphics, ItemStack itemStack, int i, int j, @Nullable String string) {
		guiGraphics.renderItem(itemStack, i, j);
		guiGraphics.renderItemDecorations(this.font, itemStack, i, j - (this.draggingItem.isEmpty() ? 0 : 8), string);
	}

	protected void renderLabels(GuiGraphics guiGraphics, int i, int j) {
		guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, -12566464, false);
		guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, -12566464, false);
	}

	protected abstract void renderBg(GuiGraphics guiGraphics, float f, int i, int j);

	protected void renderSlot(GuiGraphics guiGraphics, Slot slot, int i, int j) {
		int k = slot.x;
		int l = slot.y;
		ItemStack itemStack = slot.getItem();
		boolean bl = false;
		boolean bl2 = slot == this.clickedSlot && !this.draggingItem.isEmpty() && !this.isSplittingStack;
		ItemStack itemStack2 = this.menu.getCarried();
		String string = null;
		if (slot == this.clickedSlot && !this.draggingItem.isEmpty() && this.isSplittingStack && !itemStack.isEmpty()) {
			itemStack = itemStack.copyWithCount(itemStack.getCount() / 2);
		} else if (this.isQuickCrafting && this.quickCraftSlots.contains(slot) && !itemStack2.isEmpty()) {
			if (this.quickCraftSlots.size() == 1) {
				return;
			}

			if (AbstractContainerMenu.canItemQuickReplace(slot, itemStack2, true) && this.menu.canDragTo(slot)) {
				bl = true;
				int m = Math.min(itemStack2.getMaxStackSize(), slot.getMaxStackSize(itemStack2));
				int n = slot.getItem().isEmpty() ? 0 : slot.getItem().getCount();
				int o = AbstractContainerMenu.getQuickCraftPlaceCount(this.quickCraftSlots, this.quickCraftingType, itemStack2) + n;
				if (o > m) {
					o = m;
					string = ChatFormatting.YELLOW.toString() + m;
				}

				itemStack = itemStack2.copyWithCount(o);
			} else {
				this.quickCraftSlots.remove(slot);
				this.recalculateQuickCraftRemaining();
			}
		}

		if (itemStack.isEmpty() && slot.isActive()) {
			Identifier identifier = slot.getNoItemIcon();
			if (identifier != null) {
				guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, identifier, k, l, 16, 16);
				bl2 = true;
			}
		}

		if (!bl2) {
			if (bl) {
				guiGraphics.fill(k, l, k + 16, l + 16, -2130706433);
			}

			int m = slot.x + slot.y * this.imageWidth;
			if (slot.isFake()) {
				guiGraphics.renderFakeItem(itemStack, k, l, m);
			} else {
				guiGraphics.renderItem(itemStack, k, l, m);
			}

			guiGraphics.renderItemDecorations(this.font, itemStack, k, l, string);
		}
	}

	private void recalculateQuickCraftRemaining() {
		ItemStack itemStack = this.menu.getCarried();
		if (!itemStack.isEmpty() && this.isQuickCrafting) {
			if (this.quickCraftingType == 2) {
				this.quickCraftingRemainder = itemStack.getMaxStackSize();
			} else {
				this.quickCraftingRemainder = itemStack.getCount();

				for (Slot slot : this.quickCraftSlots) {
					ItemStack itemStack2 = slot.getItem();
					int i = itemStack2.isEmpty() ? 0 : itemStack2.getCount();
					int j = Math.min(itemStack.getMaxStackSize(), slot.getMaxStackSize(itemStack));
					int k = Math.min(AbstractContainerMenu.getQuickCraftPlaceCount(this.quickCraftSlots, this.quickCraftingType, itemStack) + i, j);
					this.quickCraftingRemainder -= k - i;
				}
			}
		}
	}

	private @Nullable Slot getHoveredSlot(double d, double e) {
		for (Slot slot : this.menu.slots) {
			if (slot.isActive() && this.isHovering(slot, d, e)) {
				return slot;
			}
		}

		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
		if (super.mouseClicked(mouseButtonEvent, bl)) {
			return true;
		}

		boolean bl2 = this.minecraft.options.keyPickItem.matchesMouse(mouseButtonEvent) && this.minecraft.player.hasInfiniteMaterials();
		Slot slot = this.getHoveredSlot(mouseButtonEvent.x(), mouseButtonEvent.y());
		this.doubleclick = this.lastClickSlot == slot && bl;
		this.skipNextRelease = false;
		if (mouseButtonEvent.button() != 0 && mouseButtonEvent.button() != 1 && !bl2) {
			this.checkHotbarMouseClicked(mouseButtonEvent);
		} else {
			int i = this.leftPos;
			int j = this.topPos;
			boolean bl3 = this.hasClickedOutside(mouseButtonEvent.x(), mouseButtonEvent.y(), i, j);
			int k = -1;
			if (slot != null) {
				k = slot.index;
			}

			if (bl3) {
				k = -999;
			}

			if (this.minecraft.options.touchscreen().get() && bl3 && this.menu.getCarried().isEmpty()) {
				this.onClose();
				return true;
			}

			if (k != -1) {
				if (this.minecraft.options.touchscreen().get()) {
					if (slot != null && slot.hasItem()) {
						this.clickedSlot = slot;
						this.draggingItem = ItemStack.EMPTY;
						this.isSplittingStack = mouseButtonEvent.button() == 1;
					} else {
						this.clickedSlot = null;
					}
				} else if (!this.isQuickCrafting) {
					if (this.menu.getCarried().isEmpty()) {
						if (bl2) {
							this.slotClicked(slot, k, mouseButtonEvent.button(), ClickType.CLONE);
						} else {
							boolean bl4 = k != -999 && mouseButtonEvent.hasShiftDown();
							ClickType clickType = ClickType.PICKUP;
							if (bl4) {
								this.lastQuickMoved = slot != null && slot.hasItem() ? slot.getItem().copy() : ItemStack.EMPTY;
								clickType = ClickType.QUICK_MOVE;
							} else if (k == -999) {
								clickType = ClickType.THROW;
							}

							this.slotClicked(slot, k, mouseButtonEvent.button(), clickType);
						}

						this.skipNextRelease = true;
					} else {
						this.isQuickCrafting = true;
						this.quickCraftingButton = mouseButtonEvent.button();
						this.quickCraftSlots.clear();
						if (mouseButtonEvent.button() == 0) {
							this.quickCraftingType = 0;
						} else if (mouseButtonEvent.button() == 1) {
							this.quickCraftingType = 1;
						} else if (bl2) {
							this.quickCraftingType = 2;
						}
					}
				}
			}
		}

		this.lastClickSlot = slot;
		return true;
	}

	private void checkHotbarMouseClicked(MouseButtonEvent mouseButtonEvent) {
		if (this.hoveredSlot != null && this.menu.getCarried().isEmpty()) {
			if (this.minecraft.options.keySwapOffhand.matchesMouse(mouseButtonEvent)) {
				this.slotClicked(this.hoveredSlot, this.hoveredSlot.index, 40, ClickType.SWAP);
				return;
			}

			for (int i = 0; i < 9; i++) {
				if (this.minecraft.options.keyHotbarSlots[i].matchesMouse(mouseButtonEvent)) {
					this.slotClicked(this.hoveredSlot, this.hoveredSlot.index, i, ClickType.SWAP);
				}
			}
		}
	}

	protected boolean hasClickedOutside(double d, double e, int i, int j) {
		return d < i || e < j || d >= i + this.imageWidth || e >= j + this.imageHeight;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double d, double e) {
		Slot slot = this.getHoveredSlot(mouseButtonEvent.x(), mouseButtonEvent.y());
		ItemStack itemStack = this.menu.getCarried();
		if (this.clickedSlot != null && this.minecraft.options.touchscreen().get()) {
			if (mouseButtonEvent.button() == 0 || mouseButtonEvent.button() == 1) {
				if (this.draggingItem.isEmpty()) {
					if (slot != this.clickedSlot && !this.clickedSlot.getItem().isEmpty()) {
						this.draggingItem = this.clickedSlot.getItem().copy();
					}
				} else if (this.draggingItem.getCount() > 1 && slot != null && AbstractContainerMenu.canItemQuickReplace(slot, this.draggingItem, false)) {
					long l = Util.getMillis();
					if (this.quickdropSlot == slot) {
						if (l - this.quickdropTime > 500L) {
							this.slotClicked(this.clickedSlot, this.clickedSlot.index, 0, ClickType.PICKUP);
							this.slotClicked(slot, slot.index, 1, ClickType.PICKUP);
							this.slotClicked(this.clickedSlot, this.clickedSlot.index, 0, ClickType.PICKUP);
							this.quickdropTime = l + 750L;
							this.draggingItem.shrink(1);
						}
					} else {
						this.quickdropSlot = slot;
						this.quickdropTime = l;
					}
				}
			}

			return true;
		} else if (this.isQuickCrafting
			&& slot != null
			&& !itemStack.isEmpty()
			&& (itemStack.getCount() > this.quickCraftSlots.size() || this.quickCraftingType == 2)
			&& AbstractContainerMenu.canItemQuickReplace(slot, itemStack, true)
			&& slot.mayPlace(itemStack)
			&& this.menu.canDragTo(slot)) {
			this.quickCraftSlots.add(slot);
			this.recalculateQuickCraftRemaining();
			return true;
		} else {
			return slot == null && this.menu.getCarried().isEmpty() ? super.mouseDragged(mouseButtonEvent, d, e) : true;
		}
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
		Slot slot = this.getHoveredSlot(mouseButtonEvent.x(), mouseButtonEvent.y());
		int i = this.leftPos;
		int j = this.topPos;
		boolean bl = this.hasClickedOutside(mouseButtonEvent.x(), mouseButtonEvent.y(), i, j);
		int k = -1;
		if (slot != null) {
			k = slot.index;
		}

		if (bl) {
			k = -999;
		}

		if (this.doubleclick && slot != null && mouseButtonEvent.button() == 0 && this.menu.canTakeItemForPickAll(ItemStack.EMPTY, slot)) {
			if (mouseButtonEvent.hasShiftDown()) {
				if (!this.lastQuickMoved.isEmpty()) {
					for (Slot slot2 : this.menu.slots) {
						if (slot2 != null
							&& slot2.mayPickup(this.minecraft.player)
							&& slot2.hasItem()
							&& slot2.container == slot.container
							&& AbstractContainerMenu.canItemQuickReplace(slot2, this.lastQuickMoved, true)) {
							this.slotClicked(slot2, slot2.index, mouseButtonEvent.button(), ClickType.QUICK_MOVE);
						}
					}
				}
			} else {
				this.slotClicked(slot, k, mouseButtonEvent.button(), ClickType.PICKUP_ALL);
			}

			this.doubleclick = false;
		} else {
			if (this.isQuickCrafting && this.quickCraftingButton != mouseButtonEvent.button()) {
				this.isQuickCrafting = false;
				this.quickCraftSlots.clear();
				this.skipNextRelease = true;
				return true;
			}

			if (this.skipNextRelease) {
				this.skipNextRelease = false;
				return true;
			}

			if (this.clickedSlot != null && this.minecraft.options.touchscreen().get()) {
				if (mouseButtonEvent.button() == 0 || mouseButtonEvent.button() == 1) {
					if (this.draggingItem.isEmpty() && slot != this.clickedSlot) {
						this.draggingItem = this.clickedSlot.getItem();
					}

					boolean bl2 = AbstractContainerMenu.canItemQuickReplace(slot, this.draggingItem, false);
					if (k != -1 && !this.draggingItem.isEmpty() && bl2) {
						this.slotClicked(this.clickedSlot, this.clickedSlot.index, mouseButtonEvent.button(), ClickType.PICKUP);
						this.slotClicked(slot, k, 0, ClickType.PICKUP);
						if (this.menu.getCarried().isEmpty()) {
							this.snapbackData = null;
						} else {
							this.slotClicked(this.clickedSlot, this.clickedSlot.index, mouseButtonEvent.button(), ClickType.PICKUP);
							this.snapbackData = new AbstractContainerScreen.SnapbackData(
								this.draggingItem,
								new Vector2i((int)mouseButtonEvent.x(), (int)mouseButtonEvent.y()),
								new Vector2i(this.clickedSlot.x + i, this.clickedSlot.y + j),
								Util.getMillis()
							);
						}
					} else if (!this.draggingItem.isEmpty()) {
						this.snapbackData = new AbstractContainerScreen.SnapbackData(
							this.draggingItem,
							new Vector2i((int)mouseButtonEvent.x(), (int)mouseButtonEvent.y()),
							new Vector2i(this.clickedSlot.x + i, this.clickedSlot.y + j),
							Util.getMillis()
						);
					}

					this.clearDraggingState();
				}
			} else if (this.isQuickCrafting && !this.quickCraftSlots.isEmpty()) {
				this.slotClicked(null, -999, AbstractContainerMenu.getQuickcraftMask(0, this.quickCraftingType), ClickType.QUICK_CRAFT);

				for (Slot slot2 : this.quickCraftSlots) {
					this.slotClicked(slot2, slot2.index, AbstractContainerMenu.getQuickcraftMask(1, this.quickCraftingType), ClickType.QUICK_CRAFT);
				}

				this.slotClicked(null, -999, AbstractContainerMenu.getQuickcraftMask(2, this.quickCraftingType), ClickType.QUICK_CRAFT);
			} else if (!this.menu.getCarried().isEmpty()) {
				if (this.minecraft.options.keyPickItem.matchesMouse(mouseButtonEvent)) {
					this.slotClicked(slot, k, mouseButtonEvent.button(), ClickType.CLONE);
				} else {
					boolean bl2 = k != -999 && mouseButtonEvent.hasShiftDown();
					if (bl2) {
						this.lastQuickMoved = slot != null && slot.hasItem() ? slot.getItem().copy() : ItemStack.EMPTY;
					}

					this.slotClicked(slot, k, mouseButtonEvent.button(), bl2 ? ClickType.QUICK_MOVE : ClickType.PICKUP);
				}
			}
		}

		this.isQuickCrafting = false;
		return true;
	}

	public void clearDraggingState() {
		this.draggingItem = ItemStack.EMPTY;
		this.clickedSlot = null;
	}

	private boolean isHovering(Slot slot, double d, double e) {
		return this.isHovering(slot.x, slot.y, 16, 16, d, e);
	}

	protected boolean isHovering(int i, int j, int k, int l, double d, double e) {
		int m = this.leftPos;
		int n = this.topPos;
		d -= m;
		e -= n;
		return d >= i - 1 && d < i + k + 1 && e >= j - 1 && e < j + l + 1;
	}

	private void onStopHovering(Slot slot) {
		if (slot.hasItem()) {
			for (ItemSlotMouseAction itemSlotMouseAction : this.itemSlotMouseActions) {
				if (itemSlotMouseAction.matches(slot)) {
					itemSlotMouseAction.onStopHovering(slot);
				}
			}
		}
	}

	protected void slotClicked(Slot slot, int i, int j, ClickType clickType) {
		if (slot != null) {
			i = slot.index;
		}

		this.onMouseClickAction(slot, clickType);
		this.minecraft.gameMode.handleInventoryMouseClick(this.menu.containerId, i, j, clickType, this.minecraft.player);
	}

	void onMouseClickAction(@Nullable Slot slot, ClickType clickType) {
		if (slot != null && slot.hasItem()) {
			for (ItemSlotMouseAction itemSlotMouseAction : this.itemSlotMouseActions) {
				if (itemSlotMouseAction.matches(slot)) {
					itemSlotMouseAction.onSlotClicked(slot, clickType);
				}
			}
		}
	}

	protected void handleSlotStateChanged(int i, int j, boolean bl) {
		this.minecraft.gameMode.handleSlotStateChanged(i, j, bl);
	}

	@Override
	public boolean keyPressed(KeyEvent keyEvent) {
		if (super.keyPressed(keyEvent)) {
			return true;
		}

		if (this.minecraft.options.keyInventory.matches(keyEvent)) {
			this.onClose();
			return true;
		}

		this.checkHotbarKeyPressed(keyEvent);
		if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
			if (this.minecraft.options.keyPickItem.matches(keyEvent)) {
				this.slotClicked(this.hoveredSlot, this.hoveredSlot.index, 0, ClickType.CLONE);
			} else if (this.minecraft.options.keyDrop.matches(keyEvent)) {
				this.slotClicked(this.hoveredSlot, this.hoveredSlot.index, keyEvent.hasControlDown() ? 1 : 0, ClickType.THROW);
			}
		}

		return false;
	}

	protected boolean checkHotbarKeyPressed(KeyEvent keyEvent) {
		if (this.menu.getCarried().isEmpty() && this.hoveredSlot != null) {
			if (this.minecraft.options.keySwapOffhand.matches(keyEvent)) {
				this.slotClicked(this.hoveredSlot, this.hoveredSlot.index, 40, ClickType.SWAP);
				return true;
			}

			for (int i = 0; i < 9; i++) {
				if (this.minecraft.options.keyHotbarSlots[i].matches(keyEvent)) {
					this.slotClicked(this.hoveredSlot, this.hoveredSlot.index, i, ClickType.SWAP);
					return true;
				}
			}
		}

		return false;
	}

	@Override
	public void removed() {
		if (this.minecraft.player != null) {
			this.menu.removed(this.minecraft.player);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	@Override
	public final void tick() {
		super.tick();
		if (this.minecraft.player.isAlive() && !this.minecraft.player.isRemoved()) {
			this.containerTick();
		} else {
			this.minecraft.player.closeContainer();
		}
	}

	protected void containerTick() {
	}

	@Override
	public T getMenu() {
		return this.menu;
	}

	@Override
	public void onClose() {
		this.minecraft.player.closeContainer();
		if (this.hoveredSlot != null) {
			this.onStopHovering(this.hoveredSlot);
		}

		super.onClose();
	}

	@Environment(EnvType.CLIENT)
	record SnapbackData(ItemStack item, Vector2i start, Vector2i end, long time) {
	}
}
