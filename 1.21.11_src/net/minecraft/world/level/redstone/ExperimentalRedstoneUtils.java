package net.minecraft.world.level.redstone;

import net.minecraft.core.Direction;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class ExperimentalRedstoneUtils {
	public static @Nullable Orientation initialOrientation(Level level, @Nullable Direction direction, @Nullable Direction direction2) {
		if (level.enabledFeatures().contains(FeatureFlags.REDSTONE_EXPERIMENTS)) {
			Orientation orientation = Orientation.random(level.random).withSideBias(Orientation.SideBias.LEFT);
			if (direction2 != null) {
				orientation = orientation.withUp(direction2);
			}

			if (direction != null) {
				orientation = orientation.withFront(direction);
			}

			return orientation;
		} else {
			return null;
		}
	}

	public static @Nullable Orientation withFront(@Nullable Orientation orientation, Direction direction) {
		return orientation == null ? null : orientation.withFront(direction);
	}
}
