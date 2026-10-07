package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public interface DispensibleContainerItem {
	default void checkExtraContent(@Nullable LivingEntity livingEntity, Level level, ItemStack itemStack, BlockPos blockPos) {
	}

	boolean emptyContents(@Nullable LivingEntity livingEntity, Level level, BlockPos blockPos, @Nullable BlockHitResult blockHitResult);
}
