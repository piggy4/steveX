package net.minecraft.commands.arguments.blocks;

import com.mojang.logging.LogUtils;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class BlockInput implements Predicate<BlockInWorld> {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final BlockState state;
	private final Set<Property<?>> properties;
	private final @Nullable CompoundTag tag;

	public BlockInput(BlockState blockState, Set<Property<?>> set, @Nullable CompoundTag compoundTag) {
		this.state = blockState;
		this.properties = set;
		this.tag = compoundTag;
	}

	public BlockState getState() {
		return this.state;
	}

	public Set<Property<?>> getDefinedProperties() {
		return this.properties;
	}

	public boolean test(BlockInWorld blockInWorld) {
		BlockState blockState = blockInWorld.getState();
		if (!blockState.is(this.state.getBlock())) {
			return false;
		}

		for (Property<?> property : this.properties) {
			if (blockState.getValue(property) != this.state.getValue(property)) {
				return false;
			}
		}

		if (this.tag == null) {
			return true;
		}

		BlockEntity blockEntity = blockInWorld.getEntity();
		return blockEntity != null && NbtUtils.compareNbt(this.tag, blockEntity.saveWithFullMetadata(blockInWorld.getLevel().registryAccess()), true);
	}

	public boolean test(ServerLevel serverLevel, BlockPos blockPos) {
		return this.test(new BlockInWorld(serverLevel, blockPos, false));
	}

	public boolean place(ServerLevel serverLevel, BlockPos blockPos, @Block.UpdateFlags int i) {
		BlockState blockState = (i & 16) != 0 ? this.state : Block.updateFromNeighbourShapes(this.state, serverLevel, blockPos);
		if (blockState.isAir()) {
			blockState = this.state;
		}

		blockState = this.overwriteWithDefinedProperties(blockState);
		boolean bl = false;
		if (serverLevel.setBlock(blockPos, blockState, i)) {
			bl = true;
		}

		if (this.tag != null) {
			BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos);
			if (blockEntity != null) {
				try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(LOGGER)) {
					HolderLookup.Provider provider = serverLevel.registryAccess();
					ProblemReporter problemReporter = scopedCollector.forChild(blockEntity.problemPath());
					TagValueOutput tagValueOutput = TagValueOutput.createWithContext(problemReporter.forChild(() -> "(before)"), provider);
					blockEntity.saveWithoutMetadata(tagValueOutput);
					CompoundTag compoundTag = tagValueOutput.buildResult();
					blockEntity.loadWithComponents(TagValueInput.create(scopedCollector, provider, this.tag));
					TagValueOutput tagValueOutput2 = TagValueOutput.createWithContext(problemReporter.forChild(() -> "(after)"), provider);
					blockEntity.saveWithoutMetadata(tagValueOutput2);
					CompoundTag compoundTag2 = tagValueOutput2.buildResult();
					if (!compoundTag2.equals(compoundTag)) {
						bl = true;
						blockEntity.setChanged();
						serverLevel.getChunkSource().blockChanged(blockPos);
					}
				}
			}
		}

		return bl;
	}

	private BlockState overwriteWithDefinedProperties(BlockState blockState) {
		if (blockState == this.state) {
			return blockState;
		}

		for (Property<?> property : this.properties) {
			blockState = copyProperty(blockState, this.state, property);
		}

		return blockState;
	}

	private static <T extends Comparable<T>> BlockState copyProperty(BlockState blockState, BlockState blockState2, Property<T> property) {
		return blockState.trySetValue(property, blockState2.getValue(property));
	}
}
