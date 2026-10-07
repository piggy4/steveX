package net.minecraft.world.level.block;

import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public interface SelectableSlotContainer {
	int getRows();

	int getColumns();

	default OptionalInt getHitSlot(BlockHitResult blockHitResult, Direction direction) {
		return getRelativeHitCoordinatesForBlockFace(blockHitResult, direction).map(vec2 -> {
			int i = getSection(1.0F - vec2.y, this.getRows());
			int j = getSection(vec2.x, this.getColumns());
			return OptionalInt.of(j + i * this.getColumns());
		}).orElseGet(OptionalInt::empty);
	}

	private static Optional<Vec2> getRelativeHitCoordinatesForBlockFace(BlockHitResult blockHitResult, Direction direction) {
		Direction direction2 = blockHitResult.getDirection();
		if (direction != direction2) {
			return Optional.empty();
		}

		BlockPos blockPos = blockHitResult.getBlockPos().relative(direction2);
		Vec3 vec3 = blockHitResult.getLocation().subtract(blockPos.getX(), blockPos.getY(), blockPos.getZ());
		double d = vec3.x();
		double e = vec3.y();
		double f = vec3.z();

		return switch (direction2) {
			case NORTH -> Optional.of(new Vec2((float)(1.0 - d), (float)e));
			case SOUTH -> Optional.of(new Vec2((float)d, (float)e));
			case WEST -> Optional.of(new Vec2((float)f, (float)e));
			case EAST -> Optional.of(new Vec2((float)(1.0 - f), (float)e));
			case DOWN, UP -> Optional.empty();
		};
	}

	private static int getSection(float f, int i) {
		float g = f * 16.0F;
		float h = 16.0F / i;
		return Mth.clamp(Mth.floor(g / h), 0, i - 1);
	}
}
