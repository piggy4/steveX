package net.minecraft.world.level.block.entity;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class CrafterBlockEntity extends RandomizableContainerBlockEntity implements CraftingContainer {
	public static final int CONTAINER_WIDTH = 3;
	public static final int CONTAINER_HEIGHT = 3;
	public static final int CONTAINER_SIZE = 9;
	public static final int SLOT_DISABLED = 1;
	public static final int SLOT_ENABLED = 0;
	public static final int DATA_TRIGGERED = 9;
	public static final int NUM_DATA = 10;
	private static final int DEFAULT_CRAFTING_TICKS_REMAINING = 0;
	private static final int DEFAULT_TRIGGERED = 0;
	private static final Component DEFAULT_NAME = Component.translatable("container.crafter");
	private NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
	private int craftingTicksRemaining = 0;
	protected final ContainerData containerData = new ContainerData() {
		private final int[] slotStates = new int[9];
		private int triggered = 0;

		@Override
		public int get(int i) {
			return i == 9 ? this.triggered : this.slotStates[i];
		}

		@Override
		public void set(int i, int j) {
			if (i == 9) {
				this.triggered = j;
			} else {
				this.slotStates[i] = j;
			}
		}

		@Override
		public int getCount() {
			return 10;
		}
	};

	public CrafterBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BlockEntityType.CRAFTER, blockPos, blockState);
	}

	@Override
	protected Component getDefaultName() {
		return DEFAULT_NAME;
	}

	@Override
	protected AbstractContainerMenu createMenu(int i, Inventory inventory) {
		return new CrafterMenu(i, inventory, this, this.containerData);
	}

	public void setSlotState(int i, boolean bl) {
		if (this.slotCanBeDisabled(i)) {
			this.containerData.set(i, bl ? 0 : 1);
			this.setChanged();
		}
	}

	public boolean isSlotDisabled(int i) {
		return i >= 0 && i < 9 ? this.containerData.get(i) == 1 : false;
	}

	@Override
	public boolean canPlaceItem(int i, ItemStack itemStack) {
		if (this.containerData.get(i) == 1) {
			return false;
		} else {
			ItemStack itemStack2 = this.items.get(i);
			int j = itemStack2.getCount();
			if (j >= itemStack2.getMaxStackSize()) {
				return false;
			} else {
				return itemStack2.isEmpty() ? true : !this.smallerStackExist(j, itemStack2, i);
			}
		}
	}

	private boolean smallerStackExist(int i, ItemStack itemStack, int j) {
		for (int k = j + 1; k < 9; k++) {
			if (!this.isSlotDisabled(k)) {
				ItemStack itemStack2 = this.getItem(k);
				if (itemStack2.isEmpty() || itemStack2.getCount() < i && ItemStack.isSameItemSameComponents(itemStack2, itemStack)) {
					return true;
				}
			}
		}

		return false;
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		super.loadAdditional(valueInput);
		this.craftingTicksRemaining = valueInput.getIntOr("crafting_ticks_remaining", 0);
		this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
		if (!this.tryLoadLootTable(valueInput)) {
			ContainerHelper.loadAllItems(valueInput, this.items);
		}

		for (int i = 0; i < 9; i++) {
			this.containerData.set(i, 0);
		}

		valueInput.getIntArray("disabled_slots").ifPresent(is -> {
			for (int ix : is) {
				if (this.slotCanBeDisabled(ix)) {
					this.containerData.set(ix, 1);
				}
			}
		});
		this.containerData.set(9, valueInput.getIntOr("triggered", 0));
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		valueOutput.putInt("crafting_ticks_remaining", this.craftingTicksRemaining);
		if (!this.trySaveLootTable(valueOutput)) {
			ContainerHelper.saveAllItems(valueOutput, this.items);
		}

		this.addDisabledSlots(valueOutput);
		this.addTriggered(valueOutput);
	}

	@Override
	public int getContainerSize() {
		return 9;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack itemStack : this.items) {
			if (!itemStack.isEmpty()) {
				return false;
			}
		}

		return true;
	}

	@Override
	public ItemStack getItem(int i) {
		return this.items.get(i);
	}

	@Override
	public void setItem(int i, ItemStack itemStack) {
		if (this.isSlotDisabled(i)) {
			this.setSlotState(i, true);
		}

		super.setItem(i, itemStack);
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> nonNullList) {
		this.items = nonNullList;
	}

	@Override
	public int getWidth() {
		return 3;
	}

	@Override
	public int getHeight() {
		return 3;
	}

	@Override
	public void fillStackedContents(StackedItemContents stackedItemContents) {
		for (ItemStack itemStack : this.items) {
			stackedItemContents.accountSimpleStack(itemStack);
		}
	}

	private void addDisabledSlots(ValueOutput valueOutput) {
		IntList intList = new IntArrayList();

		for (int i = 0; i < 9; i++) {
			if (this.isSlotDisabled(i)) {
				intList.add(i);
			}
		}

		valueOutput.putIntArray("disabled_slots", intList.toIntArray());
	}

	private void addTriggered(ValueOutput valueOutput) {
		valueOutput.putInt("triggered", this.containerData.get(9));
	}

	public void setTriggered(boolean bl) {
		this.containerData.set(9, bl ? 1 : 0);
	}

	@VisibleForTesting
	public boolean isTriggered() {
		return this.containerData.get(9) == 1;
	}

	public static void serverTick(Level level, BlockPos blockPos, BlockState blockState, CrafterBlockEntity crafterBlockEntity) {
		int i = crafterBlockEntity.craftingTicksRemaining - 1;
		if (i >= 0) {
			crafterBlockEntity.craftingTicksRemaining = i;
			if (i == 0) {
				level.setBlock(blockPos, blockState.setValue(CrafterBlock.CRAFTING, false), 3);
			}
		}
	}

	public void setCraftingTicksRemaining(int i) {
		this.craftingTicksRemaining = i;
	}

	public int getRedstoneSignal() {
		int i = 0;

		for (int j = 0; j < this.getContainerSize(); j++) {
			ItemStack itemStack = this.getItem(j);
			if (!itemStack.isEmpty() || this.isSlotDisabled(j)) {
				i++;
			}
		}

		return i;
	}

	private boolean slotCanBeDisabled(int i) {
		return i > -1 && i < 9 && this.items.get(i).isEmpty();
	}
}
