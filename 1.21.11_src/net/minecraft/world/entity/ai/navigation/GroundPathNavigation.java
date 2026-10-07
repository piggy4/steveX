package net.minecraft.world.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

public class GroundPathNavigation extends PathNavigation {
	private boolean avoidSun;
	private boolean canPathToTargetsBelowSurface;

	public GroundPathNavigation(Mob mob, Level level) {
		super(mob, level);
	}

	@Override
	protected PathFinder createPathFinder(int i) {
		this.nodeEvaluator = new WalkNodeEvaluator();
		return new PathFinder(this.nodeEvaluator, i);
	}

	@Override
	protected boolean canUpdatePath() {
		return this.mob.onGround() || this.mob.isInLiquid() || this.mob.isPassenger();
	}

	@Override
	protected Vec3 getTempMobPos() {
		return new Vec3(this.mob.getX(), this.getSurfaceY(), this.mob.getZ());
	}

	@Override
	public Path createPath(BlockPos blockPos, int i) {
		LevelChunk levelChunk = this.level
			.getChunkSource()
			.getChunkNow(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()));
		if (levelChunk == null) {
			return null;
		}

		if (!this.canPathToTargetsBelowSurface) {
			blockPos = this.findSurfacePosition(levelChunk, blockPos, i);
		}

		return super.createPath(blockPos, i);
	}

	final BlockPos findSurfacePosition(LevelChunk levelChunk, BlockPos blockPos, int i) {
		if (levelChunk.getBlockState(blockPos).isAir()) {
			BlockPos.MutableBlockPos mutableBlockPos = blockPos.mutable().move(Direction.DOWN);

			while (mutableBlockPos.getY() >= this.level.getMinY() && levelChunk.getBlockState(mutableBlockPos).isAir()) {
				mutableBlockPos.move(Direction.DOWN);
			}

			if (mutableBlockPos.getY() >= this.level.getMinY()) {
				return mutableBlockPos.above();
			}

			mutableBlockPos.setY(blockPos.getY() + 1);

			while (mutableBlockPos.getY() <= this.level.getMaxY() && levelChunk.getBlockState(mutableBlockPos).isAir()) {
				mutableBlockPos.move(Direction.UP);
			}

			blockPos = mutableBlockPos;
		}

		if (!levelChunk.getBlockState(blockPos).isSolid()) {
			return blockPos;
		}

		BlockPos.MutableBlockPos mutableBlockPos = blockPos.mutable().move(Direction.UP);

		while (mutableBlockPos.getY() <= this.level.getMaxY() && levelChunk.getBlockState(mutableBlockPos).isSolid()) {
			mutableBlockPos.move(Direction.UP);
		}

		return mutableBlockPos.immutable();
	}

	@Override
	public Path createPath(Entity entity, int i) {
		return this.createPath(entity.blockPosition(), i);
	}

	private int getSurfaceY() {
		if (this.mob.isInWater() && this.canFloat()) {
			int i = this.mob.getBlockY();
			BlockState blockState = this.level.getBlockState(BlockPos.containing(this.mob.getX(), i, this.mob.getZ()));
			int j = 0;

			while (blockState.is(Blocks.WATER)) {
				blockState = this.level.getBlockState(BlockPos.containing(this.mob.getX(), ++i, this.mob.getZ()));
				if (++j > 16) {
					return this.mob.getBlockY();
				}
			}

			return i;
		} else {
			return Mth.floor(this.mob.getY() + 0.5);
		}
	}

	@Override
	protected void trimPath() {
		super.trimPath();
		if (this.avoidSun) {
			if (this.level.canSeeSky(BlockPos.containing(this.mob.getX(), this.mob.getY() + 0.5, this.mob.getZ()))) {
				return;
			}

			for (int i = 0; i < this.path.getNodeCount(); i++) {
				Node node = this.path.getNode(i);
				if (this.level.canSeeSky(new BlockPos(node.x, node.y, node.z))) {
					this.path.truncateNodes(i);
					return;
				}
			}
		}
	}

	@Override
	public boolean canNavigateGround() {
		return true;
	}

	protected boolean hasValidPathType(PathType pathType) {
		if (pathType == PathType.WATER) {
			return false;
		} else {
			return pathType == PathType.LAVA ? false : pathType != PathType.OPEN;
		}
	}

	public void setAvoidSun(boolean bl) {
		this.avoidSun = bl;
	}

	public void setCanWalkOverFences(boolean bl) {
		this.nodeEvaluator.setCanWalkOverFences(bl);
	}

	public void setCanPathToTargetsBelowSurface(boolean bl) {
		this.canPathToTargetsBelowSurface = bl;
	}
}
