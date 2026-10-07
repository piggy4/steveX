package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

public class ChargeAttack extends Behavior<Animal> {
	private final int timeBetweenAttacks;
	private final TargetingConditions chargeTargeting;
	private final float speed;
	private final float knockbackForce;
	private final double maxTargetDetectionDistance;
	private final double maxChargeDistance;
	private final SoundEvent chargeSound;
	private Vec3 chargeVelocityVector;
	private Vec3 startPosition;

	public ChargeAttack(int i, TargetingConditions targetingConditions, float f, float g, double d, double e, SoundEvent soundEvent) {
		super(ImmutableMap.of(MemoryModuleType.CHARGE_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT, MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT));
		this.timeBetweenAttacks = i;
		this.chargeTargeting = targetingConditions;
		this.speed = f;
		this.knockbackForce = g;
		this.maxChargeDistance = d;
		this.maxTargetDetectionDistance = e;
		this.chargeSound = soundEvent;
		this.chargeVelocityVector = Vec3.ZERO;
		this.startPosition = Vec3.ZERO;
	}

	protected boolean checkExtraStartConditions(ServerLevel serverLevel, Animal animal) {
		return animal.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
	}

	protected boolean canStillUse(ServerLevel serverLevel, Animal animal, long l) {
		Brain<?> brain = animal.getBrain();
		Optional<LivingEntity> optional = brain.getMemory(MemoryModuleType.ATTACK_TARGET);
		if (optional.isEmpty()) {
			return false;
		} else {
			LivingEntity livingEntity = optional.get();
			if (animal instanceof TamableAnimal tamableAnimal && tamableAnimal.isTame()) {
				return false;
			} else if (animal.position().subtract(this.startPosition).lengthSqr() >= this.maxChargeDistance * this.maxChargeDistance) {
				return false;
			} else if (livingEntity.position().subtract(animal.position()).lengthSqr() >= this.maxTargetDetectionDistance * this.maxTargetDetectionDistance) {
				return false;
			} else {
				return !animal.hasLineOfSight(livingEntity) ? false : !brain.hasMemoryValue(MemoryModuleType.CHARGE_COOLDOWN_TICKS);
			}
		}
	}

	protected void start(ServerLevel serverLevel, Animal animal, long l) {
		Brain<?> brain = animal.getBrain();
		this.startPosition = animal.position();
		LivingEntity livingEntity = brain.getMemory(MemoryModuleType.ATTACK_TARGET).get();
		Vec3 vec3 = livingEntity.position().subtract(animal.position()).normalize();
		this.chargeVelocityVector = vec3.scale(this.speed);
		if (this.canStillUse(serverLevel, animal, l)) {
			animal.playSound(this.chargeSound);
		}
	}

	protected void tick(ServerLevel serverLevel, Animal animal, long l) {
		Brain<?> brain = animal.getBrain();
		LivingEntity livingEntity = brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElseThrow();
		animal.lookAt(livingEntity, 360.0F, 360.0F);
		animal.setDeltaMovement(this.chargeVelocityVector);
		List<LivingEntity> list = new ArrayList<>(1);
		serverLevel.getEntities(
			EntityTypeTest.forClass(LivingEntity.class),
			animal.getBoundingBox(),
			livingEntityx -> this.chargeTargeting.test(serverLevel, animal, livingEntityx),
			list,
			1
		);
		if (!list.isEmpty()) {
			LivingEntity livingEntity2 = list.get(0);
			if (animal.hasPassenger(livingEntity2)) {
				return;
			}

			this.dealDamageToTarget(serverLevel, animal, livingEntity2);
			this.dealKnockBack(animal, livingEntity2);
			this.stop(serverLevel, animal, l);
		}
	}

	private void dealDamageToTarget(ServerLevel serverLevel, Animal animal, LivingEntity livingEntity) {
		DamageSource damageSource = serverLevel.damageSources().mobAttack(animal);
		float f = (float)animal.getAttributeValue(Attributes.ATTACK_DAMAGE);
		if (livingEntity.hurtServer(serverLevel, damageSource, f)) {
			EnchantmentHelper.doPostAttackEffects(serverLevel, livingEntity, damageSource);
		}
	}

	private void dealKnockBack(Animal animal, LivingEntity livingEntity) {
		int i = animal.hasEffect(MobEffects.SPEED) ? animal.getEffect(MobEffects.SPEED).getAmplifier() + 1 : 0;
		int j = animal.hasEffect(MobEffects.SLOWNESS) ? animal.getEffect(MobEffects.SLOWNESS).getAmplifier() + 1 : 0;
		float f = 0.25F * (i - j);
		float g = Mth.clamp(this.speed * (float)animal.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.2F, 2.0F) + f;
		animal.causeExtraKnockback(livingEntity, g * this.knockbackForce, animal.getDeltaMovement());
	}

	protected void stop(ServerLevel serverLevel, Animal animal, long l) {
		animal.getBrain().setMemory(MemoryModuleType.CHARGE_COOLDOWN_TICKS, this.timeBetweenAttacks);
		animal.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
	}
}
