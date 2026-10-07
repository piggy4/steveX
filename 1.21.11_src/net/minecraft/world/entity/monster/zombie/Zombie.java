package net.minecraft.world.entity.monster.zombie;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SpecialDates;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.entity.ai.goal.SpearUseGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class Zombie extends Monster {
	private static final Identifier SPEED_MODIFIER_BABY_ID = Identifier.withDefaultNamespace("baby");
	private static final AttributeModifier SPEED_MODIFIER_BABY = new AttributeModifier(
		SPEED_MODIFIER_BABY_ID, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE
	);
	private static final Identifier REINFORCEMENT_CALLER_CHARGE_ID = Identifier.withDefaultNamespace("reinforcement_caller_charge");
	private static final AttributeModifier ZOMBIE_REINFORCEMENT_CALLEE_CHARGE = new AttributeModifier(
		Identifier.withDefaultNamespace("reinforcement_callee_charge"), -0.05F, AttributeModifier.Operation.ADD_VALUE
	);
	private static final Identifier LEADER_ZOMBIE_BONUS_ID = Identifier.withDefaultNamespace("leader_zombie_bonus");
	private static final Identifier ZOMBIE_RANDOM_SPAWN_BONUS_ID = Identifier.withDefaultNamespace("zombie_random_spawn_bonus");
	private static final EntityDataAccessor<Boolean> DATA_BABY_ID = SynchedEntityData.defineId(Zombie.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_SPECIAL_TYPE_ID = SynchedEntityData.defineId(Zombie.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_DROWNED_CONVERSION_ID = SynchedEntityData.defineId(Zombie.class, EntityDataSerializers.BOOLEAN);
	public static final float ZOMBIE_LEADER_CHANCE = 0.05F;
	public static final int REINFORCEMENT_ATTEMPTS = 50;
	public static final int REINFORCEMENT_RANGE_MAX = 40;
	public static final int REINFORCEMENT_RANGE_MIN = 7;
	private static final int NOT_CONVERTING = -1;
	private static final EntityDimensions BABY_DIMENSIONS = EntityType.ZOMBIE.getDimensions().scale(0.5F).withEyeHeight(0.93F);
	private static final float BREAK_DOOR_CHANCE = 0.1F;
	private static final Predicate<Difficulty> DOOR_BREAKING_PREDICATE = difficulty -> difficulty == Difficulty.HARD;
	private static final boolean DEFAULT_BABY = false;
	private static final boolean DEFAULT_CAN_BREAK_DOORS = false;
	private static final int DEFAULT_IN_WATER_TIME = 0;
	private final BreakDoorGoal breakDoorGoal = new BreakDoorGoal(this, DOOR_BREAKING_PREDICATE);
	private boolean canBreakDoors = false;
	private int inWaterTime = 0;
	private int conversionTime;

	public Zombie(EntityType<? extends Zombie> entityType, Level level) {
		super(entityType, level);
	}

	public Zombie(Level level) {
		this(EntityType.ZOMBIE, level);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(4, new Zombie.ZombieAttackTurtleEggGoal(this, 1.0, 3));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.addBehaviourGoals();
	}

	protected void addBehaviourGoals() {
		this.goalSelector.addGoal(2, new SpearUseGoal<>(this, 1.0, 1.0, 10.0F, 2.0F));
		this.goalSelector.addGoal(3, new ZombieAttackGoal(this, 1.0, false));
		this.goalSelector.addGoal(6, new MoveThroughVillageGoal(this, 1.0, true, 4, this::canBreakDoors));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(ZombifiedPiglin.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
		this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
		this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Turtle.class, 10, true, false, Turtle.BABY_ON_LAND_SELECTOR));
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.FOLLOW_RANGE, 35.0)
			.add(Attributes.MOVEMENT_SPEED, 0.23F)
			.add(Attributes.ATTACK_DAMAGE, 3.0)
			.add(Attributes.ARMOR, 2.0)
			.add(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_BABY_ID, false);
		builder.define(DATA_SPECIAL_TYPE_ID, 0);
		builder.define(DATA_DROWNED_CONVERSION_ID, false);
	}

	public boolean isUnderWaterConverting() {
		return this.getEntityData().get(DATA_DROWNED_CONVERSION_ID);
	}

	public boolean canBreakDoors() {
		return this.canBreakDoors;
	}

	public void setCanBreakDoors(boolean bl) {
		if (this.navigation.canNavigateGround()) {
			if (this.canBreakDoors != bl) {
				this.canBreakDoors = bl;
				this.navigation.setCanOpenDoors(bl);
				if (bl) {
					this.goalSelector.addGoal(1, this.breakDoorGoal);
				} else {
					this.goalSelector.removeGoal(this.breakDoorGoal);
				}
			}
		} else if (this.canBreakDoors) {
			this.goalSelector.removeGoal(this.breakDoorGoal);
			this.canBreakDoors = false;
		}
	}

	@Override
	public boolean isBaby() {
		return this.getEntityData().get(DATA_BABY_ID);
	}

	@Override
	protected int getBaseExperienceReward(ServerLevel serverLevel) {
		if (this.isBaby()) {
			this.xpReward = (int)(this.xpReward * 2.5);
		}

		return super.getBaseExperienceReward(serverLevel);
	}

	@Override
	public void setBaby(boolean bl) {
		this.getEntityData().set(DATA_BABY_ID, bl);
		if (this.level() != null && !this.level().isClientSide()) {
			AttributeInstance attributeInstance = this.getAttribute(Attributes.MOVEMENT_SPEED);
			attributeInstance.removeModifier(SPEED_MODIFIER_BABY_ID);
			if (bl) {
				attributeInstance.addTransientModifier(SPEED_MODIFIER_BABY);
			}
		}
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> entityDataAccessor) {
		if (DATA_BABY_ID.equals(entityDataAccessor)) {
			this.refreshDimensions();
		}

		super.onSyncedDataUpdated(entityDataAccessor);
	}

	protected boolean convertsInWater() {
		return true;
	}

	@Override
	public void tick() {
		if (this.level() instanceof ServerLevel serverLevel && this.isAlive() && !this.isNoAi()) {
			if (this.isUnderWaterConverting()) {
				this.conversionTime--;
				if (this.conversionTime < 0) {
					this.doUnderWaterConversion(serverLevel);
				}
			} else if (this.convertsInWater()) {
				if (this.isEyeInFluid(FluidTags.WATER)) {
					this.inWaterTime++;
					if (this.inWaterTime >= 600) {
						this.startUnderWaterConversion(300);
					}
				} else {
					this.inWaterTime = -1;
				}
			}
		}

		super.tick();
	}

	private void startUnderWaterConversion(int i) {
		this.conversionTime = i;
		this.getEntityData().set(DATA_DROWNED_CONVERSION_ID, true);
	}

	protected void doUnderWaterConversion(ServerLevel serverLevel) {
		this.convertToZombieType(serverLevel, EntityType.DROWNED);
		if (!this.isSilent()) {
			serverLevel.levelEvent(null, 1040, this.blockPosition(), 0);
		}
	}

	protected void convertToZombieType(ServerLevel serverLevel, EntityType<? extends Zombie> entityType) {
		this.convertTo(
			entityType,
			ConversionParams.single(this, true, true),
			zombie -> zombie.handleAttributes(serverLevel.getCurrentDifficultyAt(zombie.blockPosition()).getSpecialMultiplier())
		);
	}

	@VisibleForTesting
	public boolean convertVillagerToZombieVillager(ServerLevel serverLevel, Villager villager) {
		ZombieVillager zombieVillager = villager.convertTo(
			EntityType.ZOMBIE_VILLAGER,
			ConversionParams.single(villager, true, true),
			zombieVillagerx -> {
				zombieVillagerx.finalizeSpawn(
					serverLevel, serverLevel.getCurrentDifficultyAt(zombieVillagerx.blockPosition()), EntitySpawnReason.CONVERSION, new Zombie.ZombieGroupData(false, true)
				);
				zombieVillagerx.setVillagerData(villager.getVillagerData());
				zombieVillagerx.setGossips(villager.getGossips().copy());
				zombieVillagerx.setTradeOffers(villager.getOffers().copy());
				zombieVillagerx.setVillagerXp(villager.getVillagerXp());
				if (!this.isSilent()) {
					serverLevel.levelEvent(null, 1026, this.blockPosition(), 0);
				}
			}
		);
		return zombieVillager != null;
	}

	protected boolean isSunSensitive() {
		return true;
	}

	@Override
	public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float f) {
		if (!super.hurtServer(serverLevel, damageSource, f)) {
			return false;
		}

		LivingEntity livingEntity = this.getTarget();
		if (livingEntity == null && damageSource.getEntity() instanceof LivingEntity) {
			livingEntity = (LivingEntity)damageSource.getEntity();
		}

		if (livingEntity != null
			&& serverLevel.getDifficulty() == Difficulty.HARD
			&& this.random.nextFloat() < this.getAttributeValue(Attributes.SPAWN_REINFORCEMENTS_CHANCE)
			&& serverLevel.isSpawningMonsters()) {
			int i = Mth.floor(this.getX());
			int j = Mth.floor(this.getY());
			int k = Mth.floor(this.getZ());
			EntityType<? extends Zombie> entityType = this.getType();
			Zombie zombie = entityType.create(serverLevel, EntitySpawnReason.REINFORCEMENT);
			if (zombie == null) {
				return true;
			}

			for (int l = 0; l < 50; l++) {
				int m = i + Mth.nextInt(this.random, 7, 40) * Mth.nextInt(this.random, -1, 1);
				int n = j + Mth.nextInt(this.random, 7, 40) * Mth.nextInt(this.random, -1, 1);
				int o = k + Mth.nextInt(this.random, 7, 40) * Mth.nextInt(this.random, -1, 1);
				BlockPos blockPos = new BlockPos(m, n, o);
				if (SpawnPlacements.isSpawnPositionOk(entityType, serverLevel, blockPos)
					&& SpawnPlacements.checkSpawnRules(entityType, serverLevel, EntitySpawnReason.REINFORCEMENT, blockPos, serverLevel.random)) {
					zombie.setPos(m, n, o);
					if (!serverLevel.hasNearbyAlivePlayer(m, n, o, 7.0)
						&& serverLevel.isUnobstructed(zombie)
						&& serverLevel.noCollision(zombie)
						&& (zombie.canSpawnInLiquids() || !serverLevel.containsAnyLiquid(zombie.getBoundingBox()))) {
						zombie.setTarget(livingEntity);
						zombie.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(zombie.blockPosition()), EntitySpawnReason.REINFORCEMENT, null);
						serverLevel.addFreshEntityWithPassengers(zombie);
						AttributeInstance attributeInstance = this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
						AttributeModifier attributeModifier = attributeInstance.getModifier(REINFORCEMENT_CALLER_CHARGE_ID);
						double d = attributeModifier != null ? attributeModifier.amount() : 0.0;
						attributeInstance.removeModifier(REINFORCEMENT_CALLER_CHARGE_ID);
						attributeInstance.addPermanentModifier(new AttributeModifier(REINFORCEMENT_CALLER_CHARGE_ID, d - 0.05, AttributeModifier.Operation.ADD_VALUE));
						zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).addPermanentModifier(ZOMBIE_REINFORCEMENT_CALLEE_CHARGE);
						break;
					}
				}
			}
		}

		return true;
	}

	@Override
	public boolean doHurtTarget(ServerLevel serverLevel, Entity entity) {
		boolean bl = super.doHurtTarget(serverLevel, entity);
		if (bl) {
			float f = serverLevel.getCurrentDifficultyAt(this.blockPosition()).getEffectiveDifficulty();
			if (this.getMainHandItem().isEmpty() && this.isOnFire() && this.random.nextFloat() < f * 0.3F) {
				entity.igniteForSeconds(2 * (int)f);
			}
		}

		return bl;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ZOMBIE_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.ZOMBIE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ZOMBIE_DEATH;
	}

	protected SoundEvent getStepSound() {
		return SoundEvents.ZOMBIE_STEP;
	}

	@Override
	protected void playStepSound(BlockPos blockPos, BlockState blockState) {
		this.playSound(this.getStepSound(), 0.15F, 1.0F);
	}

	@Override
	public EntityType<? extends Zombie> getType() {
		return (EntityType<? extends Zombie>)super.getType();
	}

	protected boolean canSpawnInLiquids() {
		return false;
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource randomSource, DifficultyInstance difficultyInstance) {
		super.populateDefaultEquipmentSlots(randomSource, difficultyInstance);
		if (randomSource.nextFloat() < (this.level().getDifficulty() == Difficulty.HARD ? 0.05F : 0.01F)) {
			int i = randomSource.nextInt(6);
			if (i == 0) {
				this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			} else if (i == 1) {
				this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
			} else {
				this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
			}
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.putBoolean("IsBaby", this.isBaby());
		valueOutput.putBoolean("CanBreakDoors", this.canBreakDoors());
		valueOutput.putInt("InWaterTime", this.isInWater() ? this.inWaterTime : -1);
		valueOutput.putInt("DrownedConversionTime", this.isUnderWaterConverting() ? this.conversionTime : -1);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.setBaby(valueInput.getBooleanOr("IsBaby", false));
		this.setCanBreakDoors(valueInput.getBooleanOr("CanBreakDoors", false));
		this.inWaterTime = valueInput.getIntOr("InWaterTime", 0);
		int i = valueInput.getIntOr("DrownedConversionTime", -1);
		if (i != -1) {
			this.startUnderWaterConversion(i);
		} else {
			this.getEntityData().set(DATA_DROWNED_CONVERSION_ID, false);
		}
	}

	@Override
	public boolean killedEntity(ServerLevel serverLevel, LivingEntity livingEntity, DamageSource damageSource) {
		boolean bl = super.killedEntity(serverLevel, livingEntity, damageSource);
		if ((serverLevel.getDifficulty() == Difficulty.NORMAL || serverLevel.getDifficulty() == Difficulty.HARD) && livingEntity instanceof Villager villager) {
			if (serverLevel.getDifficulty() != Difficulty.HARD && this.random.nextBoolean()) {
				return bl;
			}

			if (this.convertVillagerToZombieVillager(serverLevel, villager)) {
				bl = false;
			}
		}

		return bl;
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return this.isBaby() ? BABY_DIMENSIONS : super.getDefaultDimensions(pose);
	}

	@Override
	public boolean canHoldItem(ItemStack itemStack) {
		return itemStack.is(ItemTags.EGGS) && this.isBaby() && this.isPassenger() ? false : super.canHoldItem(itemStack);
	}

	@Override
	public boolean wantsToPickUp(ServerLevel serverLevel, ItemStack itemStack) {
		return itemStack.is(Items.GLOW_INK_SAC) ? false : super.wantsToPickUp(serverLevel, itemStack);
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

		if (spawnGroupData == null) {
			spawnGroupData = new Zombie.ZombieGroupData(getSpawnAsBabyOdds(randomSource), true);
		}

		if (spawnGroupData instanceof Zombie.ZombieGroupData zombieGroupData) {
			if (zombieGroupData.isBaby) {
				this.setBaby(true);
				if (zombieGroupData.canSpawnJockey) {
					if (randomSource.nextFloat() < 0.05) {
						List<Chicken> list = serverLevelAccessor.getEntitiesOfClass(
							Chicken.class, this.getBoundingBox().inflate(5.0, 3.0, 5.0), EntitySelector.ENTITY_NOT_BEING_RIDDEN
						);
						if (!list.isEmpty()) {
							Chicken chicken = list.get(0);
							chicken.setChickenJockey(true);
							this.startRiding(chicken, false, false);
						}
					} else if (randomSource.nextFloat() < 0.05) {
						Chicken chicken2 = EntityType.CHICKEN.create(this.level(), EntitySpawnReason.JOCKEY);
						if (chicken2 != null) {
							chicken2.snapTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
							chicken2.finalizeSpawn(serverLevelAccessor, difficultyInstance, EntitySpawnReason.JOCKEY, null);
							chicken2.setChickenJockey(true);
							this.startRiding(chicken2, false, false);
							serverLevelAccessor.addFreshEntity(chicken2);
						}
					}
				}
			}

			this.setCanBreakDoors(randomSource.nextFloat() < f * 0.1F);
			if (entitySpawnReason != EntitySpawnReason.CONVERSION) {
				this.populateDefaultEquipmentSlots(randomSource, difficultyInstance);
				this.populateDefaultEquipmentEnchantments(serverLevelAccessor, randomSource, difficultyInstance);
			}
		}

		if (this.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && SpecialDates.isHalloween() && randomSource.nextFloat() < 0.25F) {
			this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(randomSource.nextFloat() < 0.1F ? Blocks.JACK_O_LANTERN : Blocks.CARVED_PUMPKIN));
			this.setDropChance(EquipmentSlot.HEAD, 0.0F);
		}

		this.handleAttributes(f);
		return spawnGroupData;
	}

	@VisibleForTesting
	public void setInWaterTime(int i) {
		this.inWaterTime = i;
	}

	@VisibleForTesting
	public void setConversionTime(int i) {
		this.conversionTime = i;
	}

	public static boolean getSpawnAsBabyOdds(RandomSource randomSource) {
		return randomSource.nextFloat() < 0.05F;
	}

	protected void handleAttributes(float f) {
		this.randomizeReinforcementsChance();
		this.getAttribute(Attributes.KNOCKBACK_RESISTANCE)
			.addOrReplacePermanentModifier(new AttributeModifier(RANDOM_SPAWN_BONUS_ID, this.random.nextDouble() * 0.05F, AttributeModifier.Operation.ADD_VALUE));
		double d = this.random.nextDouble() * 1.5 * f;
		if (d > 1.0) {
			this.getAttribute(Attributes.FOLLOW_RANGE)
				.addOrReplacePermanentModifier(new AttributeModifier(ZOMBIE_RANDOM_SPAWN_BONUS_ID, d, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}

		if (this.random.nextFloat() < f * 0.05F) {
			this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE)
				.addOrReplacePermanentModifier(new AttributeModifier(LEADER_ZOMBIE_BONUS_ID, this.random.nextDouble() * 0.25 + 0.5, AttributeModifier.Operation.ADD_VALUE));
			this.getAttribute(Attributes.MAX_HEALTH)
				.addOrReplacePermanentModifier(
					new AttributeModifier(LEADER_ZOMBIE_BONUS_ID, this.random.nextDouble() * 3.0 + 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
				);
			this.setCanBreakDoors(true);
		}
	}

	protected void randomizeReinforcementsChance() {
		this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(this.random.nextDouble() * 0.1F);
	}

	class ZombieAttackTurtleEggGoal extends RemoveBlockGoal {
		ZombieAttackTurtleEggGoal(final PathfinderMob pathfinderMob, final double d, final int i) {
			super(Blocks.TURTLE_EGG, pathfinderMob, d, i);
		}

		@Override
		public void playDestroyProgressSound(LevelAccessor levelAccessor, BlockPos blockPos) {
			levelAccessor.playSound(null, blockPos, SoundEvents.ZOMBIE_DESTROY_EGG, SoundSource.HOSTILE, 0.5F, 0.9F + Zombie.this.random.nextFloat() * 0.2F);
		}

		@Override
		public void playBreakSound(Level level, BlockPos blockPos) {
			level.playSound(null, blockPos, SoundEvents.TURTLE_EGG_BREAK, SoundSource.BLOCKS, 0.7F, 0.9F + level.random.nextFloat() * 0.2F);
		}

		@Override
		public double acceptedDistance() {
			return 1.14;
		}
	}

	public static class ZombieGroupData implements SpawnGroupData {
		public final boolean isBaby;
		public final boolean canSpawnJockey;

		public ZombieGroupData(boolean bl, boolean bl2) {
			this.isBaby = bl;
			this.canSpawnJockey = bl2;
		}
	}
}
