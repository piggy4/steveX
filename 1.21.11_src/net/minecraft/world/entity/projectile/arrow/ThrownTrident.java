package net.minecraft.world.entity.projectile.arrow;

import java.util.Collection;
import java.util.List;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class ThrownTrident extends AbstractArrow {
	private static final EntityDataAccessor<Byte> ID_LOYALTY = SynchedEntityData.defineId(ThrownTrident.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> ID_FOIL = SynchedEntityData.defineId(ThrownTrident.class, EntityDataSerializers.BOOLEAN);
	private static final float WATER_INERTIA = 0.99F;
	private static final boolean DEFAULT_DEALT_DAMAGE = false;
	private boolean dealtDamage = false;
	public int clientSideReturnTridentTickCount;

	public ThrownTrident(EntityType<? extends ThrownTrident> entityType, Level level) {
		super(entityType, level);
	}

	public ThrownTrident(Level level, LivingEntity livingEntity, ItemStack itemStack) {
		super(EntityType.TRIDENT, livingEntity, level, itemStack, null);
		this.entityData.set(ID_LOYALTY, this.getLoyaltyFromItem(itemStack));
		this.entityData.set(ID_FOIL, itemStack.hasFoil());
	}

	public ThrownTrident(Level level, double d, double e, double f, ItemStack itemStack) {
		super(EntityType.TRIDENT, d, e, f, level, itemStack, itemStack);
		this.entityData.set(ID_LOYALTY, this.getLoyaltyFromItem(itemStack));
		this.entityData.set(ID_FOIL, itemStack.hasFoil());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ID_LOYALTY, (byte)0);
		builder.define(ID_FOIL, false);
	}

	@Override
	public void tick() {
		if (this.inGroundTime > 4) {
			this.dealtDamage = true;
		}

		Entity entity = this.getOwner();
		int i = this.entityData.get(ID_LOYALTY);
		if (i > 0 && (this.dealtDamage || this.isNoPhysics()) && entity != null) {
			if (!this.isAcceptibleReturnOwner()) {
				if (this.level() instanceof ServerLevel serverLevel && this.pickup == AbstractArrow.Pickup.ALLOWED) {
					this.spawnAtLocation(serverLevel, this.getPickupItem(), 0.1F);
				}

				this.discard();
			} else {
				if (!(entity instanceof Player) && this.position().distanceTo(entity.getEyePosition()) < entity.getBbWidth() + 1.0) {
					this.discard();
					return;
				}

				this.setNoPhysics(true);
				Vec3 vec3 = entity.getEyePosition().subtract(this.position());
				this.setPosRaw(this.getX(), this.getY() + vec3.y * 0.015 * i, this.getZ());
				double d = 0.05 * i;
				this.setDeltaMovement(this.getDeltaMovement().scale(0.95).add(vec3.normalize().scale(d)));
				if (this.clientSideReturnTridentTickCount == 0) {
					this.playSound(SoundEvents.TRIDENT_RETURN, 10.0F, 1.0F);
				}

				this.clientSideReturnTridentTickCount++;
			}
		}

		super.tick();
	}

	private boolean isAcceptibleReturnOwner() {
		Entity entity = this.getOwner();
		return entity == null || !entity.isAlive() ? false : !(entity instanceof ServerPlayer) || !entity.isSpectator();
	}

	public boolean isFoil() {
		return this.entityData.get(ID_FOIL);
	}

	@Override
	protected @Nullable EntityHitResult findHitEntity(Vec3 vec3, Vec3 vec32) {
		return this.dealtDamage ? null : super.findHitEntity(vec3, vec32);
	}

	@Override
	protected Collection<EntityHitResult> findHitEntities(Vec3 vec3, Vec3 vec32) {
		EntityHitResult entityHitResult = this.findHitEntity(vec3, vec32);
		return entityHitResult != null ? List.of(entityHitResult) : List.of();
	}

	@Override
	protected void onHitEntity(EntityHitResult entityHitResult) {
		Entity entity = entityHitResult.getEntity();
		float f = 8.0F;
		Entity entity2 = this.getOwner();
		DamageSource damageSource = this.damageSources().trident(this, entity2 == null ? this : entity2);
		if (this.level() instanceof ServerLevel serverLevel) {
			f = EnchantmentHelper.modifyDamage(serverLevel, this.getWeaponItem(), entity, damageSource, f);
		}

		this.dealtDamage = true;
		if (entity.hurtOrSimulate(damageSource, f)) {
			if (entity.getType() == EntityType.ENDERMAN) {
				return;
			}

			if (this.level() instanceof ServerLevel serverLevel) {
				EnchantmentHelper.doPostAttackEffectsWithItemSourceOnBreak(serverLevel, entity, damageSource, this.getWeaponItem(), item -> this.kill(serverLevel));
			}

			if (entity instanceof LivingEntity livingEntity) {
				this.doKnockback(livingEntity, damageSource);
				this.doPostHurtEffects(livingEntity);
			}
		}

		this.deflect(ProjectileDeflection.REVERSE, entity, this.owner, false);
		this.setDeltaMovement(this.getDeltaMovement().multiply(0.02, 0.2, 0.02));
		this.playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);
	}

	@Override
	protected void hitBlockEnchantmentEffects(ServerLevel serverLevel, BlockHitResult blockHitResult, ItemStack itemStack) {
		Vec3 vec3 = blockHitResult.getBlockPos().clampLocationWithin(blockHitResult.getLocation());
		EnchantmentHelper.onHitBlock(
			serverLevel,
			itemStack,
			this.getOwner() instanceof LivingEntity livingEntity ? livingEntity : null,
			this,
			null,
			vec3,
			serverLevel.getBlockState(blockHitResult.getBlockPos()),
			item -> this.kill(serverLevel)
		);
	}

	@Override
	public ItemStack getWeaponItem() {
		return this.getPickupItemStackOrigin();
	}

	@Override
	protected boolean tryPickup(Player player) {
		return super.tryPickup(player) || this.isNoPhysics() && this.ownedBy(player) && player.getInventory().add(this.getPickupItem());
	}

	@Override
	protected ItemStack getDefaultPickupItem() {
		return new ItemStack(Items.TRIDENT);
	}

	@Override
	protected SoundEvent getDefaultHitGroundSoundEvent() {
		return SoundEvents.TRIDENT_HIT_GROUND;
	}

	@Override
	public void playerTouch(Player player) {
		if (this.ownedBy(player) || this.getOwner() == null) {
			super.playerTouch(player);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.dealtDamage = valueInput.getBooleanOr("DealtDamage", false);
		this.entityData.set(ID_LOYALTY, this.getLoyaltyFromItem(this.getPickupItemStackOrigin()));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.putBoolean("DealtDamage", this.dealtDamage);
	}

	private byte getLoyaltyFromItem(ItemStack itemStack) {
		return this.level() instanceof ServerLevel serverLevel
			? (byte)Mth.clamp(EnchantmentHelper.getTridentReturnToOwnerAcceleration(serverLevel, itemStack, this), 0, 127)
			: 0;
	}

	@Override
	public void tickDespawn() {
		int i = this.entityData.get(ID_LOYALTY);
		if (this.pickup != AbstractArrow.Pickup.ALLOWED || i <= 0) {
			super.tickDespawn();
		}
	}

	@Override
	protected float getWaterInertia() {
		return 0.99F;
	}

	@Override
	public boolean shouldRender(double d, double e, double f) {
		return true;
	}
}
