package net.minecraft.world.level.block.entity;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

public abstract class ContainerOpenersCounter {
	private static final int CHECK_TICK_DELAY = 5;
	private int openCount;
	private double maxInteractionRange;

	protected abstract void onOpen(Level level, BlockPos blockPos, BlockState blockState);

	protected abstract void onClose(Level level, BlockPos blockPos, BlockState blockState);

	protected abstract void openerCountChanged(Level level, BlockPos blockPos, BlockState blockState, int i, int j);

	public abstract boolean isOwnContainer(Player player);

	public void incrementOpeners(LivingEntity livingEntity, Level level, BlockPos blockPos, BlockState blockState, double d) {
		int i = this.openCount++;
		if (i == 0) {
			this.onOpen(level, blockPos, blockState);
			level.gameEvent(livingEntity, GameEvent.CONTAINER_OPEN, blockPos);
			scheduleRecheck(level, blockPos, blockState);
		}

		this.openerCountChanged(level, blockPos, blockState, i, this.openCount);
		this.maxInteractionRange = Math.max(d, this.maxInteractionRange);
	}

	public void decrementOpeners(LivingEntity livingEntity, Level level, BlockPos blockPos, BlockState blockState) {
		int i = this.openCount--;
		if (this.openCount == 0) {
			this.onClose(level, blockPos, blockState);
			level.gameEvent(livingEntity, GameEvent.CONTAINER_CLOSE, blockPos);
			this.maxInteractionRange = 0.0;
		}

		this.openerCountChanged(level, blockPos, blockState, i, this.openCount);
	}

	public List<ContainerUser> getEntitiesWithContainerOpen(Level level, BlockPos blockPos) {
		double d = this.maxInteractionRange + 4.0;
		AABB aABB = new AABB(blockPos).inflate(d);
		return level.getEntities((Entity)null, aABB, entity -> this.hasContainerOpen(entity, blockPos))
			.stream()
			.map(entity -> (ContainerUser)entity)
			.collect(Collectors.toList());
	}

	private boolean hasContainerOpen(Entity entity, BlockPos blockPos) {
		return entity instanceof ContainerUser containerUser && !containerUser.getLivingEntity().isSpectator()
			? containerUser.hasContainerOpen(this, blockPos)
			: false;
	}

	public void recheckOpeners(Level level, BlockPos blockPos, BlockState blockState) {
		List<ContainerUser> list = this.getEntitiesWithContainerOpen(level, blockPos);
		this.maxInteractionRange = 0.0;

		for (ContainerUser containerUser : list) {
			this.maxInteractionRange = Math.max(containerUser.getContainerInteractionRange(), this.maxInteractionRange);
		}

		int i = list.size();
		int j = this.openCount;
		if (j != i) {
			boolean bl = i != 0;
			boolean bl2 = j != 0;
			if (bl && !bl2) {
				this.onOpen(level, blockPos, blockState);
				level.gameEvent(null, GameEvent.CONTAINER_OPEN, blockPos);
			} else if (!bl) {
				this.onClose(level, blockPos, blockState);
				level.gameEvent(null, GameEvent.CONTAINER_CLOSE, blockPos);
			}

			this.openCount = i;
		}

		this.openerCountChanged(level, blockPos, blockState, j, i);
		if (i > 0) {
			scheduleRecheck(level, blockPos, blockState);
		}
	}

	public int getOpenerCount() {
		return this.openCount;
	}

	private static void scheduleRecheck(Level level, BlockPos blockPos, BlockState blockState) {
		level.scheduleTick(blockPos, blockState.getBlock(), 5);
	}
}
