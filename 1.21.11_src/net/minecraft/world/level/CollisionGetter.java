package net.minecraft.world.level;

import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public interface CollisionGetter extends BlockGetter {
	WorldBorder getWorldBorder();

	@Nullable BlockGetter getChunkForCollisions(int i, int j);

	default boolean isUnobstructed(@Nullable Entity entity, VoxelShape voxelShape) {
		return true;
	}

	default boolean isUnobstructed(BlockState blockState, BlockPos blockPos, CollisionContext collisionContext) {
		VoxelShape voxelShape = blockState.getCollisionShape(this, blockPos, collisionContext);
		return voxelShape.isEmpty() || this.isUnobstructed(null, voxelShape.move(blockPos));
	}

	default boolean isUnobstructed(Entity entity) {
		return this.isUnobstructed(entity, Shapes.create(entity.getBoundingBox()));
	}

	default boolean noCollision(AABB aABB) {
		return this.noCollision(null, aABB);
	}

	default boolean noCollision(Entity entity) {
		return this.noCollision(entity, entity.getBoundingBox());
	}

	default boolean noCollision(@Nullable Entity entity, AABB aABB) {
		return this.noCollision(entity, aABB, false);
	}

	default boolean noCollision(@Nullable Entity entity, AABB aABB, boolean bl) {
		return this.noBlockCollision(entity, aABB, bl) && this.noEntityCollision(entity, aABB) && this.noBorderCollision(entity, aABB);
	}

	default boolean noBlockCollision(@Nullable Entity entity, AABB aABB) {
		return this.noBlockCollision(entity, aABB, false);
	}

	default boolean noBlockCollision(@Nullable Entity entity, AABB aABB, boolean bl) {
		for (VoxelShape voxelShape : bl ? this.getBlockAndLiquidCollisions(entity, aABB) : this.getBlockCollisions(entity, aABB)) {
			if (!voxelShape.isEmpty()) {
				return false;
			}
		}

		return true;
	}

	default boolean noEntityCollision(@Nullable Entity entity, AABB aABB) {
		return this.getEntityCollisions(entity, aABB).isEmpty();
	}

	default boolean noBorderCollision(@Nullable Entity entity, AABB aABB) {
		if (entity == null) {
			return true;
		}

		VoxelShape voxelShape = this.borderCollision(entity, aABB);
		return voxelShape == null || !Shapes.joinIsNotEmpty(voxelShape, Shapes.create(aABB), BooleanOp.AND);
	}

	List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB aABB);

	default Iterable<VoxelShape> getCollisions(@Nullable Entity entity, AABB aABB) {
		List<VoxelShape> list = this.getEntityCollisions(entity, aABB);
		Iterable<VoxelShape> iterable = this.getBlockCollisions(entity, aABB);
		return list.isEmpty() ? iterable : Iterables.concat(list, iterable);
	}

	default Iterable<VoxelShape> getPreMoveCollisions(@Nullable Entity entity, AABB aABB, Vec3 vec3) {
		List<VoxelShape> list = this.getEntityCollisions(entity, aABB);
		Iterable<VoxelShape> iterable = this.getBlockCollisionsFromContext(CollisionContext.withPosition(entity, vec3.y), aABB);
		return list.isEmpty() ? iterable : Iterables.concat(list, iterable);
	}

	default Iterable<VoxelShape> getBlockCollisions(@Nullable Entity entity, AABB aABB) {
		return this.getBlockCollisionsFromContext(entity == null ? CollisionContext.empty() : CollisionContext.of(entity), aABB);
	}

	default Iterable<VoxelShape> getBlockAndLiquidCollisions(@Nullable Entity entity, AABB aABB) {
		return this.getBlockCollisionsFromContext(entity == null ? CollisionContext.emptyWithFluidCollisions() : CollisionContext.of(entity, true), aABB);
	}

	private Iterable<VoxelShape> getBlockCollisionsFromContext(CollisionContext collisionContext, AABB aABB) {
		return () -> new BlockCollisions<>(this, collisionContext, aABB, false, (mutableBlockPos, voxelShape) -> voxelShape);
	}

	private @Nullable VoxelShape borderCollision(Entity entity, AABB aABB) {
		WorldBorder worldBorder = this.getWorldBorder();
		return worldBorder.isInsideCloseToBorder(entity, aABB) ? worldBorder.getCollisionShape() : null;
	}

	default BlockHitResult clipIncludingBorder(ClipContext clipContext) {
		BlockHitResult blockHitResult = this.clip(clipContext);
		WorldBorder worldBorder = this.getWorldBorder();
		if (worldBorder.isWithinBounds(clipContext.getFrom()) && !worldBorder.isWithinBounds(blockHitResult.getLocation())) {
			Vec3 vec3 = blockHitResult.getLocation().subtract(clipContext.getFrom());
			Direction direction = Direction.getApproximateNearest(vec3.x, vec3.y, vec3.z);
			Vec3 vec32 = worldBorder.clampVec3ToBound(blockHitResult.getLocation());
			return new BlockHitResult(vec32, direction, BlockPos.containing(vec32), false, true);
		} else {
			return blockHitResult;
		}
	}

	default boolean collidesWithSuffocatingBlock(@Nullable Entity entity, AABB aABB) {
		BlockCollisions<VoxelShape> blockCollisions = new BlockCollisions<>(this, entity, aABB, true, (mutableBlockPos, voxelShape) -> voxelShape);

		while (blockCollisions.hasNext()) {
			if (!blockCollisions.next().isEmpty()) {
				return true;
			}
		}

		return false;
	}

	default Optional<BlockPos> findSupportingBlock(Entity entity, AABB aABB) {
		BlockPos blockPos = null;
		double d = Double.MAX_VALUE;
		BlockCollisions<BlockPos> blockCollisions = new BlockCollisions<>(this, entity, aABB, false, (mutableBlockPos, voxelShape) -> mutableBlockPos);

		while (blockCollisions.hasNext()) {
			BlockPos blockPos2 = blockCollisions.next();
			double e = blockPos2.distToCenterSqr(entity.position());
			if (e < d || e == d && (blockPos == null || blockPos.compareTo(blockPos2) < 0)) {
				blockPos = blockPos2.immutable();
				d = e;
			}
		}

		return Optional.ofNullable(blockPos);
	}

	default Optional<Vec3> findFreePosition(@Nullable Entity entity, VoxelShape voxelShape, Vec3 vec3, double d, double e, double f) {
		if (voxelShape.isEmpty()) {
			return Optional.empty();
		}

		AABB aABB = voxelShape.bounds().inflate(d, e, f);
		VoxelShape voxelShape2 = StreamSupport.stream(this.getBlockCollisions(entity, aABB).spliterator(), false)
			.filter(voxelShapex -> this.getWorldBorder() == null || this.getWorldBorder().isWithinBounds(voxelShapex.bounds()))
			.flatMap(voxelShapex -> voxelShapex.toAabbs().stream())
			.map(aABBx -> aABBx.inflate(d / 2.0, e / 2.0, f / 2.0))
			.map(Shapes::create)
			.reduce(Shapes.empty(), Shapes::or);
		VoxelShape voxelShape3 = Shapes.join(voxelShape, voxelShape2, BooleanOp.ONLY_FIRST);
		return voxelShape3.closestPointTo(vec3);
	}
}
