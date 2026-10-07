package net.minecraft.world.entity.ai.util;

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class RandomPos {
	private static final int RANDOM_POS_ATTEMPTS = 10;

	public static BlockPos generateRandomDirection(RandomSource randomSource, int i, int j) {
		int k = randomSource.nextInt(2 * i + 1) - i;
		int l = randomSource.nextInt(2 * j + 1) - j;
		int m = randomSource.nextInt(2 * i + 1) - i;
		return new BlockPos(k, l, m);
	}

	public static @Nullable BlockPos generateRandomDirectionWithinRadians(
		RandomSource randomSource, double d, double e, int i, int j, double f, double g, double h
	) {
		double k = Mth.atan2(g, f) - (float) (Math.PI / 2);
		double l = k + (2.0F * randomSource.nextFloat() - 1.0F) * h;
		double m = Mth.lerp(Math.sqrt(randomSource.nextDouble()), d, e) * Mth.SQRT_OF_TWO;
		double n = -m * Math.sin(l);
		double o = m * Math.cos(l);
		if (!(Math.abs(n) > e) && !(Math.abs(o) > e)) {
			int p = randomSource.nextInt(2 * i + 1) - i + j;
			return BlockPos.containing(n, p, o);
		} else {
			return null;
		}
	}

	@VisibleForTesting
	public static BlockPos moveUpOutOfSolid(BlockPos blockPos, int i, Predicate<BlockPos> predicate) {
		if (!predicate.test(blockPos)) {
			return blockPos;
		}

		BlockPos.MutableBlockPos mutableBlockPos = blockPos.mutable().move(Direction.UP);

		while (mutableBlockPos.getY() <= i && predicate.test(mutableBlockPos)) {
			mutableBlockPos.move(Direction.UP);
		}

		return mutableBlockPos.immutable();
	}

	@VisibleForTesting
	public static BlockPos moveUpToAboveSolid(BlockPos blockPos, int i, int j, Predicate<BlockPos> predicate) {
		if (i < 0) {
			throw new IllegalArgumentException("aboveSolidAmount was " + i + ", expected >= 0");
		}

		if (!predicate.test(blockPos)) {
			return blockPos;
		}

		BlockPos.MutableBlockPos mutableBlockPos = blockPos.mutable().move(Direction.UP);

		while (mutableBlockPos.getY() <= j && predicate.test(mutableBlockPos)) {
			mutableBlockPos.move(Direction.UP);
		}

		int k = mutableBlockPos.getY();

		while (mutableBlockPos.getY() <= j && mutableBlockPos.getY() - k < i) {
			mutableBlockPos.move(Direction.UP);
			if (predicate.test(mutableBlockPos)) {
				mutableBlockPos.move(Direction.DOWN);
				break;
			}
		}

		return mutableBlockPos.immutable();
	}

	public static @Nullable Vec3 generateRandomPos(PathfinderMob pathfinderMob, Supplier<@Nullable BlockPos> supplier) {
		return generateRandomPos(supplier, pathfinderMob::getWalkTargetValue);
	}

	public static @Nullable Vec3 generateRandomPos(Supplier<@Nullable BlockPos> supplier, ToDoubleFunction<BlockPos> toDoubleFunction) {
		double d = Double.NEGATIVE_INFINITY;
		BlockPos blockPos = null;

		for (int i = 0; i < 10; i++) {
			BlockPos blockPos2 = supplier.get();
			if (blockPos2 != null) {
				double e = toDoubleFunction.applyAsDouble(blockPos2);
				if (e > d) {
					d = e;
					blockPos = blockPos2;
				}
			}
		}

		return blockPos != null ? Vec3.atBottomCenterOf(blockPos) : null;
	}

	public static BlockPos generateRandomPosTowardDirection(PathfinderMob pathfinderMob, double d, RandomSource randomSource, BlockPos blockPos) {
		double e = blockPos.getX();
		double f = blockPos.getZ();
		if (pathfinderMob.hasHome() && d > 1.0) {
			BlockPos blockPos2 = pathfinderMob.getHomePosition();
			if (pathfinderMob.getX() > blockPos2.getX()) {
				e -= randomSource.nextDouble() * d / 2.0;
			} else {
				e += randomSource.nextDouble() * d / 2.0;
			}

			if (pathfinderMob.getZ() > blockPos2.getZ()) {
				f -= randomSource.nextDouble() * d / 2.0;
			} else {
				f += randomSource.nextDouble() * d / 2.0;
			}
		}

		return BlockPos.containing(e + pathfinderMob.getX(), blockPos.getY() + pathfinderMob.getY(), f + pathfinderMob.getZ());
	}
}
