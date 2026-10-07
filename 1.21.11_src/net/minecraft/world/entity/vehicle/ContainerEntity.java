package net.minecraft.world.entity.vehicle;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public interface ContainerEntity extends Container, MenuProvider {
	Vec3 position();

	AABB getBoundingBox();

	@Nullable ResourceKey<LootTable> getContainerLootTable();

	void setContainerLootTable(@Nullable ResourceKey<LootTable> resourceKey);

	long getContainerLootTableSeed();

	void setContainerLootTableSeed(long l);

	NonNullList<ItemStack> getItemStacks();

	void clearItemStacks();

	Level level();

	boolean isRemoved();

	@Override
	default boolean isEmpty() {
		return this.isChestVehicleEmpty();
	}

	default void addChestVehicleSaveData(ValueOutput valueOutput) {
		if (this.getContainerLootTable() != null) {
			valueOutput.putString("LootTable", this.getContainerLootTable().identifier().toString());
			if (this.getContainerLootTableSeed() != 0L) {
				valueOutput.putLong("LootTableSeed", this.getContainerLootTableSeed());
			}
		} else {
			ContainerHelper.saveAllItems(valueOutput, this.getItemStacks());
		}
	}

	default void readChestVehicleSaveData(ValueInput valueInput) {
		this.clearItemStacks();
		ResourceKey<LootTable> resourceKey = valueInput.read("LootTable", LootTable.KEY_CODEC).orElse(null);
		this.setContainerLootTable(resourceKey);
		this.setContainerLootTableSeed(valueInput.getLongOr("LootTableSeed", 0L));
		if (resourceKey == null) {
			ContainerHelper.loadAllItems(valueInput, this.getItemStacks());
		}
	}

	default void chestVehicleDestroyed(DamageSource damageSource, ServerLevel serverLevel, Entity entity) {
		if (serverLevel.getGameRules().get(GameRules.ENTITY_DROPS)) {
			Containers.dropContents(serverLevel, entity, this);
			Entity entity2 = damageSource.getDirectEntity();
			if (entity2 != null && entity2.getType() == EntityType.PLAYER) {
				PiglinAi.angerNearbyPiglins(serverLevel, (Player)entity2, true);
			}
		}
	}

	default InteractionResult interactWithContainerVehicle(Player player) {
		player.openMenu(this);
		return InteractionResult.SUCCESS;
	}

	default void unpackChestVehicleLootTable(@Nullable Player player) {
		MinecraftServer minecraftServer = this.level().getServer();
		if (this.getContainerLootTable() != null && minecraftServer != null) {
			LootTable lootTable = minecraftServer.reloadableRegistries().getLootTable(this.getContainerLootTable());
			if (player != null) {
				CriteriaTriggers.GENERATE_LOOT.trigger((ServerPlayer)player, this.getContainerLootTable());
			}

			this.setContainerLootTable(null);
			LootParams.Builder builder = new LootParams.Builder((ServerLevel)this.level()).withParameter(LootContextParams.ORIGIN, this.position());
			if (player != null) {
				builder.withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player);
			}

			lootTable.fill(this, builder.create(LootContextParamSets.CHEST), this.getContainerLootTableSeed());
		}
	}

	default void clearChestVehicleContent() {
		this.unpackChestVehicleLootTable(null);
		this.getItemStacks().clear();
	}

	default boolean isChestVehicleEmpty() {
		for (ItemStack itemStack : this.getItemStacks()) {
			if (!itemStack.isEmpty()) {
				return false;
			}
		}

		return true;
	}

	default ItemStack removeChestVehicleItemNoUpdate(int i) {
		this.unpackChestVehicleLootTable(null);
		ItemStack itemStack = this.getItemStacks().get(i);
		if (itemStack.isEmpty()) {
			return ItemStack.EMPTY;
		}

		this.getItemStacks().set(i, ItemStack.EMPTY);
		return itemStack;
	}

	default ItemStack getChestVehicleItem(int i) {
		this.unpackChestVehicleLootTable(null);
		return this.getItemStacks().get(i);
	}

	default ItemStack removeChestVehicleItem(int i, int j) {
		this.unpackChestVehicleLootTable(null);
		return ContainerHelper.removeItem(this.getItemStacks(), i, j);
	}

	default void setChestVehicleItem(int i, ItemStack itemStack) {
		this.unpackChestVehicleLootTable(null);
		this.getItemStacks().set(i, itemStack);
		itemStack.limitSize(this.getMaxStackSize(itemStack));
	}

	default @Nullable SlotAccess getChestVehicleSlot(int i) {
		return i >= 0 && i < this.getContainerSize() ? new SlotAccess() {
			@Override
			public ItemStack get() {
				return ContainerEntity.this.getChestVehicleItem(i);
			}

			@Override
			public boolean set(ItemStack itemStack) {
				ContainerEntity.this.setChestVehicleItem(i, itemStack);
				return true;
			}
		} : null;
	}

	default boolean isChestVehicleStillValid(Player player) {
		return !this.isRemoved() && player.isWithinEntityInteractionRange(this.getBoundingBox(), 4.0);
	}
}
