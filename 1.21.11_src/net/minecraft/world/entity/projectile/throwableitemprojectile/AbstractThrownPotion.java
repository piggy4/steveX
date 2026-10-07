package net.minecraft.world.entity.projectile.throwableitemprojectile;

import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public abstract class AbstractThrownPotion extends ThrowableItemProjectile {
	public static final double SPLASH_RANGE = 4.0;
	protected static final double SPLASH_RANGE_SQ = 16.0;
	public static final Predicate<LivingEntity> WATER_SENSITIVE_OR_ON_FIRE = livingEntity -> livingEntity.isSensitiveToWater() || livingEntity.isOnFire();

	public AbstractThrownPotion(EntityType<? extends AbstractThrownPotion> entityType, Level level) {
		super(entityType, level);
	}

	public AbstractThrownPotion(EntityType<? extends AbstractThrownPotion> entityType, Level level, LivingEntity livingEntity, ItemStack itemStack) {
		super(entityType, livingEntity, level, itemStack);
	}

	public AbstractThrownPotion(EntityType<? extends AbstractThrownPotion> entityType, Level level, double d, double e, double f, ItemStack itemStack) {
		super(entityType, d, e, f, level, itemStack);
	}

	@Override
	protected double getDefaultGravity() {
		return 0.05;
	}

	@Override
	protected void onHitBlock(BlockHitResult blockHitResult) {
		super.onHitBlock(blockHitResult);
		if (!this.level().isClientSide()) {
			ItemStack itemStack = this.getItem();
			Direction direction = blockHitResult.getDirection();
			BlockPos blockPos = blockHitResult.getBlockPos();
			BlockPos blockPos2 = blockPos.relative(direction);
			PotionContents potionContents = itemStack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
			if (potionContents.is(Potions.WATER)) {
				this.dowseFire(blockPos2);
				this.dowseFire(blockPos2.relative(direction.getOpposite()));

				for (Direction direction2 : Direction.Plane.HORIZONTAL) {
					this.dowseFire(blockPos2.relative(direction2));
				}
			}
		}
	}

	@Override
	protected void onHit(HitResult hitResult) {
		super.onHit(hitResult);
		if (this.level() instanceof ServerLevel serverLevel) {
			ItemStack itemStack = this.getItem();
			PotionContents potionContents = itemStack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
			if (potionContents.is(Potions.WATER)) {
				this.onHitAsWater(serverLevel);
			} else if (potionContents.hasEffects()) {
				this.onHitAsPotion(serverLevel, itemStack, hitResult);
			}

			int i = potionContents.potion().isPresent() && potionContents.potion().get().value().hasInstantEffects() ? 2007 : 2002;
			serverLevel.levelEvent(i, this.blockPosition(), potionContents.getColor());
			this.discard();
		}
	}

	private void onHitAsWater(ServerLevel serverLevel) {
		AABB aABB = this.getBoundingBox().inflate(4.0, 2.0, 4.0);

		for (LivingEntity livingEntity : this.level().getEntitiesOfClass(LivingEntity.class, aABB, WATER_SENSITIVE_OR_ON_FIRE)) {
			double d = this.distanceToSqr(livingEntity);
			if (d < 16.0) {
				if (livingEntity.isSensitiveToWater()) {
					livingEntity.hurtServer(serverLevel, this.damageSources().indirectMagic(this, this.getOwner()), 1.0F);
				}

				if (livingEntity.isOnFire() && livingEntity.isAlive()) {
					livingEntity.extinguishFire();
				}
			}
		}

		for (Axolotl axolotl : this.level().getEntitiesOfClass(Axolotl.class, aABB)) {
			axolotl.rehydrate();
		}
	}

	protected abstract void onHitAsPotion(ServerLevel serverLevel, ItemStack itemStack, HitResult hitResult);

	private void dowseFire(BlockPos blockPos) {
		BlockState blockState = this.level().getBlockState(blockPos);
		if (blockState.is(BlockTags.FIRE)) {
			this.level().destroyBlock(blockPos, false, this);
		} else if (AbstractCandleBlock.isLit(blockState)) {
			AbstractCandleBlock.extinguish(null, blockState, this.level(), blockPos);
		} else if (CampfireBlock.isLitCampfire(blockState)) {
			this.level().levelEvent(null, 1009, blockPos, 0);
			CampfireBlock.dowse(this.getOwner(), this.level(), blockPos, blockState);
			this.level().setBlockAndUpdate(blockPos, blockState.setValue(CampfireBlock.LIT, false));
		}
	}

	@Override
	public DoubleDoubleImmutablePair calculateHorizontalHurtKnockbackDirection(LivingEntity livingEntity, DamageSource damageSource) {
		double d = livingEntity.position().x - this.position().x;
		double e = livingEntity.position().z - this.position().z;
		return DoubleDoubleImmutablePair.of(d, e);
	}
}
