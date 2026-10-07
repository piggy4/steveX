package net.minecraft.world.entity.vehicle.minecart;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class AbstractMinecartContainer extends AbstractMinecart implements ContainerEntity {
	private NonNullList<ItemStack> itemStacks = NonNullList.withSize(36, ItemStack.EMPTY);
	private @Nullable ResourceKey<LootTable> lootTable;
	private long lootTableSeed;

	protected AbstractMinecartContainer(EntityType<?> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	public void destroy(ServerLevel serverLevel, DamageSource damageSource) {
		super.destroy(serverLevel, damageSource);
		this.chestVehicleDestroyed(damageSource, serverLevel, this);
	}

	@Override
	public ItemStack getItem(int i) {
		return this.getChestVehicleItem(i);
	}

	@Override
	public ItemStack removeItem(int i, int j) {
		return this.removeChestVehicleItem(i, j);
	}

	@Override
	public ItemStack removeItemNoUpdate(int i) {
		return this.removeChestVehicleItemNoUpdate(i);
	}

	@Override
	public void setItem(int i, ItemStack itemStack) {
		this.setChestVehicleItem(i, itemStack);
	}

	@Override
	public SlotAccess getSlot(int i) {
		return this.getChestVehicleSlot(i);
	}

	@Override
	public void setChanged() {
	}

	@Override
	public boolean stillValid(Player player) {
		return this.isChestVehicleStillValid(player);
	}

	@Override
	public void remove(Entity.RemovalReason removalReason) {
		if (!this.level().isClientSide() && removalReason.shouldDestroy()) {
			Containers.dropContents(this.level(), this, this);
		}

		super.remove(removalReason);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		this.addChestVehicleSaveData(valueOutput);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.readChestVehicleSaveData(valueInput);
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand interactionHand) {
		return this.interactWithContainerVehicle(player);
	}

	@Override
	protected Vec3 applyNaturalSlowdown(Vec3 vec3) {
		float f = 0.98F;
		if (this.lootTable == null) {
			int i = 15 - AbstractContainerMenu.getRedstoneSignalFromContainer(this);
			f += i * 0.001F;
		}

		if (this.isInWater()) {
			f *= 0.95F;
		}

		return vec3.multiply(f, 0.0, f);
	}

	@Override
	public void clearContent() {
		this.clearChestVehicleContent();
	}

	public void setLootTable(ResourceKey<LootTable> resourceKey, long l) {
		this.lootTable = resourceKey;
		this.lootTableSeed = l;
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
		if (this.lootTable != null && player.isSpectator()) {
			return null;
		}

		this.unpackChestVehicleLootTable(inventory.player);
		return this.createMenu(i, inventory);
	}

	protected abstract AbstractContainerMenu createMenu(int i, Inventory inventory);

	@Override
	public @Nullable ResourceKey<LootTable> getContainerLootTable() {
		return this.lootTable;
	}

	@Override
	public void setContainerLootTable(@Nullable ResourceKey<LootTable> resourceKey) {
		this.lootTable = resourceKey;
	}

	@Override
	public long getContainerLootTableSeed() {
		return this.lootTableSeed;
	}

	@Override
	public void setContainerLootTableSeed(long l) {
		this.lootTableSeed = l;
	}

	@Override
	public NonNullList<ItemStack> getItemStacks() {
		return this.itemStacks;
	}

	@Override
	public void clearItemStacks() {
		this.itemStacks = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
	}
}
