package net.minecraft.world.level.block;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SideChainPart;

public interface SideChainPartBlock {
	SideChainPart getSideChainPart(BlockState blockState);

	BlockState setSideChainPart(BlockState blockState, SideChainPart sideChainPart);

	Direction getFacing(BlockState blockState);

	boolean isConnectable(BlockState blockState);

	int getMaxChainLength();

	default List<BlockPos> getAllBlocksConnectedTo(LevelAccessor levelAccessor, BlockPos blockPos) {
		BlockState blockState = levelAccessor.getBlockState(blockPos);
		if (!this.isConnectable(blockState)) {
			return List.of();
		}

		SideChainPartBlock.Neighbors neighbors = this.getNeighbors(levelAccessor, blockPos, this.getFacing(blockState));
		List<BlockPos> list = new LinkedList<>();
		list.add(blockPos);
		this.addBlocksConnectingTowards(neighbors::left, SideChainPart.LEFT, list::addFirst);
		this.addBlocksConnectingTowards(neighbors::right, SideChainPart.RIGHT, list::addLast);
		return list;
	}

	private void addBlocksConnectingTowards(IntFunction<SideChainPartBlock.Neighbor> intFunction, SideChainPart sideChainPart, Consumer<BlockPos> consumer) {
		for (int i = 1; i < this.getMaxChainLength(); i++) {
			SideChainPartBlock.Neighbor neighbor = intFunction.apply(i);
			if (neighbor.connectsTowards(sideChainPart)) {
				consumer.accept(neighbor.pos());
			}

			if (neighbor.isUnconnectableOrChainEnd()) {
				break;
			}
		}
	}

	default void updateNeighborsAfterPoweringDown(LevelAccessor levelAccessor, BlockPos blockPos, BlockState blockState) {
		SideChainPartBlock.Neighbors neighbors = this.getNeighbors(levelAccessor, blockPos, this.getFacing(blockState));
		neighbors.left().disconnectFromRight();
		neighbors.right().disconnectFromLeft();
	}

	default void updateSelfAndNeighborsOnPoweringUp(LevelAccessor levelAccessor, BlockPos blockPos, BlockState blockState, BlockState blockState2) {
		if (this.isConnectable(blockState)) {
			if (!this.isBeingUpdatedByNeighbor(blockState, blockState2)) {
				SideChainPartBlock.Neighbors neighbors = this.getNeighbors(levelAccessor, blockPos, this.getFacing(blockState));
				SideChainPart sideChainPart = SideChainPart.UNCONNECTED;
				int i = neighbors.left().isConnectable() ? this.getAllBlocksConnectedTo(levelAccessor, neighbors.left().pos()).size() : 0;
				int j = neighbors.right().isConnectable() ? this.getAllBlocksConnectedTo(levelAccessor, neighbors.right().pos()).size() : 0;
				int k = 1;
				if (this.canConnect(i, k)) {
					sideChainPart = sideChainPart.whenConnectedToTheLeft();
					neighbors.left().connectToTheRight();
					k += i;
				}

				if (this.canConnect(j, k)) {
					sideChainPart = sideChainPart.whenConnectedToTheRight();
					neighbors.right().connectToTheLeft();
				}

				this.setPart(levelAccessor, blockPos, sideChainPart);
			}
		}
	}

	private boolean canConnect(int i, int j) {
		return i > 0 && j + i <= this.getMaxChainLength();
	}

	private boolean isBeingUpdatedByNeighbor(BlockState blockState, BlockState blockState2) {
		boolean bl = this.getSideChainPart(blockState).isConnected();
		boolean bl2 = this.isConnectable(blockState2) && this.getSideChainPart(blockState2).isConnected();
		return bl || bl2;
	}

	private SideChainPartBlock.Neighbors getNeighbors(LevelAccessor levelAccessor, BlockPos blockPos, Direction direction) {
		return new SideChainPartBlock.Neighbors(this, levelAccessor, direction, blockPos, new HashMap<>());
	}

	default void setPart(LevelAccessor levelAccessor, BlockPos blockPos, SideChainPart sideChainPart) {
		BlockState blockState = levelAccessor.getBlockState(blockPos);
		if (this.getSideChainPart(blockState) != sideChainPart) {
			levelAccessor.setBlock(blockPos, this.setSideChainPart(blockState, sideChainPart), 3);
		}
	}

	record EmptyNeighbor(BlockPos pos) implements SideChainPartBlock.Neighbor {
		@Override
		public boolean isConnectable() {
			return false;
		}

		@Override
		public boolean isUnconnectableOrChainEnd() {
			return true;
		}

		@Override
		public boolean connectsTowards(SideChainPart sideChainPart) {
			return false;
		}
	}

	sealed interface Neighbor permits SideChainPartBlock.EmptyNeighbor, SideChainPartBlock.SideChainNeighbor {
		BlockPos pos();

		boolean isConnectable();

		boolean isUnconnectableOrChainEnd();

		boolean connectsTowards(SideChainPart sideChainPart);

		default void connectToTheRight() {
		}

		default void connectToTheLeft() {
		}

		default void disconnectFromRight() {
		}

		default void disconnectFromLeft() {
		}
	}

	record Neighbors(SideChainPartBlock block, LevelAccessor level, Direction facing, BlockPos center, Map<BlockPos, SideChainPartBlock.Neighbor> cache) {
		private boolean isConnectableToThisBlock(BlockState blockState) {
			return this.block.isConnectable(blockState) && this.block.getFacing(blockState) == this.facing;
		}

		private SideChainPartBlock.Neighbor createNewNeighbor(BlockPos blockPos) {
			BlockState blockState = this.level.getBlockState(blockPos);
			SideChainPart sideChainPart = this.isConnectableToThisBlock(blockState) ? this.block.getSideChainPart(blockState) : null;
			return sideChainPart == null
				? new SideChainPartBlock.EmptyNeighbor(blockPos)
				: new SideChainPartBlock.SideChainNeighbor(this.level, this.block, blockPos, sideChainPart);
		}

		private SideChainPartBlock.Neighbor getOrCreateNeighbor(Direction direction, Integer integer) {
			return this.cache.computeIfAbsent(this.center.relative(direction, integer), this::createNewNeighbor);
		}

		public SideChainPartBlock.Neighbor left(int i) {
			return this.getOrCreateNeighbor(this.facing.getClockWise(), i);
		}

		public SideChainPartBlock.Neighbor right(int i) {
			return this.getOrCreateNeighbor(this.facing.getCounterClockWise(), i);
		}

		public SideChainPartBlock.Neighbor left() {
			return this.left(1);
		}

		public SideChainPartBlock.Neighbor right() {
			return this.right(1);
		}
	}

	record SideChainNeighbor(LevelAccessor level, SideChainPartBlock block, BlockPos pos, SideChainPart part) implements SideChainPartBlock.Neighbor {
		@Override
		public boolean isConnectable() {
			return true;
		}

		@Override
		public boolean isUnconnectableOrChainEnd() {
			return this.part.isChainEnd();
		}

		@Override
		public boolean connectsTowards(SideChainPart sideChainPart) {
			return this.part.isConnectionTowards(sideChainPart);
		}

		@Override
		public void connectToTheRight() {
			this.block.setPart(this.level, this.pos, this.part.whenConnectedToTheRight());
		}

		@Override
		public void connectToTheLeft() {
			this.block.setPart(this.level, this.pos, this.part.whenConnectedToTheLeft());
		}

		@Override
		public void disconnectFromRight() {
			this.block.setPart(this.level, this.pos, this.part.whenDisconnectedFromTheRight());
		}

		@Override
		public void disconnectFromLeft() {
			this.block.setPart(this.level, this.pos, this.part.whenDisconnectedFromTheLeft());
		}
	}
}
