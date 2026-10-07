package net.minecraft.world.entity.animal.wolf;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Crackiness;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.BegGoal;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NonTameRandomTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class Wolf extends TamableAnimal implements NeutralMob {
	private static final EntityDataAccessor<Boolean> DATA_INTERESTED_ID = SynchedEntityData.defineId(Wolf.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_COLLAR_COLOR = SynchedEntityData.defineId(Wolf.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> DATA_ANGER_END_TIME = SynchedEntityData.defineId(Wolf.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Holder<WolfVariant>> DATA_VARIANT_ID = SynchedEntityData.defineId(Wolf.class, EntityDataSerializers.WOLF_VARIANT);
	private static final EntityDataAccessor<Holder<WolfSoundVariant>> DATA_SOUND_VARIANT_ID = SynchedEntityData.defineId(
		Wolf.class, EntityDataSerializers.WOLF_SOUND_VARIANT
	);
	public static final TargetingConditions.Selector PREY_SELECTOR = (livingEntity, serverLevel) -> {
		EntityType<?> entityType = livingEntity.getType();
		return entityType == EntityType.SHEEP || entityType == EntityType.RABBIT || entityType == EntityType.FOX;
	};
	private static final float START_HEALTH = 8.0F;
	private static final float TAME_HEALTH = 40.0F;
	private static final float ARMOR_REPAIR_UNIT = 0.125F;
	public static final float DEFAULT_TAIL_ANGLE = (float) (Math.PI / 5);
	private static final DyeColor DEFAULT_COLLAR_COLOR = DyeColor.RED;
	private float interestedAngle;
	private float interestedAngleO;
	private boolean isWet;
	private boolean isShaking;
	private float shakeAnim;
	private float shakeAnimO;
	private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
	private @Nullable EntityReference<LivingEntity> persistentAngerTarget;

	public Wolf(EntityType<? extends Wolf> entityType, Level level) {
		super(entityType, level);
		this.setTame(false, false);
		this.setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
		this.setPathfindingMalus(PathType.DANGER_POWDER_SNOW, -1.0F);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new FloatGoal(this));
		this.goalSelector.addGoal(1, new TamableAnimal.TamableAnimalPanicGoal(1.5, DamageTypeTags.PANIC_ENVIRONMENTAL_CAUSES));
		this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
		this.goalSelector.addGoal(3, new Wolf.WolfAvoidEntityGoal<>(this, Llama.class, 24.0F, 1.5, 1.5));
		this.goalSelector.addGoal(4, new LeapAtTargetGoal(this, 0.4F));
		this.goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 10.0F, 2.0F));
		this.goalSelector.addGoal(7, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0));
		this.goalSelector.addGoal(9, new BegGoal(this, 8.0F));
		this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
		this.targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
		this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
		this.targetSelector.addGoal(5, new NonTameRandomTargetGoal<>(this, Animal.class, false, PREY_SELECTOR));
		this.targetSelector.addGoal(6, new NonTameRandomTargetGoal<>(this, Turtle.class, false, Turtle.BABY_ON_LAND_SELECTOR));
		this.targetSelector.addGoal(7, new NearestAttackableTargetGoal<>(this, AbstractSkeleton.class, false));
		this.targetSelector.addGoal(8, new ResetUniversalAngerTargetGoal<>(this, true));
	}

	public Identifier getTexture() {
		WolfVariant wolfVariant = this.getVariant().value();
		if (this.isTame()) {
			return wolfVariant.assetInfo().tame().texturePath();
		} else {
			return this.isAngry() ? wolfVariant.assetInfo().angry().texturePath() : wolfVariant.assetInfo().wild().texturePath();
		}
	}

	private Holder<WolfVariant> getVariant() {
		return this.entityData.get(DATA_VARIANT_ID);
	}

	private void setVariant(Holder<WolfVariant> holder) {
		this.entityData.set(DATA_VARIANT_ID, holder);
	}

	private Holder<WolfSoundVariant> getSoundVariant() {
		return this.entityData.get(DATA_SOUND_VARIANT_ID);
	}

	private void setSoundVariant(Holder<WolfSoundVariant> holder) {
		this.entityData.set(DATA_SOUND_VARIANT_ID, holder);
	}

	@Override
	public <T> @Nullable T get(DataComponentType<? extends T> dataComponentType) {
		if (dataComponentType == DataComponents.WOLF_VARIANT) {
			return castComponentValue((DataComponentType<T>)dataComponentType, this.getVariant());
		} else if (dataComponentType == DataComponents.WOLF_SOUND_VARIANT) {
			return castComponentValue((DataComponentType<T>)dataComponentType, this.getSoundVariant());
		} else {
			return dataComponentType == DataComponents.WOLF_COLLAR
				? castComponentValue((DataComponentType<T>)dataComponentType, this.getCollarColor())
				: super.get(dataComponentType);
		}
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter dataComponentGetter) {
		this.applyImplicitComponentIfPresent(dataComponentGetter, DataComponents.WOLF_VARIANT);
		this.applyImplicitComponentIfPresent(dataComponentGetter, DataComponents.WOLF_SOUND_VARIANT);
		this.applyImplicitComponentIfPresent(dataComponentGetter, DataComponents.WOLF_COLLAR);
		super.applyImplicitComponents(dataComponentGetter);
	}

	@Override
	protected <T> boolean applyImplicitComponent(DataComponentType<T> dataComponentType, T object) {
		if (dataComponentType == DataComponents.WOLF_VARIANT) {
			this.setVariant(castComponentValue(DataComponents.WOLF_VARIANT, object));
			return true;
		} else if (dataComponentType == DataComponents.WOLF_SOUND_VARIANT) {
			this.setSoundVariant(castComponentValue(DataComponents.WOLF_SOUND_VARIANT, object));
			return true;
		} else if (dataComponentType == DataComponents.WOLF_COLLAR) {
			this.setCollarColor(castComponentValue(DataComponents.WOLF_COLLAR, object));
			return true;
		} else {
			return super.applyImplicitComponent(dataComponentType, object);
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes().add(Attributes.MOVEMENT_SPEED, 0.3F).add(Attributes.MAX_HEALTH, 8.0).add(Attributes.ATTACK_DAMAGE, 4.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		Registry<WolfSoundVariant> registry = this.registryAccess().lookupOrThrow(Registries.WOLF_SOUND_VARIANT);
		builder.define(DATA_VARIANT_ID, VariantUtils.getDefaultOrAny(this.registryAccess(), WolfVariants.DEFAULT));
		builder.define(DATA_SOUND_VARIANT_ID, registry.get(WolfSoundVariants.CLASSIC).or(registry::getAny).orElseThrow());
		builder.define(DATA_INTERESTED_ID, false);
		builder.define(DATA_COLLAR_COLOR, DEFAULT_COLLAR_COLOR.getId());
		builder.define(DATA_ANGER_END_TIME, -1L);
	}

	@Override
	protected void playStepSound(BlockPos blockPos, BlockState blockState) {
		this.playSound(SoundEvents.WOLF_STEP, 0.15F, 1.0F);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.store("CollarColor", DyeColor.LEGACY_ID_CODEC, this.getCollarColor());
		VariantUtils.writeVariant(valueOutput, this.getVariant());
		this.addPersistentAngerSaveData(valueOutput);
		this.getSoundVariant()
			.unwrapKey()
			.ifPresent(resourceKey -> valueOutput.store("sound_variant", ResourceKey.codec(Registries.WOLF_SOUND_VARIANT), (ResourceKey<WolfSoundVariant>)resourceKey));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		VariantUtils.readVariant(valueInput, Registries.WOLF_VARIANT).ifPresent(this::setVariant);
		this.setCollarColor(valueInput.read("CollarColor", DyeColor.LEGACY_ID_CODEC).orElse(DEFAULT_COLLAR_COLOR));
		this.readPersistentAngerSaveData(this.level(), valueInput);
		valueInput.read("sound_variant", ResourceKey.codec(Registries.WOLF_SOUND_VARIANT))
			.flatMap(resourceKey -> this.registryAccess().lookupOrThrow(Registries.WOLF_SOUND_VARIANT).get((ResourceKey<WolfSoundVariant>)resourceKey))
			.ifPresent(this::setSoundVariant);
	}

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(
		ServerLevelAccessor serverLevelAccessor, DifficultyInstance difficultyInstance, EntitySpawnReason entitySpawnReason, @Nullable SpawnGroupData spawnGroupData
	) {
		if (spawnGroupData instanceof Wolf.WolfPackData wolfPackData) {
			this.setVariant(wolfPackData.type);
		} else {
			Optional<? extends Holder<WolfVariant>> optional = VariantUtils.selectVariantToSpawn(
				SpawnContext.create(serverLevelAccessor, this.blockPosition()), Registries.WOLF_VARIANT
			);
			if (optional.isPresent()) {
				this.setVariant((Holder<WolfVariant>)optional.get());
				spawnGroupData = new Wolf.WolfPackData((Holder<WolfVariant>)optional.get());
			}
		}

		this.setSoundVariant(WolfSoundVariants.pickRandomSoundVariant(this.registryAccess(), serverLevelAccessor.getRandom()));
		return super.finalizeSpawn(serverLevelAccessor, difficultyInstance, entitySpawnReason, spawnGroupData);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		if (this.isAngry()) {
			return this.getSoundVariant().value().growlSound().value();
		} else if (this.random.nextInt(3) == 0) {
			return this.isTame() && this.getHealth() < 20.0F ? this.getSoundVariant().value().whineSound().value() : this.getSoundVariant().value().pantSound().value();
		} else {
			return this.getSoundVariant().value().ambientSound().value();
		}
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return this.canArmorAbsorb(damageSource) ? SoundEvents.WOLF_ARMOR_DAMAGE : this.getSoundVariant().value().hurtSound().value();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return this.getSoundVariant().value().deathSound().value();
	}

	@Override
	protected float getSoundVolume() {
		return 0.4F;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!this.level().isClientSide() && this.isWet && !this.isShaking && !this.isPathFinding() && this.onGround()) {
			this.isShaking = true;
			this.shakeAnim = 0.0F;
			this.shakeAnimO = 0.0F;
			this.level().broadcastEntityEvent(this, (byte)8);
		}

		if (!this.level().isClientSide()) {
			this.updatePersistentAnger((ServerLevel)this.level(), true);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.isAlive()) {
			this.interestedAngleO = this.interestedAngle;
			if (this.isInterested()) {
				this.interestedAngle = this.interestedAngle + (1.0F - this.interestedAngle) * 0.4F;
			} else {
				this.interestedAngle = this.interestedAngle + (0.0F - this.interestedAngle) * 0.4F;
			}

			if (this.isInWaterOrRain()) {
				this.isWet = true;
				if (this.isShaking && !this.level().isClientSide()) {
					this.level().broadcastEntityEvent(this, (byte)56);
					this.cancelShake();
				}
			} else if ((this.isWet || this.isShaking) && this.isShaking) {
				if (this.shakeAnim == 0.0F) {
					this.playSound(SoundEvents.WOLF_SHAKE, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
					this.gameEvent(GameEvent.ENTITY_ACTION);
				}

				this.shakeAnimO = this.shakeAnim;
				this.shakeAnim += 0.05F;
				if (this.shakeAnimO >= 2.0F) {
					this.isWet = false;
					this.isShaking = false;
					this.shakeAnimO = 0.0F;
					this.shakeAnim = 0.0F;
				}

				if (this.shakeAnim > 0.4F) {
					float f = (float)this.getY();
					int i = (int)(Mth.sin((this.shakeAnim - 0.4F) * (float) Math.PI) * 7.0F);
					Vec3 vec3 = this.getDeltaMovement();

					for (int j = 0; j < i; j++) {
						float g = (this.random.nextFloat() * 2.0F - 1.0F) * this.getBbWidth() * 0.5F;
						float h = (this.random.nextFloat() * 2.0F - 1.0F) * this.getBbWidth() * 0.5F;
						this.level().addParticle(ParticleTypes.SPLASH, this.getX() + g, f + 0.8F, this.getZ() + h, vec3.x, vec3.y, vec3.z);
					}
				}
			}
		}
	}

	private void cancelShake() {
		this.isShaking = false;
		this.shakeAnim = 0.0F;
		this.shakeAnimO = 0.0F;
	}

	@Override
	public void die(DamageSource damageSource) {
		this.isWet = false;
		this.isShaking = false;
		this.shakeAnimO = 0.0F;
		this.shakeAnim = 0.0F;
		super.die(damageSource);
	}

	public float getWetShade(float f) {
		return !this.isWet ? 1.0F : Math.min(0.75F + Mth.lerp(f, this.shakeAnimO, this.shakeAnim) / 2.0F * 0.25F, 1.0F);
	}

	public float getShakeAnim(float f) {
		return Mth.lerp(f, this.shakeAnimO, this.shakeAnim);
	}

	public float getHeadRollAngle(float f) {
		return Mth.lerp(f, this.interestedAngleO, this.interestedAngle) * 0.15F * (float) Math.PI;
	}

	@Override
	public int getMaxHeadXRot() {
		return this.isInSittingPose() ? 20 : super.getMaxHeadXRot();
	}

	@Override
	public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float f) {
		if (this.isInvulnerableTo(serverLevel, damageSource)) {
			return false;
		}

		this.setOrderedToSit(false);
		return super.hurtServer(serverLevel, damageSource, f);
	}

	@Override
	protected void actuallyHurt(ServerLevel serverLevel, DamageSource damageSource, float f) {
		if (!this.canArmorAbsorb(damageSource)) {
			super.actuallyHurt(serverLevel, damageSource, f);
		} else {
			ItemStack itemStack = this.getBodyArmorItem();
			int i = itemStack.getDamageValue();
			int j = itemStack.getMaxDamage();
			itemStack.hurtAndBreak(Mth.ceil(f), this, EquipmentSlot.BODY);
			if (Crackiness.WOLF_ARMOR.byDamage(i, j) != Crackiness.WOLF_ARMOR.byDamage(this.getBodyArmorItem())) {
				this.playSound(SoundEvents.WOLF_ARMOR_CRACK);
				serverLevel.sendParticles(
					new ItemParticleOption(ParticleTypes.ITEM, Items.ARMADILLO_SCUTE.getDefaultInstance()),
					this.getX(),
					this.getY() + 1.0,
					this.getZ(),
					20,
					0.2,
					0.1,
					0.2,
					0.1
				);
			}
		}
	}

	private boolean canArmorAbsorb(DamageSource damageSource) {
		return this.getBodyArmorItem().is(Items.WOLF_ARMOR) && !damageSource.is(DamageTypeTags.BYPASSES_WOLF_ARMOR);
	}

	@Override
	protected void applyTamingSideEffects() {
		if (this.isTame()) {
			this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
			this.setHealth(40.0F);
		} else {
			this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(8.0);
		}
	}

	@Override
	protected void hurtArmor(DamageSource damageSource, float f) {
		this.doHurtEquipment(damageSource, f, EquipmentSlot.BODY);
	}

	@Override
	protected boolean canShearEquipment(Player player) {
		return this.isOwnedBy(player);
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand interactionHand) {
		ItemStack itemStack = player.getItemInHand(interactionHand);
		Item item = itemStack.getItem();
		if (this.isTame()) {
			if (this.isFood(itemStack) && this.getHealth() < this.getMaxHealth()) {
				this.usePlayerItem(player, interactionHand, itemStack);
				FoodProperties foodProperties = itemStack.get(DataComponents.FOOD);
				float f = foodProperties != null ? foodProperties.nutrition() : 1.0F;
				this.heal(2.0F * f);
				return InteractionResult.SUCCESS;
			}

			if (!(item instanceof DyeItem dyeItem && this.isOwnedBy(player))) {
				if (this.isEquippableInSlot(itemStack, EquipmentSlot.BODY) && !this.isWearingBodyArmor() && this.isOwnedBy(player) && !this.isBaby()) {
					this.setBodyArmorItem(itemStack.copyWithCount(1));
					itemStack.consume(1, player);
					return InteractionResult.SUCCESS;
				}

				if (this.isInSittingPose()
					&& this.isWearingBodyArmor()
					&& this.isOwnedBy(player)
					&& this.getBodyArmorItem().isDamaged()
					&& this.getBodyArmorItem().isValidRepairItem(itemStack)) {
					itemStack.shrink(1);
					this.playSound(SoundEvents.WOLF_ARMOR_REPAIR);
					ItemStack itemStack2 = this.getBodyArmorItem();
					int i = (int)(itemStack2.getMaxDamage() * 0.125F);
					itemStack2.setDamageValue(Math.max(0, itemStack2.getDamageValue() - i));
					return InteractionResult.SUCCESS;
				}

				InteractionResult interactionResult = super.mobInteract(player, interactionHand);
				if (!interactionResult.consumesAction() && this.isOwnedBy(player)) {
					this.setOrderedToSit(!this.isOrderedToSit());
					this.jumping = false;
					this.navigation.stop();
					this.setTarget(null);
					return InteractionResult.SUCCESS.withoutItem();
				}

				return interactionResult;
			}

			DyeColor dyeColor = dyeItem.getDyeColor();
			if (dyeColor != this.getCollarColor()) {
				this.setCollarColor(dyeColor);
				itemStack.consume(1, player);
				return InteractionResult.SUCCESS;
			}
		} else if (!this.level().isClientSide() && itemStack.is(Items.BONE) && !this.isAngry()) {
			itemStack.consume(1, player);
			this.tryToTame(player);
			return InteractionResult.SUCCESS_SERVER;
		}

		return super.mobInteract(player, interactionHand);
	}

	private void tryToTame(Player player) {
		if (this.random.nextInt(3) == 0) {
			this.tame(player);
			this.navigation.stop();
			this.setTarget(null);
			this.setOrderedToSit(true);
			this.level().broadcastEntityEvent(this, (byte)7);
		} else {
			this.level().broadcastEntityEvent(this, (byte)6);
		}
	}

	@Override
	public void handleEntityEvent(byte b) {
		if (b == 8) {
			this.isShaking = true;
			this.shakeAnim = 0.0F;
			this.shakeAnimO = 0.0F;
		} else if (b == 56) {
			this.cancelShake();
		} else {
			super.handleEntityEvent(b);
		}
	}

	public float getTailAngle() {
		if (this.isAngry()) {
			return 1.5393804F;
		} else if (this.isTame()) {
			float f = this.getMaxHealth();
			float g = (f - this.getHealth()) / f;
			return (0.55F - g * 0.4F) * (float) Math.PI;
		} else {
			return (float) (Math.PI / 5);
		}
	}

	@Override
	public boolean isFood(ItemStack itemStack) {
		return itemStack.is(ItemTags.WOLF_FOOD);
	}

	@Override
	public int getMaxSpawnClusterSize() {
		return 8;
	}

	@Override
	public long getPersistentAngerEndTime() {
		return this.entityData.get(DATA_ANGER_END_TIME);
	}

	@Override
	public void setPersistentAngerEndTime(long l) {
		this.entityData.set(DATA_ANGER_END_TIME, l);
	}

	@Override
	public void startPersistentAngerTimer() {
		this.setTimeToRemainAngry(PERSISTENT_ANGER_TIME.sample(this.random));
	}

	@Override
	public @Nullable EntityReference<LivingEntity> getPersistentAngerTarget() {
		return this.persistentAngerTarget;
	}

	@Override
	public void setPersistentAngerTarget(@Nullable EntityReference<LivingEntity> entityReference) {
		this.persistentAngerTarget = entityReference;
	}

	public DyeColor getCollarColor() {
		return DyeColor.byId(this.entityData.get(DATA_COLLAR_COLOR));
	}

	private void setCollarColor(DyeColor dyeColor) {
		this.entityData.set(DATA_COLLAR_COLOR, dyeColor.getId());
	}

	public @Nullable Wolf getBreedOffspring(ServerLevel serverLevel, AgeableMob ageableMob) {
		Wolf wolf = EntityType.WOLF.create(serverLevel, EntitySpawnReason.BREEDING);
		if (wolf != null && ageableMob instanceof Wolf wolf2) {
			if (this.random.nextBoolean()) {
				wolf.setVariant(this.getVariant());
			} else {
				wolf.setVariant(wolf2.getVariant());
			}

			if (this.isTame()) {
				wolf.setOwnerReference(this.getOwnerReference());
				wolf.setTame(true, true);
				DyeColor dyeColor = this.getCollarColor();
				DyeColor dyeColor2 = wolf2.getCollarColor();
				wolf.setCollarColor(DyeColor.getMixedColor(serverLevel, dyeColor, dyeColor2));
			}

			wolf.setSoundVariant(WolfSoundVariants.pickRandomSoundVariant(this.registryAccess(), this.random));
		}

		return wolf;
	}

	public void setIsInterested(boolean bl) {
		this.entityData.set(DATA_INTERESTED_ID, bl);
	}

	@Override
	public boolean canMate(Animal animal) {
		if (animal == this) {
			return false;
		} else if (!this.isTame()) {
			return false;
		} else if (!(animal instanceof Wolf wolf)) {
			return false;
		} else if (!wolf.isTame()) {
			return false;
		} else {
			return wolf.isInSittingPose() ? false : this.isInLove() && wolf.isInLove();
		}
	}

	public boolean isInterested() {
		return this.entityData.get(DATA_INTERESTED_ID);
	}

	@Override
	public boolean wantsToAttack(LivingEntity livingEntity, LivingEntity livingEntity2) {
		if (livingEntity instanceof Creeper || livingEntity instanceof Ghast || livingEntity instanceof ArmorStand) {
			return false;
		} else if (livingEntity instanceof Wolf wolf) {
			return !wolf.isTame() || wolf.getOwner() != livingEntity2;
		} else if (livingEntity instanceof Player player && livingEntity2 instanceof Player player2 && !player2.canHarmPlayer(player)) {
			return false;
		} else {
			return livingEntity instanceof AbstractHorse abstractHorse && abstractHorse.isTamed()
				? false
				: !(livingEntity instanceof TamableAnimal tamableAnimal && tamableAnimal.isTame());
		}
	}

	@Override
	public boolean canBeLeashed() {
		return !this.isAngry();
	}

	@Override
	public Vec3 getLeashOffset() {
		return new Vec3(0.0, 0.6F * this.getEyeHeight(), this.getBbWidth() * 0.4F);
	}

	public static boolean checkWolfSpawnRules(
		EntityType<Wolf> entityType, LevelAccessor levelAccessor, EntitySpawnReason entitySpawnReason, BlockPos blockPos, RandomSource randomSource
	) {
		return levelAccessor.getBlockState(blockPos.below()).is(BlockTags.WOLVES_SPAWNABLE_ON) && isBrightEnoughToSpawn(levelAccessor, blockPos);
	}

	class WolfAvoidEntityGoal<T extends LivingEntity> extends AvoidEntityGoal<T> {
		private final Wolf wolf;

		public WolfAvoidEntityGoal(final Wolf wolf2, final Class<T> class_, final float f, final double d, final double e) {
			super(wolf2, class_, f, d, e);
			this.wolf = wolf2;
		}

		@Override
		public boolean canUse() {
			return super.canUse() && this.toAvoid instanceof Llama ? !this.wolf.isTame() && this.avoidLlama((Llama)this.toAvoid) : false;
		}

		private boolean avoidLlama(Llama llama) {
			return llama.getStrength() >= Wolf.this.random.nextInt(5);
		}

		@Override
		public void start() {
			Wolf.this.setTarget(null);
			super.start();
		}

		@Override
		public void tick() {
			Wolf.this.setTarget(null);
			super.tick();
		}
	}

	public static class WolfPackData extends AgeableMob.AgeableMobGroupData {
		public final Holder<WolfVariant> type;

		public WolfPackData(Holder<WolfVariant> holder) {
			super(false);
			this.type = holder;
		}
	}
}
