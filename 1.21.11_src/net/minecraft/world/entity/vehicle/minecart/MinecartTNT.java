package net.minecraft.world.entity.vehicle.minecart;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class MinecartTNT extends AbstractMinecart {
	private static final byte EVENT_PRIME = 10;
	private static final String TAG_EXPLOSION_POWER = "explosion_power";
	private static final String TAG_EXPLOSION_SPEED_FACTOR = "explosion_speed_factor";
	private static final String TAG_FUSE = "fuse";
	private static final float DEFAULT_EXPLOSION_POWER_BASE = 4.0F;
	private static final float DEFAULT_EXPLOSION_SPEED_FACTOR = 1.0F;
	private static final int NO_FUSE = -1;
	private @Nullable DamageSource ignitionSource;
	private int fuse = -1;
	private float explosionPowerBase = 4.0F;
	private float explosionSpeedFactor = 1.0F;

	public MinecartTNT(EntityType<? extends MinecartTNT> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	public BlockState getDefaultDisplayBlockState() {
		return Blocks.TNT.defaultBlockState();
	}

	@Override
	public void tick() {
		super.tick();
		if (this.fuse > 0) {
			this.fuse--;
			this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5, this.getZ(), 0.0, 0.0, 0.0);
		} else if (this.fuse == 0) {
			this.explode(this.ignitionSource, this.getDeltaMovement().horizontalDistanceSqr());
		}

		if (this.horizontalCollision) {
			double d = this.getDeltaMovement().horizontalDistanceSqr();
			if (d >= 0.01F) {
				this.explode(this.ignitionSource, d);
			}
		}
	}

	@Override
	public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float f) {
		if (damageSource.getDirectEntity() instanceof AbstractArrow abstractArrow && abstractArrow.isOnFire()) {
			DamageSource damageSource2 = this.damageSources().explosion(this, damageSource.getEntity());
			this.explode(damageSource2, abstractArrow.getDeltaMovement().lengthSqr());
		}

		return super.hurtServer(serverLevel, damageSource, f);
	}

	@Override
	public void destroy(ServerLevel serverLevel, DamageSource damageSource) {
		double d = this.getDeltaMovement().horizontalDistanceSqr();
		if (!damageSourceIgnitesTnt(damageSource) && !(d >= 0.01F)) {
			this.destroy(serverLevel, this.getDropItem());
		} else {
			if (this.fuse < 0) {
				this.primeFuse(damageSource);
				this.fuse = this.random.nextInt(20) + this.random.nextInt(20);
			}
		}
	}

	@Override
	protected Item getDropItem() {
		return Items.TNT_MINECART;
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(Items.TNT_MINECART);
	}

	protected void explode(@Nullable DamageSource damageSource, double d) {
		if (this.level() instanceof ServerLevel serverLevel) {
			if (serverLevel.getGameRules().get(GameRules.TNT_EXPLODES)) {
				double e = Math.min(Math.sqrt(d), 5.0);
				serverLevel.explode(
					this,
					damageSource,
					null,
					this.getX(),
					this.getY(),
					this.getZ(),
					(float)(this.explosionPowerBase + this.explosionSpeedFactor * this.random.nextDouble() * 1.5 * e),
					false,
					Level.ExplosionInteraction.TNT
				);
				this.discard();
			} else if (this.isPrimed()) {
				this.discard();
			}
		}
	}

	@Override
	public boolean causeFallDamage(double d, float f, DamageSource damageSource) {
		if (d >= 3.0) {
			double e = d / 10.0;
			this.explode(this.ignitionSource, e * e);
		}

		return super.causeFallDamage(d, f, damageSource);
	}

	@Override
	public void activateMinecart(ServerLevel serverLevel, int i, int j, int k, boolean bl) {
		if (bl && this.fuse < 0) {
			this.primeFuse(null);
		}
	}

	@Override
	public void handleEntityEvent(byte b) {
		if (b == 10) {
			this.primeFuse(null);
		} else {
			super.handleEntityEvent(b);
		}
	}

	public void primeFuse(@Nullable DamageSource damageSource) {
		if (!(this.level() instanceof ServerLevel serverLevel && !serverLevel.getGameRules().get(GameRules.TNT_EXPLODES))) {
			this.fuse = 80;
			if (!this.level().isClientSide()) {
				if (damageSource != null && this.ignitionSource == null) {
					this.ignitionSource = this.damageSources().explosion(this, damageSource.getEntity());
				}

				this.level().broadcastEntityEvent(this, (byte)10);
				if (!this.isSilent()) {
					this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
				}
			}
		}
	}

	public int getFuse() {
		return this.fuse;
	}

	public boolean isPrimed() {
		return this.fuse > -1;
	}

	@Override
	public float getBlockExplosionResistance(
		Explosion explosion, BlockGetter blockGetter, BlockPos blockPos, BlockState blockState, FluidState fluidState, float f
	) {
		return !this.isPrimed() || !blockState.is(BlockTags.RAILS) && !blockGetter.getBlockState(blockPos.above()).is(BlockTags.RAILS)
			? super.getBlockExplosionResistance(explosion, blockGetter, blockPos, blockState, fluidState, f)
			: 0.0F;
	}

	@Override
	public boolean shouldBlockExplode(Explosion explosion, BlockGetter blockGetter, BlockPos blockPos, BlockState blockState, float f) {
		return !this.isPrimed() || !blockState.is(BlockTags.RAILS) && !blockGetter.getBlockState(blockPos.above()).is(BlockTags.RAILS)
			? super.shouldBlockExplode(explosion, blockGetter, blockPos, blockState, f)
			: false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		super.readAdditionalSaveData(valueInput);
		this.fuse = valueInput.getIntOr("fuse", -1);
		this.explosionPowerBase = Mth.clamp(valueInput.getFloatOr("explosion_power", 4.0F), 0.0F, 128.0F);
		this.explosionSpeedFactor = Mth.clamp(valueInput.getFloatOr("explosion_speed_factor", 1.0F), 0.0F, 128.0F);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		super.addAdditionalSaveData(valueOutput);
		valueOutput.putInt("fuse", this.fuse);
		if (this.explosionPowerBase != 4.0F) {
			valueOutput.putFloat("explosion_power", this.explosionPowerBase);
		}

		if (this.explosionSpeedFactor != 1.0F) {
			valueOutput.putFloat("explosion_speed_factor", this.explosionSpeedFactor);
		}
	}

	@Override
	protected boolean shouldSourceDestroy(DamageSource damageSource) {
		return damageSourceIgnitesTnt(damageSource);
	}

	private static boolean damageSourceIgnitesTnt(DamageSource damageSource) {
		return damageSource.getDirectEntity() instanceof Projectile projectile
			? projectile.isOnFire()
			: damageSource.is(DamageTypeTags.IS_FIRE) || damageSource.is(DamageTypeTags.IS_EXPLOSION);
	}
}
