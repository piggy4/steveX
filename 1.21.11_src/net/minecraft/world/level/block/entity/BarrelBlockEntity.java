package net.minecraft.world.level.block.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class BarrelBlockEntity extends RandomizableContainerBlockEntity {
	private static final Component DEFAULT_NAME = Component.translatable("container.barrel");
	private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
	private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
		@Override
		protected void onOpen(Level level, BlockPos blockPos, BlockState blockState) {
			BarrelBlockEntity.this.playSound(blockState, SoundEvents.BARREL_OPEN);
			BarrelBlockEntity.this.updateBlockState(blockState, true);
		}

		@Override
		protected void onClose(Level level, BlockPos blockPos, BlockState blockState) {
			BarrelBlockEntity.this.playSound(blockState, SoundEvents.BARREL_CLOSE);
			BarrelBlockEntity.this.updateBlockState(blockState, false);
		}

		@Override
		protected void openerCountChanged(Level level, BlockPos blockPos, BlockState blockState, int i, int j) {
		}

		@Override
		public boolean isOwnContainer(Player player) {
			if (player.containerMenu instanceof ChestMenu) {
				Container container = ((ChestMenu)player.containerMenu).getContainer();
				return container == BarrelBlockEntity.this;
			} else {
				return false;
			}
		}
	};

	public BarrelBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BlockEntityType.BARREL, blockPos, blockState);
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		if (!this.trySaveLootTable(valueOutput)) {
			ContainerHelper.saveAllItems(valueOutput, this.items);
		}
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		super.loadAdditional(valueInput);
		this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
		if (!this.tryLoadLootTable(valueInput)) {
			ContainerHelper.loadAllItems(valueInput, this.items);
		}
	}

	@Override
	public int getContainerSize() {
		return 27;
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> nonNullList) {
		this.items = nonNullList;
	}

	@Override
	protected Component getDefaultName() {
		return DEFAULT_NAME;
	}

	@Override
	protected AbstractContainerMenu createMenu(int i, Inventory inventory) {
		return ChestMenu.threeRows(i, inventory, this);
	}

	@Override
	public void startOpen(ContainerUser containerUser) {
		if (!this.remove && !containerUser.getLivingEntity().isSpectator()) {
			this.openersCounter
				.incrementOpeners(containerUser.getLivingEntity(), this.getLevel(), this.getBlockPos(), this.getBlockState(), containerUser.getContainerInteractionRange());
		}
	}

	@Override
	public void stopOpen(ContainerUser containerUser) {
		if (!this.remove && !containerUser.getLivingEntity().isSpectator()) {
			this.openersCounter.decrementOpeners(containerUser.getLivingEntity(), this.getLevel(), this.getBlockPos(), this.getBlockState());
		}
	}

	@Override
	public List<ContainerUser> getEntitiesWithContainerOpen() {
		return this.openersCounter.getEntitiesWithContainerOpen(this.getLevel(), this.getBlockPos());
	}

	public void recheckOpen() {
		if (!this.remove) {
			this.openersCounter.recheckOpeners(this.getLevel(), this.getBlockPos(), this.getBlockState());
		}
	}

	void updateBlockState(BlockState blockState, boolean bl) {
		this.level.setBlock(this.getBlockPos(), blockState.setValue(BarrelBlock.OPEN, bl), 3);
	}

	void playSound(BlockState blockState, SoundEvent soundEvent) {
		Vec3i vec3i = blockState.getValue(BarrelBlock.FACING).getUnitVec3i();
		double d = this.worldPosition.getX() + 0.5 + vec3i.getX() / 2.0;
		double e = this.worldPosition.getY() + 0.5 + vec3i.getY() / 2.0;
		double f = this.worldPosition.getZ() + 0.5 + vec3i.getZ() / 2.0;
		this.level.playSound(null, d, e, f, soundEvent, SoundSource.BLOCKS, 0.5F, this.level.random.nextFloat() * 0.1F + 0.9F);
	}
}
