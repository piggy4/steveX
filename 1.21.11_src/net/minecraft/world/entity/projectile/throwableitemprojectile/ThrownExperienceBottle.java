package net.minecraft.world.entity.projectile.throwableitemprojectile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class ThrownExperienceBottle extends ThrowableItemProjectile {
	public ThrownExperienceBottle(EntityType<? extends ThrownExperienceBottle> entityType, Level level) {
		super(entityType, level);
	}

	public ThrownExperienceBottle(Level level, LivingEntity livingEntity, ItemStack itemStack) {
		super(EntityType.EXPERIENCE_BOTTLE, livingEntity, level, itemStack);
	}

	public ThrownExperienceBottle(Level level, double d, double e, double f, ItemStack itemStack) {
		super(EntityType.EXPERIENCE_BOTTLE, d, e, f, level, itemStack);
	}

	@Override
	protected Item getDefaultItem() {
		return Items.EXPERIENCE_BOTTLE;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.07;
	}

	@Override
	protected void onHit(HitResult hitResult) {
		super.onHit(hitResult);
		if (this.level() instanceof ServerLevel serverLevel) {
			serverLevel.levelEvent(2002, this.blockPosition(), -13083194);
			int i = 3 + serverLevel.random.nextInt(5) + serverLevel.random.nextInt(5);
			if (hitResult instanceof BlockHitResult blockHitResult) {
				Vec3 vec3 = blockHitResult.getDirection().getUnitVec3();
				ExperienceOrb.awardWithDirection(serverLevel, hitResult.getLocation(), vec3, i);
			} else {
				ExperienceOrb.awardWithDirection(serverLevel, hitResult.getLocation(), this.getDeltaMovement().scale(-1.0), i);
			}

			this.discard();
		}
	}
}
