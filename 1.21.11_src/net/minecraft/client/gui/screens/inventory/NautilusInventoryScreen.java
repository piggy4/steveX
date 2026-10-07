package net.minecraft.client.gui.screens.inventory;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.NautilusInventoryMenu;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class NautilusInventoryScreen extends AbstractMountInventoryScreen<NautilusInventoryMenu> {
	private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("container/slot");
	private static final Identifier NAUTILUS_INVENTORY_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/nautilus.png");

	public NautilusInventoryScreen(NautilusInventoryMenu nautilusInventoryMenu, Inventory inventory, AbstractNautilus abstractNautilus, int i) {
		super(nautilusInventoryMenu, inventory, abstractNautilus.getDisplayName(), i, abstractNautilus);
	}

	@Override
	protected Identifier getBackgroundTextureLocation() {
		return NAUTILUS_INVENTORY_LOCATION;
	}

	@Override
	protected Identifier getSlotSpriteLocation() {
		return SLOT_SPRITE;
	}

	@Override
	protected @Nullable Identifier getChestSlotsSpriteLocation() {
		return null;
	}

	@Override
	protected boolean shouldRenderSaddleSlot() {
		return this.mount.canUseSlot(EquipmentSlot.SADDLE);
	}

	@Override
	protected boolean shouldRenderArmorSlot() {
		return this.mount.canUseSlot(EquipmentSlot.BODY);
	}
}
