package net.minecraft.world.level;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.blockview.v2.FabricBlockView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.blockview.v2.FabricBlockView} injected by mod fabric-block-view-api-v2</p>
 */
public interface BlockGetter extends LevelHeightAccessor, FabricBlockView {
	@Nullable BlockEntity getBlockEntity(BlockPos blockPos);

	default <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos blockPos, BlockEntityType<T> blockEntityType) {
		BlockEntity blockEntity = this.getBlockEntity(blockPos);
		return blockEntity != null && blockEntity.getType() == blockEntityType ? Optional.of((T)blockEntity) : Optional.empty();
	}

	BlockState getBlockState(BlockPos blockPos);

	FluidState getFluidState(BlockPos blockPos);

	default int getLightEmission(BlockPos blockPos) {
		return this.getBlockState(blockPos).getLightEmission();
	}

	default Stream<BlockState> getBlockStates(AABB aABB) {
		return BlockPos.betweenClosedStream(aABB).map(this::getBlockState);
	}

	default BlockHitResult isBlockInLine(ClipBlockStateContext clipBlockStateContext) {
		return traverseBlocks(
			clipBlockStateContext.getFrom(),
			clipBlockStateContext.getTo(),
			clipBlockStateContext,
			(clipBlockStateContextx, blockPos) -> {
				BlockState blockState = this.getBlockState(blockPos);
				Vec3 vec3 = clipBlockStateContextx.getFrom().subtract(clipBlockStateContextx.getTo());
				return clipBlockStateContextx.isTargetBlock().test(blockState)
					? new BlockHitResult(
						clipBlockStateContextx.getTo(), Direction.getApproximateNearest(vec3.x, vec3.y, vec3.z), BlockPos.containing(clipBlockStateContextx.getTo()), false
					)
					: null;
			},
			clipBlockStateContextx -> {
				Vec3 vec3 = clipBlockStateContextx.getFrom().subtract(clipBlockStateContextx.getTo());
				return BlockHitResult.miss(
					clipBlockStateContextx.getTo(), Direction.getApproximateNearest(vec3.x, vec3.y, vec3.z), BlockPos.containing(clipBlockStateContextx.getTo())
				);
			}
		);
	}

	default BlockHitResult clip(ClipContext clipContext) {
		return traverseBlocks(clipContext.getFrom(), clipContext.getTo(), clipContext, (clipContextx, blockPos) -> {
			BlockState blockState = this.getBlockState(blockPos);
			FluidState fluidState = this.getFluidState(blockPos);
			Vec3 vec3 = clipContextx.getFrom();
			Vec3 vec32 = clipContextx.getTo();
			VoxelShape voxelShape = clipContextx.getBlockShape(blockState, this, blockPos);
			BlockHitResult blockHitResult = this.clipWithInteractionOverride(vec3, vec32, blockPos, voxelShape, blockState);
			VoxelShape voxelShape2 = clipContextx.getFluidShape(fluidState, this, blockPos);
			BlockHitResult blockHitResult2 = voxelShape2.clip(vec3, vec32, blockPos);
			double d = blockHitResult == null ? Double.MAX_VALUE : clipContextx.getFrom().distanceToSqr(blockHitResult.getLocation());
			double e = blockHitResult2 == null ? Double.MAX_VALUE : clipContextx.getFrom().distanceToSqr(blockHitResult2.getLocation());
			return d <= e ? blockHitResult : blockHitResult2;
		}, clipContextx -> {
			Vec3 vec3 = clipContextx.getFrom().subtract(clipContextx.getTo());
			return BlockHitResult.miss(clipContextx.getTo(), Direction.getApproximateNearest(vec3.x, vec3.y, vec3.z), BlockPos.containing(clipContextx.getTo()));
		});
	}

	default @Nullable BlockHitResult clipWithInteractionOverride(Vec3 vec3, Vec3 vec32, BlockPos blockPos, VoxelShape voxelShape, BlockState blockState) {
		BlockHitResult blockHitResult = voxelShape.clip(vec3, vec32, blockPos);
		if (blockHitResult != null) {
			BlockHitResult blockHitResult2 = blockState.getInteractionShape(this, blockPos).clip(vec3, vec32, blockPos);
			if (blockHitResult2 != null && blockHitResult2.getLocation().subtract(vec3).lengthSqr() < blockHitResult.getLocation().subtract(vec3).lengthSqr()) {
				return blockHitResult.withDirection(blockHitResult2.getDirection());
			}
		}

		return blockHitResult;
	}

	default double getBlockFloorHeight(VoxelShape voxelShape, Supplier<VoxelShape> supplier) {
		if (!voxelShape.isEmpty()) {
			return voxelShape.max(Direction.Axis.Y);
		}

		double d = supplier.get().max(Direction.Axis.Y);
		return d >= 1.0 ? d - 1.0 : Double.NEGATIVE_INFINITY;
	}

	default double getBlockFloorHeight(BlockPos blockPos) {
		return this.getBlockFloorHeight(this.getBlockState(blockPos).getCollisionShape(this, blockPos), () -> {
			BlockPos blockPos2 = blockPos.below();
			return this.getBlockState(blockPos2).getCollisionShape(this, blockPos2);
		});
	}

	static <T, C> T traverseBlocks(Vec3 vec3, Vec3 vec32, C object, BiFunction<C, BlockPos, @Nullable T> biFunction, Function<C, T> function) {
		if (vec3.equals(vec32)) {
			return function.apply(object);
		}

		double d = Mth.lerp(-1.0E-7, vec32.x, vec3.x);
		double e = Mth.lerp(-1.0E-7, vec32.y, vec3.y);
		double f = Mth.lerp(-1.0E-7, vec32.z, vec3.z);
		double g = Mth.lerp(-1.0E-7, vec3.x, vec32.x);
		double h = Mth.lerp(-1.0E-7, vec3.y, vec32.y);
		double i = Mth.lerp(-1.0E-7, vec3.z, vec32.z);
		int j = Mth.floor(g);
		int k = Mth.floor(h);
		int l = Mth.floor(i);
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(j, k, l);
		T object2 = biFunction.apply(object, mutableBlockPos);
		if (object2 != null) {
			return object2;
		}

		double m = d - g;
		double n = e - h;
		double o = f - i;
		int p = Mth.sign(m);
		int q = Mth.sign(n);
		int r = Mth.sign(o);
		double s = p == 0 ? Double.MAX_VALUE : p / m;
		double t = q == 0 ? Double.MAX_VALUE : q / n;
		double u = r == 0 ? Double.MAX_VALUE : r / o;
		double v = s * (p > 0 ? 1.0 - Mth.frac(g) : Mth.frac(g));
		double w = t * (q > 0 ? 1.0 - Mth.frac(h) : Mth.frac(h));
		double x = u * (r > 0 ? 1.0 - Mth.frac(i) : Mth.frac(i));

		while (v <= 1.0 || w <= 1.0 || x <= 1.0) {
			if (v < w) {
				if (v < x) {
					j += p;
					v += s;
				} else {
					l += r;
					x += u;
				}
			} else if (w < x) {
				k += q;
				w += t;
			} else {
				l += r;
				x += u;
			}

			T object3 = biFunction.apply(object, mutableBlockPos.set(j, k, l));
			if (object3 != null) {
				return object3;
			}
		}

		return function.apply(object);
	}

	static boolean forEachBlockIntersectedBetween(Vec3 vec3, Vec3 vec32, AABB aABB, BlockGetter.BlockStepVisitor blockStepVisitor) {
		Vec3 vec33 = vec32.subtract(vec3);
		if (vec33.lengthSqr() < Mth.square(1.0E-5F)) {
			for (BlockPos blockPos : BlockPos.betweenClosed(aABB)) {
				if (!blockStepVisitor.visit(blockPos, 0)) {
					return false;
				}
			}

			return true;
		} else {
			LongSet longSet = new LongOpenHashSet();

			for (BlockPos blockPos2 : BlockPos.betweenCornersInDirection(aABB.move(vec33.scale(-1.0)), vec33)) {
				if (!blockStepVisitor.visit(blockPos2, 0)) {
					return false;
				}

				longSet.add(blockPos2.asLong());
			}

			int i = addCollisionsAlongTravel(longSet, vec33, aABB, blockStepVisitor);
			if (i < 0) {
				return false;
			}

			for (BlockPos blockPos3 : BlockPos.betweenCornersInDirection(aABB, vec33)) {
				if (longSet.add(blockPos3.asLong()) && !blockStepVisitor.visit(blockPos3, i + 1)) {
					return false;
				}
			}

			return true;
		}
	}

	private static int addCollisionsAlongTravel(LongSet longSet, Vec3 vec3, AABB aABB, BlockGetter.BlockStepVisitor blockStepVisitor) {
		double d = aABB.getXsize();
		double e = aABB.getYsize();
		double f = aABB.getZsize();
		Vec3i vec3i = getFurthestCorner(vec3);
		Vec3 vec32 = aABB.getCenter();
		Vec3 vec33 = new Vec3(vec32.x() + d * 0.5 * vec3i.getX(), vec32.y() + e * 0.5 * vec3i.getY(), vec32.z() + f * 0.5 * vec3i.getZ());
		Vec3 vec34 = vec33.subtract(vec3);
		int i = Mth.floor(vec34.x);
		int j = Mth.floor(vec34.y);
		int k = Mth.floor(vec34.z);
		int l = Mth.sign(vec3.x);
		int m = Mth.sign(vec3.y);
		int n = Mth.sign(vec3.z);
		double g = l == 0 ? Double.MAX_VALUE : l / vec3.x;
		double h = m == 0 ? Double.MAX_VALUE : m / vec3.y;
		double o = n == 0 ? Double.MAX_VALUE : n / vec3.z;
		double p = g * (l > 0 ? 1.0 - Mth.frac(vec34.x) : Mth.frac(vec34.x));
		double q = h * (m > 0 ? 1.0 - Mth.frac(vec34.y) : Mth.frac(vec34.y));
		double r = o * (n > 0 ? 1.0 - Mth.frac(vec34.z) : Mth.frac(vec34.z));
		int s = 0;

		while (p <= 1.0 || q <= 1.0 || r <= 1.0) {
			if (p < q) {
				if (p < r) {
					i += l;
					p += g;
				} else {
					k += n;
					r += o;
				}
			} else if (q < r) {
				j += m;
				q += h;
			} else {
				k += n;
				r += o;
			}

			Optional<Vec3> optional = AABB.clip(i, j, k, i + 1, j + 1, k + 1, vec34, vec33);
			if (!optional.isEmpty()) {
				s++;
				Vec3 vec35 = optional.get();
				double t = Mth.clamp(vec35.x, i + 1.0E-5F, i + 1.0 - 1.0E-5F);
				double u = Mth.clamp(vec35.y, j + 1.0E-5F, j + 1.0 - 1.0E-5F);
				double v = Mth.clamp(vec35.z, k + 1.0E-5F, k + 1.0 - 1.0E-5F);
				int w = Mth.floor(t - d * vec3i.getX());
				int x = Mth.floor(u - e * vec3i.getY());
				int y = Mth.floor(v - f * vec3i.getZ());
				int z = s;

				for (BlockPos blockPos : BlockPos.betweenCornersInDirection(i, j, k, w, x, y, vec3)) {
					if (longSet.add(blockPos.asLong()) && !blockStepVisitor.visit(blockPos, z)) {
						return -1;
					}
				}
			}
		}

		return s;
	}

	private static Vec3i getFurthestCorner(Vec3 vec3) {
		double d = Math.abs(Vec3.X_AXIS.dot(vec3));
		double e = Math.abs(Vec3.Y_AXIS.dot(vec3));
		double f = Math.abs(Vec3.Z_AXIS.dot(vec3));
		int i = vec3.x >= 0.0 ? 1 : -1;
		int j = vec3.y >= 0.0 ? 1 : -1;
		int k = vec3.z >= 0.0 ? 1 : -1;
		if (d <= e && d <= f) {
			return new Vec3i(-i, -k, j);
		} else {
			return e <= f ? new Vec3i(k, -j, -i) : new Vec3i(-j, i, -k);
		}
	}

	@FunctionalInterface
	interface BlockStepVisitor {
		boolean visit(BlockPos blockPos, int i);
	}
}
