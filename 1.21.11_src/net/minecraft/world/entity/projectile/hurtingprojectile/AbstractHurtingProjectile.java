package net.minecraft.world.entity.projectile.hurtingprojectile;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public abstract class AbstractHurtingProjectile extends Projectile {
	public static final double INITAL_ACCELERATION_POWER = 0.1;
	public static final double DEFLECTION_SCALE = 0.5;
	public double accelerationPower = 0.1;

	protected AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> entityType, Level level) {
		super(entityType, level);
	}

	protected AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> entityType, double d, double e, double f, Level level) {
		this(entityType, level);
		this.setPos(d, e, f);
	}

	public AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> entityType, double d, double e, double f, Vec3 vec3, Level level) {
		this(entityType, level);
		this.snapTo(d, e, f, this.getYRot(), this.getXRot());
		this.reapplyPosition();
		this.assignDirectionalMovement(vec3, this.accelerationPower);
	}

	public AbstractHurtingProjectile(EntityType<? extends AbstractHurtingProjectile> entityType, LivingEntity livingEntity, Vec3 vec3, Level level) {
		this(entityType, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(), vec3, level);
		this.setOwner(livingEntity);
		this.setRot(livingEntity.getYRot(), livingEntity.getXRot());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		double e = this.getBoundingBox().getSize() * 4.0;
		if (Double.isNaN(e)) {
			e = 4.0;
		}

		e *= 64.0;
		return d < e * e;
	}

	protected ClipContext.Block getClipType() {
		return ClipContext.Block.COLLIDER;
	}

	@Override
	public void tick() {
		Entity entity = this.getOwner();
		this.applyInertia();
		if (this.level().isClientSide() || (entity == null || !entity.isRemoved()) && this.level().hasChunkAt(this.blockPosition())) {
			HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity, this.getClipType());
			Vec3 vec3;
			if (hitResult.getType() != HitResult.Type.MISS) {
				vec3 = hitResult.getLocation();
			} else {
				vec3 = this.position().add(this.getDeltaMovement());
			}

			ProjectileUtil.rotateTowardsMovement(this, 0.2F);
			this.setPos(vec3);
			this.applyEffectsFromBlocks();
			super.tick();
			if (this.shouldBurn()) {
				this.igniteForSeconds(1.0F);
			}

			if (hitResult.getType() != HitResult.Type.MISS && this.isAlive()) {
				this.hitTargetOrDeflectSelf(hitResult);
			}

			this.createParticleTrail();
		} else {
			this.discard();
		}
	}

	private void applyInertia() {
		Vec3 vec3 = this.getDeltaMovement();
		Vec3 vec32 = this.position();
		float g;
		if (this.isInWater()) {
			for (int i = 0; i < 4; i++) {
				float f = 0.25F;
				this.level().addParticle(ParticleTypes.BUBBLE, vec32.x - vec3.x * 0.25, vec32.y - vec3.y * 0.25, vec32.z - vec3.z * 0.25, vec3.x, vec3.y, vec3.z);
			}

			g = this.getLiquidInertia();
		} else {
			g = this.getInertia();
		}

		this.setDeltaMovement(vec3.add(vec3.normalize().scale(this.accelerationPower)).scale(g));
	}

	private void createParticleTrail() {
		ParticleOptions particleOptions = this.getTrailParticle();
		Vec3 vec3 = this.position();
		if (particleOptions != null) {
			this.level().addParticle(particleOptions, vec3.x, vec3.y + 0.5, vec3.z, 0.0, 0.0, 0.0);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float f) {
		return false;
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return super.canHitEntity(entity) && !entity.noPhysics;
	}

	protected boolean shouldBurn() {
		return true;
	}

	protected @Nullable ParticleOptions getTrailParticle() {
		return ParticleTypes.SMOKE;
	}

	protected float getInertia() {
		return 0.95F;
	}

	protected float getLiquidInertia() {
		return 0.8F;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.putDouble("acceleration_power", this.accelerationPower);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.accelerationPower = valueInput.getDoubleOr("acceleration_power", 0.1);
	}

	@Override
	public float getLightLevelDependentMagicValue() {
		return 1.0F;
	}

	private void assignDirectionalMovement(Vec3 vec3, double d) {
		this.setDeltaMovement(vec3.normalize().scale(d));
		this.needsSync = true;
	}

	@Override
	protected void onDeflection(boolean bl) {
		super.onDeflection(bl);
		if (bl) {
			this.accelerationPower = 0.1;
		} else {
			this.accelerationPower *= 0.5;
		}
	}
}
