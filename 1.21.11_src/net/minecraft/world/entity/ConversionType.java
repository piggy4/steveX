package net.minecraft.world.entity;

import java.util.Set;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Scoreboard;

public enum ConversionType {
	SINGLE(true) {
		@Override
		void convert(Mob mob, Mob mob2, ConversionParams conversionParams) {
			Entity entity = mob.getFirstPassenger();
			mob2.copyPosition(mob);
			mob2.setDeltaMovement(mob.getDeltaMovement());
			if (entity != null) {
				entity.stopRiding();
				entity.boardingCooldown = 0;

				for (Entity entity2 : mob2.getPassengers()) {
					entity2.stopRiding();
					entity2.remove(Entity.RemovalReason.DISCARDED);
				}

				entity.startRiding(mob2);
			}

			Entity entity3 = mob.getVehicle();
			if (entity3 != null) {
				mob.stopRiding();
				mob2.startRiding(entity3, false, false);
			}

			if (conversionParams.keepEquipment()) {
				for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
					ItemStack itemStack = mob.getItemBySlot(equipmentSlot);
					if (!itemStack.isEmpty()) {
						mob2.setItemSlot(equipmentSlot, itemStack.copyAndClear());
						mob2.setDropChance(equipmentSlot, mob.getDropChances().byEquipment(equipmentSlot));
					}
				}
			}

			mob2.fallDistance = mob.fallDistance;
			mob2.setSharedFlag(7, mob.isFallFlying());
			mob2.lastHurtByPlayerMemoryTime = mob.lastHurtByPlayerMemoryTime;
			mob2.hurtTime = mob.hurtTime;
			mob2.yBodyRot = mob.yBodyRot;
			mob2.setOnGround(mob.onGround());
			mob.getSleepingPos().ifPresent(mob2::setSleepingPos);
			Entity entity2 = mob.getLeashHolder();
			if (entity2 != null) {
				mob2.setLeashedTo(entity2, true);
			}

			this.convertCommon(mob, mob2, conversionParams);
		}
	},
	SPLIT_ON_DEATH(false) {
		@Override
		void convert(Mob mob, Mob mob2, ConversionParams conversionParams) {
			Entity entity = mob.getFirstPassenger();
			if (entity != null) {
				entity.stopRiding();
			}

			Entity entity2 = mob.getLeashHolder();
			if (entity2 != null) {
				mob.dropLeash();
			}

			this.convertCommon(mob, mob2, conversionParams);
		}
	};

	private static final Set<DataComponentType<?>> COMPONENTS_TO_COPY = Set.of(DataComponents.CUSTOM_NAME, DataComponents.CUSTOM_DATA);
	private final boolean discardAfterConversion;

	ConversionType(final boolean bl) {
		this.discardAfterConversion = bl;
	}

	public boolean shouldDiscardAfterConversion() {
		return this.discardAfterConversion;
	}

	abstract void convert(Mob mob, Mob mob2, ConversionParams conversionParams);

	void convertCommon(Mob mob, Mob mob2, ConversionParams conversionParams) {
		mob2.setAbsorptionAmount(mob.getAbsorptionAmount());

		for (MobEffectInstance mobEffectInstance : mob.getActiveEffects()) {
			mob2.addEffect(new MobEffectInstance(mobEffectInstance));
		}

		if (mob.isBaby()) {
			mob2.setBaby(true);
		}

		if (mob instanceof AgeableMob ageableMob && mob2 instanceof AgeableMob ageableMob2) {
			ageableMob2.setAge(ageableMob.getAge());
			ageableMob2.forcedAge = ageableMob.forcedAge;
			ageableMob2.forcedAgeTimer = ageableMob.forcedAgeTimer;
		}

		Brain<?> brain = mob.getBrain();
		Brain<?> brain2 = mob2.getBrain();
		if (brain.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.REGISTERED) && brain.hasMemoryValue(MemoryModuleType.ANGRY_AT)) {
			brain2.setMemory(MemoryModuleType.ANGRY_AT, brain.getMemory(MemoryModuleType.ANGRY_AT));
		}

		if (conversionParams.preserveCanPickUpLoot()) {
			mob2.setCanPickUpLoot(mob.canPickUpLoot());
		}

		mob2.setLeftHanded(mob.isLeftHanded());
		mob2.setNoAi(mob.isNoAi());
		if (mob.isPersistenceRequired()) {
			mob2.setPersistenceRequired();
		}

		mob2.setCustomNameVisible(mob.isCustomNameVisible());
		mob2.setSharedFlagOnFire(mob.isOnFire());
		mob2.setInvulnerable(mob.isInvulnerable());
		mob2.setNoGravity(mob.isNoGravity());
		mob2.setPortalCooldown(mob.getPortalCooldown());
		mob2.setSilent(mob.isSilent());
		mob.getTags().forEach(mob2::addTag);

		for (DataComponentType<?> dataComponentType : COMPONENTS_TO_COPY) {
			copyComponent(mob, mob2, dataComponentType);
		}

		if (conversionParams.team() != null) {
			Scoreboard scoreboard = mob2.level().getScoreboard();
			scoreboard.addPlayerToTeam(mob2.getStringUUID(), conversionParams.team());
			if (mob.getTeam() != null && mob.getTeam() == conversionParams.team()) {
				scoreboard.removePlayerFromTeam(mob.getStringUUID(), mob.getTeam());
			}
		}

		if (mob instanceof Zombie zombie && zombie.canBreakDoors() && mob2 instanceof Zombie zombie2) {
			zombie2.setCanBreakDoors(true);
		}
	}

	private static <T> void copyComponent(Mob mob, Mob mob2, DataComponentType<T> dataComponentType) {
		T object = mob.get(dataComponentType);
		if (object != null) {
			mob2.setComponent(dataComponentType, object);
		}
	}
}
