package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.LockCode;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class BaseContainerBlockEntity extends BlockEntity implements Container, MenuProvider, Nameable {
	private LockCode lockKey = LockCode.NO_LOCK;
	private @Nullable Component name;

	protected BaseContainerBlockEntity(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
		super(blockEntityType, blockPos, blockState);
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		super.loadAdditional(valueInput);
		this.lockKey = LockCode.fromTag(valueInput);
		this.name = parseCustomNameSafe(valueInput, "CustomName");
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		this.lockKey.addToTag(valueOutput);
		valueOutput.storeNullable("CustomName", ComponentSerialization.CODEC, this.name);
	}

	@Override
	public Component getName() {
		return this.name != null ? this.name : this.getDefaultName();
	}

	@Override
	public Component getDisplayName() {
		return this.getName();
	}

	@Override
	public @Nullable Component getCustomName() {
		return this.name;
	}

	protected abstract Component getDefaultName();

	public boolean canOpen(Player player) {
		return this.lockKey.canUnlock(player);
	}

	public static void sendChestLockedNotifications(Vec3 vec3, Player player, Component component) {
		Level level = player.level();
		player.displayClientMessage(Component.translatable("container.isLocked", component), true);
		if (!level.isClientSide()) {
			level.playSound(null, vec3.x(), vec3.y(), vec3.z(), SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
	}

	public boolean isLocked() {
		return !this.lockKey.equals(LockCode.NO_LOCK);
	}

	protected abstract NonNullList<ItemStack> getItems();

	protected abstract void setItems(NonNullList<ItemStack> nonNullList);

	@Override
	public boolean isEmpty() {
		for (ItemStack itemStack : this.getItems()) {
			if (!itemStack.isEmpty()) {
				return false;
			}
		}

		return true;
	}

	@Override
	public ItemStack getItem(int i) {
		return this.getItems().get(i);
	}

	@Override
	public ItemStack removeItem(int i, int j) {
		ItemStack itemStack = ContainerHelper.removeItem(this.getItems(), i, j);
		if (!itemStack.isEmpty()) {
			this.setChanged();
		}

		return itemStack;
	}

	@Override
	public ItemStack removeItemNoUpdate(int i) {
		return ContainerHelper.takeItem(this.getItems(), i);
	}

	@Override
	public void setItem(int i, ItemStack itemStack) {
		this.getItems().set(i, itemStack);
		itemStack.limitSize(this.getMaxStackSize(itemStack));
		this.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		this.getItems().clear();
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
		if (this.canOpen(player)) {
			return this.createMenu(i, inventory);
		}

		sendChestLockedNotifications(this.getBlockPos().getCenter(), player, this.getDisplayName());
		return null;
	}

	protected abstract AbstractContainerMenu createMenu(int i, Inventory inventory);

	@Override
	protected void applyImplicitComponents(DataComponentGetter dataComponentGetter) {
		super.applyImplicitComponents(dataComponentGetter);
		this.name = dataComponentGetter.get(DataComponents.CUSTOM_NAME);
		this.lockKey = dataComponentGetter.getOrDefault(DataComponents.LOCK, LockCode.NO_LOCK);
		dataComponentGetter.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(this.getItems());
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		builder.set(DataComponents.CUSTOM_NAME, this.name);
		if (this.isLocked()) {
			builder.set(DataComponents.LOCK, this.lockKey);
		}

		builder.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.getItems()));
	}

	@Override
	public void removeComponentsFromTag(ValueOutput valueOutput) {
		valueOutput.discard("CustomName");
		valueOutput.discard("lock");
		valueOutput.discard("Items");
	}
}
