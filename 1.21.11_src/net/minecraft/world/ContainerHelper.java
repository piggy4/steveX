package net.minecraft.world;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ContainerHelper {
	public static final String TAG_ITEMS = "Items";

	public static ItemStack removeItem(List<ItemStack> list, int i, int j) {
		return i >= 0 && i < list.size() && !list.get(i).isEmpty() && j > 0 ? list.get(i).split(j) : ItemStack.EMPTY;
	}

	public static ItemStack takeItem(List<ItemStack> list, int i) {
		return i >= 0 && i < list.size() ? list.set(i, ItemStack.EMPTY) : ItemStack.EMPTY;
	}

	public static void saveAllItems(ValueOutput valueOutput, NonNullList<ItemStack> nonNullList) {
		saveAllItems(valueOutput, nonNullList, true);
	}

	public static void saveAllItems(ValueOutput valueOutput, NonNullList<ItemStack> nonNullList, boolean bl) {
		ValueOutput.TypedOutputList<ItemStackWithSlot> typedOutputList = valueOutput.list("Items", ItemStackWithSlot.CODEC);

		for (int i = 0; i < nonNullList.size(); i++) {
			ItemStack itemStack = nonNullList.get(i);
			if (!itemStack.isEmpty()) {
				typedOutputList.add(new ItemStackWithSlot(i, itemStack));
			}
		}

		if (typedOutputList.isEmpty() && !bl) {
			valueOutput.discard("Items");
		}
	}

	public static void loadAllItems(ValueInput valueInput, NonNullList<ItemStack> nonNullList) {
		for (ItemStackWithSlot itemStackWithSlot : valueInput.listOrEmpty("Items", ItemStackWithSlot.CODEC)) {
			if (itemStackWithSlot.isValidInContainer(nonNullList.size())) {
				nonNullList.set(itemStackWithSlot.slot(), itemStackWithSlot.stack());
			}
		}
	}

	public static int clearOrCountMatchingItems(Container container, Predicate<ItemStack> predicate, int i, boolean bl) {
		int j = 0;

		for (int k = 0; k < container.getContainerSize(); k++) {
			ItemStack itemStack = container.getItem(k);
			int l = clearOrCountMatchingItems(itemStack, predicate, i - j, bl);
			if (l > 0 && !bl && itemStack.isEmpty()) {
				container.setItem(k, ItemStack.EMPTY);
			}

			j += l;
		}

		return j;
	}

	public static int clearOrCountMatchingItems(ItemStack itemStack, Predicate<ItemStack> predicate, int i, boolean bl) {
		if (itemStack.isEmpty() || !predicate.test(itemStack)) {
			return 0;
		}

		if (bl) {
			return itemStack.getCount();
		}

		int j = i < 0 ? itemStack.getCount() : Math.min(i, itemStack.getCount());
		itemStack.shrink(j);
		return j;
	}
}
