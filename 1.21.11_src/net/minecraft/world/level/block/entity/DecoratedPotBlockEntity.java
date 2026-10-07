package net.minecraft.world.level.block.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.ticks.ContainerSingleItem;
import org.jspecify.annotations.Nullable;

public class DecoratedPotBlockEntity extends BlockEntity implements RandomizableContainer, ContainerSingleItem.BlockContainerSingleItem {
	public static final String TAG_SHERDS = "sherds";
	public static final String TAG_ITEM = "item";
	public static final int EVENT_POT_WOBBLES = 1;
	public long wobbleStartedAtTick;
	public DecoratedPotBlockEntity.@Nullable WobbleStyle lastWobbleStyle;
	private PotDecorations decorations;
	private ItemStack item = ItemStack.EMPTY;
	protected @Nullable ResourceKey<LootTable> lootTable;
	protected long lootTableSeed;

	public DecoratedPotBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(BlockEntityType.DECORATED_POT, blockPos, blockState);
		this.decorations = PotDecorations.EMPTY;
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		if (!this.decorations.equals(PotDecorations.EMPTY)) {
			valueOutput.store("sherds", PotDecorations.CODEC, this.decorations);
		}

		if (!this.trySaveLootTable(valueOutput) && !this.item.isEmpty()) {
			valueOutput.store("item", ItemStack.CODEC, this.item);
		}
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		super.loadAdditional(valueInput);
		this.decorations = valueInput.read("sherds", PotDecorations.CODEC).orElse(PotDecorations.EMPTY);
		if (!this.tryLoadLootTable(valueInput)) {
			this.item = valueInput.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY);
		} else {
			this.item = ItemStack.EMPTY;
		}
	}

	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		return this.saveCustomOnly(provider);
	}

	public Direction getDirection() {
		return this.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
	}

	public PotDecorations getDecorations() {
		return this.decorations;
	}

	public static ItemStack createDecoratedPotItem(PotDecorations potDecorations) {
		ItemStack itemStack = Items.DECORATED_POT.getDefaultInstance();
		itemStack.set(DataComponents.POT_DECORATIONS, potDecorations);
		return itemStack;
	}

	@Override
	public @Nullable ResourceKey<LootTable> getLootTable() {
		return this.lootTable;
	}

	@Override
	public void setLootTable(@Nullable ResourceKey<LootTable> resourceKey) {
		this.lootTable = resourceKey;
	}

	@Override
	public long getLootTableSeed() {
		return this.lootTableSeed;
	}

	@Override
	public void setLootTableSeed(long l) {
		this.lootTableSeed = l;
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		builder.set(DataComponents.POT_DECORATIONS, this.decorations);
		builder.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(this.item)));
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter dataComponentGetter) {
		super.applyImplicitComponents(dataComponentGetter);
		this.decorations = dataComponentGetter.getOrDefault(DataComponents.POT_DECORATIONS, PotDecorations.EMPTY);
		this.item = dataComponentGetter.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyOne();
	}

	@Override
	public void removeComponentsFromTag(ValueOutput valueOutput) {
		super.removeComponentsFromTag(valueOutput);
		valueOutput.discard("sherds");
		valueOutput.discard("item");
	}

	@Override
	public ItemStack getTheItem() {
		this.unpackLootTable(null);
		return this.item;
	}

	@Override
	public ItemStack splitTheItem(int i) {
		this.unpackLootTable(null);
		ItemStack itemStack = this.item.split(i);
		if (this.item.isEmpty()) {
			this.item = ItemStack.EMPTY;
		}

		return itemStack;
	}

	@Override
	public void setTheItem(ItemStack itemStack) {
		this.unpackLootTable(null);
		this.item = itemStack;
	}

	@Override
	public BlockEntity getContainerBlockEntity() {
		return this;
	}

	public void wobble(DecoratedPotBlockEntity.WobbleStyle wobbleStyle) {
		if (this.level != null && !this.level.isClientSide()) {
			this.level.blockEvent(this.getBlockPos(), this.getBlockState().getBlock(), 1, wobbleStyle.ordinal());
		}
	}

	@Override
	public boolean triggerEvent(int i, int j) {
		if (this.level != null && i == 1 && j >= 0 && j < DecoratedPotBlockEntity.WobbleStyle.values().length) {
			this.wobbleStartedAtTick = this.level.getGameTime();
			this.lastWobbleStyle = DecoratedPotBlockEntity.WobbleStyle.values()[j];
			return true;
		} else {
			return super.triggerEvent(i, j);
		}
	}

	public enum WobbleStyle {
		POSITIVE(7),
		NEGATIVE(10);

		public final int duration;

		WobbleStyle(final int j) {
			this.duration = j;
		}
	}
}
