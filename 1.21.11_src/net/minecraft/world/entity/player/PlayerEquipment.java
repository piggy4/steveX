package net.minecraft.world.entity.player;

import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class PlayerEquipment extends EntityEquipment {
	private final Player player;

	public PlayerEquipment(Player player) {
		this.player = player;
	}

	@Override
	public ItemStack set(EquipmentSlot equipmentSlot, ItemStack itemStack) {
		return equipmentSlot == EquipmentSlot.MAINHAND ? this.player.getInventory().setSelectedItem(itemStack) : super.set(equipmentSlot, itemStack);
	}

	@Override
	public ItemStack get(EquipmentSlot equipmentSlot) {
		return equipmentSlot == EquipmentSlot.MAINHAND ? this.player.getInventory().getSelectedItem() : super.get(equipmentSlot);
	}

	@Override
	public boolean isEmpty() {
		return this.player.getInventory().getSelectedItem().isEmpty() && super.isEmpty();
	}
}
