package net.minecraft.world.level.block.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ChestBlockEntity extends RandomizableContainerBlockEntity implements LidBlockEntity {
	private static final int EVENT_SET_OPEN_COUNT = 1;
	private static final Component DEFAULT_NAME = Component.translatable("container.chest");
	private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
	private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
		@Override
		protected void onOpen(Level level, BlockPos blockPos, BlockState blockState) {
			if (blockState.getBlock() instanceof ChestBlock chestBlock) {
				ChestBlockEntity.playSound(level, blockPos, blockState, chestBlock.getOpenChestSound());
			}
		}

		@Override
		protected void onClose(Level level, BlockPos blockPos, BlockState blockState) {
			if (blockState.getBlock() instanceof ChestBlock chestBlock) {
				ChestBlockEntity.playSound(level, blockPos, blockState, chestBlock.getCloseChestSound());
			}
		}

		@Override
		protected void openerCountChanged(Level level, BlockPos blockPos, BlockState blockState, int i, int j) {
			ChestBlockEntity.this.signalOpenCount(level, blockPos, blockState, i, j);
		}

		@Override
		public boolean isOwnContainer(Player player) {
			if (!(player.containerMenu instanceof ChestMenu)) {
				return false;
			}

			Container container = ((ChestMenu)player.containerMenu).getContainer();
			return container == ChestBlockEntity.this || container instanceof CompoundContainer && ((CompoundContainer)container).contains(ChestBlockEntity.this);
		}
	};
	private final ChestLidController chestLidController = new ChestLidController();

	protected ChestBlockEntity(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
		super(blockEntityType, blockPos, blockState);
	}

	public ChestBlockEntity(BlockPos blockPos, BlockState blockState) {
		this(BlockEntityType.CHEST, blockPos, blockState);
	}

	@Override
	public int getContainerSize() {
		return 27;
	}

	@Override
	protected Component getDefaultName() {
		return DEFAULT_NAME;
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
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		if (!this.trySaveLootTable(valueOutput)) {
			ContainerHelper.saveAllItems(valueOutput, this.items);
		}
	}

	public static void lidAnimateTick(Level level, BlockPos blockPos, BlockState blockState, ChestBlockEntity chestBlockEntity) {
		chestBlockEntity.chestLidController.tickLid();
	}

	static void playSound(Level level, BlockPos blockPos, BlockState blockState, SoundEvent soundEvent) {
		ChestType chestType = blockState.getValue(ChestBlock.TYPE);
		if (chestType != ChestType.LEFT) {
			double d = blockPos.getX() + 0.5;
			double e = blockPos.getY() + 0.5;
			double f = blockPos.getZ() + 0.5;
			if (chestType == ChestType.RIGHT) {
				Direction direction = ChestBlock.getConnectedDirection(blockState);
				d += direction.getStepX() * 0.5;
				f += direction.getStepZ() * 0.5;
			}

			level.playSound(null, d, e, f, soundEvent, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
		}
	}

	@Override
	public boolean triggerEvent(int i, int j) {
		if (i == 1) {
			this.chestLidController.shouldBeOpen(j > 0);
			return true;
		} else {
			return super.triggerEvent(i, j);
		}
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

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> nonNullList) {
		this.items = nonNullList;
	}

	@Override
	public float getOpenNess(float f) {
		return this.chestLidController.getOpenness(f);
	}

	public static int getOpenCount(BlockGetter blockGetter, BlockPos blockPos) {
		BlockState blockState = blockGetter.getBlockState(blockPos);
		if (blockState.hasBlockEntity()) {
			BlockEntity blockEntity = blockGetter.getBlockEntity(blockPos);
			if (blockEntity instanceof ChestBlockEntity) {
				return ((ChestBlockEntity)blockEntity).openersCounter.getOpenerCount();
			}
		}

		return 0;
	}

	public static void swapContents(ChestBlockEntity chestBlockEntity, ChestBlockEntity chestBlockEntity2) {
		NonNullList<ItemStack> nonNullList = chestBlockEntity.getItems();
		chestBlockEntity.setItems(chestBlockEntity2.getItems());
		chestBlockEntity2.setItems(nonNullList);
	}

	@Override
	protected AbstractContainerMenu createMenu(int i, Inventory inventory) {
		return ChestMenu.threeRows(i, inventory, this);
	}

	public void recheckOpen() {
		if (!this.remove) {
			this.openersCounter.recheckOpeners(this.getLevel(), this.getBlockPos(), this.getBlockState());
		}
	}

	protected void signalOpenCount(Level level, BlockPos blockPos, BlockState blockState, int i, int j) {
		Block block = blockState.getBlock();
		level.blockEvent(blockPos, block, 1, j);
	}
}
