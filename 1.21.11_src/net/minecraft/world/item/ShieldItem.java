package net.minecraft.world.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;

public class ShieldItem extends Item {
	public ShieldItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public Component getName(ItemStack itemStack) {
		DyeColor dyeColor = itemStack.get(DataComponents.BASE_COLOR);
		return dyeColor != null ? Component.translatable(this.descriptionId + "." + dyeColor.getName()) : super.getName(itemStack);
	}
}
