package net.minecraft.world.entity.animal.happyghast;

import com.mojang.serialization.Dynamic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class HappyGhast extends Animal {
	public static final float BABY_SCALE = 0.2375F;
	public static final int WANDER_GROUND_DISTANCE = 16;
	public static final int SMALL_RESTRICTION_RADIUS = 32;
	public static final int LARGE_RESTRICTION_RADIUS = 64;
	public static final int RESTRICTION_RADIUS_BUFFER = 16;
	public static final int FAST_HEALING_TICKS = 20;
	public static final int SLOW_HEALING_TICKS = 600;
	public static final int MAX_PASSANGERS = 4;
	private static final int STILL_TIMEOUT_ON_LOAD_GRACE_PERIOD = 60;
	private static final int MAX_STILL_TIMEOUT = 10;
	public static final float SPEED_MULTIPLIER_WHEN_PANICKING = 2.0F;
	private int leashHolderTime = 0;
	private int serverStillTimeout;
	private static final EntityDataAccessor<Boolean> IS_LEASH_HOLDER = SynchedEntityData.defineId(HappyGhast.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> STAYS_STILL = SynchedEntityData.defineId(HappyGhast.class, EntityDataSerializers.BOOLEAN);
	private static final float MAX_SCALE = 1.0F;

	public HappyGhast(EntityType<? extends HappyGhast> entityType, Level level) {
		super(entityType, level);
		this.moveControl = new Ghast.GhastMoveControl(this, true, this::isOnStillTimeout);
		this.lookControl = new HappyGhast.HappyGhastLookControl();
	}

	private void setServerStillTimeout(int i) {
		if (this.serverStillTimeout <= 0 && i > 0 && this.level() instanceof ServerLevel serverLevel) {
			this.syncPacketPositionCodec(this.getX(), this.getY(), this.getZ());
			serverLevel.getChunkSource().chunkMap.sendToTrackingPlayers(this, ClientboundEntityPositionSyncPacket.of(this));
		}

		this.serverStillTimeout = i;
		this.syncStayStillFlag();
	}

	private PathNavigation createBabyNavigation(Level level) {
		return new HappyGhast.BabyFlyingPathNavigation(this, level);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(3, new HappyGhast.HappyGhastFloatGoal());
		this.goalSelector
			.addGoal(
				4,
				new TemptGoal.ForNonPathfinders(
					this,
					1.0,
					itemStack -> !this.isWearingBodyArmor() && !this.isBaby() ? itemStack.is(ItemTags.HAPPY_GHAST_TEMPT_ITEMS) : itemStack.is(ItemTags.HAPPY_GHAST_FOOD),
					false,
					7.0
				)
			);
		this.goalSelector.addGoal(5, new Ghast.RandomFloatAroundGoal(this, 16));
	}

	private void adultGhastSetup() {
		this.moveControl = new Ghast.GhastMoveControl(this, true, this::isOnStillTimeout);
		this.lookControl = new HappyGhast.HappyGhastLookControl();
		this.navigation = this.createNavigation(this.level());
		if (this.level() instanceof ServerLevel serverLevel) {
			this.removeAllGoals(goal -> true);
			this.registerGoals();
			((Brain<HappyGhast>)this.brain).stopAll(serverLevel, this);
			this.brain.clearMemories();
		}
	}

	private void babyGhastSetup() {
		this.moveControl = new FlyingMoveControl(this, 180, true);
		this.lookControl = new LookControl(this);
		this.navigation = this.createBabyNavigation(this.level());
		this.setServerStillTimeout(0);
		this.removeAllGoals(goal -> true);
	}

	@Override
	protected void ageBoundaryReached() {
		if (this.isBaby()) {
			this.babyGhastSetup();
		} else {
			this.adultGhastSetup();
		}

		super.ageBoundaryReached();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			.add(Attributes.TEMPT_RANGE, 16.0)
			.add(Attributes.FLYING_SPEED, 0.05)
			.add(Attributes.MOVEMENT_SPEED, 0.05)
			.add(Attributes.FOLLOW_RANGE, 16.0)
			.add(Attributes.CAMERA_DISTANCE, 8.0);
	}

	@Override
	protected float sanitizeScale(float f) {
		return Math.min(f, 1.0F);
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
		float f = (float)this.getAttributeValue(Attributes.FLYING_SPEED) * 5.0F / 3.0F;
		this.travelFlying(vec3, f, f, f);
	}

	@Override
	public float getWalkTargetValue(BlockPos blockPos, LevelReader levelReader) {
		if (!levelReader.isEmptyBlock(blockPos)) {
			return 0.0F;
		} else {
			return levelReader.isEmptyBlock(blockPos.below()) && !levelReader.isEmptyBlock(blockPos.below(2)) ? 10.0F : 5.0F;
		}
	}

	@Override
	public boolean canBreatheUnderwater() {
		return this.isBaby() ? true : super.canBreatheUnderwater();
	}

	@Override
	protected boolean shouldStayCloseToLeashHolder() {
		return false;
	}

	@Override
	protected void playStepSound(BlockPos blockPos, BlockState blockState) {
	}

	@Override
	public float getVoicePitch() {
		return 1.0F;
	}

	@Override
	public SoundSource getSoundSource() {
		return SoundSource.NEUTRAL;
	}

	@Override
	public int getAmbientSoundInterval() {
		int i = super.getAmbientSoundInterval();
		return this.isVehicle() ? i * 6 : i;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.isBaby() ? SoundEvents.GHASTLING_AMBIENT : SoundEvents.HAPPY_GHAST_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return this.isBaby() ? SoundEvents.GHASTLING_HURT : SoundEvents.HAPPY_GHAST_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return this.isBaby() ? SoundEvents.GHASTLING_DEATH : SoundEvents.HAPPY_GHAST_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return this.isBaby() ? 1.0F : 4.0F;
	}

	@Override
	public int getMaxSpawnClusterSize() {
		return 1;
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
		return EntityType.HAPPY_GHAST.create(serverLevel, EntitySpawnReason.BREEDING);
	}

	@Override
	public boolean canFallInLove() {
		return false;
	}

	@Override
	public float getAgeScale() {
		return this.isBaby() ? 0.2375F : 1.0F;
	}

	@Override
	public boolean isFood(ItemStack itemStack) {
		return itemStack.is(ItemTags.HAPPY_GHAST_FOOD);
	}

	@Override
	public boolean canUseSlot(EquipmentSlot equipmentSlot) {
		return equipmentSlot != EquipmentSlot.BODY ? super.canUseSlot(equipmentSlot) : this.isAlive() && !this.isBaby();
	}

	@Override
	protected boolean canDispenserEquipIntoSlot(EquipmentSlot equipmentSlot) {
		return equipmentSlot == EquipmentSlot.BODY;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand interactionHand) {
		if (this.isBaby()) {
			return super.mobInteract(player, interactionHand);
		}

		ItemStack itemStack = player.getItemInHand(interactionHand);
		if (!itemStack.isEmpty()) {
			InteractionResult interactionResult = itemStack.interactLivingEntity(player, this, interactionHand);
			if (interactionResult.consumesAction()) {
				return interactionResult;
			}
		}

		if (this.isWearingBodyArmor() && !player.isSecondaryUseActive()) {
			this.doPlayerRide(player);
			return InteractionResult.SUCCESS;
		} else {
			return super.mobInteract(player, interactionHand);
		}
	}

	private void doPlayerRide(Player player) {
		if (!this.level().isClientSide()) {
			player.startRiding(this);
		}
	}

	@Override
	protected void addPassenger(Entity entity) {
		if (!this.isVehicle()) {
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.HARNESS_GOGGLES_DOWN, this.getSoundSource(), 1.0F, 1.0F);
		}

		super.addPassenger(entity);
		if (!this.level().isClientSide()) {
			if (!this.scanPlayerAboveGhast()) {
				this.setServerStillTimeout(0);
			} else if (this.serverStillTimeout > 10) {
				this.setServerStillTimeout(10);
			}
		}
	}

	@Override
	protected void removePassenger(Entity entity) {
		super.removePassenger(entity);
		if (!this.level().isClientSide()) {
			this.setServerStillTimeout(10);
		}

		if (!this.isVehicle()) {
			this.clearHome();
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.HARNESS_GOGGLES_UP, this.getSoundSource(), 1.0F, 1.0F);
		}
	}

	@Override
	protected boolean canAddPassenger(Entity entity) {
		return this.getPassengers().size() < 4;
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return this.isWearingBodyArmor() && !this.isOnStillTimeout() && this.getFirstPassenger() instanceof Player player ? player : super.getControllingPassenger();
	}

	@Override
	protected Vec3 getRiddenInput(Player player, Vec3 vec3) {
		float f = player.xxa;
		float g = 0.0F;
		float h = 0.0F;
		if (player.zza != 0.0F) {
			float i = Mth.cos(player.getXRot() * (float) (Math.PI / 180.0));
			float j = -Mth.sin(player.getXRot() * (float) (Math.PI / 180.0));
			if (player.zza < 0.0F) {
				i *= -0.5F;
				j *= -0.5F;
			}

			h = j;
			g = i;
		}

		if (player.isJumping()) {
			h += 0.5F;
		}

		return new Vec3(f, h, g).scale(3.9F * this.getAttributeValue(Attributes.FLYING_SPEED));
	}

	protected Vec2 getRiddenRotation(LivingEntity livingEntity) {
		return new Vec2(livingEntity.getXRot() * 0.5F, livingEntity.getYRot());
	}

	@Override
	protected void tickRidden(Player player, Vec3 vec3) {
		super.tickRidden(player, vec3);
		Vec2 vec2 = this.getRiddenRotation(player);
		float f = this.getYRot();
		float g = Mth.wrapDegrees(vec2.y - f);
		float h = 0.08F;
		f += g * 0.08F;
		this.setRot(f, vec2.x);
		this.yRotO = this.yBodyRot = this.yHeadRot = f;
	}

	@Override
	protected Brain.Provider<HappyGhast> brainProvider() {
		return HappyGhastAi.brainProvider();
	}

	@Override
	protected Brain<?> makeBrain(Dynamic<?> dynamic) {
		return HappyGhastAi.makeBrain(this.brainProvider().makeBrain(dynamic));
	}

	@Override
	protected void customServerAiStep(ServerLevel serverLevel) {
		if (this.isBaby()) {
			ProfilerFiller profilerFiller = Profiler.get();
			profilerFiller.push("happyGhastBrain");
			((Brain<HappyGhast>)this.brain).tick(serverLevel, this);
			profilerFiller.pop();
			profilerFiller.push("happyGhastActivityUpdate");
			HappyGhastAi.updateActivity(this);
			profilerFiller.pop();
		}

		this.checkRestriction();
		super.customServerAiStep(serverLevel);
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide()) {
			if (this.leashHolderTime > 0) {
				this.leashHolderTime--;
			}

			this.setLeashHolder(this.leashHolderTime > 0);
			if (this.serverStillTimeout > 0) {
				if (this.tickCount > 60) {
					this.serverStillTimeout--;
				}

				this.setServerStillTimeout(this.serverStillTimeout);
			}

			if (this.scanPlayerAboveGhast()) {
				this.setServerStillTimeout(10);
			}
		}
	}

	@Override
	public void aiStep() {
		if (!this.level().isClientSide()) {
			this.setRequiresPrecisePosition(this.isOnStillTimeout());
		}

		super.aiStep();
		this.continuousHeal();
	}

	private int getHappyGhastRestrictionRadius() {
		return !this.isBaby() && this.getItemBySlot(EquipmentSlot.BODY).isEmpty() ? 64 : 32;
	}

	private void checkRestriction() {
		if (!this.isLeashed() && !this.isVehicle()) {
			int i = this.getHappyGhastRestrictionRadius();
			if (!this.hasHome() || !this.getHomePosition().closerThan(this.blockPosition(), i + 16) || i != this.getHomeRadius()) {
				this.setHomeTo(this.blockPosition(), i);
			}
		}
	}

	private void continuousHeal() {
		if (this.level() instanceof ServerLevel serverLevel && this.isAlive() && this.deathTime == 0 && this.getMaxHealth() != this.getHealth()) {
			boolean bl = this.isInClouds() || serverLevel.precipitationAt(this.blockPosition()) != Biome.Precipitation.NONE;
			if (this.tickCount % (bl ? 20 : 600) == 0) {
				this.heal(1.0F);
			}
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(IS_LEASH_HOLDER, false);
		builder.define(STAYS_STILL, false);
	}

	private void setLeashHolder(boolean bl) {
		this.entityData.set(IS_LEASH_HOLDER, bl);
	}

	public boolean isLeashHolder() {
		return this.entityData.get(IS_LEASH_HOLDER);
	}

	private void syncStayStillFlag() {
		this.entityData.set(STAYS_STILL, this.serverStillTimeout > 0);
	}

	public boolean staysStill() {
		return this.entityData.get(STAYS_STILL);
	}

	@Override
	public boolean supportQuadLeashAsHolder() {
		return true;
	}

	@Override
	public Vec3[] getQuadLeashHolderOffsets() {
		return Leashable.createQuadLeashOffsets(this, -0.03125, 0.4375, 0.46875, 0.03125);
	}

	@Override
	public Vec3 getLeashOffset() {
		return Vec3.ZERO;
	}

	@Override
	public double leashElasticDistance() {
		return 10.0;
	}

	@Override
	public double leashSnapDistance() {
		return 16.0;
	}

	@Override
	public void onElasticLeashPull() {
		super.onElasticLeashPull();
		this.getMoveControl().setWait();
	}

	@Override
	public void notifyLeashHolder(Leashable leashable) {
		if (leashable.supportQuadLeash()) {
			this.leashHolderTime = 5;
		}
	}

	@Override
	public void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.putInt("still_timeout", this.serverStillTimeout);
	}

	@Override
	public void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.setServerStillTimeout(valueInput.getIntOr("still_timeout", 0));
	}

	public boolean isOnStillTimeout() {
		return this.staysStill() || this.serverStillTimeout > 0;
	}

	private boolean scanPlayerAboveGhast() {
		AABB aABB = this.getBoundingBox();
		AABB aABB2 = new AABB(aABB.minX - 1.0, aABB.maxY - 1.0E-5F, aABB.minZ - 1.0, aABB.maxX + 1.0, aABB.maxY + aABB.getYsize() / 2.0, aABB.maxZ + 1.0);

		for (Player player : this.level().players()) {
			if (!player.isSpectator()) {
				Entity entity = player.getRootVehicle();
				if (!(entity instanceof HappyGhast) && aABB2.contains(entity.position())) {
					return true;
				}
			}
		}

		return false;
	}

	@Override
	protected BodyRotationControl createBodyControl() {
		return new HappyGhast.HappyGhastBodyRotationControl();
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity entity) {
		if (!this.isBaby() && this.isAlive()) {
			if (this.level().isClientSide() && entity instanceof Player && entity.position().y >= this.getBoundingBox().maxY) {
				return true;
			} else {
				return this.isVehicle() && entity instanceof HappyGhast ? true : this.isOnStillTimeout();
			}
		} else {
			return false;
		}
	}

	@Override
	public boolean isFlyingVehicle() {
		return !this.isBaby();
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity livingEntity) {
		return new Vec3(this.getX(), this.getBoundingBox().maxY, this.getZ());
	}

	static class BabyFlyingPathNavigation extends FlyingPathNavigation {
		public BabyFlyingPathNavigation(HappyGhast happyGhast, Level level) {
			super(happyGhast, level);
			this.setCanOpenDoors(false);
			this.setCanFloat(true);
			this.setRequiredPathLength(48.0F);
		}

		@Override
		protected boolean canMoveDirectly(Vec3 vec3, Vec3 vec32) {
			return isClearForMovementBetween(this.mob, vec3, vec32, false);
		}
	}

	class HappyGhastBodyRotationControl extends BodyRotationControl {
		public HappyGhastBodyRotationControl() {
			super(HappyGhast.this);
		}

		@Override
		public void clientTick() {
			if (HappyGhast.this.isVehicle()) {
				HappyGhast.this.yHeadRot = HappyGhast.this.getYRot();
				HappyGhast.this.yBodyRot = HappyGhast.this.yHeadRot;
			}

			super.clientTick();
		}
	}

	class HappyGhastFloatGoal extends FloatGoal {
		public HappyGhastFloatGoal() {
			super(HappyGhast.this);
		}

		@Override
		public boolean canUse() {
			return !HappyGhast.this.isOnStillTimeout() && super.canUse();
		}
	}

	class HappyGhastLookControl extends LookControl {
		HappyGhastLookControl() {
			super(HappyGhast.this);
		}

		@Override
		public void tick() {
			if (HappyGhast.this.isOnStillTimeout()) {
				float f = wrapDegrees90(HappyGhast.this.getYRot());
				HappyGhast.this.setYRot(HappyGhast.this.getYRot() - f);
				HappyGhast.this.setYHeadRot(HappyGhast.this.getYRot());
			} else if (this.lookAtCooldown > 0) {
				this.lookAtCooldown--;
				double d = this.wantedX - HappyGhast.this.getX();
				double e = this.wantedZ - HappyGhast.this.getZ();
				HappyGhast.this.setYRot(-((float)Mth.atan2(d, e)) * (180.0F / (float)Math.PI));
				HappyGhast.this.yBodyRot = HappyGhast.this.getYRot();
				HappyGhast.this.yHeadRot = HappyGhast.this.yBodyRot;
			} else {
				Ghast.faceMovementDirection(this.mob);
			}
		}

		public static float wrapDegrees90(float f) {
			float g = f % 90.0F;
			if (g >= 45.0F) {
				g -= 90.0F;
			}

			if (g < -45.0F) {
				g += 90.0F;
			}

			return g;
		}
	}
}
