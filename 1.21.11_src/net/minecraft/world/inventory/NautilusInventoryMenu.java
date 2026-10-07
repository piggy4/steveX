package net.minecraft.world.inventory;

import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.entity.player.Inventory;

public class NautilusInventoryMenu extends AbstractMountInventoryMenu {
	private static final Identifier SADDLE_SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot/saddle");
	private static final Identifier ARMOR_SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot/nautilus_armor_inventory");

	public NautilusInventoryMenu(int i, Inventory inventory, Container container, AbstractNautilus abstractNautilus, int j) {
		super(i, inventory, container, abstractNautilus);
		Container container2 = abstractNautilus.createEquipmentSlotContainer(EquipmentSlot.SADDLE);
		this.addSlot(new ArmorSlot(container2, abstractNautilus, EquipmentSlot.SADDLE, 0, 8, 18, SADDLE_SLOT_SPRITE) {
			@Override
			public boolean isActive() {
				return abstractNautilus.canUseSlot(EquipmentSlot.SADDLE);
			}
		});
		Container container3 = abstractNautilus.createEquipmentSlotContainer(EquipmentSlot.BODY);
		this.addSlot(new ArmorSlot(container3, abstractNautilus, EquipmentSlot.BODY, 0, 8, 36, ARMOR_SLOT_SPRITE) {
			@Override
			public boolean isActive() {
				return abstractNautilus.canUseSlot(EquipmentSlot.BODY);
			}
		});
		this.addStandardInventorySlots(inventory, 8, 84);
	}

	@Override
	protected boolean hasInventoryChanged(Container container) {
		return ((AbstractNautilus)this.mount).hasInventoryChanged(container);
	}
}
