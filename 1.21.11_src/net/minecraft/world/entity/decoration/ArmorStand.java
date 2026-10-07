package net.minecraft.world.entity.decoration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ArmorStand extends LivingEntity {
	public static final int WOBBLE_TIME = 5;
	private static final boolean ENABLE_ARMS = true;
	public static final Rotations DEFAULT_HEAD_POSE = new Rotations(0.0F, 0.0F, 0.0F);
	public static final Rotations DEFAULT_BODY_POSE = new Rotations(0.0F, 0.0F, 0.0F);
	public static final Rotations DEFAULT_LEFT_ARM_POSE = new Rotations(-10.0F, 0.0F, -10.0F);
	public static final Rotations DEFAULT_RIGHT_ARM_POSE = new Rotations(-15.0F, 0.0F, 10.0F);
	public static final Rotations DEFAULT_LEFT_LEG_POSE = new Rotations(-1.0F, 0.0F, -1.0F);
	public static final Rotations DEFAULT_RIGHT_LEG_POSE = new Rotations(1.0F, 0.0F, 1.0F);
	private static final EntityDimensions MARKER_DIMENSIONS = EntityDimensions.fixed(0.0F, 0.0F);
	private static final EntityDimensions BABY_DIMENSIONS = EntityType.ARMOR_STAND.getDimensions().scale(0.5F).withEyeHeight(0.9875F);
	private static final double FEET_OFFSET = 0.1;
	private static final double CHEST_OFFSET = 0.9;
	private static final double LEGS_OFFSET = 0.4;
	private static final double HEAD_OFFSET = 1.6;
	public static final int DISABLE_TAKING_OFFSET = 8;
	public static final int DISABLE_PUTTING_OFFSET = 16;
	public static final int CLIENT_FLAG_SMALL = 1;
	public static final int CLIENT_FLAG_SHOW_ARMS = 4;
	public static final int CLIENT_FLAG_NO_BASEPLATE = 8;
	public static final int CLIENT_FLAG_MARKER = 16;
	public static final EntityDataAccessor<Byte> DATA_CLIENT_FLAGS = SynchedEntityData.defineId(ArmorStand.class, EntityDataSerializers.BYTE);
	public static final EntityDataAccessor<Rotations> DATA_HEAD_POSE = SynchedEntityData.defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
	public static final EntityDataAccessor<Rotations> DATA_BODY_POSE = SynchedEntityData.defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
	public static final EntityDataAccessor<Rotations> DATA_LEFT_ARM_POSE = SynchedEntityData.defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
	public static final EntityDataAccessor<Rotations> DATA_RIGHT_ARM_POSE = SynchedEntityData.defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
	public static final EntityDataAccessor<Rotations> DATA_LEFT_LEG_POSE = SynchedEntityData.defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
	public static final EntityDataAccessor<Rotations> DATA_RIGHT_LEG_POSE = SynchedEntityData.defineId(ArmorStand.class, EntityDataSerializers.ROTATIONS);
	private static final Predicate<Entity> RIDABLE_MINECARTS = entity -> entity instanceof AbstractMinecart abstractMinecart && abstractMinecart.isRideable();
	private static final boolean DEFAULT_INVISIBLE = false;
	private static final int DEFAULT_DISABLED_SLOTS = 0;
	private static final boolean DEFAULT_SMALL = false;
	private static final boolean DEFAULT_SHOW_ARMS = false;
	private static final boolean DEFAULT_NO_BASE_PLATE = false;
	private static final boolean DEFAULT_MARKER = false;
	private boolean invisible = false;
	public long lastHit;
	private int disabledSlots = 0;

	public ArmorStand(EntityType<? extends ArmorStand> entityType, Level level) {
		super(entityType, level);
	}

	public ArmorStand(Level level, double d, double e, double f) {
		this(EntityType.ARMOR_STAND, level);
		this.setPos(d, e, f);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createLivingAttributes().add(Attributes.STEP_HEIGHT, 0.0);
	}

	@Override
	public void refreshDimensions() {
		double d = this.getX();
		double e = this.getY();
		double f = this.getZ();
		super.refreshDimensions();
		this.setPos(d, e, f);
	}

	private boolean hasPhysics() {
		return !this.isMarker() && !this.isNoGravity();
	}

	@Override
	public boolean isEffectiveAi() {
		return super.isEffectiveAi() && this.hasPhysics();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_CLIENT_FLAGS, (byte)0);
		builder.define(DATA_HEAD_POSE, DEFAULT_HEAD_POSE);
		builder.define(DATA_BODY_POSE, DEFAULT_BODY_POSE);
		builder.define(DATA_LEFT_ARM_POSE, DEFAULT_LEFT_ARM_POSE);
		builder.define(DATA_RIGHT_ARM_POSE, DEFAULT_RIGHT_ARM_POSE);
		builder.define(DATA_LEFT_LEG_POSE, DEFAULT_LEFT_LEG_POSE);
		builder.define(DATA_RIGHT_LEG_POSE, DEFAULT_RIGHT_LEG_POSE);
	}

	@Override
	public boolean canUseSlot(EquipmentSlot equipmentSlot) {
		return equipmentSlot != EquipmentSlot.BODY && equipmentSlot != EquipmentSlot.SADDLE && !this.isDisabled(equipmentSlot);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.putBoolean("Invisible", this.isInvisible());
		valueOutput.putBoolean("Small", this.isSmall());
		valueOutput.putBoolean("ShowArms", this.showArms());
		valueOutput.putInt("DisabledSlots", this.disabledSlots);
		valueOutput.putBoolean("NoBasePlate", !this.showBasePlate());
		if (this.isMarker()) {
			valueOutput.putBoolean("Marker", this.isMarker());
		}

		valueOutput.store("Pose", ArmorStand.ArmorStandPose.CODEC, this.getArmorStandPose());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.setInvisible(valueInput.getBooleanOr("Invisible", false));
		this.setSmall(valueInput.getBooleanOr("Small", false));
		this.setShowArms(valueInput.getBooleanOr("ShowArms", false));
		this.disabledSlots = valueInput.getIntOr("DisabledSlots", 0);
		this.setNoBasePlate(valueInput.getBooleanOr("NoBasePlate", false));
		this.setMarker(valueInput.getBooleanOr("Marker", false));
		this.noPhysics = !this.hasPhysics();
		valueInput.read("Pose", ArmorStand.ArmorStandPose.CODEC).ifPresent(this::setArmorStandPose);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	protected void pushEntities() {
		for (Entity entity : this.level().getEntities(this, this.getBoundingBox(), RIDABLE_MINECARTS)) {
			if (this.distanceToSqr(entity) <= 0.2) {
				entity.push(this);
			}
		}
	}

	@Override
	public InteractionResult interactAt(Player player, Vec3 vec3, InteractionHand interactionHand) {
		ItemStack itemStack = player.getItemInHand(interactionHand);
		if (this.isMarker() || itemStack.is(Items.NAME_TAG)) {
			return InteractionResult.PASS;
		}

		if (player.isSpectator()) {
			return InteractionResult.SUCCESS;
		}

		if (player.level().isClientSide()) {
			return InteractionResult.SUCCESS_SERVER;
		}

		EquipmentSlot equipmentSlot = this.getEquipmentSlotForItem(itemStack);
		if (itemStack.isEmpty()) {
			EquipmentSlot equipmentSlot2 = this.getClickedSlot(vec3);
			EquipmentSlot equipmentSlot3 = this.isDisabled(equipmentSlot2) ? equipmentSlot : equipmentSlot2;
			if (this.hasItemInSlot(equipmentSlot3) && this.swapItem(player, equipmentSlot3, itemStack, interactionHand)) {
				return InteractionResult.SUCCESS_SERVER;
			}
		} else {
			if (this.isDisabled(equipmentSlot)) {
				return InteractionResult.FAIL;
			}

			if (equipmentSlot.getType() == EquipmentSlot.Type.HAND && !this.showArms()) {
				return InteractionResult.FAIL;
			}

			if (this.swapItem(player, equipmentSlot, itemStack, interactionHand)) {
				return InteractionResult.SUCCESS_SERVER;
			}
		}

		return InteractionResult.PASS;
	}

	private EquipmentSlot getClickedSlot(Vec3 vec3) {
		EquipmentSlot equipmentSlot = EquipmentSlot.MAINHAND;
		boolean bl = this.isSmall();
		double d = vec3.y / (this.getScale() * this.getAgeScale());
		EquipmentSlot equipmentSlot2 = EquipmentSlot.FEET;
		if (d >= 0.1 && d < 0.1 + (bl ? 0.8 : 0.45) && this.hasItemInSlot(equipmentSlot2)) {
			equipmentSlot = EquipmentSlot.FEET;
		} else if (d >= 0.9 + (bl ? 0.3 : 0.0) && d < 0.9 + (bl ? 1.0 : 0.7) && this.hasItemInSlot(EquipmentSlot.CHEST)) {
			equipmentSlot = EquipmentSlot.CHEST;
		} else if (d >= 0.4 && d < 0.4 + (bl ? 1.0 : 0.8) && this.hasItemInSlot(EquipmentSlot.LEGS)) {
			equipmentSlot = EquipmentSlot.LEGS;
		} else if (d >= 1.6 && this.hasItemInSlot(EquipmentSlot.HEAD)) {
			equipmentSlot = EquipmentSlot.HEAD;
		} else if (!this.hasItemInSlot(EquipmentSlot.MAINHAND) && this.hasItemInSlot(EquipmentSlot.OFFHAND)) {
			equipmentSlot = EquipmentSlot.OFFHAND;
		}

		return equipmentSlot;
	}

	private boolean isDisabled(EquipmentSlot equipmentSlot) {
		return (this.disabledSlots & 1 << equipmentSlot.getFilterBit(0)) != 0 || equipmentSlot.getType() == EquipmentSlot.Type.HAND && !this.showArms();
	}

	private boolean swapItem(Player player, EquipmentSlot equipmentSlot, ItemStack itemStack, InteractionHand interactionHand) {
		ItemStack itemStack2 = this.getItemBySlot(equipmentSlot);
		if (!itemStack2.isEmpty() && (this.disabledSlots & 1 << equipmentSlot.getFilterBit(8)) != 0) {
			return false;
		}

		if (itemStack2.isEmpty() && (this.disabledSlots & 1 << equipmentSlot.getFilterBit(16)) != 0) {
			return false;
		}

		if (player.hasInfiniteMaterials() && itemStack2.isEmpty() && !itemStack.isEmpty()) {
			this.setItemSlot(equipmentSlot, itemStack.copyWithCount(1));
			return true;
		}

		if (itemStack.isEmpty() || itemStack.getCount() <= 1) {
			this.setItemSlot(equipmentSlot, itemStack);
			player.setItemInHand(interactionHand, itemStack2);
			return true;
		}

		if (!itemStack2.isEmpty()) {
			return false;
		}

		this.setItemSlot(equipmentSlot, itemStack.split(1));
		return true;
	}

	@Override
	public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float f) {
		if (this.isRemoved()) {
			return false;
		}

		if (!serverLevel.getGameRules().get(GameRules.MOB_GRIEFING) && damageSource.getEntity() instanceof Mob) {
			return false;
		}

		if (damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			this.kill(serverLevel);
			return false;
		}

		if (this.isInvulnerableTo(serverLevel, damageSource) || this.invisible || this.isMarker()) {
			return false;
		}

		if (damageSource.is(DamageTypeTags.IS_EXPLOSION)) {
			this.brokenByAnything(serverLevel, damageSource);
			this.kill(serverLevel);
			return false;
		}

		if (damageSource.is(DamageTypeTags.IGNITES_ARMOR_STANDS)) {
			if (this.isOnFire()) {
				this.causeDamage(serverLevel, damageSource, 0.15F);
			} else {
				this.igniteForSeconds(5.0F);
			}

			return false;
		} else if (damageSource.is(DamageTypeTags.BURNS_ARMOR_STANDS) && this.getHealth() > 0.5F) {
			this.causeDamage(serverLevel, damageSource, 4.0F);
			return false;
		} else {
			boolean bl = damageSource.is(DamageTypeTags.CAN_BREAK_ARMOR_STAND);
			boolean bl2 = damageSource.is(DamageTypeTags.ALWAYS_KILLS_ARMOR_STANDS);
			if (!bl && !bl2) {
				return false;
			} else if (damageSource.getEntity() instanceof Player player && !player.getAbilities().mayBuild) {
				return false;
			} else {
				if (damageSource.isCreativePlayer()) {
					this.playBrokenSound();
					this.showBreakingParticles();
					this.kill(serverLevel);
					return true;
				}

				long l = serverLevel.getGameTime();
				if (l - this.lastHit > 5L && !bl2) {
					serverLevel.broadcastEntityEvent(this, (byte)32);
					this.gameEvent(GameEvent.ENTITY_DAMAGE, damageSource.getEntity());
					this.lastHit = l;
				} else {
					this.brokenByPlayer(serverLevel, damageSource);
					this.showBreakingParticles();
					this.kill(serverLevel);
				}

				return true;
			}
		}
	}

	@Override
	public void handleEntityEvent(byte b) {
		if (b == 32) {
			if (this.level().isClientSide()) {
				this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.ARMOR_STAND_HIT, this.getSoundSource(), 0.3F, 1.0F, false);
				this.lastHit = this.level().getGameTime();
			}
		} else {
			super.handleEntityEvent(b);
		}
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		double e = this.getBoundingBox().getSize() * 4.0;
		if (Double.isNaN(e) || e == 0.0) {
			e = 4.0;
		}

		e *= 64.0;
		return d < e * e;
	}

	private void showBreakingParticles() {
		if (this.level() instanceof ServerLevel) {
			((ServerLevel)this.level())
				.sendParticles(
					new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()),
					this.getX(),
					this.getY(0.6666666666666666),
					this.getZ(),
					10,
					this.getBbWidth() / 4.0F,
					this.getBbHeight() / 4.0F,
					this.getBbWidth() / 4.0F,
					0.05
				);
		}
	}

	private void causeDamage(ServerLevel serverLevel, DamageSource damageSource, float f) {
		float g = this.getHealth();
		g -= f;
		if (g <= 0.5F) {
			this.brokenByAnything(serverLevel, damageSource);
			this.kill(serverLevel);
		} else {
			this.setHealth(g);
			this.gameEvent(GameEvent.ENTITY_DAMAGE, damageSource.getEntity());
		}
	}

	private void brokenByPlayer(ServerLevel serverLevel, DamageSource damageSource) {
		ItemStack itemStack = new ItemStack(Items.ARMOR_STAND);
		itemStack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
		Block.popResource(this.level(), this.blockPosition(), itemStack);
		this.brokenByAnything(serverLevel, damageSource);
	}

	private void brokenByAnything(ServerLevel serverLevel, DamageSource damageSource) {
		this.playBrokenSound();
		this.dropAllDeathLoot(serverLevel, damageSource);

		for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
			ItemStack itemStack = this.equipment.set(equipmentSlot, ItemStack.EMPTY);
			if (!itemStack.isEmpty()) {
				Block.popResource(this.level(), this.blockPosition().above(), itemStack);
			}
		}
	}

	private void playBrokenSound() {
		this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ARMOR_STAND_BREAK, this.getSoundSource(), 1.0F, 1.0F);
	}

	@Override
	protected void tickHeadTurn(float f) {
		this.yBodyRotO = this.yRotO;
		this.yBodyRot = this.getYRot();
	}

	@Override
	public void travel(Vec3 vec3) {
		if (this.hasPhysics()) {
			super.travel(vec3);
		}
	}

	@Override
	public void setYBodyRot(float f) {
		this.yBodyRotO = this.yRotO = f;
		this.yHeadRotO = this.yHeadRot = f;
	}

	@Override
	public void setYHeadRot(float f) {
		this.yBodyRotO = this.yRotO = f;
		this.yHeadRotO = this.yHeadRot = f;
	}

	@Override
	protected void updateInvisibilityStatus() {
		this.setInvisible(this.invisible);
	}

	@Override
	public void setInvisible(boolean bl) {
		this.invisible = bl;
		super.setInvisible(bl);
	}

	@Override
	public boolean isBaby() {
		return this.isSmall();
	}

	@Override
	public void kill(ServerLevel serverLevel) {
		this.remove(Entity.RemovalReason.KILLED);
		this.gameEvent(GameEvent.ENTITY_DIE);
	}

	@Override
	public boolean ignoreExplosion(Explosion explosion) {
		return explosion.shouldAffectBlocklikeEntities() ? this.isInvisible() : true;
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return this.isMarker() ? PushReaction.IGNORE : super.getPistonPushReaction();
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return this.isMarker();
	}

	private void setSmall(boolean bl) {
		this.entityData.set(DATA_CLIENT_FLAGS, this.setBit(this.entityData.get(DATA_CLIENT_FLAGS), 1, bl));
	}

	public boolean isSmall() {
		return (this.entityData.get(DATA_CLIENT_FLAGS) & 1) != 0;
	}

	public void setShowArms(boolean bl) {
		this.entityData.set(DATA_CLIENT_FLAGS, this.setBit(this.entityData.get(DATA_CLIENT_FLAGS), 4, bl));
	}

	public boolean showArms() {
		return (this.entityData.get(DATA_CLIENT_FLAGS) & 4) != 0;
	}

	public void setNoBasePlate(boolean bl) {
		this.entityData.set(DATA_CLIENT_FLAGS, this.setBit(this.entityData.get(DATA_CLIENT_FLAGS), 8, bl));
	}

	public boolean showBasePlate() {
		return (this.entityData.get(DATA_CLIENT_FLAGS) & 8) == 0;
	}

	private void setMarker(boolean bl) {
		this.entityData.set(DATA_CLIENT_FLAGS, this.setBit(this.entityData.get(DATA_CLIENT_FLAGS), 16, bl));
	}

	public boolean isMarker() {
		return (this.entityData.get(DATA_CLIENT_FLAGS) & 16) != 0;
	}

	private byte setBit(byte b, int i, boolean bl) {
		if (bl) {
			b = (byte)(b | i);
		} else {
			b = (byte)(b & ~i);
		}

		return b;
	}

	public void setHeadPose(Rotations rotations) {
		this.entityData.set(DATA_HEAD_POSE, rotations);
	}

	public void setBodyPose(Rotations rotations) {
		this.entityData.set(DATA_BODY_POSE, rotations);
	}

	public void setLeftArmPose(Rotations rotations) {
		this.entityData.set(DATA_LEFT_ARM_POSE, rotations);
	}

	public void setRightArmPose(Rotations rotations) {
		this.entityData.set(DATA_RIGHT_ARM_POSE, rotations);
	}

	public void setLeftLegPose(Rotations rotations) {
		this.entityData.set(DATA_LEFT_LEG_POSE, rotations);
	}

	public void setRightLegPose(Rotations rotations) {
		this.entityData.set(DATA_RIGHT_LEG_POSE, rotations);
	}

	public Rotations getHeadPose() {
		return this.entityData.get(DATA_HEAD_POSE);
	}

	public Rotations getBodyPose() {
		return this.entityData.get(DATA_BODY_POSE);
	}

	public Rotations getLeftArmPose() {
		return this.entityData.get(DATA_LEFT_ARM_POSE);
	}

	public Rotations getRightArmPose() {
		return this.entityData.get(DATA_RIGHT_ARM_POSE);
	}

	public Rotations getLeftLegPose() {
		return this.entityData.get(DATA_LEFT_LEG_POSE);
	}

	public Rotations getRightLegPose() {
		return this.entityData.get(DATA_RIGHT_LEG_POSE);
	}

	@Override
	public boolean isPickable() {
		return super.isPickable() && !this.isMarker();
	}

	@Override
	public boolean skipAttackInteraction(Entity entity) {
		return entity instanceof Player player && !this.level().mayInteract(player, this.blockPosition());
	}

	@Override
	public HumanoidArm getMainArm() {
		return HumanoidArm.RIGHT;
	}

	@Override
	public LivingEntity.Fallsounds getFallSounds() {
		return new LivingEntity.Fallsounds(SoundEvents.ARMOR_STAND_FALL, SoundEvents.ARMOR_STAND_FALL);
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.ARMOR_STAND_HIT;
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return SoundEvents.ARMOR_STAND_BREAK;
	}

	@Override
	public void thunderHit(ServerLevel serverLevel, LightningBolt lightningBolt) {
	}

	@Override
	public boolean isAffectedByPotions() {
		return false;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> entityDataAccessor) {
		if (DATA_CLIENT_FLAGS.equals(entityDataAccessor)) {
			this.refreshDimensions();
			this.blocksBuilding = !this.isMarker();
		}

		super.onSyncedDataUpdated(entityDataAccessor);
	}

	@Override
	public boolean attackable() {
		return false;
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return this.getDimensionsMarker(this.isMarker());
	}

	private EntityDimensions getDimensionsMarker(boolean bl) {
		if (bl) {
			return MARKER_DIMENSIONS;
		} else {
			return this.isBaby() ? BABY_DIMENSIONS : this.getType().getDimensions();
		}
	}

	@Override
	public Vec3 getLightProbePosition(float f) {
		if (this.isMarker()) {
			AABB aABB = this.getDimensionsMarker(false).makeBoundingBox(this.position());
			BlockPos blockPos = this.blockPosition();
			int i = Integer.MIN_VALUE;

			for (BlockPos blockPos2 : BlockPos.betweenClosed(BlockPos.containing(aABB.minX, aABB.minY, aABB.minZ), BlockPos.containing(aABB.maxX, aABB.maxY, aABB.maxZ))) {
				int j = Math.max(this.level().getBrightness(LightLayer.BLOCK, blockPos2), this.level().getBrightness(LightLayer.SKY, blockPos2));
				if (j == 15) {
					return Vec3.atCenterOf(blockPos2);
				}

				if (j > i) {
					i = j;
					blockPos = blockPos2.immutable();
				}
			}

			return Vec3.atCenterOf(blockPos);
		} else {
			return super.getLightProbePosition(f);
		}
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(Items.ARMOR_STAND);
	}

	@Override
	public boolean canBeSeenByAnyone() {
		return !this.isInvisible() && !this.isMarker();
	}

	public void setArmorStandPose(ArmorStand.ArmorStandPose armorStandPose) {
		this.setHeadPose(armorStandPose.head());
		this.setBodyPose(armorStandPose.body());
		this.setLeftArmPose(armorStandPose.leftArm());
		this.setRightArmPose(armorStandPose.rightArm());
		this.setLeftLegPose(armorStandPose.leftLeg());
		this.setRightLegPose(armorStandPose.rightLeg());
	}

	public ArmorStand.ArmorStandPose getArmorStandPose() {
		return new ArmorStand.ArmorStandPose(
			this.getHeadPose(), this.getBodyPose(), this.getLeftArmPose(), this.getRightArmPose(), this.getLeftLegPose(), this.getRightLegPose()
		);
	}

	public record ArmorStandPose(Rotations head, Rotations body, Rotations leftArm, Rotations rightArm, Rotations leftLeg, Rotations rightLeg) {
		public static final ArmorStand.ArmorStandPose DEFAULT = new ArmorStand.ArmorStandPose(
			ArmorStand.DEFAULT_HEAD_POSE,
			ArmorStand.DEFAULT_BODY_POSE,
			ArmorStand.DEFAULT_LEFT_ARM_POSE,
			ArmorStand.DEFAULT_RIGHT_ARM_POSE,
			ArmorStand.DEFAULT_LEFT_LEG_POSE,
			ArmorStand.DEFAULT_RIGHT_LEG_POSE
		);
		public static final Codec<ArmorStand.ArmorStandPose> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Rotations.CODEC.optionalFieldOf("Head", ArmorStand.DEFAULT_HEAD_POSE).forGetter(ArmorStand.ArmorStandPose::head),
					Rotations.CODEC.optionalFieldOf("Body", ArmorStand.DEFAULT_BODY_POSE).forGetter(ArmorStand.ArmorStandPose::body),
					Rotations.CODEC.optionalFieldOf("LeftArm", ArmorStand.DEFAULT_LEFT_ARM_POSE).forGetter(ArmorStand.ArmorStandPose::leftArm),
					Rotations.CODEC.optionalFieldOf("RightArm", ArmorStand.DEFAULT_RIGHT_ARM_POSE).forGetter(ArmorStand.ArmorStandPose::rightArm),
					Rotations.CODEC.optionalFieldOf("LeftLeg", ArmorStand.DEFAULT_LEFT_LEG_POSE).forGetter(ArmorStand.ArmorStandPose::leftLeg),
					Rotations.CODEC.optionalFieldOf("RightLeg", ArmorStand.DEFAULT_RIGHT_LEG_POSE).forGetter(ArmorStand.ArmorStandPose::rightLeg)
				)
				.apply(instance, ArmorStand.ArmorStandPose::new)
		);
	}
}
