package net.minecraft.world.entity.ai.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class HoverRandomPos {
	public static @Nullable Vec3 getPos(PathfinderMob pathfinderMob, int i, int j, double d, double e, float f, int k, int l) {
		boolean bl = GoalUtils.mobRestricted(pathfinderMob, i);
		return RandomPos.generateRandomPos(
			pathfinderMob,
			() -> {
				BlockPos blockPos = RandomPos.generateRandomDirectionWithinRadians(pathfinderMob.getRandom(), 0.0, i, j, 0, d, e, f);
				if (blockPos == null) {
					return null;
				}

				BlockPos blockPos2 = LandRandomPos.generateRandomPosTowardDirection(pathfinderMob, i, bl, blockPos);
				if (blockPos2 == null) {
					return null;
				}

				blockPos2 = RandomPos.moveUpToAboveSolid(
					blockPos2, pathfinderMob.getRandom().nextInt(k - l + 1) + l, pathfinderMob.level().getMaxY(), blockPosx -> GoalUtils.isSolid(pathfinderMob, blockPosx)
				);
				return !GoalUtils.isWater(pathfinderMob, blockPos2) && !GoalUtils.hasMalus(pathfinderMob, blockPos2) ? blockPos2 : null;
			}
		);
	}
}
