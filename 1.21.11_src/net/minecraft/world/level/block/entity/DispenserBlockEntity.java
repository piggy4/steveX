package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class DispenserBlockEntity extends RandomizableContainerBlockEntity {
	public static final int CONTAINER_SIZE = 9;
	private static final Component DEFAULT_NAME = Component.translatable("container.dispenser");
	private NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);

	protected DispenserBlockEntity(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
		super(blockEntityType, blockPos, blockState);
	}

	public DispenserBlockEntity(BlockPos blockPos, BlockState blockState) {
		this(BlockEntityType.DISPENSER, blockPos, blockState);
	}

	@Override
	public int getContainerSize() {
		return 9;
	}

	public int getRandomSlot(RandomSource randomSource) {
		this.unpackLootTable(null);
		int i = -1;
		int j = 1;

		for (int k = 0; k < this.items.size(); k++) {
			if (!this.items.get(k).isEmpty() && randomSource.nextInt(j++) == 0) {
				i = k;
			}
		}

		return i;
	}

	public ItemStack insertItem(ItemStack itemStack) {
		int i = this.getMaxStackSize(itemStack);

		for (int j = 0; j < this.items.size(); j++) {
			ItemStack itemStack2 = this.items.get(j);
			if (itemStack2.isEmpty() || ItemStack.isSameItemSameComponents(itemStack, itemStack2)) {
				int k = Math.min(itemStack.getCount(), i - itemStack2.getCount());
				if (k > 0) {
					if (itemStack2.isEmpty()) {
						this.setItem(j, itemStack.split(k));
					} else {
						itemStack.shrink(k);
						itemStack2.grow(k);
					}
				}

				if (itemStack.isEmpty()) {
					break;
				}
			}
		}

		return itemStack;
	}

	@Override
	protected Component getDefaultName() {
		return DEFAULT_NAME;
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		super.loadAdditional(valueInput);
		this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
		if (!this.tryLoadLootTable(valueInput)) {
			ContainerHelper.loadAllItems(valueInput, this.items);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		if (!this.trySaveLootTable(valueOutput)) {
			ContainerHelper.saveAllItems(valueOutput, this.items);
		}
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> nonNullList) {
		this.items = nonNullList;
	}

	@Override
	protected AbstractContainerMenu createMenu(int i, Inventory inventory) {
		return new DispenserMenu(i, inventory, this);
	}
}
