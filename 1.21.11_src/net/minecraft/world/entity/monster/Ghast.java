package net.minecraft.world.entity.monster;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Ghast extends Mob implements Enemy {
	private static final EntityDataAccessor<Boolean> DATA_IS_CHARGING = SynchedEntityData.defineId(Ghast.class, EntityDataSerializers.BOOLEAN);
	private static final byte DEFAULT_EXPLOSION_POWER = 1;
	private int explosionPower = 1;

	public Ghast(EntityType<? extends Ghast> entityType, Level level) {
		super(entityType, level);
		this.xpReward = 5;
		this.moveControl = new Ghast.GhastMoveControl(this, false, () -> false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(5, new Ghast.RandomFloatAroundGoal(this));
		this.goalSelector.addGoal(7, new Ghast.GhastLookGoal(this));
		this.goalSelector.addGoal(7, new Ghast.GhastShootFireballGoal(this));
		this.targetSelector
			.addGoal(
				1,
				new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (livingEntity, serverLevel) -> Math.abs(livingEntity.getY() - this.getY()) <= 4.0)
			);
	}

	public boolean isCharging() {
		return this.entityData.get(DATA_IS_CHARGING);
	}

	public void setCharging(boolean bl) {
		this.entityData.set(DATA_IS_CHARGING, bl);
	}

	public int getExplosionPower() {
		return this.explosionPower;
	}

	private static boolean isReflectedFireball(DamageSource damageSource) {
		return damageSource.getDirectEntity() instanceof LargeFireball && damageSource.getEntity() instanceof Player;
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel serverLevel, DamageSource damageSource) {
		return this.isInvulnerable() && !damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
			|| !isReflectedFireball(damageSource) && super.isInvulnerableTo(serverLevel, damageSource);
	}

	@Override
	protected void checkFallDamage(double d, boolean bl, BlockState blockState, BlockPos blockPos) {
	}

	@Override
	public boolean onClimbable() {
		return false;
	}

	@Override
	public void travel(Vec3 vec3) {
		this.travelFlying(vec3, 0.02F);
	}

	@Override
	public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float f) {
		if (isReflectedFireball(damageSource)) {
			super.hurtServer(serverLevel, damageSource, 1000.0F);
			return true;
		} else {
			return this.isInvulnerableTo(serverLevel, damageSource) ? false : super.hurtServer(serverLevel, damageSource, f);
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_IS_CHARGING, false);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 10.0)
			.add(Attributes.FOLLOW_RANGE, 100.0)
			.add(Attributes.CAMERA_DISTANCE, 8.0)
			.add(Attributes.FLYING_SPEED, 0.06);
	}

	@Override
	public SoundSource getSoundSource() {
		return SoundSource.HOSTILE;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.GHAST_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.GHAST_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.GHAST_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 5.0F;
	}

	public static boolean checkGhastSpawnRules(
		EntityType<Ghast> entityType, LevelAccessor levelAccessor, EntitySpawnReason entitySpawnReason, BlockPos blockPos, RandomSource randomSource
	) {
		return levelAccessor.getDifficulty() != Difficulty.PEACEFUL
			&& randomSource.nextInt(20) == 0
			&& checkMobSpawnRules(entityType, levelAccessor, entitySpawnReason, blockPos, randomSource);
	}

	@Override
	public int getMaxSpawnClusterSize() {
		return 1;
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

	@Override
	public boolean supportQuadLeashAsHolder() {
		return true;
	}

	@Override
	public double leashElasticDistance() {
		return 10.0;
	}

	@Override
	public double leashSnapDistance() {
		return 16.0;
	}

	public static void faceMovementDirection(Mob mob) {
		if (mob.getTarget() == null) {
			Vec3 vec3 = mob.getDeltaMovement();
			mob.setYRot(-((float)Mth.atan2(vec3.x, vec3.z)) * (180.0F / (float)Math.PI));
			mob.yBodyRot = mob.getYRot();
		} else {
			LivingEntity livingEntity = mob.getTarget();
			double d = 64.0;
			if (livingEntity.distanceToSqr(mob) < 4096.0) {
				double e = livingEntity.getX() - mob.getX();
				double f = livingEntity.getZ() - mob.getZ();
				mob.setYRot(-((float)Mth.atan2(e, f)) * (180.0F / (float)Math.PI));
				mob.yBodyRot = mob.getYRot();
			}
		}
	}

	public static class GhastLookGoal extends Goal {
		private final Mob ghast;

		public GhastLookGoal(Mob mob) {
			this.ghast = mob;
			this.setFlags(EnumSet.of(Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return true;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			Ghast.faceMovementDirection(this.ghast);
		}
	}

	public static class GhastMoveControl extends MoveControl {
		private final Mob ghast;
		private int floatDuration;
		private final boolean careful;
		private final BooleanSupplier shouldBeStopped;

		public GhastMoveControl(Mob mob, boolean bl, BooleanSupplier booleanSupplier) {
			super(mob);
			this.ghast = mob;
			this.careful = bl;
			this.shouldBeStopped = booleanSupplier;
		}

		@Override
		public void tick() {
			if (this.shouldBeStopped.getAsBoolean()) {
				this.operation = MoveControl.Operation.WAIT;
				this.ghast.stopInPlace();
			}

			if (this.operation == MoveControl.Operation.MOVE_TO) {
				if (this.floatDuration-- <= 0) {
					this.floatDuration = this.floatDuration + this.ghast.getRandom().nextInt(5) + 2;
					Vec3 vec3 = new Vec3(this.wantedX - this.ghast.getX(), this.wantedY - this.ghast.getY(), this.wantedZ - this.ghast.getZ());
					if (this.canReach(vec3)) {
						this.ghast.setDeltaMovement(this.ghast.getDeltaMovement().add(vec3.normalize().scale(this.ghast.getAttributeValue(Attributes.FLYING_SPEED) * 5.0 / 3.0)));
					} else {
						this.operation = MoveControl.Operation.WAIT;
					}
				}
			}
		}

		private boolean canReach(Vec3 vec3) {
			AABB aABB = this.ghast.getBoundingBox();
			AABB aABB2 = aABB.move(vec3);
			if (this.careful) {
				for (BlockPos blockPos : BlockPos.betweenClosed(aABB2.inflate(1.0))) {
					if (!this.blockTraversalPossible(this.ghast.level(), null, null, blockPos, false, false)) {
						return false;
					}
				}
			}

			boolean bl = this.ghast.isInWater();
			boolean bl2 = this.ghast.isInLava();
			Vec3 vec32 = this.ghast.position();
			Vec3 vec33 = vec32.add(vec3);
			return BlockGetter.forEachBlockIntersectedBetween(
				vec32, vec33, aABB2, (blockPos, i) -> aABB.intersects(blockPos) ? true : this.blockTraversalPossible(this.ghast.level(), vec32, vec33, blockPos, bl, bl2)
			);
		}

		private boolean blockTraversalPossible(BlockGetter blockGetter, @Nullable Vec3 vec3, @Nullable Vec3 vec32, BlockPos blockPos, boolean bl, boolean bl2) {
			BlockState blockState = blockGetter.getBlockState(blockPos);
			if (blockState.isAir()) {
				return true;
			}

			boolean bl3 = vec3 != null && vec32 != null;
			boolean bl4 = bl3
				? !this.ghast.collidedWithShapeMovingFrom(vec3, vec32, blockState.getCollisionShape(blockGetter, blockPos).move(new Vec3(blockPos)).toAabbs())
				: blockState.getCollisionShape(blockGetter, blockPos).isEmpty();
			if (!this.careful) {
				return bl4;
			}

			if (blockState.is(BlockTags.HAPPY_GHAST_AVOIDS)) {
				return false;
			}

			FluidState fluidState = blockGetter.getFluidState(blockPos);
			if (!fluidState.isEmpty() && (!bl3 || this.ghast.collidedWithFluid(fluidState, blockPos, vec3, vec32))) {
				if (fluidState.is(FluidTags.WATER)) {
					return bl;
				}

				if (fluidState.is(FluidTags.LAVA)) {
					return bl2;
				}
			}

			return bl4;
		}
	}

	static class GhastShootFireballGoal extends Goal {
		private final Ghast ghast;
		public int chargeTime;

		public GhastShootFireballGoal(Ghast ghast) {
			this.ghast = ghast;
		}

		@Override
		public boolean canUse() {
			return this.ghast.getTarget() != null;
		}

		@Override
		public void start() {
			this.chargeTime = 0;
		}

		@Override
		public void stop() {
			this.ghast.setCharging(false);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity livingEntity = this.ghast.getTarget();
			if (livingEntity != null) {
				double d = 64.0;
				if (livingEntity.distanceToSqr(this.ghast) < 4096.0 && this.ghast.hasLineOfSight(livingEntity)) {
					Level level = this.ghast.level();
					this.chargeTime++;
					if (this.chargeTime == 10 && !this.ghast.isSilent()) {
						level.levelEvent(null, 1015, this.ghast.blockPosition(), 0);
					}

					if (this.chargeTime == 20) {
						double e = 4.0;
						Vec3 vec3 = this.ghast.getViewVector(1.0F);
						double f = livingEntity.getX() - (this.ghast.getX() + vec3.x * 4.0);
						double g = livingEntity.getY(0.5) - (0.5 + this.ghast.getY(0.5));
						double h = livingEntity.getZ() - (this.ghast.getZ() + vec3.z * 4.0);
						Vec3 vec32 = new Vec3(f, g, h);
						if (!this.ghast.isSilent()) {
							level.levelEvent(null, 1016, this.ghast.blockPosition(), 0);
						}

						LargeFireball largeFireball = new LargeFireball(level, this.ghast, vec32.normalize(), this.ghast.getExplosionPower());
						largeFireball.setPos(this.ghast.getX() + vec3.x * 4.0, this.ghast.getY(0.5) + 0.5, largeFireball.getZ() + vec3.z * 4.0);
						level.addFreshEntity(largeFireball);
						this.chargeTime = -40;
					}
				} else if (this.chargeTime > 0) {
					this.chargeTime--;
				}

				this.ghast.setCharging(this.chargeTime > 10);
			}
		}
	}

	public static class RandomFloatAroundGoal extends Goal {
		private static final int MAX_ATTEMPTS = 64;
		private final Mob ghast;
		private final int distanceToBlocks;

		public RandomFloatAroundGoal(Mob mob) {
			this(mob, 0);
		}

		public RandomFloatAroundGoal(Mob mob, int i) {
			this.ghast = mob;
			this.distanceToBlocks = i;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			MoveControl moveControl = this.ghast.getMoveControl();
			if (!moveControl.hasWanted()) {
				return true;
			}

			double d = moveControl.getWantedX() - this.ghast.getX();
			double e = moveControl.getWantedY() - this.ghast.getY();
			double f = moveControl.getWantedZ() - this.ghast.getZ();
			double g = d * d + e * e + f * f;
			return g < 1.0 || g > 3600.0;
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}

		@Override
		public void start() {
			Vec3 vec3 = getSuitableFlyToPosition(this.ghast, this.distanceToBlocks);
			this.ghast.getMoveControl().setWantedPosition(vec3.x(), vec3.y(), vec3.z(), 1.0);
		}

		public static Vec3 getSuitableFlyToPosition(Mob mob, int i) {
			Level level = mob.level();
			RandomSource randomSource = mob.getRandom();
			Vec3 vec3 = mob.position();
			Vec3 vec32 = null;

			for (int j = 0; j < 64; j++) {
				vec32 = chooseRandomPositionWithRestriction(mob, vec3, randomSource);
				if (vec32 != null && isGoodTarget(level, vec32, i)) {
					return vec32;
				}
			}

			if (vec32 == null) {
				vec32 = chooseRandomPosition(vec3, randomSource);
			}

			BlockPos blockPos = BlockPos.containing(vec32);
			int k = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockPos.getX(), blockPos.getZ());
			if (k < blockPos.getY() && k > level.getMinY()) {
				vec32 = new Vec3(vec32.x(), mob.getY() - Math.abs(mob.getY() - vec32.y()), vec32.z());
			}

			return vec32;
		}

		private static boolean isGoodTarget(Level level, Vec3 vec3, int i) {
			if (i <= 0) {
				return true;
			}

			BlockPos blockPos = BlockPos.containing(vec3);
			if (!level.getBlockState(blockPos).isAir()) {
				return false;
			}

			for (Direction direction : Direction.values()) {
				for (int j = 1; j < i; j++) {
					BlockPos blockPos2 = blockPos.relative(direction, j);
					if (!level.getBlockState(blockPos2).isAir()) {
						return true;
					}
				}
			}

			return false;
		}

		private static Vec3 chooseRandomPosition(Vec3 vec3, RandomSource randomSource) {
			double d = vec3.x() + (randomSource.nextFloat() * 2.0F - 1.0F) * 16.0F;
			double e = vec3.y() + (randomSource.nextFloat() * 2.0F - 1.0F) * 16.0F;
			double f = vec3.z() + (randomSource.nextFloat() * 2.0F - 1.0F) * 16.0F;
			return new Vec3(d, e, f);
		}

		private static @Nullable Vec3 chooseRandomPositionWithRestriction(Mob mob, Vec3 vec3, RandomSource randomSource) {
			Vec3 vec32 = chooseRandomPosition(vec3, randomSource);
			return mob.hasHome() && !mob.isWithinHome(vec32) ? null : vec32;
		}
	}
}
