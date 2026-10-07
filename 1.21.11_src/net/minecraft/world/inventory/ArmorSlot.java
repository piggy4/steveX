package net.minecraft.world.inventory;

import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jspecify.annotations.Nullable;

class ArmorSlot extends Slot {
	private final LivingEntity owner;
	private final EquipmentSlot slot;
	private final @Nullable Identifier emptyIcon;

	public ArmorSlot(Container container, LivingEntity livingEntity, EquipmentSlot equipmentSlot, int i, int j, int k, @Nullable Identifier identifier) {
		super(container, i, j, k);
		this.owner = livingEntity;
		this.slot = equipmentSlot;
		this.emptyIcon = identifier;
	}

	@Override
	public void setByPlayer(ItemStack itemStack, ItemStack itemStack2) {
		this.owner.onEquipItem(this.slot, itemStack2, itemStack);
		super.setByPlayer(itemStack, itemStack2);
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public boolean mayPlace(ItemStack itemStack) {
		return this.owner.isEquippableInSlot(itemStack, this.slot);
	}

	@Override
	public boolean isActive() {
		return this.owner.canUseSlot(this.slot);
	}

	@Override
	public boolean mayPickup(Player player) {
		ItemStack itemStack = this.getItem();
		return !itemStack.isEmpty() && !player.isCreative() && EnchantmentHelper.has(itemStack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)
			? false
			: super.mayPickup(player);
	}

	@Override
	public @Nullable Identifier getNoItemIcon() {
		return this.emptyIcon;
	}
}
