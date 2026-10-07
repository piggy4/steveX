package net.minecraft.world.entity;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class ExperienceOrb extends Entity {
	protected static final EntityDataAccessor<Integer> DATA_VALUE = SynchedEntityData.defineId(ExperienceOrb.class, EntityDataSerializers.INT);
	private static final int LIFETIME = 6000;
	private static final int ENTITY_SCAN_PERIOD = 20;
	private static final int MAX_FOLLOW_DIST = 8;
	private static final int ORB_GROUPS_PER_AREA = 40;
	private static final double ORB_MERGE_DISTANCE = 0.5;
	private static final short DEFAULT_HEALTH = 5;
	private static final short DEFAULT_AGE = 0;
	private static final short DEFAULT_VALUE = 0;
	private static final int DEFAULT_COUNT = 1;
	private int age = 0;
	private int health = 5;
	private int count = 1;
	private @Nullable Player followingPlayer;
	private final InterpolationHandler interpolation = new InterpolationHandler(this);

	public ExperienceOrb(Level level, double d, double e, double f, int i) {
		this(level, new Vec3(d, e, f), Vec3.ZERO, i);
	}

	public ExperienceOrb(Level level, Vec3 vec3, Vec3 vec32, int i) {
		this(EntityType.EXPERIENCE_ORB, level);
		this.setPos(vec3);
		if (!level.isClientSide()) {
			this.setYRot(this.random.nextFloat() * 360.0F);
			Vec3 vec33 = new Vec3((this.random.nextDouble() * 0.2 - 0.1) * 2.0, this.random.nextDouble() * 0.2 * 2.0, (this.random.nextDouble() * 0.2 - 0.1) * 2.0);
			if (vec32.lengthSqr() > 0.0 && vec32.dot(vec33) < 0.0) {
				vec33 = vec33.scale(-1.0);
			}

			double d = this.getBoundingBox().getSize();
			this.setPos(vec3.add(vec32.normalize().scale(d * 0.5)));
			this.setDeltaMovement(vec33);
			if (!level.noCollision(this.getBoundingBox())) {
				this.unstuckIfPossible(d);
			}
		}

		this.setValue(i);
	}

	public ExperienceOrb(EntityType<? extends ExperienceOrb> entityType, Level level) {
		super(entityType, level);
	}

	protected void unstuckIfPossible(double d) {
		Vec3 vec3 = this.position().add(0.0, this.getBbHeight() / 2.0, 0.0);
		VoxelShape voxelShape = Shapes.create(AABB.ofSize(vec3, d, d, d));
		this.level()
			.findFreePosition(this, voxelShape, vec3, this.getBbWidth(), this.getBbHeight(), this.getBbWidth())
			.ifPresent(vec3x -> this.setPos(vec3x.add(0.0, -this.getBbHeight() / 2.0, 0.0)));
	}

	@Override
	protected Entity.MovementEmission getMovementEmission() {
		return Entity.MovementEmission.NONE;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_VALUE, 0);
	}

	@Override
	protected double getDefaultGravity() {
		return 0.03;
	}

	@Override
	public void tick() {
		this.interpolation.interpolate();
		if (this.firstTick && this.level().isClientSide()) {
			this.firstTick = false;
		} else {
			super.tick();
			boolean bl = !this.level().noCollision(this.getBoundingBox());
			if (this.isEyeInFluid(FluidTags.WATER)) {
				this.setUnderwaterMovement();
			} else if (!bl) {
				this.applyGravity();
			}

			if (this.level().getFluidState(this.blockPosition()).is(FluidTags.LAVA)) {
				this.setDeltaMovement((this.random.nextFloat() - this.random.nextFloat()) * 0.2F, 0.2F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
			}

			if (this.tickCount % 20 == 1) {
				this.scanForMerges();
			}

			this.followNearbyPlayer();
			if (this.followingPlayer == null && !this.level().isClientSide() && bl) {
				boolean bl2 = !this.level().noCollision(this.getBoundingBox().move(this.getDeltaMovement()));
				if (bl2) {
					this.moveTowardsClosestSpace(this.getX(), (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0, this.getZ());
					this.needsSync = true;
				}
			}

			double d = this.getDeltaMovement().y;
			this.move(MoverType.SELF, this.getDeltaMovement());
			this.applyEffectsFromBlocks();
			float f = 0.98F;
			if (this.onGround()) {
				f = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.98F;
			}

			this.setDeltaMovement(this.getDeltaMovement().scale(f));
			if (this.verticalCollisionBelow && d < -this.getGravity()) {
				this.setDeltaMovement(new Vec3(this.getDeltaMovement().x, -d * 0.4, this.getDeltaMovement().z));
			}

			this.age++;
			if (this.age >= 6000) {
				this.discard();
			}
		}
	}

	private void followNearbyPlayer() {
		if (this.followingPlayer == null || this.followingPlayer.isSpectator() || this.followingPlayer.distanceToSqr(this) > 64.0) {
			Player player = this.level().getNearestPlayer(this, 8.0);
			if (player != null && !player.isSpectator() && !player.isDeadOrDying()) {
				this.followingPlayer = player;
			} else {
				this.followingPlayer = null;
			}
		}

		if (this.followingPlayer != null) {
			Vec3 vec3 = new Vec3(
				this.followingPlayer.getX() - this.getX(),
				this.followingPlayer.getY() + this.followingPlayer.getEyeHeight() / 2.0 - this.getY(),
				this.followingPlayer.getZ() - this.getZ()
			);
			double d = vec3.lengthSqr();
			double e = 1.0 - Math.sqrt(d) / 8.0;
			this.setDeltaMovement(this.getDeltaMovement().add(vec3.normalize().scale(e * e * 0.1)));
		}
	}

	@Override
	public BlockPos getBlockPosBelowThatAffectsMyMovement() {
		return this.getOnPos(0.999999F);
	}

	private void scanForMerges() {
		if (this.level() instanceof ServerLevel) {
			for (ExperienceOrb experienceOrb : this.level()
				.getEntities(EntityTypeTest.forClass(ExperienceOrb.class), this.getBoundingBox().inflate(0.5), this::canMerge)) {
				this.merge(experienceOrb);
			}
		}
	}

	public static void award(ServerLevel serverLevel, Vec3 vec3, int i) {
		awardWithDirection(serverLevel, vec3, Vec3.ZERO, i);
	}

	public static void awardWithDirection(ServerLevel serverLevel, Vec3 vec3, Vec3 vec32, int i) {
		while (i > 0) {
			int j = getExperienceValue(i);
			i -= j;
			if (!tryMergeToExisting(serverLevel, vec3, j)) {
				serverLevel.addFreshEntity(new ExperienceOrb(serverLevel, vec3, vec32, j));
			}
		}
	}

	private static boolean tryMergeToExisting(ServerLevel serverLevel, Vec3 vec3, int i) {
		AABB aABB = AABB.ofSize(vec3, 1.0, 1.0, 1.0);
		int j = serverLevel.getRandom().nextInt(40);
		List<ExperienceOrb> list = serverLevel.getEntities(EntityTypeTest.forClass(ExperienceOrb.class), aABB, experienceOrbx -> canMerge(experienceOrbx, j, i));
		if (!list.isEmpty()) {
			ExperienceOrb experienceOrb = list.get(0);
			experienceOrb.count++;
			experienceOrb.age = 0;
			return true;
		} else {
			return false;
		}
	}

	private boolean canMerge(ExperienceOrb experienceOrb) {
		return experienceOrb != this && canMerge(experienceOrb, this.getId(), this.getValue());
	}

	private static boolean canMerge(ExperienceOrb experienceOrb, int i, int j) {
		return !experienceOrb.isRemoved() && (experienceOrb.getId() - i) % 40 == 0 && experienceOrb.getValue() == j;
	}

	private void merge(ExperienceOrb experienceOrb) {
		this.count = this.count + experienceOrb.count;
		this.age = Math.min(this.age, experienceOrb.age);
		experienceOrb.discard();
	}

	private void setUnderwaterMovement() {
		Vec3 vec3 = this.getDeltaMovement();
		this.setDeltaMovement(vec3.x * 0.99F, Math.min(vec3.y + 5.0E-4F, 0.06F), vec3.z * 0.99F);
	}

	@Override
	protected void doWaterSplashEffect() {
	}

	@Override
	public final boolean hurtClient(DamageSource damageSource) {
		return !this.isInvulnerableToBase(damageSource);
	}

	@Override
	public final boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float f) {
		if (this.isInvulnerableToBase(damageSource)) {
			return false;
		}

		this.markHurt();
		this.health = (int)(this.health - f);
		if (this.health <= 0) {
			this.discard();
		}

		return true;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
		valueOutput.putShort("Health", (short)this.health);
		valueOutput.putShort("Age", (short)this.age);
		valueOutput.putShort("Value", (short)this.getValue());
		valueOutput.putInt("Count", this.count);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
		this.health = valueInput.getShortOr("Health", (short)5);
		this.age = valueInput.getShortOr("Age", (short)0);
		this.setValue(valueInput.getShortOr("Value", (short)0));
		this.count = valueInput.read("Count", ExtraCodecs.POSITIVE_INT).orElse(1);
	}

	@Override
	public void playerTouch(Player player) {
		if (player instanceof ServerPlayer serverPlayer) {
			if (player.takeXpDelay == 0) {
				player.takeXpDelay = 2;
				player.take(this, 1);
				int i = this.repairPlayerItems(serverPlayer, this.getValue());
				if (i > 0) {
					player.giveExperiencePoints(i);
				}

				this.count--;
				if (this.count == 0) {
					this.discard();
				}
			}
		}
	}

	private int repairPlayerItems(ServerPlayer serverPlayer, int i) {
		Optional<EnchantedItemInUse> optional = EnchantmentHelper.getRandomItemWith(EnchantmentEffectComponents.REPAIR_WITH_XP, serverPlayer, ItemStack::isDamaged);
		if (optional.isPresent()) {
			ItemStack itemStack = optional.get().itemStack();
			int j = EnchantmentHelper.modifyDurabilityToRepairFromXp(serverPlayer.level(), itemStack, i);
			int k = Math.min(j, itemStack.getDamageValue());
			itemStack.setDamageValue(itemStack.getDamageValue() - k);
			if (k > 0) {
				int l = i - k * i / j;
				if (l > 0) {
					return this.repairPlayerItems(serverPlayer, l);
				}
			}

			return 0;
		} else {
			return i;
		}
	}

	public int getValue() {
		return this.entityData.get(DATA_VALUE);
	}

	private void setValue(int i) {
		this.entityData.set(DATA_VALUE, i);
	}

	public int getIcon() {
		int i = this.getValue();
		if (i >= 2477) {
			return 10;
		} else if (i >= 1237) {
			return 9;
		} else if (i >= 617) {
			return 8;
		} else if (i >= 307) {
			return 7;
		} else if (i >= 149) {
			return 6;
		} else if (i >= 73) {
			return 5;
		} else if (i >= 37) {
			return 4;
		} else if (i >= 17) {
			return 3;
		} else if (i >= 7) {
			return 2;
		} else {
			return i >= 3 ? 1 : 0;
		}
	}

	public static int getExperienceValue(int i) {
		if (i >= 2477) {
			return 2477;
		} else if (i >= 1237) {
			return 1237;
		} else if (i >= 617) {
			return 617;
		} else if (i >= 307) {
			return 307;
		} else if (i >= 149) {
			return 149;
		} else if (i >= 73) {
			return 73;
		} else if (i >= 37) {
			return 37;
		} else if (i >= 17) {
			return 17;
		} else if (i >= 7) {
			return 7;
		} else {
			return i >= 3 ? 3 : 1;
		}
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public SoundSource getSoundSource() {
		return SoundSource.AMBIENT;
	}

	@Override
	public InterpolationHandler getInterpolation() {
		return this.interpolation;
	}
}
