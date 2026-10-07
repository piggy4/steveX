package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public abstract class AbstractMountInventoryMenu extends AbstractContainerMenu {
	protected final Container mountContainer;
	protected final LivingEntity mount;
	protected final int SLOT_SADDLE = 0;
	protected final int SLOT_BODY_ARMOR = 1;
	protected final int SLOT_INVENTORY_START = 2;
	protected static final int INVENTORY_ROWS = 3;

	protected AbstractMountInventoryMenu(int i, Inventory inventory, Container container, LivingEntity livingEntity) {
		super(null, i);
		this.mountContainer = container;
		this.mount = livingEntity;
		container.startOpen(inventory.player);
	}

	protected abstract boolean hasInventoryChanged(Container container);

	@Override
	public boolean stillValid(Player player) {
		return !this.hasInventoryChanged(this.mountContainer)
			&& this.mountContainer.stillValid(player)
			&& this.mount.isAlive()
			&& player.isWithinEntityInteractionRange(this.mount, 4.0);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.mountContainer.stopOpen(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int i) {
		ItemStack itemStack = ItemStack.EMPTY;
		Slot slot = this.slots.get(i);
		if (slot != null && slot.hasItem()) {
			ItemStack itemStack2 = slot.getItem();
			itemStack = itemStack2.copy();
			int j = 2 + this.mountContainer.getContainerSize();
			if (i < j) {
				if (!this.moveItemStackTo(itemStack2, j, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (this.getSlot(1).mayPlace(itemStack2) && !this.getSlot(1).hasItem()) {
				if (!this.moveItemStackTo(itemStack2, 1, 2, false)) {
					return ItemStack.EMPTY;
				}
			} else if (this.getSlot(0).mayPlace(itemStack2) && !this.getSlot(0).hasItem()) {
				if (!this.moveItemStackTo(itemStack2, 0, 1, false)) {
					return ItemStack.EMPTY;
				}
			} else if (this.mountContainer.getContainerSize() == 0 || !this.moveItemStackTo(itemStack2, 2, j, false)) {
				int k = j + 27;
				int l = k;
				int m = l + 9;
				if (i >= l && i < m) {
					if (!this.moveItemStackTo(itemStack2, j, k, false)) {
						return ItemStack.EMPTY;
					}
				} else if (i >= j && i < k) {
					if (!this.moveItemStackTo(itemStack2, l, m, false)) {
						return ItemStack.EMPTY;
					}
				} else if (!this.moveItemStackTo(itemStack2, l, k, false)) {
					return ItemStack.EMPTY;
				}

				return ItemStack.EMPTY;
			}

			if (itemStack2.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}

		return itemStack;
	}

	public static int getInventorySize(int i) {
		return i * 3;
	}
}
