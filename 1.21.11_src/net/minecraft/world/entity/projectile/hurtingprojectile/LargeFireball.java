package net.minecraft.world.entity.projectile.hurtingprojectile;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class LargeFireball extends Fireball {
	private static final byte DEFAULT_EXPLOSION_POWER = 1;
	private int explosionPower = 1;

	public LargeFireball(EntityType<? extends LargeFireball> entityType, Level level) {
		super(entityType, level);
	}

	public LargeFireball(Level level, LivingEntity livingEntity, Vec3 vec3, int i) {
		super(EntityType.FIREBALL, livingEntity, vec3, level);
		this.explosionPower = i;
	}

	@Override
	protected void onHit(HitResult hitResult) {
		super.onHit(hitResult);
		if (this.level() instanceof ServerLevel serverLevel) {
			boolean bl = serverLevel.getGameRules().get(GameRules.MOB_GRIEFING);
			this.level().explode(this, this.getX(), this.getY(), this.getZ(), this.explosionPower, bl, Level.ExplosionInteraction.MOB);
			this.discard();
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult entityHitResult) {
		super.onHitEntity(entityHitResult);
		if (this.level() instanceof ServerLevel serverLevel) {
			Entity var6 = entityHitResult.getEntity();
			Entity entity2 = this.getOwner();
			DamageSource damageSource = this.damageSources().fireball(this, entity2);
			var6.hurtServer(serverLevel, damageSource, 6.0F);
			EnchantmentHelper.doPostAttackEffects(serverLevel, var6, damageSource);
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.putByte("ExplosionPower", (byte)this.explosionPower);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.explosionPower = valueInput.getByteOr("ExplosionPower", (byte)1);
	}
}
