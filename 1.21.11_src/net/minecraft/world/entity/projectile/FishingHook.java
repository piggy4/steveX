package net.minecraft.world.entity.projectile;

import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.List;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class FishingHook extends Projectile {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final RandomSource syncronizedRandom = RandomSource.create();
	private boolean biting;
	private int outOfWaterTime;
	private static final int MAX_OUT_OF_WATER_TIME = 10;
	private static final EntityDataAccessor<Integer> DATA_HOOKED_ENTITY = SynchedEntityData.defineId(FishingHook.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_BITING = SynchedEntityData.defineId(FishingHook.class, EntityDataSerializers.BOOLEAN);
	private int life;
	private int nibble;
	private int timeUntilLured;
	private int timeUntilHooked;
	private float fishAngle;
	private boolean openWater = true;
	private @Nullable Entity hookedIn;
	private FishingHook.FishHookState currentState = FishingHook.FishHookState.FLYING;
	private final int luck;
	private final int lureSpeed;
	private final InterpolationHandler interpolationHandler = new InterpolationHandler(this);

	public FishingHook(EntityType<? extends FishingHook> entityType, Level level, int i, int j) {
		super(entityType, level);
		this.luck = Math.max(0, i);
		this.lureSpeed = Math.max(0, j);
	}

	public FishingHook(EntityType<? extends FishingHook> entityType, Level level) {
		this(entityType, level, 0, 0);
	}

	public FishingHook(Player player, Level level, int i, int j) {
		this(EntityType.FISHING_BOBBER, level, i, j);
		this.setOwner(player);
		float f = player.getXRot();
		float g = player.getYRot();
		float h = Mth.cos(-g * (float) (Math.PI / 180.0) - (float) Math.PI);
		float k = Mth.sin(-g * (float) (Math.PI / 180.0) - (float) Math.PI);
		float l = -Mth.cos(-f * (float) (Math.PI / 180.0));
		float m = Mth.sin(-f * (float) (Math.PI / 180.0));
		double d = player.getX() - k * 0.3;
		double e = player.getEyeY();
		double n = player.getZ() - h * 0.3;
		this.snapTo(d, e, n, g, f);
		Vec3 vec3 = new Vec3(-k, Mth.clamp(-(m / l), -5.0F, 5.0F), -h);
		double o = vec3.length();
		vec3 = vec3.multiply(
			0.6 / o + this.random.triangle(0.5, 0.0103365), 0.6 / o + this.random.triangle(0.5, 0.0103365), 0.6 / o + this.random.triangle(0.5, 0.0103365)
		);
		this.setDeltaMovement(vec3);
		this.setYRot((float)(Mth.atan2(vec3.x, vec3.z) * 180.0F / (float)Math.PI));
		this.setXRot((float)(Mth.atan2(vec3.y, vec3.horizontalDistance()) * 180.0F / (float)Math.PI));
		this.yRotO = this.getYRot();
		this.xRotO = this.getXRot();
	}

	@Override
	public InterpolationHandler getInterpolation() {
		return this.interpolationHandler;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_HOOKED_ENTITY, 0);
		builder.define(DATA_BITING, false);
	}

	@Override
	protected boolean shouldBounceOnWorldBorder() {
		return true;
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> entityDataAccessor) {
		if (DATA_HOOKED_ENTITY.equals(entityDataAccessor)) {
			int i = this.getEntityData().get(DATA_HOOKED_ENTITY);
			this.hookedIn = i > 0 ? this.level().getEntity(i - 1) : null;
		}

		if (DATA_BITING.equals(entityDataAccessor)) {
			this.biting = this.getEntityData().get(DATA_BITING);
			if (this.biting) {
				this.setDeltaMovement(this.getDeltaMovement().x, -0.4F * Mth.nextFloat(this.syncronizedRandom, 0.6F, 1.0F), this.getDeltaMovement().z);
			}
		}

		super.onSyncedDataUpdated(entityDataAccessor);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		double e = 64.0;
		return d < 4096.0;
	}

	@Override
	public void tick() {
		this.syncronizedRandom.setSeed(this.getUUID().getLeastSignificantBits() ^ this.level().getGameTime());
		this.getInterpolation().interpolate();
		super.tick();
		Player player = this.getPlayerOwner();
		if (player == null) {
			this.discard();
		} else if (this.level().isClientSide() || !this.shouldStopFishing(player)) {
			if (this.onGround()) {
				this.life++;
				if (this.life >= 1200) {
					this.discard();
					return;
				}
			} else {
				this.life = 0;
			}

			float f = 0.0F;
			BlockPos blockPos = this.blockPosition();
			FluidState fluidState = this.level().getFluidState(blockPos);
			if (fluidState.is(FluidTags.WATER)) {
				f = fluidState.getHeight(this.level(), blockPos);
			}

			boolean bl = f > 0.0F;
			if (this.currentState == FishingHook.FishHookState.FLYING) {
				if (this.hookedIn != null) {
					this.setDeltaMovement(Vec3.ZERO);
					this.currentState = FishingHook.FishHookState.HOOKED_IN_ENTITY;
					return;
				}

				if (bl) {
					this.setDeltaMovement(this.getDeltaMovement().multiply(0.3, 0.2, 0.3));
					this.currentState = FishingHook.FishHookState.BOBBING;
					return;
				}

				this.checkCollision();
			} else {
				if (this.currentState == FishingHook.FishHookState.HOOKED_IN_ENTITY) {
					if (this.hookedIn != null) {
						if (!this.hookedIn.isRemoved() && this.hookedIn.canInteractWithLevel() && this.hookedIn.level().dimension() == this.level().dimension()) {
							this.setPos(this.hookedIn.getX(), this.hookedIn.getY(0.8), this.hookedIn.getZ());
						} else {
							this.setHookedEntity(null);
							this.currentState = FishingHook.FishHookState.FLYING;
						}
					}

					return;
				}

				if (this.currentState == FishingHook.FishHookState.BOBBING) {
					Vec3 vec3 = this.getDeltaMovement();
					double d = this.getY() + vec3.y - blockPos.getY() - f;
					if (Math.abs(d) < 0.01) {
						d += Math.signum(d) * 0.1;
					}

					this.setDeltaMovement(vec3.x * 0.9, vec3.y - d * this.random.nextFloat() * 0.2, vec3.z * 0.9);
					if (this.nibble <= 0 && this.timeUntilHooked <= 0) {
						this.openWater = true;
					} else {
						this.openWater = this.openWater && this.outOfWaterTime < 10 && this.calculateOpenWater(blockPos);
					}

					if (bl) {
						this.outOfWaterTime = Math.max(0, this.outOfWaterTime - 1);
						if (this.biting) {
							this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.1 * this.syncronizedRandom.nextFloat() * this.syncronizedRandom.nextFloat(), 0.0));
						}

						if (!this.level().isClientSide()) {
							this.catchingFish(blockPos);
						}
					} else {
						this.outOfWaterTime = Math.min(10, this.outOfWaterTime + 1);
					}
				}
			}

			if (!fluidState.is(FluidTags.WATER) && !this.onGround() && this.hookedIn == null) {
				this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.03, 0.0));
			}

			this.move(MoverType.SELF, this.getDeltaMovement());
			this.applyEffectsFromBlocks();
			this.updateRotation();
			if (this.currentState == FishingHook.FishHookState.FLYING && (this.onGround() || this.horizontalCollision)) {
				this.setDeltaMovement(Vec3.ZERO);
			}

			double e = 0.92;
			this.setDeltaMovement(this.getDeltaMovement().scale(0.92));
			this.reapplyPosition();
		}
	}

	private boolean shouldStopFishing(Player player) {
		if (player.canInteractWithLevel()) {
			ItemStack itemStack = player.getMainHandItem();
			ItemStack itemStack2 = player.getOffhandItem();
			boolean bl = itemStack.is(Items.FISHING_ROD);
			boolean bl2 = itemStack2.is(Items.FISHING_ROD);
			if ((bl || bl2) && this.distanceToSqr(player) <= 1024.0) {
				return false;
			}
		}

		this.discard();
		return true;
	}

	private void checkCollision() {
		HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
		this.hitTargetOrDeflectSelf(hitResult);
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return super.canHitEntity(entity) || entity.isAlive() && entity instanceof ItemEntity;
	}

	@Override
	protected void onHitEntity(EntityHitResult entityHitResult) {
		super.onHitEntity(entityHitResult);
		if (!this.level().isClientSide()) {
			this.setHookedEntity(entityHitResult.getEntity());
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult blockHitResult) {
		super.onHitBlock(blockHitResult);
		this.setDeltaMovement(this.getDeltaMovement().normalize().scale(blockHitResult.distanceTo(this)));
	}

	private void setHookedEntity(@Nullable Entity entity) {
		this.hookedIn = entity;
		this.getEntityData().set(DATA_HOOKED_ENTITY, entity == null ? 0 : entity.getId() + 1);
	}

	private void catchingFish(BlockPos blockPos) {
		ServerLevel serverLevel = (ServerLevel)this.level();
		int i = 1;
		BlockPos blockPos2 = blockPos.above();
		if (this.random.nextFloat() < 0.25F && this.level().isRainingAt(blockPos2)) {
			i++;
		}

		if (this.random.nextFloat() < 0.5F && !this.level().canSeeSky(blockPos2)) {
			i--;
		}

		if (this.nibble > 0) {
			this.nibble--;
			if (this.nibble <= 0) {
				this.timeUntilLured = 0;
				this.timeUntilHooked = 0;
				this.getEntityData().set(DATA_BITING, false);
			}
		} else if (this.timeUntilHooked > 0) {
			this.timeUntilHooked -= i;
			if (this.timeUntilHooked > 0) {
				this.fishAngle = this.fishAngle + (float)this.random.triangle(0.0, 9.188);
				float f = this.fishAngle * (float) (Math.PI / 180.0);
				float g = Mth.sin(f);
				float h = Mth.cos(f);
				double d = this.getX() + g * this.timeUntilHooked * 0.1F;
				double e = Mth.floor(this.getY()) + 1.0F;
				double j = this.getZ() + h * this.timeUntilHooked * 0.1F;
				BlockState blockState = serverLevel.getBlockState(BlockPos.containing(d, e - 1.0, j));
				if (blockState.is(Blocks.WATER)) {
					if (this.random.nextFloat() < 0.15F) {
						serverLevel.sendParticles(ParticleTypes.BUBBLE, d, e - 0.1F, j, 1, g, 0.1, h, 0.0);
					}

					float k = g * 0.04F;
					float l = h * 0.04F;
					serverLevel.sendParticles(ParticleTypes.FISHING, d, e, j, 0, l, 0.01, -k, 1.0);
					serverLevel.sendParticles(ParticleTypes.FISHING, d, e, j, 0, -l, 0.01, k, 1.0);
				}
			} else {
				this.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.25F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
				double m = this.getY() + 0.5;
				serverLevel.sendParticles(
					ParticleTypes.BUBBLE, this.getX(), m, this.getZ(), (int)(1.0F + this.getBbWidth() * 20.0F), this.getBbWidth(), 0.0, this.getBbWidth(), 0.2F
				);
				serverLevel.sendParticles(
					ParticleTypes.FISHING, this.getX(), m, this.getZ(), (int)(1.0F + this.getBbWidth() * 20.0F), this.getBbWidth(), 0.0, this.getBbWidth(), 0.2F
				);
				this.nibble = Mth.nextInt(this.random, 20, 40);
				this.getEntityData().set(DATA_BITING, true);
			}
		} else if (this.timeUntilLured > 0) {
			this.timeUntilLured -= i;
			float f = 0.15F;
			if (this.timeUntilLured < 20) {
				f += (20 - this.timeUntilLured) * 0.05F;
			} else if (this.timeUntilLured < 40) {
				f += (40 - this.timeUntilLured) * 0.02F;
			} else if (this.timeUntilLured < 60) {
				f += (60 - this.timeUntilLured) * 0.01F;
			}

			if (this.random.nextFloat() < f) {
				float g = Mth.nextFloat(this.random, 0.0F, 360.0F) * (float) (Math.PI / 180.0);
				float h = Mth.nextFloat(this.random, 25.0F, 60.0F);
				double d = this.getX() + Mth.sin(g) * h * 0.1;
				double e = Mth.floor(this.getY()) + 1.0F;
				double j = this.getZ() + Mth.cos(g) * h * 0.1;
				BlockState blockState = serverLevel.getBlockState(BlockPos.containing(d, e - 1.0, j));
				if (blockState.is(Blocks.WATER)) {
					serverLevel.sendParticles(ParticleTypes.SPLASH, d, e, j, 2 + this.random.nextInt(2), 0.1F, 0.0, 0.1F, 0.0);
				}
			}

			if (this.timeUntilLured <= 0) {
				this.fishAngle = Mth.nextFloat(this.random, 0.0F, 360.0F);
				this.timeUntilHooked = Mth.nextInt(this.random, 20, 80);
			}
		} else {
			this.timeUntilLured = Mth.nextInt(this.random, 100, 600);
			this.timeUntilLured = this.timeUntilLured - this.lureSpeed;
		}
	}

	private boolean calculateOpenWater(BlockPos blockPos) {
		FishingHook.OpenWaterType openWaterType = FishingHook.OpenWaterType.INVALID;

		for (int i = -1; i <= 2; i++) {
			FishingHook.OpenWaterType openWaterType2 = this.getOpenWaterTypeForArea(blockPos.offset(-2, i, -2), blockPos.offset(2, i, 2));
			switch (openWaterType2) {
				case ABOVE_WATER:
					if (openWaterType == FishingHook.OpenWaterType.INVALID) {
						return false;
					}
					break;
				case INSIDE_WATER:
					if (openWaterType == FishingHook.OpenWaterType.ABOVE_WATER) {
						return false;
					}
					break;
				case INVALID:
					return false;
			}

			openWaterType = openWaterType2;
		}

		return true;
	}

	private FishingHook.OpenWaterType getOpenWaterTypeForArea(BlockPos blockPos, BlockPos blockPos2) {
		return BlockPos.betweenClosedStream(blockPos, blockPos2)
			.map(this::getOpenWaterTypeForBlock)
			.reduce((openWaterType, openWaterType2) -> openWaterType == openWaterType2 ? openWaterType : FishingHook.OpenWaterType.INVALID)
			.orElse(FishingHook.OpenWaterType.INVALID);
	}

	private FishingHook.OpenWaterType getOpenWaterTypeForBlock(BlockPos blockPos) {
		BlockState blockState = this.level().getBlockState(blockPos);
		if (!blockState.isAir() && !blockState.is(Blocks.LILY_PAD)) {
			FluidState fluidState = blockState.getFluidState();
			return fluidState.is(FluidTags.WATER) && fluidState.isSource() && blockState.getCollisionShape(this.level(), blockPos).isEmpty()
				? FishingHook.OpenWaterType.INSIDE_WATER
				: FishingHook.OpenWaterType.INVALID;
		} else {
			return FishingHook.OpenWaterType.ABOVE_WATER;
		}
	}

	public boolean isOpenWaterFishing() {
		return this.openWater;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput valueOutput) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput valueInput) {
	}

	public int retrieve(ItemStack itemStack) {
		Player player = this.getPlayerOwner();
		if (!this.level().isClientSide() && player != null && !this.shouldStopFishing(player)) {
			int i = 0;
			if (this.hookedIn != null) {
				this.pullEntity(this.hookedIn);
				CriteriaTriggers.FISHING_ROD_HOOKED.trigger((ServerPlayer)player, itemStack, this, Collections.emptyList());
				this.level().broadcastEntityEvent(this, (byte)31);
				i = this.hookedIn instanceof ItemEntity ? 3 : 5;
			} else if (this.nibble > 0) {
				LootParams lootParams = new LootParams.Builder((ServerLevel)this.level())
					.withParameter(LootContextParams.ORIGIN, this.position())
					.withParameter(LootContextParams.TOOL, itemStack)
					.withParameter(LootContextParams.THIS_ENTITY, this)
					.withLuck(this.luck + player.getLuck())
					.create(LootContextParamSets.FISHING);
				LootTable lootTable = this.level().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
				List<ItemStack> list = lootTable.getRandomItems(lootParams);
				CriteriaTriggers.FISHING_ROD_HOOKED.trigger((ServerPlayer)player, itemStack, this, list);

				for (ItemStack itemStack2 : list) {
					ItemEntity itemEntity = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), itemStack2);
					double d = player.getX() - this.getX();
					double e = player.getY() - this.getY();
					double f = player.getZ() - this.getZ();
					double g = 0.1;
					itemEntity.setDeltaMovement(d * 0.1, e * 0.1 + Math.sqrt(Math.sqrt(d * d + e * e + f * f)) * 0.08, f * 0.1);
					this.level().addFreshEntity(itemEntity);
					player.level().addFreshEntity(new ExperienceOrb(player.level(), player.getX(), player.getY() + 0.5, player.getZ() + 0.5, this.random.nextInt(6) + 1));
					if (itemStack2.is(ItemTags.FISHES)) {
						player.awardStat(Stats.FISH_CAUGHT, 1);
					}
				}

				i = 1;
			}

			if (this.onGround()) {
				i = 2;
			}

			this.discard();
			return i;
		} else {
			return 0;
		}
	}

	@Override
	public void handleEntityEvent(byte b) {
		if (b == 31 && this.level().isClientSide() && this.hookedIn instanceof Player player && player.isLocalPlayer()) {
			this.pullEntity(this.hookedIn);
		}

		super.handleEntityEvent(b);
	}

	protected void pullEntity(Entity entity) {
		Entity entity2 = this.getOwner();
		if (entity2 != null) {
			Vec3 vec3 = new Vec3(entity2.getX() - this.getX(), entity2.getY() - this.getY(), entity2.getZ() - this.getZ()).scale(0.1);
			entity.setDeltaMovement(entity.getDeltaMovement().add(vec3));
		}
	}

	@Override
	protected Entity.MovementEmission getMovementEmission() {
		return Entity.MovementEmission.NONE;
	}

	@Override
	public void remove(Entity.RemovalReason removalReason) {
		this.updateOwnerInfo(null);
		super.remove(removalReason);
	}

	@Override
	public void onClientRemoval() {
		this.updateOwnerInfo(null);
	}

	@Override
	public void setOwner(@Nullable Entity entity) {
		super.setOwner(entity);
		this.updateOwnerInfo(this);
	}

	private void updateOwnerInfo(@Nullable FishingHook fishingHook) {
		Player player = this.getPlayerOwner();
		if (player != null) {
			player.fishing = fishingHook;
		}
	}

	public @Nullable Player getPlayerOwner() {
		return this.getOwner() instanceof Player player ? player : null;
	}

	public @Nullable Entity getHookedIn() {
		return this.hookedIn;
	}

	@Override
	public boolean canUsePortal(boolean bl) {
		return false;
	}

	@Override
	public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
		Entity entity = this.getOwner();
		return new ClientboundAddEntityPacket(this, serverEntity, entity == null ? this.getId() : entity.getId());
	}

	@Override
	public void recreateFromPacket(ClientboundAddEntityPacket clientboundAddEntityPacket) {
		super.recreateFromPacket(clientboundAddEntityPacket);
		if (this.getPlayerOwner() == null) {
			int i = clientboundAddEntityPacket.getData();
			LOGGER.error("Failed to recreate fishing hook on client. {} (id: {}) is not a valid owner.", this.level().getEntity(i), i);
			this.discard();
		}
	}

	enum FishHookState {
		FLYING,
		HOOKED_IN_ENTITY,
		BOBBING;
	}

	enum OpenWaterType {
		ABOVE_WATER,
		INSIDE_WATER,
		INVALID;
	}
}
