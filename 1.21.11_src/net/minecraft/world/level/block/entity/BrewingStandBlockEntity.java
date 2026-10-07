package net.minecraft.world.level.block.entity;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class BrewingStandBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
	private static final int INGREDIENT_SLOT = 3;
	private static final int FUEL_SLOT = 4;
	private static final int[] SLOTS_FOR_UP = new int[]{3};
	private static final int[] SLOTS_FOR_DOWN = new int[]{0, 1, 2, 3};
	private static final int[] SLOTS_FOR_SIDES = new int[]{0, 1, 2, 4};
	public static final int FUEL_USES = 20;
	public static final int DATA_BREW_TIME = 0;
	public static final int DATA_FUEL_USES = 1;
	public static final int NUM_DATA_VALUES = 2;
	private static final short DEFAULT_BREW_TIME = 0;
	private static final byte DEFAULT_FUEL = 0;
	private static final Component DEFAULT_NAME = Component.translatable("container.brewing");
	private NonNullList<ItemStack> items = NonNullList.withSize(5, ItemStack.EMPTY);
	int brewTime;
	private boolean[] lastPotionCount;
	private Item ingredient;
	int fuel;
	protected final ContainerData dataAccess = new ContainerData() {
		@Override
		public int get(int i) {
			return switch (i) {
				case 0 -> BrewingStandBlockEntity.this.brewTime;
				case 1 -> BrewingStandBlockEntity.this.fuel;
				default -> 0;
			};
		}

		@Override
		public void set(int i, int j) {
			switch (i) {
				case 0:
					BrewingStandBlockEntity.this.brewTime = j;
					break;
				case 1:
					BrewingStandBlockEntity.this.fuel = j;
			}
		}

		@Override
		public int getCount() {
			return 2;
		}
	};

	public BrewingStandBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BlockEntityType.BREWING_STAND, blockPos, blockState);
	}

	@Override
	protected Component getDefaultName() {
		return DEFAULT_NAME;
	}

	@Override
	public int getContainerSize() {
		return this.items.size();
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> nonNullList) {
		this.items = nonNullList;
	}

	public static void serverTick(Level level, BlockPos blockPos, BlockState blockState, BrewingStandBlockEntity brewingStandBlockEntity) {
		ItemStack itemStack = brewingStandBlockEntity.items.get(4);
		if (brewingStandBlockEntity.fuel <= 0 && itemStack.is(ItemTags.BREWING_FUEL)) {
			brewingStandBlockEntity.fuel = 20;
			itemStack.shrink(1);
			setChanged(level, blockPos, blockState);
		}

		boolean bl = isBrewable(level.potionBrewing(), brewingStandBlockEntity.items);
		boolean bl2 = brewingStandBlockEntity.brewTime > 0;
		ItemStack itemStack2 = brewingStandBlockEntity.items.get(3);
		if (bl2) {
			brewingStandBlockEntity.brewTime--;
			boolean bl3 = brewingStandBlockEntity.brewTime == 0;
			if (bl3 && bl) {
				doBrew(level, blockPos, brewingStandBlockEntity.items);
			} else if (!bl || !itemStack2.is(brewingStandBlockEntity.ingredient)) {
				brewingStandBlockEntity.brewTime = 0;
			}

			setChanged(level, blockPos, blockState);
		} else if (bl && brewingStandBlockEntity.fuel > 0) {
			brewingStandBlockEntity.fuel--;
			brewingStandBlockEntity.brewTime = 400;
			brewingStandBlockEntity.ingredient = itemStack2.getItem();
			setChanged(level, blockPos, blockState);
		}

		boolean[] bls = brewingStandBlockEntity.getPotionBits();
		if (!Arrays.equals(bls, brewingStandBlockEntity.lastPotionCount)) {
			brewingStandBlockEntity.lastPotionCount = bls;
			BlockState blockState2 = blockState;
			if (!(blockState2.getBlock() instanceof BrewingStandBlock)) {
				return;
			}

			for (int i = 0; i < BrewingStandBlock.HAS_BOTTLE.length; i++) {
				blockState2 = blockState2.setValue(BrewingStandBlock.HAS_BOTTLE[i], bls[i]);
			}

			level.setBlock(blockPos, blockState2, 2);
		}
	}

	private boolean[] getPotionBits() {
		boolean[] bls = new boolean[3];

		for (int i = 0; i < 3; i++) {
			if (!this.items.get(i).isEmpty()) {
				bls[i] = true;
			}
		}

		return bls;
	}

	private static boolean isBrewable(PotionBrewing potionBrewing, NonNullList<ItemStack> nonNullList) {
		ItemStack itemStack = nonNullList.get(3);
		if (itemStack.isEmpty()) {
			return false;
		}

		if (!potionBrewing.isIngredient(itemStack)) {
			return false;
		}

		for (int i = 0; i < 3; i++) {
			ItemStack itemStack2 = nonNullList.get(i);
			if (!itemStack2.isEmpty() && potionBrewing.hasMix(itemStack2, itemStack)) {
				return true;
			}
		}

		return false;
	}

	private static void doBrew(Level level, BlockPos blockPos, NonNullList<ItemStack> nonNullList) {
		ItemStack itemStack = nonNullList.get(3);
		PotionBrewing potionBrewing = level.potionBrewing();

		for (int i = 0; i < 3; i++) {
			nonNullList.set(i, potionBrewing.mix(itemStack, nonNullList.get(i)));
		}

		itemStack.shrink(1);
		ItemStack itemStack2 = itemStack.getItem().getCraftingRemainder();
		if (!itemStack2.isEmpty()) {
			if (itemStack.isEmpty()) {
				itemStack = itemStack2;
			} else {
				Containers.dropItemStack(level, blockPos.getX(), blockPos.getY(), blockPos.getZ(), itemStack2);
			}
		}

		nonNullList.set(3, itemStack);
		level.levelEvent(1035, blockPos, 0);
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		super.loadAdditional(valueInput);
		this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
		ContainerHelper.loadAllItems(valueInput, this.items);
		this.brewTime = valueInput.getShortOr("BrewTime", (short)0);
		if (this.brewTime > 0) {
			this.ingredient = this.items.get(3).getItem();
		}

		this.fuel = valueInput.getByteOr("Fuel", (byte)0);
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		valueOutput.putShort("BrewTime", (short)this.brewTime);
		ContainerHelper.saveAllItems(valueOutput, this.items);
		valueOutput.putByte("Fuel", (byte)this.fuel);
	}

	@Override
	public boolean canPlaceItem(int i, ItemStack itemStack) {
		if (i == 3) {
			PotionBrewing potionBrewing = this.level != null ? this.level.potionBrewing() : PotionBrewing.EMPTY;
			return potionBrewing.isIngredient(itemStack);
		} else {
			return i == 4
				? itemStack.is(ItemTags.BREWING_FUEL)
				: (itemStack.is(Items.POTION) || itemStack.is(Items.SPLASH_POTION) || itemStack.is(Items.LINGERING_POTION) || itemStack.is(Items.GLASS_BOTTLE))
					&& this.getItem(i).isEmpty();
		}
	}

	@Override
	public int[] getSlotsForFace(Direction direction) {
		if (direction == Direction.UP) {
			return SLOTS_FOR_UP;
		} else {
			return direction == Direction.DOWN ? SLOTS_FOR_DOWN : SLOTS_FOR_SIDES;
		}
	}

	@Override
	public boolean canPlaceItemThroughFace(int i, ItemStack itemStack, @Nullable Direction direction) {
		return this.canPlaceItem(i, itemStack);
	}

	@Override
	public boolean canTakeItemThroughFace(int i, ItemStack itemStack, Direction direction) {
		return i == 3 ? itemStack.is(Items.GLASS_BOTTLE) : true;
	}

	@Override
	protected AbstractContainerMenu createMenu(int i, Inventory inventory) {
		return new BrewingStandMenu(i, inventory, this, this.dataAccess);
	}
}
