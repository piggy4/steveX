package net.minecraft.world.entity.animal.chicken;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Chicken extends Animal {
	private static final EntityDimensions BABY_DIMENSIONS = EntityType.CHICKEN.getDimensions().scale(0.5F).withEyeHeight(0.2975F);
	private static final EntityDataAccessor<Holder<ChickenVariant>> DATA_VARIANT_ID = SynchedEntityData.defineId(
		Chicken.class, EntityDataSerializers.CHICKEN_VARIANT
	);
	private static final boolean DEFAULT_CHICKEN_JOCKEY = false;
	public float flap;
	public float flapSpeed;
	public float oFlapSpeed;
	public float oFlap;
	public float flapping = 1.0F;
	private float nextFlap = 1.0F;
	public int eggTime;
	public boolean isChickenJockey = false;

	public Chicken(EntityType<? extends Chicken> entityType, Level level) {
		super(entityType, level);
		this.eggTime = this.random.nextInt(6000) + 6000;
		this.setPathfindingMalus(PathType.WATER, 0.0F);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 1.4));
		this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.0, itemStack -> itemStack.is(ItemTags.CHICKEN_FOOD), false));
		this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return this.isBaby() ? BABY_DIMENSIONS : super.getDefaultDimensions(pose);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 4.0).add(Attributes.MOVEMENT_SPEED, 0.25);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		this.oFlap = this.flap;
		this.oFlapSpeed = this.flapSpeed;
		this.flapSpeed = this.flapSpeed + (this.onGround() ? -1.0F : 4.0F) * 0.3F;
		this.flapSpeed = Mth.clamp(this.flapSpeed, 0.0F, 1.0F);
		if (!this.onGround() && this.flapping < 1.0F) {
			this.flapping = 1.0F;
		}

		this.flapping *= 0.9F;
		Vec3 vec3 = this.getDeltaMovement();
		if (!this.onGround() && vec3.y < 0.0) {
			this.setDeltaMovement(vec3.multiply(1.0, 0.6, 1.0));
		}

		this.flap = this.flap + this.flapping * 2.0F;
		if (this.level() instanceof ServerLevel serverLevel && this.isAlive() && !this.isBaby() && !this.isChickenJockey() && --this.eggTime <= 0) {
			if (this.dropFromGiftLootTable(serverLevel, BuiltInLootTables.CHICKEN_LAY, this::spawnAtLocation)) {
				this.playSound(SoundEvents.CHICKEN_EGG, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
				this.gameEvent(GameEvent.ENTITY_PLACE);
			}

			this.eggTime = this.random.nextInt(6000) + 6000;
		}
	}

	@Override
	protected boolean isFlapping() {
		return this.flyDist > this.nextFlap;
	}

	@Override
	protected void onFlap() {
		this.nextFlap = this.flyDist + this.flapSpeed / 2.0F;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.CHICKEN_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.CHICKEN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.CHICKEN_DEATH;
	}

	@Override
	protected void playStepSound(BlockPos blockPos, BlockState blockState) {
		this.playSound(SoundEvents.CHICKEN_STEP, 0.15F, 1.0F);
	}

	public @Nullable Chicken getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
		Chicken chicken = EntityType.CHICKEN.create(serverLevel, EntitySpawnReason.BREEDING);
		if (chicken != null && ageableMob instanceof Chicken chicken2) {
			chicken.setVariant(this.random.nextBoolean() ? this.getVariant() : chicken2.getVariant());
		}

		return chicken;
	}

	@Override
	public SpawnGroupData finalizeSpawn(
		ServerLevelAccessor serverLevelAccessor, DifficultyInstance difficultyInstance, EntitySpawnReason entitySpawnReason, @Nullable SpawnGroupData spawnGroupData
	) {
		VariantUtils.selectVariantToSpawn(SpawnContext.create(serverLevelAccessor, this.blockPosition()), Registries.CHICKEN_VARIANT).ifPresent(this::setVariant);
		return super.finalizeSpawn(serverLevelAccessor, difficultyInstance, entitySpawnReason, spawnGroupData);
	}

	@Override
	public boolean isFood(ItemStack itemStack) {
		return itemStack.is(ItemTags.CHICKEN_FOOD);
	}

	@Override
	protected int getBaseExperienceReward(ServerLevel serverLevel) {
		return this.isChickenJockey() ? 10 : super.getBaseExperienceReward(serverLevel);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT_ID, VariantUtils.getDefaultOrAny(this.registryAccess(), ChickenVariants.TEMPERATE));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.isChickenJockey = valueInput.getBooleanOr("IsChickenJockey", false);
		valueInput.getInt("EggLayTime").ifPresent(integer -> this.eggTime = integer);
		VariantUtils.readVariant(valueInput, Registries.CHICKEN_VARIANT).ifPresent(this::setVariant);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.putBoolean("IsChickenJockey", this.isChickenJockey);
		valueOutput.putInt("EggLayTime", this.eggTime);
		VariantUtils.writeVariant(valueOutput, this.getVariant());
	}

	public void setVariant(Holder<ChickenVariant> holder) {
		this.entityData.set(DATA_VARIANT_ID, holder);
	}

	public Holder<ChickenVariant> getVariant() {
		return this.entityData.get(DATA_VARIANT_ID);
	}

	@Override
	public <T> @Nullable T get(DataComponentType<? extends T> dataComponentType) {
		return dataComponentType == DataComponents.CHICKEN_VARIANT
			? castComponentValue((DataComponentType<T>)dataComponentType, new EitherHolder<>(this.getVariant()))
			: super.get(dataComponentType);
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter dataComponentGetter) {
		this.applyImplicitComponentIfPresent(dataComponentGetter, DataComponents.CHICKEN_VARIANT);
		super.applyImplicitComponents(dataComponentGetter);
	}

	@Override
	protected <T> boolean applyImplicitComponent(DataComponentType<T> dataComponentType, T object) {
		if (dataComponentType == DataComponents.CHICKEN_VARIANT) {
			Optional<Holder<ChickenVariant>> optional = castComponentValue(DataComponents.CHICKEN_VARIANT, object).unwrap(this.registryAccess());
			if (optional.isPresent()) {
				this.setVariant(optional.get());
				return true;
			} else {
				return false;
			}
		} else {
			return super.applyImplicitComponent(dataComponentType, object);
		}
	}

	@Override
	public boolean removeWhenFarAway(double d) {
		return this.isChickenJockey();
	}

	@Override
	protected void positionRider(Entity entity, Entity.MoveFunction moveFunction) {
		super.positionRider(entity, moveFunction);
		if (entity instanceof LivingEntity) {
			((LivingEntity)entity).yBodyRot = this.yBodyRot;
		}
	}

	public boolean isChickenJockey() {
		return this.isChickenJockey;
	}

	public void setChickenJockey(boolean bl) {
		this.isChickenJockey = bl;
	}
}
