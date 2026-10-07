package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShelfBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ShelfBlockEntity extends BlockEntity implements ItemOwner, ListBackedContainer {
	public static final int MAX_ITEMS = 3;
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String ALIGN_ITEMS_TO_BOTTOM_TAG = "align_items_to_bottom";
	private final NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
	private boolean alignItemsToBottom;

	public ShelfBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BlockEntityType.SHELF, blockPos, blockState);
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		super.loadAdditional(valueInput);
		this.items.clear();
		ContainerHelper.loadAllItems(valueInput, this.items);
		this.alignItemsToBottom = valueInput.getBooleanOr("align_items_to_bottom", false);
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		ContainerHelper.saveAllItems(valueOutput, this.items, true);
		valueOutput.putBoolean("align_items_to_bottom", this.alignItemsToBottom);
	}

	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
			TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, provider);
			ContainerHelper.saveAllItems(tagValueOutput, this.items, true);
			tagValueOutput.putBoolean("align_items_to_bottom", this.alignItemsToBottom);
			return tagValueOutput.buildResult();
		}
	}

	@Override
	public NonNullList<ItemStack> getItems() {
		return this.items;
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	public ItemStack swapItemNoUpdate(int i, ItemStack itemStack) {
		ItemStack itemStack2 = this.removeItemNoUpdate(i);
		this.setItemNoUpdate(i, itemStack);
		return itemStack2;
	}

	public void setChanged(Holder.@Nullable Reference<GameEvent> reference) {
		super.setChanged();
		if (this.level != null) {
			if (reference != null) {
				this.level.gameEvent(reference, this.worldPosition, GameEvent.Context.of(this.getBlockState()));
			}

			this.getLevel().sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
		}
	}

	@Override
	public void setChanged() {
		this.setChanged(GameEvent.BLOCK_ACTIVATE);
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

	@Override
	public Level level() {
		return this.level;
	}

	@Override
	public Vec3 position() {
		return this.getBlockPos().getCenter();
	}

	@Override
	public float getVisualRotationYInDegrees() {
		return this.getBlockState().getValue(ShelfBlock.FACING).getOpposite().toYRot();
	}

	public boolean getAlignItemsToBottom() {
		return this.alignItemsToBottom;
	}
}
