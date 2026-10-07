package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.blockview.v2.RenderDataBlockEntity;
import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.debug.DebugValueSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.blockview.v2.RenderDataBlockEntity} injected by mod fabric-block-view-api-v2</p>
 * <p>Interface {@link net.fabricmc.fabric.api.attachment.v1.AttachmentTarget} injected by mod fabric-data-attachment-api-v1</p>
 */
public abstract class BlockEntity implements DebugValueSource, RenderDataBlockEntity, AttachmentTarget {
	private static final Codec<BlockEntityType<?>> TYPE_CODEC = BuiltInRegistries.BLOCK_ENTITY_TYPE.byNameCodec();
	private static final Logger LOGGER = LogUtils.getLogger();
	private final BlockEntityType<?> type;
	protected @Nullable Level level;
	protected final BlockPos worldPosition;
	protected boolean remove;
	private BlockState blockState;
	private DataComponentMap components = DataComponentMap.EMPTY;

	public BlockEntity(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
		this.type = blockEntityType;
		this.worldPosition = blockPos.immutable();
		this.validateBlockState(blockState);
		this.blockState = blockState;
	}

	private void validateBlockState(BlockState blockState) {
		if (!this.isValidBlockState(blockState)) {
			throw new IllegalStateException("Invalid block entity " + this.getNameForReporting() + " state at " + this.worldPosition + ", got " + blockState);
		}
	}

	public boolean isValidBlockState(BlockState blockState) {
		return this.type.isValid(blockState);
	}

	public static BlockPos getPosFromTag(ChunkPos chunkPos, CompoundTag compoundTag) {
		int i = compoundTag.getIntOr("x", 0);
		int j = compoundTag.getIntOr("y", 0);
		int k = compoundTag.getIntOr("z", 0);
		int l = SectionPos.blockToSectionCoord(i);
		int m = SectionPos.blockToSectionCoord(k);
		if (l != chunkPos.x || m != chunkPos.z) {
			LOGGER.warn("Block entity {} found in a wrong chunk, expected position from chunk {}", compoundTag, chunkPos);
			i = chunkPos.getBlockX(SectionPos.sectionRelative(i));
			k = chunkPos.getBlockZ(SectionPos.sectionRelative(k));
		}

		return new BlockPos(i, j, k);
	}

	public @Nullable Level getLevel() {
		return this.level;
	}

	public void setLevel(Level level) {
		this.level = level;
	}

	public boolean hasLevel() {
		return this.level != null;
	}

	protected void loadAdditional(ValueInput valueInput) {
	}

	public final void loadWithComponents(ValueInput valueInput) {
		this.loadAdditional(valueInput);
		this.components = valueInput.read("components", DataComponentMap.CODEC).orElse(DataComponentMap.EMPTY);
	}

	public final void loadCustomOnly(ValueInput valueInput) {
		this.loadAdditional(valueInput);
	}

	protected void saveAdditional(ValueOutput valueOutput) {
	}

	public final CompoundTag saveWithFullMetadata(HolderLookup.Provider provider) {
		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
			TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, provider);
			this.saveWithFullMetadata(tagValueOutput);
			return tagValueOutput.buildResult();
		}
	}

	public void saveWithFullMetadata(ValueOutput valueOutput) {
		this.saveWithoutMetadata(valueOutput);
		this.saveMetadata(valueOutput);
	}

	public void saveWithId(ValueOutput valueOutput) {
		this.saveWithoutMetadata(valueOutput);
		this.saveId(valueOutput);
	}

	public final CompoundTag saveWithoutMetadata(HolderLookup.Provider provider) {
		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
			TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, provider);
			this.saveWithoutMetadata(tagValueOutput);
			return tagValueOutput.buildResult();
		}
	}

	public void saveWithoutMetadata(ValueOutput valueOutput) {
		this.saveAdditional(valueOutput);
		valueOutput.store("components", DataComponentMap.CODEC, this.components);
	}

	public final CompoundTag saveCustomOnly(HolderLookup.Provider provider) {
		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
			TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, provider);
			this.saveCustomOnly(tagValueOutput);
			return tagValueOutput.buildResult();
		}
	}

	public void saveCustomOnly(ValueOutput valueOutput) {
		this.saveAdditional(valueOutput);
	}

	private void saveId(ValueOutput valueOutput) {
		addEntityType(valueOutput, this.getType());
	}

	public static void addEntityType(ValueOutput valueOutput, BlockEntityType<?> blockEntityType) {
		valueOutput.store("id", TYPE_CODEC, blockEntityType);
	}

	private void saveMetadata(ValueOutput valueOutput) {
		this.saveId(valueOutput);
		valueOutput.putInt("x", this.worldPosition.getX());
		valueOutput.putInt("y", this.worldPosition.getY());
		valueOutput.putInt("z", this.worldPosition.getZ());
	}

	public static @Nullable BlockEntity loadStatic(BlockPos blockPos, BlockState blockState, CompoundTag compoundTag, HolderLookup.Provider provider) {
		BlockEntityType<?> blockEntityType = compoundTag.read("id", TYPE_CODEC).orElse(null);
		if (blockEntityType == null) {
			LOGGER.error("Skipping block entity with invalid type: {}", compoundTag.get("id"));
			return null;
		}

		BlockEntity blockEntity;
		try {
			blockEntity = blockEntityType.create(blockPos, blockState);
		} catch (Throwable throwable) {
			LOGGER.error("Failed to create block entity {} for block {} at position {} ", blockEntityType, blockPos, blockState, throwable);
			return null;
		}

		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(blockEntity.problemPath(), LOGGER)) {
			blockEntity.loadWithComponents(TagValueInput.create(scopedCollector, provider, compoundTag));
			return blockEntity;
		} catch (Throwable throwable) {
			LOGGER.error("Failed to load data for block entity {} for block {} at position {}", blockEntityType, blockPos, blockState, throwable);
			return null;
		}
	}

	public void setChanged() {
		if (this.level != null) {
			setChanged(this.level, this.worldPosition, this.blockState);
		}
	}

	protected static void setChanged(Level level, BlockPos blockPos, BlockState blockState) {
		level.blockEntityChanged(blockPos);
		if (!blockState.isAir()) {
			level.updateNeighbourForOutputSignal(blockPos, blockState.getBlock());
		}
	}

	public BlockPos getBlockPos() {
		return this.worldPosition;
	}

	public BlockState getBlockState() {
		return this.blockState;
	}

	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return null;
	}

	public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
		return new CompoundTag();
	}

	public boolean isRemoved() {
		return this.remove;
	}

	public void setRemoved() {
		this.remove = true;
	}

	public void clearRemoved() {
		this.remove = false;
	}

	public void preRemoveSideEffects(BlockPos blockPos, BlockState blockState) {
		if (this instanceof Container container && this.level != null) {
			Containers.dropContents(this.level, blockPos, container);
		}
	}

	public boolean triggerEvent(int i, int j) {
		return false;
	}

	public void fillCrashReportCategory(CrashReportCategory crashReportCategory) {
		crashReportCategory.setDetail("Name", this::getNameForReporting);
		crashReportCategory.setDetail("Cached block", this.getBlockState()::toString);
		if (this.level == null) {
			crashReportCategory.setDetail("Block location", () -> this.worldPosition + " (world missing)");
		} else {
			crashReportCategory.setDetail("Actual block", this.level.getBlockState(this.worldPosition)::toString);
			CrashReportCategory.populateBlockLocationDetails(crashReportCategory, this.level, this.worldPosition);
		}
	}

	public String getNameForReporting() {
		return BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(this.getType()) + " // " + this.getClass().getCanonicalName();
	}

	public BlockEntityType<?> getType() {
		return this.type;
	}

	@Deprecated
	public void setBlockState(BlockState blockState) {
		this.validateBlockState(blockState);
		this.blockState = blockState;
	}

	protected void applyImplicitComponents(DataComponentGetter dataComponentGetter) {
	}

	public final void applyComponentsFromItemStack(ItemStack itemStack) {
		this.applyComponents(itemStack.getPrototype(), itemStack.getComponentsPatch());
	}

	public final void applyComponents(DataComponentMap dataComponentMap, DataComponentPatch dataComponentPatch) {
		final Set<DataComponentType<?>> set = new HashSet<>();
		set.add(DataComponents.BLOCK_ENTITY_DATA);
		set.add(DataComponents.BLOCK_STATE);
		final DataComponentMap dataComponentMap2 = PatchedDataComponentMap.fromPatch(dataComponentMap, dataComponentPatch);
		this.applyImplicitComponents(new DataComponentGetter() {
			@Override
			public <T> @Nullable T get(DataComponentType<? extends T> dataComponentType) {
				set.add(dataComponentType);
				return dataComponentMap2.get(dataComponentType);
			}

			@Override
			public <T> T getOrDefault(DataComponentType<? extends T> dataComponentType, T object) {
				set.add(dataComponentType);
				return dataComponentMap2.getOrDefault(dataComponentType, object);
			}
		});
		DataComponentPatch dataComponentPatch2 = dataComponentPatch.forget(set::contains);
		this.components = dataComponentPatch2.split().added();
	}

	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
	}

	@Deprecated
	public void removeComponentsFromTag(ValueOutput valueOutput) {
	}

	public final DataComponentMap collectComponents() {
		DataComponentMap.Builder builder = DataComponentMap.builder();
		builder.addAll(this.components);
		this.collectImplicitComponents(builder);
		return builder.build();
	}

	public DataComponentMap components() {
		return this.components;
	}

	public void setComponents(DataComponentMap dataComponentMap) {
		this.components = dataComponentMap;
	}

	public static @Nullable Component parseCustomNameSafe(ValueInput valueInput, String string) {
		return valueInput.read(string, ComponentSerialization.CODEC).orElse(null);
	}

	public ProblemReporter.PathElement problemPath() {
		return new BlockEntity.BlockEntityPathElement(this);
	}

	@Override
	public void registerDebugValues(ServerLevel serverLevel, DebugValueSource.Registration registration) {
	}

	record BlockEntityPathElement(BlockEntity blockEntity) implements ProblemReporter.PathElement {
		@Override
		public String get() {
			return this.blockEntity.getNameForReporting() + "@" + this.blockEntity.getBlockPos();
		}
	}
}
