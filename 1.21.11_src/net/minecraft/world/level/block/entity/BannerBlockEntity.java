package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class BannerBlockEntity extends BlockEntity implements Nameable {
	public static final int MAX_PATTERNS = 6;
	private static final String TAG_PATTERNS = "patterns";
	private static final Component DEFAULT_NAME = Component.translatable("block.minecraft.banner");
	private @Nullable Component name;
	private final DyeColor baseColor;
	private BannerPatternLayers patterns = BannerPatternLayers.EMPTY;

	public BannerBlockEntity(BlockPos blockPos, BlockState blockState) {
		this(blockPos, blockState, ((AbstractBannerBlock)blockState.getBlock()).getColor());
	}

	public BannerBlockEntity(BlockPos blockPos, BlockState blockState, DyeColor dyeColor) {
		super(BlockEntityType.BANNER, blockPos, blockState);
		this.baseColor = dyeColor;
	}

	@Override
	public Component getName() {
		return this.name != null ? this.name : DEFAULT_NAME;
	}

	@Override
	public @Nullable Component getCustomName() {
		return this.name;
	}

	@Override
	protected void saveAdditional(ValueOutput valueOutput) {
		super.saveAdditional(valueOutput);
		if (!this.patterns.equals(BannerPatternLayers.EMPTY)) {
			valueOutput.store("patterns", BannerPatternLayers.CODEC, this.patterns);
		}

		valueOutput.storeNullable("CustomName", ComponentSerialization.CODEC, this.name);
	}

	@Override
	protected void loadAdditional(ValueInput valueInput) {
		super.loadAdditional(valueInput);
		this.name = parseCustomNameSafe(valueInput, "CustomName");
		this.patterns = valueInput.read("patterns", BannerPatternLayers.CODEC).orElse(BannerPatternLayers.EMPTY);
	}

	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		return this.saveWithoutMetadata(provider);
	}

	public BannerPatternLayers getPatterns() {
		return this.patterns;
	}

	public ItemStack getItem() {
		ItemStack itemStack = new ItemStack(BannerBlock.byColor(this.baseColor));
		itemStack.applyComponents(this.collectComponents());
		return itemStack;
	}

	public DyeColor getBaseColor() {
		return this.baseColor;
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter dataComponentGetter) {
		super.applyImplicitComponents(dataComponentGetter);
		this.patterns = dataComponentGetter.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
		this.name = dataComponentGetter.get(DataComponents.CUSTOM_NAME);
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		builder.set(DataComponents.BANNER_PATTERNS, this.patterns);
		builder.set(DataComponents.CUSTOM_NAME, this.name);
	}

	@Override
	public void removeComponentsFromTag(ValueOutput valueOutput) {
		valueOutput.discard("patterns");
		valueOutput.discard("CustomName");
	}
}
