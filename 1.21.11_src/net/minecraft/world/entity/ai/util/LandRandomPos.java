package net.minecraft.world.entity.ai.util;

import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class LandRandomPos {
	public static @Nullable Vec3 getPos(PathfinderMob pathfinderMob, int i, int j) {
		return getPos(pathfinderMob, i, j, pathfinderMob::getWalkTargetValue);
	}

	public static @Nullable Vec3 getPos(PathfinderMob pathfinderMob, int i, int j, ToDoubleFunction<BlockPos> toDoubleFunction) {
		boolean bl = GoalUtils.mobRestricted(pathfinderMob, i);
		return RandomPos.generateRandomPos(() -> {
			BlockPos blockPos = RandomPos.generateRandomDirection(pathfinderMob.getRandom(), i, j);
			BlockPos blockPos2 = generateRandomPosTowardDirection(pathfinderMob, i, bl, blockPos);
			return blockPos2 == null ? null : movePosUpOutOfSolid(pathfinderMob, blockPos2);
		}, toDoubleFunction);
	}

	public static @Nullable Vec3 getPosTowards(PathfinderMob pathfinderMob, int i, int j, Vec3 vec3) {
		Vec3 vec32 = vec3.subtract(pathfinderMob.getX(), pathfinderMob.getY(), pathfinderMob.getZ());
		boolean bl = GoalUtils.mobRestricted(pathfinderMob, i);
		return getPosInDirection(pathfinderMob, 0.0, i, j, vec32, bl);
	}

	public static @Nullable Vec3 getPosAway(PathfinderMob pathfinderMob, int i, int j, Vec3 vec3) {
		return getPosAway(pathfinderMob, 0.0, i, j, vec3);
	}

	public static @Nullable Vec3 getPosAway(PathfinderMob pathfinderMob, double d, double e, int i, Vec3 vec3) {
		Vec3 vec32 = pathfinderMob.position().subtract(vec3);
		if (vec32.length() == 0.0) {
			vec32 = new Vec3(pathfinderMob.getRandom().nextDouble() - 0.5, 0.0, pathfinderMob.getRandom().nextDouble() - 0.5);
		}

		boolean bl = GoalUtils.mobRestricted(pathfinderMob, e);
		return getPosInDirection(pathfinderMob, d, e, i, vec32, bl);
	}

	private static @Nullable Vec3 getPosInDirection(PathfinderMob pathfinderMob, double d, double e, int i, Vec3 vec3, boolean bl) {
		return RandomPos.generateRandomPos(pathfinderMob, () -> {
			BlockPos blockPos = RandomPos.generateRandomDirectionWithinRadians(pathfinderMob.getRandom(), d, e, i, 0, vec3.x, vec3.z, (float) (Math.PI / 2));
			if (blockPos == null) {
				return null;
			}

			BlockPos blockPos2 = generateRandomPosTowardDirection(pathfinderMob, e, bl, blockPos);
			return blockPos2 == null ? null : movePosUpOutOfSolid(pathfinderMob, blockPos2);
		});
	}

	public static @Nullable BlockPos movePosUpOutOfSolid(PathfinderMob pathfinderMob, BlockPos blockPos) {
		blockPos = RandomPos.moveUpOutOfSolid(blockPos, pathfinderMob.level().getMaxY(), blockPosx -> GoalUtils.isSolid(pathfinderMob, blockPosx));
		return !GoalUtils.isWater(pathfinderMob, blockPos) && !GoalUtils.hasMalus(pathfinderMob, blockPos) ? blockPos : null;
	}

	public static @Nullable BlockPos generateRandomPosTowardDirection(PathfinderMob pathfinderMob, double d, boolean bl, BlockPos blockPos) {
		BlockPos blockPos2 = RandomPos.generateRandomPosTowardDirection(pathfinderMob, d, pathfinderMob.getRandom(), blockPos);
		return !GoalUtils.isOutsideLimits(blockPos2, pathfinderMob)
				&& !GoalUtils.isRestricted(bl, pathfinderMob, blockPos2)
				&& !GoalUtils.isNotStable(pathfinderMob.getNavigation(), blockPos2)
			? blockPos2
			: null;
	}
}
