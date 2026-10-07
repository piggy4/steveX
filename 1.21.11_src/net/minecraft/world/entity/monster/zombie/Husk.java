package net.minecraft.world.entity.monster.zombie;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.camel.CamelHusk;
import net.minecraft.world.entity.monster.skeleton.Parched;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;

public class Husk extends Zombie {
	public Husk(EntityType<? extends Husk> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	protected boolean isSunSensitive() {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.HUSK_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.HUSK_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.HUSK_DEATH;
	}

	@Override
	protected SoundEvent getStepSound() {
		return SoundEvents.HUSK_STEP;
	}

	@Override
	public boolean doHurtTarget(ServerLevel serverLevel, Entity entity) {
		boolean bl = super.doHurtTarget(serverLevel, entity);
		if (bl && this.getMainHandItem().isEmpty() && entity instanceof LivingEntity) {
			float f = serverLevel.getCurrentDifficultyAt(this.blockPosition()).getEffectiveDifficulty();
			((LivingEntity)entity).addEffect(new MobEffectInstance(MobEffects.HUNGER, 140 * (int)f), this);
		}

		return bl;
	}

	@Override
	protected boolean convertsInWater() {
		return true;
	}

	@Override
	protected void doUnderWaterConversion(ServerLevel serverLevel) {
		this.convertToZombieType(serverLevel, EntityType.ZOMBIE);
		if (!this.isSilent()) {
			serverLevel.levelEvent(null, 1041, this.blockPosition(), 0);
		}
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(
		ServerLevelAccessor serverLevelAccessor, DifficultyInstance difficultyInstance, EntitySpawnReason entitySpawnReason, @Nullable SpawnGroupData spawnGroupData
	) {
		RandomSource randomSource = serverLevelAccessor.getRandom();
		spawnGroupData = super.finalizeSpawn(serverLevelAccessor, difficultyInstance, entitySpawnReason, spawnGroupData);
		float f = difficultyInstance.getSpecialMultiplier();
		if (entitySpawnReason != EntitySpawnReason.CONVERSION) {
			this.setCanPickUpLoot(randomSource.nextFloat() < 0.55F * f);
		}

		if (spawnGroupData != null) {
			spawnGroupData = new Husk.HuskGroupData((Zombie.ZombieGroupData)spawnGroupData);
			((Husk.HuskGroupData)spawnGroupData).triedToSpawnCamelHusk = entitySpawnReason != EntitySpawnReason.NATURAL;
		}

		if (spawnGroupData instanceof Husk.HuskGroupData huskGroupData && !huskGroupData.triedToSpawnCamelHusk) {
			BlockPos blockPos = this.blockPosition();
			if (serverLevelAccessor.noCollision(EntityType.CAMEL_HUSK.getSpawnAABB(blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5))) {
				huskGroupData.triedToSpawnCamelHusk = true;
				if (randomSource.nextFloat() < 0.1F) {
					this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
					CamelHusk camelHusk = EntityType.CAMEL_HUSK.create(this.level(), EntitySpawnReason.NATURAL);
					if (camelHusk != null) {
						camelHusk.setPos(this.getX(), this.getY(), this.getZ());
						camelHusk.finalizeSpawn(serverLevelAccessor, difficultyInstance, entitySpawnReason, null);
						this.startRiding(camelHusk, true, true);
						serverLevelAccessor.addFreshEntity(camelHusk);
						Parched parched = EntityType.PARCHED.create(this.level(), EntitySpawnReason.NATURAL);
						if (parched != null) {
							parched.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
							parched.finalizeSpawn(serverLevelAccessor, difficultyInstance, entitySpawnReason, null);
							parched.startRiding(camelHusk, false, false);
							serverLevelAccessor.addFreshEntityWithPassengers(parched);
						}
					}
				}
			}
		}

		return spawnGroupData;
	}

	public static class HuskGroupData extends Zombie.ZombieGroupData {
		public boolean triedToSpawnCamelHusk = false;

		public HuskGroupData(Zombie.ZombieGroupData zombieGroupData) {
			super(zombieGroupData.isBaby, zombieGroupData.canSpawnJockey);
		}
	}
}
