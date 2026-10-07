package net.minecraft.world.entity;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;

public interface SlotAccess {
	ItemStack get();

	boolean set(ItemStack itemStack);

	static SlotAccess of(Supplier<ItemStack> supplier, Consumer<ItemStack> consumer) {
		return new SlotAccess() {
			@Override
			public ItemStack get() {
				return supplier.get();
			}

			@Override
			public boolean set(ItemStack itemStack) {
				consumer.accept(itemStack);
				return true;
			}
		};
	}

	static SlotAccess forEquipmentSlot(LivingEntity livingEntity, EquipmentSlot equipmentSlot, Predicate<ItemStack> predicate) {
		return new SlotAccess() {
			@Override
			public ItemStack get() {
				return livingEntity.getItemBySlot(equipmentSlot);
			}

			@Override
			public boolean set(ItemStack itemStack) {
				if (!predicate.test(itemStack)) {
					return false;
				}

				livingEntity.setItemSlot(equipmentSlot, itemStack);
				return true;
			}
		};
	}

	static SlotAccess forEquipmentSlot(LivingEntity livingEntity, EquipmentSlot equipmentSlot) {
		return forEquipmentSlot(livingEntity, equipmentSlot, itemStack -> true);
	}

	static SlotAccess forListElement(List<ItemStack> list, int i) {
		return new SlotAccess() {
			@Override
			public ItemStack get() {
				return list.get(i);
			}

			@Override
			public boolean set(ItemStack itemStack) {
				list.set(i, itemStack);
				return true;
			}
		};
	}
}
