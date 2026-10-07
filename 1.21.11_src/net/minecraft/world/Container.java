package net.minecraft.world;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SlotProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

public interface Container extends Clearable, SlotProvider, Iterable<ItemStack> {
	float DEFAULT_DISTANCE_BUFFER = 4.0F;

	int getContainerSize();

	boolean isEmpty();

	ItemStack getItem(int i);

	ItemStack removeItem(int i, int j);

	ItemStack removeItemNoUpdate(int i);

	void setItem(int i, ItemStack itemStack);

	default int getMaxStackSize() {
		return 99;
	}

	default int getMaxStackSize(ItemStack itemStack) {
		return Math.min(this.getMaxStackSize(), itemStack.getMaxStackSize());
	}

	void setChanged();

	boolean stillValid(Player player);

	default void startOpen(ContainerUser containerUser) {
	}

	default void stopOpen(ContainerUser containerUser) {
	}

	default List<ContainerUser> getEntitiesWithContainerOpen() {
		return List.of();
	}

	default boolean canPlaceItem(int i, ItemStack itemStack) {
		return true;
	}

	default boolean canTakeItem(Container container, int i, ItemStack itemStack) {
		return true;
	}

	default int countItem(Item item) {
		int i = 0;

		for (ItemStack itemStack : this) {
			if (itemStack.getItem().equals(item)) {
				i += itemStack.getCount();
			}
		}

		return i;
	}

	default boolean hasAnyOf(Set<Item> set) {
		return this.hasAnyMatching(itemStack -> !itemStack.isEmpty() && set.contains(itemStack.getItem()));
	}

	default boolean hasAnyMatching(Predicate<ItemStack> predicate) {
		for (ItemStack itemStack : this) {
			if (predicate.test(itemStack)) {
				return true;
			}
		}

		return false;
	}

	static boolean stillValidBlockEntity(BlockEntity blockEntity, Player player) {
		return stillValidBlockEntity(blockEntity, player, 4.0F);
	}

	static boolean stillValidBlockEntity(BlockEntity blockEntity, Player player, float f) {
		Level level = blockEntity.getLevel();
		BlockPos blockPos = blockEntity.getBlockPos();
		if (level == null) {
			return false;
		} else {
			return level.getBlockEntity(blockPos) != blockEntity ? false : player.isWithinBlockInteractionRange(blockPos, f);
		}
	}

	@Override
	default @Nullable SlotAccess getSlot(int i) {
		return i >= 0 && i < this.getContainerSize() ? new SlotAccess() {
			@Override
			public ItemStack get() {
				return Container.this.getItem(i);
			}

			@Override
			public boolean set(ItemStack itemStack) {
				Container.this.setItem(i, itemStack);
				return true;
			}
		} : null;
	}

	@Override
	default Iterator<ItemStack> iterator() {
		return new Container.ContainerIterator(this);
	}

	class ContainerIterator implements Iterator<ItemStack> {
		private final Container container;
		private int index;
		private final int size;

		public ContainerIterator(Container container) {
			this.container = container;
			this.size = container.getContainerSize();
		}

		@Override
		public boolean hasNext() {
			return this.index < this.size;
		}

		public ItemStack next() {
			if (!this.hasNext()) {
				throw new NoSuchElementException();
			} else {
				return this.container.getItem(this.index++);
			}
		}
	}
}
