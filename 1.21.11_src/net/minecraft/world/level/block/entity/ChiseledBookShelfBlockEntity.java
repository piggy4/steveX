package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.slf4j.Logger;

public class ChiseledBookShelfBlockEntity extends BlockEntity implements ListBackedContainer {
	public static final int MAX_BOOKS_IN_STORAGE = 6;
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int DEFAULT_LAST_INTERACTED_SLOT = -1;
	private final NonNullList<ItemStack> items = NonNullList.withSize(6, ItemStack.EMPTY);
	private int lastInteractedSlot = -1;

	public ChiseledBookShelfBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BlockEntityType.CHISELED_BOOKSHELF, blockPos, blockState);
	}

	private void updateState(int i) {
		if (i >= 0 && i < 6) {
			this.lastInteractedSlot = i;
			BlockState blockState = this.getBlockState();

			for (int j = 0; j < ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.size(); j++) {
				boolean bl = !this.getItem(j).isEmpty();
				BooleanProperty booleanProperty = ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(j);
				blockState = blockState.setValue(booleanProperty, bl);
			}

			Objects.requireNonNull(this.level).setBlock(this.worldPosition, blockState, 3);
			this.level.gameEvent(GameEvent.BLOCK_CHANGE, this.worldPosition, GameEvent.Context.of(blockState));
		} else {
			LOGGER.error("Expected slot 0-5, got {}", i);
		}
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		super.loadAdditional(valueInput);
		this.items.clear();
		ContainerHelper.loadAllItems(valueInput, this.items);
		this.lastInteractedSlot = valueInput.getIntOr("last_interacted_slot", -1);
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		ContainerHelper.saveAllItems(valueOutput, this.items, true);
		valueOutput.putInt("last_interacted_slot", this.lastInteractedSlot);
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public boolean acceptsItemType(ItemStack itemStack) {
		return itemStack.is(ItemTags.BOOKSHELF_BOOKS);
	}

	@Override
	public ItemStack removeItem(int i, int j) {
		ItemStack itemStack = Objects.requireNonNullElse(this.getItems().get(i), ItemStack.EMPTY);
		this.getItems().set(i, ItemStack.EMPTY);
		if (!itemStack.isEmpty()) {
			this.updateState(i);
		}

		return itemStack;
	}

	@Override
	public void setItem(int i, ItemStack itemStack) {
		if (this.acceptsItemType(itemStack)) {
			this.getItems().set(i, itemStack);
			this.updateState(i);
		} else if (itemStack.isEmpty()) {
			this.removeItem(i, this.getMaxStackSize());
		}
	}

	@Override
	public boolean canTakeItem(Container container, int i, ItemStack itemStack) {
		return container.hasAnyMatching(
			itemStack2 -> itemStack2.isEmpty()
				? true
				: ItemStack.isSameItemSameComponents(itemStack, itemStack2) && itemStack2.getCount() + itemStack.getCount() <= container.getMaxStackSize(itemStack2)
		);
	}

	@Override
	public NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	public int getLastInteractedSlot() {
		return this.lastInteractedSlot;
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter dataComponentGetter) {
		super.applyImplicitComponents(dataComponentGetter);
		dataComponentGetter.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(this.items);
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		builder.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.items));
	}

	@Override
	public void removeComponentsFromTag(ValueOutput valueOutput) {
		valueOutput.discard("Items");
	}
}
