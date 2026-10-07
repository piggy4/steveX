package net.minecraft.world.entity.ai.behavior;

import java.util.Map;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class SpearAttack extends Behavior<PathfinderMob> {
	public static final int MIN_REPOSITION_DISTANCE = 6;
	public static final int MAX_REPOSITION_DISTANCE = 7;
	double speedModifierWhenCharging;
	double speedModifierWhenRepositioning;
	float approachDistanceSq;
	float targetInRangeRadiusSq;

	public SpearAttack(double d, double e, float f, float g) {
		super(Map.of(MemoryModuleType.SPEAR_STATUS, MemoryStatus.VALUE_PRESENT));
		this.speedModifierWhenCharging = d;
		this.speedModifierWhenRepositioning = e;
		this.approachDistanceSq = f * f;
		this.targetInRangeRadiusSq = g * g;
	}

	private @Nullable LivingEntity getTarget(PathfinderMob pathfinderMob) {
		return pathfinderMob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
	}

	private boolean ableToAttack(PathfinderMob pathfinderMob) {
		return this.getTarget(pathfinderMob) != null && pathfinderMob.getMainHandItem().has(DataComponents.KINETIC_WEAPON);
	}

	private int getKineticWeaponUseDuration(PathfinderMob pathfinderMob) {
		return Optional.ofNullable(pathfinderMob.getMainHandItem().get(DataComponents.KINETIC_WEAPON)).map(KineticWeapon::computeDamageUseDuration).orElse(0);
	}

	protected boolean checkExtraStartConditions(ServerLevel serverLevel, PathfinderMob pathfinderMob) {
		return pathfinderMob.getBrain().getMemory(MemoryModuleType.SPEAR_STATUS).orElse(SpearAttack.SpearStatus.APPROACH) == SpearAttack.SpearStatus.CHARGING
			&& this.ableToAttack(pathfinderMob)
			&& !pathfinderMob.isUsingItem();
	}

	protected void start(ServerLevel serverLevel, PathfinderMob pathfinderMob, long l) {
		pathfinderMob.setAggressive(true);
		pathfinderMob.getBrain().setMemory(MemoryModuleType.SPEAR_ENGAGE_TIME, this.getKineticWeaponUseDuration(pathfinderMob));
		pathfinderMob.getBrain().eraseMemory(MemoryModuleType.SPEAR_CHARGE_POSITION);
		pathfinderMob.startUsingItem(InteractionHand.MAIN_HAND);
		super.start(serverLevel, pathfinderMob, l);
	}

	protected boolean canStillUse(ServerLevel serverLevel, PathfinderMob pathfinderMob, long l) {
		return pathfinderMob.getBrain().getMemory(MemoryModuleType.SPEAR_ENGAGE_TIME).orElse(0) > 0 && this.ableToAttack(pathfinderMob);
	}

	protected void tick(ServerLevel serverLevel, PathfinderMob pathfinderMob, long l) {
		LivingEntity livingEntity = this.getTarget(pathfinderMob);
		double d = pathfinderMob.distanceToSqr(livingEntity.getX(), livingEntity.getY(), livingEntity.getZ());
		Entity entity = pathfinderMob.getRootVehicle();
		float f = 1.0F;
		if (entity instanceof Mob mob) {
			f = mob.chargeSpeedModifier();
		}

		int i = pathfinderMob.isPassenger() ? 2 : 0;
		pathfinderMob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(livingEntity, true));
		pathfinderMob.getBrain().setMemory(MemoryModuleType.SPEAR_ENGAGE_TIME, pathfinderMob.getBrain().getMemory(MemoryModuleType.SPEAR_ENGAGE_TIME).orElse(0) - 1);
		Vec3 vec3 = pathfinderMob.getBrain().getMemory(MemoryModuleType.SPEAR_CHARGE_POSITION).orElse(null);
		if (vec3 != null) {
			pathfinderMob.getNavigation().moveTo(vec3.x, vec3.y, vec3.z, f * this.speedModifierWhenRepositioning);
			if (pathfinderMob.getNavigation().isDone()) {
				pathfinderMob.getBrain().eraseMemory(MemoryModuleType.SPEAR_CHARGE_POSITION);
			}
		} else {
			pathfinderMob.getNavigation().moveTo(livingEntity, f * this.speedModifierWhenCharging);
			if (d < this.targetInRangeRadiusSq || pathfinderMob.getNavigation().isDone()) {
				double e = Math.sqrt(d);
				Vec3 vec32 = LandRandomPos.getPosAway(pathfinderMob, 6 + i - e, 7 + i - e, 7, livingEntity.position());
				pathfinderMob.getBrain().setMemory(MemoryModuleType.SPEAR_CHARGE_POSITION, vec32);
			}
		}
	}

	protected void stop(ServerLevel serverLevel, PathfinderMob pathfinderMob, long l) {
		pathfinderMob.getNavigation().stop();
		pathfinderMob.stopUsingItem();
		pathfinderMob.getBrain().eraseMemory(MemoryModuleType.SPEAR_CHARGE_POSITION);
		pathfinderMob.getBrain().eraseMemory(MemoryModuleType.SPEAR_ENGAGE_TIME);
		pathfinderMob.getBrain().setMemory(MemoryModuleType.SPEAR_STATUS, SpearAttack.SpearStatus.RETREAT);
	}

	@Override
	protected boolean timedOut(long l) {
		return false;
	}

	public enum SpearStatus {
		APPROACH,
		CHARGING,
		RETREAT;
	}
}
