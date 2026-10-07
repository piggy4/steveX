package net.minecraft.world.entity;

import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public interface NeutralMob {
	String TAG_ANGER_END_TIME = "anger_end_time";
	String TAG_ANGRY_AT = "angry_at";
	long NO_ANGER_END_TIME = -1L;

	long getPersistentAngerEndTime();

	default void setTimeToRemainAngry(long l) {
		this.setPersistentAngerEndTime(this.level().getGameTime() + l);
	}

	void setPersistentAngerEndTime(long l);

	@Nullable EntityReference<LivingEntity> getPersistentAngerTarget();

	void setPersistentAngerTarget(@Nullable EntityReference<LivingEntity> entityReference);

	void startPersistentAngerTimer();

	Level level();

	default void addPersistentAngerSaveData(ValueOutput valueOutput) {
		valueOutput.putLong("anger_end_time", this.getPersistentAngerEndTime());
		valueOutput.storeNullable("angry_at", EntityReference.codec(), this.getPersistentAngerTarget());
	}

	default void readPersistentAngerSaveData(Level level, ValueInput valueInput) {
		Optional<Long> optional = valueInput.getLong("anger_end_time");
		if (optional.isPresent()) {
			this.setPersistentAngerEndTime(optional.get());
		} else {
			Optional<Integer> optional2 = valueInput.getInt("AngerTime");
			if (optional2.isPresent()) {
				this.setTimeToRemainAngry(optional2.get().intValue());
			} else {
				this.setPersistentAngerEndTime(-1L);
			}
		}

		if (level instanceof ServerLevel) {
			this.setPersistentAngerTarget(EntityReference.read(valueInput, "angry_at"));
			this.setTarget(EntityReference.getLivingEntity(this.getPersistentAngerTarget(), level));
		}
	}

	default void updatePersistentAnger(ServerLevel serverLevel, boolean bl) {
		LivingEntity livingEntity = this.getTarget();
		EntityReference<LivingEntity> entityReference = this.getPersistentAngerTarget();
		if (livingEntity != null && livingEntity.isDeadOrDying() && entityReference != null && entityReference.matches(livingEntity) && livingEntity instanceof Mob) {
			this.stopBeingAngry();
		} else {
			if (livingEntity != null) {
				if (entityReference == null || !entityReference.matches(livingEntity)) {
					this.setPersistentAngerTarget(EntityReference.of(livingEntity));
				}

				this.startPersistentAngerTimer();
			}

			if (entityReference != null && !this.isAngry() && (livingEntity == null || !isValidPlayerTarget(livingEntity) || !bl)) {
				this.stopBeingAngry();
			}
		}
	}

	private static boolean isValidPlayerTarget(LivingEntity livingEntity) {
		return livingEntity instanceof Player player && !player.isCreative() && !player.isSpectator();
	}

	default boolean isAngryAt(LivingEntity livingEntity, ServerLevel serverLevel) {
		if (!this.canAttack(livingEntity)) {
			return false;
		}

		if (isValidPlayerTarget(livingEntity) && this.isAngryAtAllPlayers(serverLevel)) {
			return true;
		}

		EntityReference<LivingEntity> entityReference = this.getPersistentAngerTarget();
		return entityReference != null && entityReference.matches(livingEntity);
	}

	default boolean isAngryAtAllPlayers(ServerLevel serverLevel) {
		return serverLevel.getGameRules().get(GameRules.UNIVERSAL_ANGER) && this.isAngry() && this.getPersistentAngerTarget() == null;
	}

	default boolean isAngry() {
		long l = this.getPersistentAngerEndTime();
		if (l > 0L) {
			long m = l - this.level().getGameTime();
			return m > 0L;
		} else {
			return false;
		}
	}

	default void playerDied(ServerLevel serverLevel, Player player) {
		if (serverLevel.getGameRules().get(GameRules.FORGIVE_DEAD_PLAYERS)) {
			EntityReference<LivingEntity> entityReference = this.getPersistentAngerTarget();
			if (entityReference != null && entityReference.matches(player)) {
				this.stopBeingAngry();
			}
		}
	}

	default void forgetCurrentTargetAndRefreshUniversalAnger() {
		this.stopBeingAngry();
		this.startPersistentAngerTimer();
	}

	default void stopBeingAngry() {
		this.setLastHurtByMob(null);
		this.setPersistentAngerTarget(null);
		this.setTarget(null);
		this.setPersistentAngerEndTime(-1L);
	}

	@Nullable LivingEntity getLastHurtByMob();

	void setLastHurtByMob(@Nullable LivingEntity livingEntity);

	void setTarget(@Nullable LivingEntity livingEntity);

	boolean canAttack(LivingEntity livingEntity);

	@Nullable LivingEntity getTarget();
}
