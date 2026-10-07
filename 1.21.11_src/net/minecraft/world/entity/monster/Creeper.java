package net.minecraft.world.entity.monster;

import java.util.Collection;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jspecify.annotations.Nullable;

public class Creeper extends Monster {
	private static final EntityDataAccessor<Integer> DATA_SWELL_DIR = SynchedEntityData.defineId(Creeper.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_IS_POWERED = SynchedEntityData.defineId(Creeper.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_IS_IGNITED = SynchedEntityData.defineId(Creeper.class, EntityDataSerializers.BOOLEAN);
	private static final boolean DEFAULT_IGNITED = false;
	private static final boolean DEFAULT_POWERED = false;
	private static final short DEFAULT_MAX_SWELL = 30;
	private static final byte DEFAULT_EXPLOSION_RADIUS = 3;
	private int oldSwell;
	private int swell;
	private int maxSwell = 30;
	private int explosionRadius = 3;
	private boolean droppedSkulls;

	public Creeper(EntityType<? extends Creeper> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new FloatGoal(this));
		this.goalSelector.addGoal(2, new SwellGoal(this));
		this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Ocelot.class, 6.0F, 1.0, 1.2));
		this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Cat.class, 6.0F, 1.0, 1.2));
		this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
		this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MOVEMENT_SPEED, 0.25);
	}

	@Override
	public int getMaxFallDistance() {
		return this.getTarget() == null ? this.getComfortableFallDistance(0.0F) : this.getComfortableFallDistance(this.getHealth() - 1.0F);
	}

	@Override
	public boolean causeFallDamage(double d, float f, DamageSource damageSource) {
		boolean bl = super.causeFallDamage(d, f, damageSource);
		this.swell += (int)(d * 1.5);
		if (this.swell > this.maxSwell - 5) {
			this.swell = this.maxSwell - 5;
		}

		return bl;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SWELL_DIR, -1);
		builder.define(DATA_IS_POWERED, false);
		builder.define(DATA_IS_IGNITED, false);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.putBoolean("powered", this.isPowered());
		valueOutput.putShort("Fuse", (short)this.maxSwell);
		valueOutput.putByte("ExplosionRadius", (byte)this.explosionRadius);
		valueOutput.putBoolean("ignited", this.isIgnited());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.entityData.set(DATA_IS_POWERED, valueInput.getBooleanOr("powered", false));
		this.maxSwell = valueInput.getShortOr("Fuse", (short)30);
		this.explosionRadius = valueInput.getByteOr("ExplosionRadius", (byte)3);
		if (valueInput.getBooleanOr("ignited", false)) {
			this.ignite();
		}
	}

	@Override
	public void tick() {
		if (this.isAlive()) {
			this.oldSwell = this.swell;
			if (this.isIgnited()) {
				this.setSwellDir(1);
			}

			int i = this.getSwellDir();
			if (i > 0 && this.swell == 0) {
				this.playSound(SoundEvents.CREEPER_PRIMED, 1.0F, 0.5F);
				this.gameEvent(GameEvent.PRIME_FUSE);
			}

			this.swell += i;
			if (this.swell < 0) {
				this.swell = 0;
			}

			if (this.swell >= this.maxSwell) {
				this.swell = this.maxSwell;
				this.explodeCreeper();
			}
		}

		super.tick();
	}

	@Override
	public void setTarget(@Nullable LivingEntity livingEntity) {
		if (!(livingEntity instanceof Goat)) {
			super.setTarget(livingEntity);
		}
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.CREEPER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.CREEPER_DEATH;
	}

	@Override
	public boolean killedEntity(ServerLevel serverLevel, LivingEntity livingEntity, DamageSource damageSource) {
		if (this.shouldDropLoot(serverLevel) && this.isPowered() && !this.droppedSkulls) {
			livingEntity.dropFromLootTable(serverLevel, damageSource, false, BuiltInLootTables.CHARGED_CREEPER, itemStack -> {
				livingEntity.spawnAtLocation(serverLevel, itemStack);
				this.droppedSkulls = true;
			});
		}

		return super.killedEntity(serverLevel, livingEntity, damageSource);
	}

	@Override
	public boolean doHurtTarget(ServerLevel serverLevel, Entity entity) {
		return true;
	}

	public boolean isPowered() {
		return this.entityData.get(DATA_IS_POWERED);
	}

	public float getSwelling(float f) {
		return Mth.lerp(f, this.oldSwell, this.swell) / (this.maxSwell - 2);
	}

	public int getSwellDir() {
		return this.entityData.get(DATA_SWELL_DIR);
	}

	public void setSwellDir(int i) {
		this.entityData.set(DATA_SWELL_DIR, i);
	}

	@Override
	public void thunderHit(ServerLevel serverLevel, LightningBolt lightningBolt) {
		super.thunderHit(serverLevel, lightningBolt);
		this.entityData.set(DATA_IS_POWERED, true);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand interactionHand) {
		ItemStack itemStack = player.getItemInHand(interactionHand);
		if (itemStack.is(ItemTags.CREEPER_IGNITERS)) {
			SoundEvent soundEvent = itemStack.is(Items.FIRE_CHARGE) ? SoundEvents.FIRECHARGE_USE : SoundEvents.FLINTANDSTEEL_USE;
			this.level().playSound(player, this.getX(), this.getY(), this.getZ(), soundEvent, this.getSoundSource(), 1.0F, this.random.nextFloat() * 0.4F + 0.8F);
			if (!this.level().isClientSide()) {
				this.ignite();
				if (!itemStack.isDamageableItem()) {
					itemStack.shrink(1);
				} else {
					itemStack.hurtAndBreak(1, player, interactionHand.asEquipmentSlot());
				}
			}

			return InteractionResult.SUCCESS;
		} else {
			return super.mobInteract(player, interactionHand);
		}
	}

	private void explodeCreeper() {
		if (this.level() instanceof ServerLevel serverLevel) {
			float f = this.isPowered() ? 2.0F : 1.0F;
			this.dead = true;
			serverLevel.explode(this, this.getX(), this.getY(), this.getZ(), this.explosionRadius * f, Level.ExplosionInteraction.MOB);
			this.spawnLingeringCloud();
			this.triggerOnDeathMobEffects(serverLevel, Entity.RemovalReason.KILLED);
			this.discard();
		}
	}

	private void spawnLingeringCloud() {
		Collection<MobEffectInstance> collection = this.getActiveEffects();
		if (!collection.isEmpty()) {
			AreaEffectCloud areaEffectCloud = new AreaEffectCloud(this.level(), this.getX(), this.getY(), this.getZ());
			areaEffectCloud.setRadius(2.5F);
			areaEffectCloud.setRadiusOnUse(-0.5F);
			areaEffectCloud.setWaitTime(10);
			areaEffectCloud.setDuration(300);
			areaEffectCloud.setPotionDurationScale(0.25F);
			areaEffectCloud.setRadiusPerTick(-areaEffectCloud.getRadius() / areaEffectCloud.getDuration());

			for (MobEffectInstance mobEffectInstance : collection) {
				areaEffectCloud.addEffect(new MobEffectInstance(mobEffectInstance));
			}

			this.level().addFreshEntity(areaEffectCloud);
		}
	}

	public boolean isIgnited() {
		return this.entityData.get(DATA_IS_IGNITED);
	}

	public void ignite() {
		this.entityData.set(DATA_IS_IGNITED, true);
	}
}
