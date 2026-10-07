package net.minecraft.world.level.block.entity;

import java.util.function.Predicate;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

public interface ListBackedContainer extends Container {
	NonNullList<ItemStack> getItems();

	default int count() {
		return (int)this.getItems().stream().filter(Predicate.not(ItemStack::isEmpty)).count();
	}

	@Override
	default int getContainerSize() {
		return this.getItems().size();
	}

	@Override
	default void clearContent() {
		this.getItems().clear();
	}

	@Override
	default boolean isEmpty() {
		return this.getItems().stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	default ItemStack getItem(int i) {
		return this.getItems().get(i);
	}

	@Override
	default ItemStack removeItem(int i, int j) {
		ItemStack itemStack = ContainerHelper.removeItem(this.getItems(), i, j);
		if (!itemStack.isEmpty()) {
			this.setChanged();
		}

		return itemStack;
	}

	@Override
	default ItemStack removeItemNoUpdate(int i) {
		return ContainerHelper.removeItem(this.getItems(), i, this.getMaxStackSize());
	}

	@Override
	default boolean canPlaceItem(int i, ItemStack itemStack) {
		return this.acceptsItemType(itemStack) && (this.getItem(i).isEmpty() || this.getItem(i).getCount() < this.getMaxStackSize(itemStack));
	}

	default boolean acceptsItemType(ItemStack itemStack) {
		return true;
	}

	@Override
	default void setItem(int i, ItemStack itemStack) {
		this.setItemNoUpdate(i, itemStack);
		this.setChanged();
	}

	default void setItemNoUpdate(int i, ItemStack itemStack) {
		this.getItems().set(i, itemStack);
		itemStack.limitSize(this.getMaxStackSize(itemStack));
	}
}
