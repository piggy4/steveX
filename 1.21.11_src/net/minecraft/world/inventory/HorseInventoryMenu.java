package net.minecraft.world.inventory;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.player.Inventory;

public class HorseInventoryMenu extends AbstractMountInventoryMenu {
	private static final Identifier SADDLE_SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot/saddle");
	private static final Identifier LLAMA_ARMOR_SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot/llama_armor");
	private static final Identifier ARMOR_SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot/horse_armor");

	public HorseInventoryMenu(int i, Inventory inventory, Container container, AbstractHorse abstractHorse, int j) {
		super(i, inventory, container, abstractHorse);
		Container container2 = abstractHorse.createEquipmentSlotContainer(EquipmentSlot.SADDLE);
		this.addSlot(new ArmorSlot(container2, abstractHorse, EquipmentSlot.SADDLE, 0, 8, 18, SADDLE_SLOT_SPRITE) {
			@Override
			public boolean isActive() {
				return abstractHorse.canUseSlot(EquipmentSlot.SADDLE) && abstractHorse.getType().is(EntityTypeTags.CAN_EQUIP_SADDLE);
			}
		});
		final boolean bl = abstractHorse instanceof Llama;
		Identifier identifier = bl ? LLAMA_ARMOR_SLOT_SPRITE : ARMOR_SLOT_SPRITE;
		Container container3 = abstractHorse.createEquipmentSlotContainer(EquipmentSlot.BODY);
		this.addSlot(new ArmorSlot(container3, abstractHorse, EquipmentSlot.BODY, 0, 8, 36, identifier) {
			@Override
			public boolean isActive() {
				return abstractHorse.canUseSlot(EquipmentSlot.BODY) && (abstractHorse.getType().is(EntityTypeTags.CAN_WEAR_HORSE_ARMOR) || bl);
			}
		});
		if (j > 0) {
			for (int k = 0; k < 3; k++) {
				for (int l = 0; l < j; l++) {
					this.addSlot(new Slot(container, l + k * j, 80 + l * 18, 18 + k * 18));
				}
			}
		}

		this.addStandardInventorySlots(inventory, 8, 84);
	}

	@Override
	protected boolean hasInventoryChanged(Container container) {
		return ((AbstractHorse)this.mount).hasInventoryChanged(container);
	}
}
