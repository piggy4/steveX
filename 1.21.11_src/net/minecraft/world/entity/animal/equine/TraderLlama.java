package net.minecraft.world.entity.animal.equine;

import java.util.EnumSet;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class TraderLlama extends Llama {
	private static final int DEFAULT_DESPAWN_DELAY = 47999;
	private int despawnDelay = 47999;

	public TraderLlama(EntityType<? extends TraderLlama> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	public boolean isTraderLlama() {
		return true;
	}

	@Override
	protected @Nullable Llama makeNewLlama() {
		return EntityType.TRADER_LLAMA.create(this.level(), EntitySpawnReason.BREEDING);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.putInt("DespawnDelay", this.despawnDelay);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.despawnDelay = valueInput.getIntOr("DespawnDelay", 47999);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.goalSelector.addGoal(1, new PanicGoal(this, 2.0));
		this.targetSelector.addGoal(1, new TraderLlama.TraderLlamaDefendWanderingTraderGoal(this));
		this.targetSelector
			.addGoal(
				2, new NearestAttackableTargetGoal<>(this, Zombie.class, true, (livingEntity, serverLevel) -> livingEntity.getType() != EntityType.ZOMBIFIED_PIGLIN)
			);
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, AbstractIllager.class, true));
	}

	public void setDespawnDelay(int i) {
		this.despawnDelay = i;
	}

	@Override
	protected void doPlayerRide(Player player) {
		Entity entity = this.getLeashHolder();
		if (!(entity instanceof WanderingTrader)) {
			super.doPlayerRide(player);
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!this.level().isClientSide()) {
			this.maybeDespawn();
		}
	}

	private void maybeDespawn() {
		if (this.canDespawn()) {
			this.despawnDelay = this.isLeashedToWanderingTrader() ? ((WanderingTrader)this.getLeashHolder()).getDespawnDelay() - 1 : this.despawnDelay - 1;
			if (this.despawnDelay <= 0) {
				this.removeLeash();
				this.discard();
			}
		}
	}

	private boolean canDespawn() {
		return !this.isTamed() && !this.isLeashedToSomethingOtherThanTheWanderingTrader() && !this.hasExactlyOnePlayerPassenger();
	}

	private boolean isLeashedToWanderingTrader() {
		return this.getLeashHolder() instanceof WanderingTrader;
	}

	private boolean isLeashedToSomethingOtherThanTheWanderingTrader() {
		return this.isLeashed() && !this.isLeashedToWanderingTrader();
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(
		ServerLevelAccessor serverLevelAccessor, DifficultyInstance difficultyInstance, EntitySpawnReason entitySpawnReason, @Nullable SpawnGroupData spawnGroupData
	) {
		if (entitySpawnReason == EntitySpawnReason.EVENT) {
			this.setAge(0);
		}

		if (spawnGroupData == null) {
			spawnGroupData = new AgeableMob.AgeableMobGroupData(false);
		}

		return super.finalizeSpawn(serverLevelAccessor, difficultyInstance, entitySpawnReason, spawnGroupData);
	}

	protected static class TraderLlamaDefendWanderingTraderGoal extends TargetGoal {
		private final Llama llama;
		private LivingEntity ownerLastHurtBy;
		private int timestamp;

		public TraderLlamaDefendWanderingTraderGoal(Llama llama) {
			super(llama, false);
			this.llama = llama;
			this.setFlags(EnumSet.of(Goal.Flag.TARGET));
		}

		@Override
		public boolean canUse() {
			if (!this.llama.isLeashed()) {
				return false;
			} else if (!(this.llama.getLeashHolder() instanceof WanderingTrader wanderingTrader)) {
				return false;
			} else {
				this.ownerLastHurtBy = wanderingTrader.getLastHurtByMob();
				int i = wanderingTrader.getLastHurtByMobTimestamp();
				return i != this.timestamp && this.canAttack(this.ownerLastHurtBy, TargetingConditions.DEFAULT);
			}
		}

		@Override
		public void start() {
			this.mob.setTarget(this.ownerLastHurtBy);
			Entity entity = this.llama.getLeashHolder();
			if (entity instanceof WanderingTrader) {
				this.timestamp = ((WanderingTrader)entity).getLastHurtByMobTimestamp();
			}

			super.start();
		}
	}
}
