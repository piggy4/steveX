package net.minecraft.world.level.redstone;

import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class CollectingNeighborUpdater implements NeighborUpdater {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final Level level;
	private final int maxChainedNeighborUpdates;
	private final ArrayDeque<CollectingNeighborUpdater.NeighborUpdates> stack = new ArrayDeque<>();
	private final List<CollectingNeighborUpdater.NeighborUpdates> addedThisLayer = new ArrayList<>();
	private int count = 0;
	private @Nullable Consumer<BlockPos> debugListener;

	public CollectingNeighborUpdater(Level level, int i) {
		this.level = level;
		this.maxChainedNeighborUpdates = i;
	}

	public void setDebugListener(@Nullable Consumer<BlockPos> consumer) {
		this.debugListener = consumer;
	}

	@Override
	public void shapeUpdate(Direction direction, BlockState blockState, BlockPos blockPos, BlockPos blockPos2, @Block.UpdateFlags int i, int j) {
		this.addAndRun(blockPos, new CollectingNeighborUpdater.ShapeUpdate(direction, blockState, blockPos.immutable(), blockPos2.immutable(), i, j));
	}

	@Override
	public void neighborChanged(BlockPos blockPos, Block block, @Nullable Orientation orientation) {
		this.addAndRun(blockPos, new CollectingNeighborUpdater.SimpleNeighborUpdate(blockPos, block, orientation));
	}

	@Override
	public void neighborChanged(BlockState blockState, BlockPos blockPos, Block block, @Nullable Orientation orientation, boolean bl) {
		this.addAndRun(blockPos, new CollectingNeighborUpdater.FullNeighborUpdate(blockState, blockPos.immutable(), block, orientation, bl));
	}

	@Override
	public void updateNeighborsAtExceptFromFacing(BlockPos blockPos, Block block, @Nullable Direction direction, @Nullable Orientation orientation) {
		this.addAndRun(blockPos, new CollectingNeighborUpdater.MultiNeighborUpdate(blockPos.immutable(), block, orientation, direction));
	}

	private void addAndRun(BlockPos blockPos, CollectingNeighborUpdater.NeighborUpdates neighborUpdates) {
		boolean bl = this.count > 0;
		boolean bl2 = this.maxChainedNeighborUpdates >= 0 && this.count >= this.maxChainedNeighborUpdates;
		this.count++;
		if (!bl2) {
			if (bl) {
				this.addedThisLayer.add(neighborUpdates);
			} else {
				this.stack.push(neighborUpdates);
			}
		} else if (this.count - 1 == this.maxChainedNeighborUpdates) {
			LOGGER.error("Too many chained neighbor updates. Skipping the rest. First skipped position: {}", blockPos.toShortString());
		}

		if (!bl) {
			this.runUpdates();
		}
	}

	private void runUpdates() {
		try {
			while (!this.stack.isEmpty() || !this.addedThisLayer.isEmpty()) {
				for (int i = this.addedThisLayer.size() - 1; i >= 0; i--) {
					this.stack.push(this.addedThisLayer.get(i));
				}

				this.addedThisLayer.clear();
				CollectingNeighborUpdater.NeighborUpdates neighborUpdates = this.stack.peek();
				if (this.debugListener != null) {
					neighborUpdates.forEachUpdatedPos(this.debugListener);
				}

				while (this.addedThisLayer.isEmpty()) {
					if (!neighborUpdates.runNext(this.level)) {
						this.stack.pop();
						break;
					}
				}
			}
		} finally {
			this.stack.clear();
			this.addedThisLayer.clear();
			this.count = 0;
		}
	}

	record FullNeighborUpdate(BlockState state, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston)
		implements CollectingNeighborUpdater.NeighborUpdates {
		@Override
		public boolean runNext(Level level) {
			NeighborUpdater.executeUpdate(level, this.state, this.pos, this.block, this.orientation, this.movedByPiston);
			return false;
		}

		@Override
		public void forEachUpdatedPos(Consumer<BlockPos> consumer) {
			consumer.accept(this.pos);
		}
	}

	static final class MultiNeighborUpdate implements CollectingNeighborUpdater.NeighborUpdates {
		private final BlockPos sourcePos;
		private final Block sourceBlock;
		private @Nullable Orientation orientation;
		private final @Nullable Direction skipDirection;
		private int idx = 0;

		MultiNeighborUpdate(BlockPos blockPos, Block block, @Nullable Orientation orientation, @Nullable Direction direction) {
			this.sourcePos = blockPos;
			this.sourceBlock = block;
			this.orientation = orientation;
			this.skipDirection = direction;
			if (NeighborUpdater.UPDATE_ORDER[this.idx] == direction) {
				this.idx++;
			}
		}

		@Override
		public boolean runNext(Level level) {
			Direction direction = NeighborUpdater.UPDATE_ORDER[this.idx++];
			BlockPos blockPos = this.sourcePos.relative(direction);
			BlockState blockState = level.getBlockState(blockPos);
			Orientation orientation = null;
			if (level.enabledFeatures().contains(FeatureFlags.REDSTONE_EXPERIMENTS)) {
				if (this.orientation == null) {
					this.orientation = ExperimentalRedstoneUtils.initialOrientation(level, this.skipDirection == null ? null : this.skipDirection.getOpposite(), null);
				}

				orientation = this.orientation.withFront(direction);
			}

			NeighborUpdater.executeUpdate(level, blockState, blockPos, this.sourceBlock, orientation, false);
			if (this.idx < NeighborUpdater.UPDATE_ORDER.length && NeighborUpdater.UPDATE_ORDER[this.idx] == this.skipDirection) {
				this.idx++;
			}

			return this.idx < NeighborUpdater.UPDATE_ORDER.length;
		}

		@Override
		public void forEachUpdatedPos(Consumer<BlockPos> consumer) {
			for (Direction direction : NeighborUpdater.UPDATE_ORDER) {
				if (direction != this.skipDirection) {
					BlockPos blockPos = this.sourcePos.relative(direction);
					consumer.accept(blockPos);
				}
			}
		}
	}

	interface NeighborUpdates {
		boolean runNext(Level level);

		void forEachUpdatedPos(Consumer<BlockPos> consumer);
	}

	record ShapeUpdate(Direction direction, BlockState neighborState, BlockPos pos, BlockPos neighborPos, @Block.UpdateFlags int updateFlags, int updateLimit)
		implements CollectingNeighborUpdater.NeighborUpdates {
		@Override
		public boolean runNext(Level level) {
			NeighborUpdater.executeShapeUpdate(level, this.direction, this.pos, this.neighborPos, this.neighborState, this.updateFlags, this.updateLimit);
			return false;
		}

		@Override
		public void forEachUpdatedPos(Consumer<BlockPos> consumer) {
			consumer.accept(this.pos);
		}
	}

	record SimpleNeighborUpdate(BlockPos pos, Block block, @Nullable Orientation orientation) implements CollectingNeighborUpdater.NeighborUpdates {
		@Override
		public boolean runNext(Level level) {
			BlockState blockState = level.getBlockState(this.pos);
			NeighborUpdater.executeUpdate(level, blockState, this.pos, this.block, this.orientation, false);
			return false;
		}

		@Override
		public void forEachUpdatedPos(Consumer<BlockPos> consumer) {
			consumer.accept(this.pos);
		}
	}
}
