package net.minecraft.world.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.floats.FloatArraySet;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import it.unimi.dsi.fastutil.floats.FloatSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.BlockUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.debug.DebugEntityBlockIntersection;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Nameable;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Team;
import net.minecraft.world.waypoints.WaypointTransmitter;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.attachment.v1.AttachmentTarget} injected by mod fabric-data-attachment-api-v1</p>
 */
public abstract class Entity
	implements SyncedDataHolder,
	DebugValueSource,
	Nameable,
	ItemOwner,
	SlotProvider,
	EntityAccess,
	ScoreHolder,
	DataComponentGetter,
	AttachmentTarget {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final String TAG_ID = "id";
	public static final String TAG_UUID = "UUID";
	public static final String TAG_PASSENGERS = "Passengers";
	public static final String TAG_DATA = "data";
	public static final String TAG_POS = "Pos";
	public static final String TAG_MOTION = "Motion";
	public static final String TAG_ROTATION = "Rotation";
	public static final String TAG_PORTAL_COOLDOWN = "PortalCooldown";
	public static final String TAG_NO_GRAVITY = "NoGravity";
	public static final String TAG_AIR = "Air";
	public static final String TAG_ON_GROUND = "OnGround";
	public static final String TAG_FALL_DISTANCE = "fall_distance";
	public static final String TAG_FIRE = "Fire";
	public static final String TAG_SILENT = "Silent";
	public static final String TAG_GLOWING = "Glowing";
	public static final String TAG_INVULNERABLE = "Invulnerable";
	public static final String TAG_CUSTOM_NAME = "CustomName";
	private static final AtomicInteger ENTITY_COUNTER = new AtomicInteger();
	public static final int CONTENTS_SLOT_INDEX = 0;
	public static final int BOARDING_COOLDOWN = 60;
	public static final int TOTAL_AIR_SUPPLY = 300;
	public static final int MAX_ENTITY_TAG_COUNT = 1024;
	private static final Codec<List<String>> TAG_LIST_CODEC = Codec.STRING.sizeLimitedListOf(1024);
	public static final float DELTA_AFFECTED_BY_BLOCKS_BELOW_0_2 = 0.2F;
	public static final double DELTA_AFFECTED_BY_BLOCKS_BELOW_0_5 = 0.500001;
	public static final double DELTA_AFFECTED_BY_BLOCKS_BELOW_1_0 = 0.999999;
	public static final int BASE_TICKS_REQUIRED_TO_FREEZE = 140;
	public static final int FREEZE_HURT_FREQUENCY = 40;
	public static final int BASE_SAFE_FALL_DISTANCE = 3;
	private static final AABB INITIAL_AABB = new AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
	private static final double WATER_FLOW_SCALE = 0.014;
	private static final double LAVA_FAST_FLOW_SCALE = 0.007;
	private static final double LAVA_SLOW_FLOW_SCALE = 0.0023333333333333335;
	private static final int MAX_BLOCK_ITERATIONS_ALONG_TRAVEL_PER_TICK = 16;
	private static final double MAX_MOVEMENT_RESETTING_TRACE_DISTANCE = 8.0;
	private static double viewScale = 1.0;
	private final EntityType<?> type;
	private boolean requiresPrecisePosition;
	private int id = ENTITY_COUNTER.incrementAndGet();
	public boolean blocksBuilding;
	private ImmutableList<Entity> passengers = ImmutableList.of();
	protected int boardingCooldown;
	private @Nullable Entity vehicle;
	private Level level;
	public double xo;
	public double yo;
	public double zo;
	private Vec3 position;
	private BlockPos blockPosition;
	private ChunkPos chunkPosition;
	private Vec3 deltaMovement = Vec3.ZERO;
	private float yRot;
	private float xRot;
	public float yRotO;
	public float xRotO;
	private AABB bb = INITIAL_AABB;
	private boolean onGround;
	public boolean horizontalCollision;
	public boolean verticalCollision;
	public boolean verticalCollisionBelow;
	public boolean minorHorizontalCollision;
	public boolean hurtMarked;
	protected Vec3 stuckSpeedMultiplier = Vec3.ZERO;
	private Entity.@Nullable RemovalReason removalReason;
	public static final float DEFAULT_BB_WIDTH = 0.6F;
	public static final float DEFAULT_BB_HEIGHT = 1.8F;
	public float moveDist;
	public float flyDist;
	public double fallDistance;
	private float nextStep = 1.0F;
	public double xOld;
	public double yOld;
	public double zOld;
	public boolean noPhysics;
	protected final RandomSource random = RandomSource.create();
	public int tickCount;
	private int remainingFireTicks;
	protected boolean wasTouchingWater;
	protected Object2DoubleMap<TagKey<Fluid>> fluidHeight = new Object2DoubleArrayMap<>(2);
	protected boolean wasEyeInWater;
	private final Set<TagKey<Fluid>> fluidOnEyes = new HashSet<>();
	public int invulnerableTime;
	protected boolean firstTick = true;
	protected final SynchedEntityData entityData;
	protected static final EntityDataAccessor<Byte> DATA_SHARED_FLAGS_ID = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BYTE);
	protected static final int FLAG_ONFIRE = 0;
	private static final int FLAG_SHIFT_KEY_DOWN = 1;
	private static final int FLAG_SPRINTING = 3;
	private static final int FLAG_SWIMMING = 4;
	private static final int FLAG_INVISIBLE = 5;
	protected static final int FLAG_GLOWING = 6;
	protected static final int FLAG_FALL_FLYING = 7;
	private static final EntityDataAccessor<Integer> DATA_AIR_SUPPLY_ID = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Optional<Component>> DATA_CUSTOM_NAME = SynchedEntityData.defineId(
		Entity.class, EntityDataSerializers.OPTIONAL_COMPONENT
	);
	private static final EntityDataAccessor<Boolean> DATA_CUSTOM_NAME_VISIBLE = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_SILENT = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_NO_GRAVITY = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BOOLEAN);
	protected static final EntityDataAccessor<Pose> DATA_POSE = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.POSE);
	private static final EntityDataAccessor<Integer> DATA_TICKS_FROZEN = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.INT);
	private EntityInLevelCallback levelCallback = EntityInLevelCallback.NULL;
	private final VecDeltaCodec packetPositionCodec = new VecDeltaCodec();
	public boolean needsSync;
	public @Nullable PortalProcessor portalProcess;
	private int portalCooldown;
	private boolean invulnerable;
	protected UUID uuid = Mth.createInsecureUUID(this.random);
	protected String stringUUID = this.uuid.toString();
	private boolean hasGlowingTag;
	private final Set<String> tags = Sets.newHashSet();
	private final double[] pistonDeltas = new double[]{0.0, 0.0, 0.0};
	private long pistonDeltasGameTime;
	private EntityDimensions dimensions;
	private float eyeHeight;
	public boolean isInPowderSnow;
	public boolean wasInPowderSnow;
	public Optional<BlockPos> mainSupportingBlockPos = Optional.empty();
	private boolean onGroundNoBlocks = false;
	private float crystalSoundIntensity;
	private int lastCrystalSoundPlayTick;
	private boolean hasVisualFire;
	private Vec3 lastKnownSpeed = Vec3.ZERO;
	private @Nullable Vec3 lastKnownPosition;
	private @Nullable BlockState inBlockState = null;
	public static final int MAX_MOVEMENTS_HANDELED_PER_TICK = 100;
	private final ArrayDeque<Entity.Movement> movementThisTick = new ArrayDeque<>(100);
	private final List<Entity.Movement> finalMovementsThisTick = new ObjectArrayList<>();
	private final LongSet visitedBlocks = new LongOpenHashSet();
	private final InsideBlockEffectApplier.StepBasedCollector insideEffectCollector = new InsideBlockEffectApplier.StepBasedCollector();
	private CustomData customData = CustomData.EMPTY;

	public Entity(EntityType<?> entityType, Level level) {
		this.type = entityType;
		this.level = level;
		this.dimensions = entityType.getDimensions();
		this.position = Vec3.ZERO;
		this.blockPosition = BlockPos.ZERO;
		this.chunkPosition = ChunkPos.ZERO;
		SynchedEntityData.Builder builder = new SynchedEntityData.Builder(this);
		builder.define(DATA_SHARED_FLAGS_ID, (byte)0);
		builder.define(DATA_AIR_SUPPLY_ID, this.getMaxAirSupply());
		builder.define(DATA_CUSTOM_NAME_VISIBLE, false);
		builder.define(DATA_CUSTOM_NAME, Optional.empty());
		builder.define(DATA_SILENT, false);
		builder.define(DATA_NO_GRAVITY, false);
		builder.define(DATA_POSE, Pose.STANDING);
		builder.define(DATA_TICKS_FROZEN, 0);
		this.defineSynchedData(builder);
		this.entityData = builder.build();
		this.setPos(0.0, 0.0, 0.0);
		this.eyeHeight = this.dimensions.eyeHeight();
	}

	public boolean isColliding(BlockPos blockPos, BlockState blockState) {
		VoxelShape voxelShape = blockState.getCollisionShape(this.level(), blockPos, CollisionContext.of(this)).move(blockPos);
		return Shapes.joinIsNotEmpty(voxelShape, Shapes.create(this.getBoundingBox()), BooleanOp.AND);
	}

	public int getTeamColor() {
		Team team = this.getTeam();
		return team != null && team.getColor().getColor() != null ? team.getColor().getColor() : 16777215;
	}

	public boolean isSpectator() {
		return false;
	}

	public boolean canInteractWithLevel() {
		return this.isAlive() && !this.isRemoved() && !this.isSpectator();
	}

	public final void unRide() {
		if (this.isVehicle()) {
			this.ejectPassengers();
		}

		if (this.isPassenger()) {
			this.stopRiding();
		}
	}

	public void syncPacketPositionCodec(double d, double e, double f) {
		this.packetPositionCodec.setBase(new Vec3(d, e, f));
	}

	public VecDeltaCodec getPositionCodec() {
		return this.packetPositionCodec;
	}

	public EntityType<?> getType() {
		return this.type;
	}

	public boolean getRequiresPrecisePosition() {
		return this.requiresPrecisePosition;
	}

	public void setRequiresPrecisePosition(boolean bl) {
		this.requiresPrecisePosition = bl;
	}

	@Override
	public int getId() {
		return this.id;
	}

	public void setId(int i) {
		this.id = i;
	}

	public Set<String> getTags() {
		return this.tags;
	}

	public boolean addTag(String string) {
		return this.tags.size() >= 1024 ? false : this.tags.add(string);
	}

	public boolean removeTag(String string) {
		return this.tags.remove(string);
	}

	public void kill(ServerLevel serverLevel) {
		this.remove(Entity.RemovalReason.KILLED);
		this.gameEvent(GameEvent.ENTITY_DIE);
	}

	public final void discard() {
		this.remove(Entity.RemovalReason.DISCARDED);
	}

	protected abstract void defineSynchedData(SynchedEntityData.Builder builder);

	public SynchedEntityData getEntityData() {
		return this.entityData;
	}

	@Override
	public boolean equals(Object object) {
		return object instanceof Entity ? ((Entity)object).id == this.id : false;
	}

	@Override
	public int hashCode() {
		return this.id;
	}

	public void remove(Entity.RemovalReason removalReason) {
		this.setRemoved(removalReason);
	}

	public void onClientRemoval() {
	}

	public void onRemoval(Entity.RemovalReason removalReason) {
	}

	public void setPose(Pose pose) {
		this.entityData.set(DATA_POSE, pose);
	}

	public Pose getPose() {
		return this.entityData.get(DATA_POSE);
	}

	public boolean hasPose(Pose pose) {
		return this.getPose() == pose;
	}

	public boolean closerThan(Entity entity, double d) {
		return this.position().closerThan(entity.position(), d);
	}

	public boolean closerThan(Entity entity, double d, double e) {
		double f = entity.getX() - this.getX();
		double g = entity.getY() - this.getY();
		double h = entity.getZ() - this.getZ();
		return Mth.lengthSquared(f, h) < Mth.square(d) && Mth.square(g) < Mth.square(e);
	}

	protected void setRot(float f, float g) {
		this.setYRot(f % 360.0F);
		this.setXRot(g % 360.0F);
	}

	public final void setPos(Vec3 vec3) {
		this.setPos(vec3.x(), vec3.y(), vec3.z());
	}

	public void setPos(double d, double e, double f) {
		this.setPosRaw(d, e, f);
		this.setBoundingBox(this.makeBoundingBox());
	}

	protected final AABB makeBoundingBox() {
		return this.makeBoundingBox(this.position);
	}

	protected AABB makeBoundingBox(Vec3 vec3) {
		return this.dimensions.makeBoundingBox(vec3);
	}

	protected void reapplyPosition() {
		this.lastKnownPosition = null;
		this.setPos(this.position.x, this.position.y, this.position.z);
	}

	public void turn(double d, double e) {
		float f = (float)e * 0.15F;
		float g = (float)d * 0.15F;
		this.setXRot(this.getXRot() + f);
		this.setYRot(this.getYRot() + g);
		this.setXRot(Mth.clamp(this.getXRot(), -90.0F, 90.0F));
		this.xRotO += f;
		this.yRotO += g;
		this.xRotO = Mth.clamp(this.xRotO, -90.0F, 90.0F);
		if (this.vehicle != null) {
			this.vehicle.onPassengerTurned(this);
		}
	}

	public void updateDataBeforeSync() {
	}

	public void tick() {
		this.baseTick();
	}

	public void baseTick() {
		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push("entityBaseTick");
		this.computeSpeed();
		this.inBlockState = null;
		if (this.isPassenger() && this.getVehicle().isRemoved()) {
			this.stopRiding();
		}

		if (this.boardingCooldown > 0) {
			this.boardingCooldown--;
		}

		this.handlePortal();
		if (this.canSpawnSprintParticle()) {
			this.spawnSprintParticle();
		}

		this.wasInPowderSnow = this.isInPowderSnow;
		this.isInPowderSnow = false;
		this.updateInWaterStateAndDoFluidPushing();
		this.updateFluidOnEyes();
		this.updateSwimming();
		if (this.level() instanceof ServerLevel serverLevel) {
			if (this.remainingFireTicks > 0) {
				if (this.fireImmune()) {
					this.clearFire();
				} else {
					if (this.remainingFireTicks % 20 == 0 && !this.isInLava()) {
						this.hurtServer(serverLevel, this.damageSources().onFire(), 1.0F);
					}

					this.setRemainingFireTicks(this.remainingFireTicks - 1);
				}
			}
		} else {
			this.clearFire();
		}

		if (this.isInLava()) {
			this.fallDistance *= 0.5;
		}

		this.checkBelowWorld();
		if (!this.level().isClientSide()) {
			this.setSharedFlagOnFire(this.remainingFireTicks > 0);
		}

		this.firstTick = false;
		if (this.level() instanceof ServerLevel serverLevel && this instanceof Leashable) {
			Leashable.tickLeash(serverLevel, (Entity & Leashable)this);
		}

		profilerFiller.pop();
	}

	protected void computeSpeed() {
		if (this.lastKnownPosition == null) {
			this.lastKnownPosition = this.position();
		}

		this.lastKnownSpeed = this.position().subtract(this.lastKnownPosition);
		this.lastKnownPosition = this.position();
	}

	public void setSharedFlagOnFire(boolean bl) {
		this.setSharedFlag(0, bl || this.hasVisualFire);
	}

	public void checkBelowWorld() {
		if (this.getY() < this.level().getMinY() - 64) {
			this.onBelowWorld();
		}
	}

	public void setPortalCooldown() {
		this.portalCooldown = this.getDimensionChangingDelay();
	}

	public void setPortalCooldown(int i) {
		this.portalCooldown = i;
	}

	public int getPortalCooldown() {
		return this.portalCooldown;
	}

	public boolean isOnPortalCooldown() {
		return this.portalCooldown > 0;
	}

	protected void processPortalCooldown() {
		if (this.isOnPortalCooldown()) {
			this.portalCooldown--;
		}
	}

	public void lavaIgnite() {
		if (!this.fireImmune()) {
			this.igniteForSeconds(15.0F);
		}
	}

	public void lavaHurt() {
		if (!this.fireImmune()) {
			if (this.level() instanceof ServerLevel serverLevel
				&& this.hurtServer(serverLevel, this.damageSources().lava(), 4.0F)
				&& this.shouldPlayLavaHurtSound()
				&& !this.isSilent()) {
				serverLevel.playSound(
					null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_BURN, this.getSoundSource(), 0.4F, 2.0F + this.random.nextFloat() * 0.4F
				);
			}
		}
	}

	protected boolean shouldPlayLavaHurtSound() {
		return true;
	}

	public final void igniteForSeconds(float f) {
		this.igniteForTicks(Mth.floor(f * 20.0F));
	}

	public void igniteForTicks(int i) {
		if (this.remainingFireTicks < i) {
			this.setRemainingFireTicks(i);
		}

		this.clearFreeze();
	}

	public void setRemainingFireTicks(int i) {
		this.remainingFireTicks = i;
	}

	public int getRemainingFireTicks() {
		return this.remainingFireTicks;
	}

	public void clearFire() {
		this.setRemainingFireTicks(Math.min(0, this.getRemainingFireTicks()));
	}

	protected void onBelowWorld() {
		this.discard();
	}

	public boolean isFree(double d, double e, double f) {
		return this.isFree(this.getBoundingBox().move(d, e, f));
	}

	private boolean isFree(AABB aABB) {
		return this.level().noCollision(this, aABB) && !this.level().containsAnyLiquid(aABB);
	}

	public void setOnGround(boolean bl) {
		this.onGround = bl;
		this.checkSupportingBlock(bl, null);
	}

	public void setOnGroundWithMovement(boolean bl, Vec3 vec3) {
		this.setOnGroundWithMovement(bl, this.horizontalCollision, vec3);
	}

	public void setOnGroundWithMovement(boolean bl, boolean bl2, Vec3 vec3) {
		this.onGround = bl;
		this.horizontalCollision = bl2;
		this.checkSupportingBlock(bl, vec3);
	}

	public boolean isSupportedBy(BlockPos blockPos) {
		return this.mainSupportingBlockPos.isPresent() && this.mainSupportingBlockPos.get().equals(blockPos);
	}

	protected void checkSupportingBlock(boolean bl, @Nullable Vec3 vec3) {
		if (bl) {
			AABB aABB = this.getBoundingBox();
			AABB aABB2 = new AABB(aABB.minX, aABB.minY - 1.0E-6, aABB.minZ, aABB.maxX, aABB.minY, aABB.maxZ);
			Optional<BlockPos> optional = this.level.findSupportingBlock(this, aABB2);
			if (optional.isPresent() || this.onGroundNoBlocks) {
				this.mainSupportingBlockPos = optional;
			} else if (vec3 != null) {
				AABB aABB3 = aABB2.move(-vec3.x, 0.0, -vec3.z);
				optional = this.level.findSupportingBlock(this, aABB3);
				this.mainSupportingBlockPos = optional;
			}

			this.onGroundNoBlocks = optional.isEmpty();
		} else {
			this.onGroundNoBlocks = false;
			if (this.mainSupportingBlockPos.isPresent()) {
				this.mainSupportingBlockPos = Optional.empty();
			}
		}
	}

	public boolean onGround() {
		return this.onGround;
	}

	public void move(MoverType moverType, Vec3 vec3) {
		if (this.noPhysics) {
			this.setPos(this.getX() + vec3.x, this.getY() + vec3.y, this.getZ() + vec3.z);
			this.horizontalCollision = false;
			this.verticalCollision = false;
			this.verticalCollisionBelow = false;
			this.minorHorizontalCollision = false;
		} else {
			if (moverType == MoverType.PISTON) {
				vec3 = this.limitPistonMovement(vec3);
				if (vec3.equals(Vec3.ZERO)) {
					return;
				}
			}

			ProfilerFiller profilerFiller = Profiler.get();
			profilerFiller.push("move");
			if (this.stuckSpeedMultiplier.lengthSqr() > 1.0E-7) {
				if (moverType != MoverType.PISTON) {
					vec3 = vec3.multiply(this.stuckSpeedMultiplier);
				}

				this.stuckSpeedMultiplier = Vec3.ZERO;
				this.setDeltaMovement(Vec3.ZERO);
			}

			vec3 = this.maybeBackOffFromEdge(vec3, moverType);
			Vec3 vec32 = this.collide(vec3);
			double d = vec32.lengthSqr();
			if (d > 1.0E-7 || vec3.lengthSqr() - d < 1.0E-7) {
				if (this.fallDistance != 0.0 && d >= 1.0) {
					double e = Math.min(vec32.length(), 8.0);
					Vec3 vec33 = this.position().add(vec32.normalize().scale(e));
					BlockHitResult blockHitResult = this.level()
						.clip(new ClipContext(this.position(), vec33, ClipContext.Block.FALLDAMAGE_RESETTING, ClipContext.Fluid.WATER, this));
					if (blockHitResult.getType() != HitResult.Type.MISS) {
						this.resetFallDistance();
					}
				}

				Vec3 vec34 = this.position();
				Vec3 vec35 = vec34.add(vec32);
				this.addMovementThisTick(new Entity.Movement(vec34, vec35, vec3));
				this.setPos(vec35);
			}

			profilerFiller.pop();
			profilerFiller.push("rest");
			boolean bl = !Mth.equal(vec3.x, vec32.x);
			boolean bl2 = !Mth.equal(vec3.z, vec32.z);
			this.horizontalCollision = bl || bl2;
			if (Math.abs(vec3.y) > 0.0 || this.isLocalInstanceAuthoritative()) {
				this.verticalCollision = vec3.y != vec32.y;
				this.verticalCollisionBelow = this.verticalCollision && vec3.y < 0.0;
				this.setOnGroundWithMovement(this.verticalCollisionBelow, this.horizontalCollision, vec32);
			}

			if (this.horizontalCollision) {
				this.minorHorizontalCollision = this.isHorizontalCollisionMinor(vec32);
			} else {
				this.minorHorizontalCollision = false;
			}

			BlockPos blockPos = this.getOnPosLegacy();
			BlockState blockState = this.level().getBlockState(blockPos);
			if (this.isLocalInstanceAuthoritative()) {
				this.checkFallDamage(vec32.y, this.onGround(), blockState, blockPos);
			}

			if (this.isRemoved()) {
				profilerFiller.pop();
			} else {
				if (this.horizontalCollision) {
					Vec3 vec36 = this.getDeltaMovement();
					this.setDeltaMovement(bl ? 0.0 : vec36.x, vec36.y, bl2 ? 0.0 : vec36.z);
				}

				if (this.canSimulateMovement()) {
					Block block = blockState.getBlock();
					if (vec3.y != vec32.y) {
						block.updateEntityMovementAfterFallOn(this.level(), this);
					}
				}

				if (!this.level().isClientSide() || this.isLocalInstanceAuthoritative()) {
					Entity.MovementEmission movementEmission = this.getMovementEmission();
					if (movementEmission.emitsAnything() && !this.isPassenger()) {
						this.applyMovementEmissionAndPlaySound(movementEmission, vec32, blockPos, blockState);
					}
				}

				float f = this.getBlockSpeedFactor();
				this.setDeltaMovement(this.getDeltaMovement().multiply(f, 1.0, f));
				profilerFiller.pop();
			}
		}
	}

	private void applyMovementEmissionAndPlaySound(Entity.MovementEmission movementEmission, Vec3 vec3, BlockPos blockPos, BlockState blockState) {
		float f = 0.6F;
		float g = (float)(vec3.length() * 0.6F);
		float h = (float)(vec3.horizontalDistance() * 0.6F);
		BlockPos blockPos2 = this.getOnPos();
		BlockState blockState2 = this.level().getBlockState(blockPos2);
		boolean bl = this.isStateClimbable(blockState2);
		this.moveDist += bl ? g : h;
		this.flyDist += g;
		if (this.moveDist > this.nextStep && !blockState2.isAir()) {
			boolean bl2 = blockPos2.equals(blockPos);
			boolean bl3 = this.vibrationAndSoundEffectsFromBlock(blockPos, blockState, movementEmission.emitsSounds(), bl2, vec3);
			if (!bl2) {
				bl3 |= this.vibrationAndSoundEffectsFromBlock(blockPos2, blockState2, false, movementEmission.emitsEvents(), vec3);
			}

			if (bl3) {
				this.nextStep = this.nextStep();
			} else if (this.isInWater()) {
				this.nextStep = this.nextStep();
				if (movementEmission.emitsSounds()) {
					this.waterSwimSound();
				}

				if (movementEmission.emitsEvents()) {
					this.gameEvent(GameEvent.SWIM);
				}
			}
		} else if (blockState2.isAir()) {
			this.processFlappingMovement();
		}
	}

	protected void applyEffectsFromBlocks() {
		this.finalMovementsThisTick.clear();
		this.finalMovementsThisTick.addAll(this.movementThisTick);
		this.movementThisTick.clear();
		if (this.finalMovementsThisTick.isEmpty()) {
			this.finalMovementsThisTick.add(new Entity.Movement(this.oldPosition(), this.position()));
		} else if (this.finalMovementsThisTick.getLast().to.distanceToSqr(this.position()) > 9.9999994E-11F) {
			this.finalMovementsThisTick.add(new Entity.Movement(this.finalMovementsThisTick.getLast().to, this.position()));
		}

		this.applyEffectsFromBlocks(this.finalMovementsThisTick);
	}

	private void addMovementThisTick(Entity.Movement movement) {
		if (this.movementThisTick.size() >= 100) {
			Entity.Movement movement2 = this.movementThisTick.removeFirst();
			Entity.Movement movement3 = this.movementThisTick.removeFirst();
			Entity.Movement movement4 = new Entity.Movement(movement2.from(), movement3.to());
			this.movementThisTick.addFirst(movement4);
		}

		this.movementThisTick.add(movement);
	}

	public void removeLatestMovementRecording() {
		if (!this.movementThisTick.isEmpty()) {
			this.movementThisTick.removeLast();
		}
	}

	protected void clearMovementThisTick() {
		this.movementThisTick.clear();
	}

	public boolean hasMovedHorizontallyRecently() {
		return Math.abs(this.lastKnownSpeed.horizontalDistance()) > 1.0E-5F;
	}

	public void applyEffectsFromBlocks(Vec3 vec3, Vec3 vec32) {
		this.applyEffectsFromBlocks(List.of(new Entity.Movement(vec3, vec32)));
	}

	private void applyEffectsFromBlocks(List<Entity.Movement> list) {
		if (this.isAffectedByBlocks()) {
			if (this.onGround()) {
				BlockPos blockPos = this.getOnPosLegacy();
				BlockState blockState = this.level().getBlockState(blockPos);
				blockState.getBlock().stepOn(this.level(), blockPos, blockState, this);
			}

			boolean bl = this.isOnFire();
			boolean bl2 = this.isFreezing();
			int i = this.getRemainingFireTicks();
			this.checkInsideBlocks(list, this.insideEffectCollector);
			this.insideEffectCollector.applyAndClear(this);
			if (this.isInRain()) {
				this.clearFire();
			}

			if (bl && !this.isOnFire() || bl2 && !this.isFreezing()) {
				this.playEntityOnFireExtinguishedSound();
			}

			boolean bl3 = this.getRemainingFireTicks() > i;
			if (!this.level().isClientSide() && !this.isOnFire() && !bl3) {
				this.setRemainingFireTicks(-this.getFireImmuneTicks());
			}
		}
	}

	protected boolean isAffectedByBlocks() {
		return !this.isRemoved() && !this.noPhysics;
	}

	private boolean isStateClimbable(BlockState blockState) {
		return blockState.is(BlockTags.CLIMBABLE) || blockState.is(Blocks.POWDER_SNOW);
	}

	private boolean vibrationAndSoundEffectsFromBlock(BlockPos blockPos, BlockState blockState, boolean bl, boolean bl2, Vec3 vec3) {
		if (blockState.isAir()) {
			return false;
		}

		boolean bl3 = this.isStateClimbable(blockState);
		if ((this.onGround() || bl3 || this.isCrouching() && vec3.y == 0.0 || this.isOnRails()) && !this.isSwimming()) {
			if (bl) {
				this.walkingStepSound(blockPos, blockState);
			}

			if (bl2) {
				this.level().gameEvent(GameEvent.STEP, this.position(), GameEvent.Context.of(this, blockState));
			}

			return true;
		} else {
			return false;
		}
	}

	protected boolean isHorizontalCollisionMinor(Vec3 vec3) {
		return false;
	}

	protected void playEntityOnFireExtinguishedSound() {
		if (!this.level.isClientSide()) {
			this.level()
				.playSound(
					null,
					this.getX(),
					this.getY(),
					this.getZ(),
					SoundEvents.GENERIC_EXTINGUISH_FIRE,
					this.getSoundSource(),
					0.7F,
					1.6F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F
				);
		}
	}

	public void extinguishFire() {
		if (this.isOnFire()) {
			this.playEntityOnFireExtinguishedSound();
		}

		this.clearFire();
	}

	protected void processFlappingMovement() {
		if (this.isFlapping()) {
			this.onFlap();
			if (this.getMovementEmission().emitsEvents()) {
				this.gameEvent(GameEvent.FLAP);
			}
		}
	}

	@Deprecated
	public BlockPos getOnPosLegacy() {
		return this.getOnPos(0.2F);
	}

	public BlockPos getBlockPosBelowThatAffectsMyMovement() {
		return this.getOnPos(0.500001F);
	}

	public BlockPos getOnPos() {
		return this.getOnPos(1.0E-5F);
	}

	protected BlockPos getOnPos(float f) {
		if (this.mainSupportingBlockPos.isPresent()) {
			BlockPos blockPos = this.mainSupportingBlockPos.get();
			if (!(f > 1.0E-5F)) {
				return blockPos;
			}

			BlockState blockState = this.level().getBlockState(blockPos);
			return (!(f <= 0.5) || !blockState.is(BlockTags.FENCES)) && !blockState.is(BlockTags.WALLS) && !(blockState.getBlock() instanceof FenceGateBlock)
				? blockPos.atY(Mth.floor(this.position.y - f))
				: blockPos;
		} else {
			int i = Mth.floor(this.position.x);
			int j = Mth.floor(this.position.y - f);
			int k = Mth.floor(this.position.z);
			return new BlockPos(i, j, k);
		}
	}

	protected float getBlockJumpFactor() {
		float f = this.level().getBlockState(this.blockPosition()).getBlock().getJumpFactor();
		float g = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getJumpFactor();
		return f == 1.0 ? g : f;
	}

	protected float getBlockSpeedFactor() {
		BlockState blockState = this.level().getBlockState(this.blockPosition());
		float f = blockState.getBlock().getSpeedFactor();
		if (!blockState.is(Blocks.WATER) && !blockState.is(Blocks.BUBBLE_COLUMN)) {
			return f == 1.0 ? this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getSpeedFactor() : f;
		} else {
			return f;
		}
	}

	protected Vec3 maybeBackOffFromEdge(Vec3 vec3, MoverType moverType) {
		return vec3;
	}

	protected Vec3 limitPistonMovement(Vec3 vec3) {
		if (vec3.lengthSqr() <= 1.0E-7) {
			return vec3;
		}

		long l = this.level().getGameTime();
		if (l != this.pistonDeltasGameTime) {
			Arrays.fill(this.pistonDeltas, 0.0);
			this.pistonDeltasGameTime = l;
		}

		if (vec3.x != 0.0) {
			double d = this.applyPistonMovementRestriction(Direction.Axis.X, vec3.x);
			return Math.abs(d) <= 1.0E-5F ? Vec3.ZERO : new Vec3(d, 0.0, 0.0);
		} else if (vec3.y != 0.0) {
			double d = this.applyPistonMovementRestriction(Direction.Axis.Y, vec3.y);
			return Math.abs(d) <= 1.0E-5F ? Vec3.ZERO : new Vec3(0.0, d, 0.0);
		} else if (vec3.z != 0.0) {
			double d = this.applyPistonMovementRestriction(Direction.Axis.Z, vec3.z);
			return Math.abs(d) <= 1.0E-5F ? Vec3.ZERO : new Vec3(0.0, 0.0, d);
		} else {
			return Vec3.ZERO;
		}
	}

	private double applyPistonMovementRestriction(Direction.Axis axis, double d) {
		int i = axis.ordinal();
		double e = Mth.clamp(d + this.pistonDeltas[i], -0.51, 0.51);
		d = e - this.pistonDeltas[i];
		this.pistonDeltas[i] = e;
		return d;
	}

	public double getAvailableSpaceBelow(double d) {
		AABB aABB = this.getBoundingBox();
		AABB aABB2 = aABB.setMinY(aABB.minY - d).setMaxY(aABB.minY);
		List<VoxelShape> list = collectAllColliders(this, this.level, aABB2);
		return list.isEmpty() ? d : -Shapes.collide(Direction.Axis.Y, aABB, list, -d);
	}

	private Vec3 collide(Vec3 vec3) {
		AABB aABB = this.getBoundingBox();
		List<VoxelShape> list = this.level().getEntityCollisions(this, aABB.expandTowards(vec3));
		Vec3 vec32 = vec3.lengthSqr() == 0.0 ? vec3 : collideBoundingBox(this, vec3, aABB, this.level(), list);
		boolean bl = vec3.x != vec32.x;
		boolean bl2 = vec3.y != vec32.y;
		boolean bl3 = vec3.z != vec32.z;
		boolean bl4 = bl2 && vec3.y < 0.0;
		if (this.maxUpStep() > 0.0F && (bl4 || this.onGround()) && (bl || bl3)) {
			AABB aABB2 = bl4 ? aABB.move(0.0, vec32.y, 0.0) : aABB;
			AABB aABB3 = aABB2.expandTowards(vec3.x, this.maxUpStep(), vec3.z);
			if (!bl4) {
				aABB3 = aABB3.expandTowards(0.0, -1.0E-5F, 0.0);
			}

			List<VoxelShape> list2 = collectColliders(this, this.level, list, aABB3);
			float f = (float)vec32.y;
			float[] fs = collectCandidateStepUpHeights(aABB2, list2, this.maxUpStep(), f);

			for (float g : fs) {
				Vec3 vec33 = collideWithShapes(new Vec3(vec3.x, g, vec3.z), aABB2, list2);
				if (vec33.horizontalDistanceSqr() > vec32.horizontalDistanceSqr()) {
					double d = aABB.minY - aABB2.minY;
					return vec33.subtract(0.0, d, 0.0);
				}
			}
		}

		return vec32;
	}

	private static float[] collectCandidateStepUpHeights(AABB aABB, List<VoxelShape> list, float f, float g) {
		FloatSet floatSet = new FloatArraySet(4);

		for (VoxelShape voxelShape : list) {
			for (double d : voxelShape.getCoords(Direction.Axis.Y)) {
				float h = (float)(d - aABB.minY);
				if (!(h < 0.0F) && h != g) {
					if (h > f) {
						break;
					}

					floatSet.add(h);
				}
			}
		}

		float[] fs = floatSet.toFloatArray();
		FloatArrays.unstableSort(fs);
		return fs;
	}

	public static Vec3 collideBoundingBox(@Nullable Entity entity, Vec3 vec3, AABB aABB, Level level, List<VoxelShape> list) {
		List<VoxelShape> list2 = collectColliders(entity, level, list, aABB.expandTowards(vec3));
		return collideWithShapes(vec3, aABB, list2);
	}

	public static List<VoxelShape> collectAllColliders(@Nullable Entity entity, Level level, AABB aABB) {
		List<VoxelShape> list = level.getEntityCollisions(entity, aABB);
		return collectColliders(entity, level, list, aABB);
	}

	private static List<VoxelShape> collectColliders(@Nullable Entity entity, Level level, List<VoxelShape> list, AABB aABB) {
		Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(list.size() + 1);
		if (!list.isEmpty()) {
			builder.addAll(list);
		}

		WorldBorder worldBorder = level.getWorldBorder();
		boolean bl = entity != null && worldBorder.isInsideCloseToBorder(entity, aABB);
		if (bl) {
			builder.add(worldBorder.getCollisionShape());
		}

		builder.addAll(level.getBlockCollisions(entity, aABB));
		return builder.build();
	}

	private static Vec3 collideWithShapes(Vec3 vec3, AABB aABB, List<VoxelShape> list) {
		if (list.isEmpty()) {
			return vec3;
		}

		Vec3 vec32 = Vec3.ZERO;

		for (Direction.Axis axis : Direction.axisStepOrder(vec3)) {
			double d = vec3.get(axis);
			if (d != 0.0) {
				double e = Shapes.collide(axis, aABB.move(vec32), list, d);
				vec32 = vec32.with(axis, e);
			}
		}

		return vec32;
	}

	protected float nextStep() {
		return (int)this.moveDist + 1;
	}

	protected SoundEvent getSwimSound() {
		return SoundEvents.GENERIC_SWIM;
	}

	protected SoundEvent getSwimSplashSound() {
		return SoundEvents.GENERIC_SPLASH;
	}

	protected SoundEvent getSwimHighSpeedSplashSound() {
		return SoundEvents.GENERIC_SPLASH;
	}

	private void checkInsideBlocks(List<Entity.Movement> list, InsideBlockEffectApplier.StepBasedCollector stepBasedCollector) {
		if (this.isAffectedByBlocks()) {
			LongSet longSet = this.visitedBlocks;

			for (Entity.Movement movement : list) {
				Vec3 vec3 = movement.from;
				Vec3 vec32 = movement.to().subtract(movement.from());
				int i = 16;
				if (movement.axisDependentOriginalMovement().isPresent() && vec32.lengthSqr() > 0.0) {
					for (Direction.Axis axis : Direction.axisStepOrder(movement.axisDependentOriginalMovement().get())) {
						double d = vec32.get(axis);
						if (d != 0.0) {
							Vec3 vec33 = vec3.relative(axis.getPositive(), d);
							i -= this.checkInsideBlocks(vec3, vec33, stepBasedCollector, longSet, i);
							vec3 = vec33;
						}
					}
				} else {
					i -= this.checkInsideBlocks(movement.from(), movement.to(), stepBasedCollector, longSet, 16);
				}

				if (i <= 0) {
					this.checkInsideBlocks(movement.to(), movement.to(), stepBasedCollector, longSet, 1);
				}
			}

			longSet.clear();
		}
	}

	private int checkInsideBlocks(Vec3 vec3, Vec3 vec32, InsideBlockEffectApplier.StepBasedCollector stepBasedCollector, LongSet longSet, int i) {
		AABB aABB = this.makeBoundingBox(vec32).deflate(1.0E-5F);
		boolean bl = vec3.distanceToSqr(vec32) > Mth.square(0.9999900000002526);
		boolean bl2 = this.level instanceof ServerLevel serverLevel
			&& serverLevel.getServer().debugSubscribers().hasAnySubscriberFor(DebugSubscriptions.ENTITY_BLOCK_INTERSECTIONS);
		AtomicInteger atomicInteger = new AtomicInteger();
		BlockGetter.forEachBlockIntersectedBetween(vec3, vec32, aABB, (blockPos, j) -> {
			if (!this.isAlive()) {
				return false;
			}

			if (j >= i) {
				return false;
			}

			atomicInteger.set(j);
			BlockState blockState = this.level().getBlockState(blockPos);
			if (blockState.isAir()) {
				if (bl2) {
					this.debugBlockIntersection((ServerLevel)this.level(), blockPos.immutable(), false, false);
				}

				return true;
			} else {
				VoxelShape voxelShape = blockState.getEntityInsideCollisionShape(this.level(), blockPos, this);
				boolean bl3 = voxelShape == Shapes.block() || this.collidedWithShapeMovingFrom(vec3, vec32, voxelShape.move(new Vec3(blockPos)).toAabbs());
				boolean bl4 = this.collidedWithFluid(blockState.getFluidState(), blockPos, vec3, vec32);
				if ((bl3 || bl4) && longSet.add(blockPos.asLong())) {
					if (bl3) {
						try {
							boolean bl5 = bl || aABB.intersects(blockPos);
							stepBasedCollector.advanceStep(j);
							blockState.entityInside(this.level(), blockPos, this, stepBasedCollector, bl5);
							this.onInsideBlock(blockState);
						} catch (Throwable throwable) {
							CrashReport crashReport = CrashReport.forThrowable(throwable, "Colliding entity with block");
							CrashReportCategory crashReportCategory = crashReport.addCategory("Block being collided with");
							CrashReportCategory.populateBlockDetails(crashReportCategory, this.level(), blockPos, blockState);
							CrashReportCategory crashReportCategory2 = crashReport.addCategory("Entity being checked for collision");
							this.fillCrashReportCategory(crashReportCategory2);
							throw new ReportedException(crashReport);
						}
					}

					if (bl4) {
						stepBasedCollector.advanceStep(j);
						blockState.getFluidState().entityInside(this.level(), blockPos, this, stepBasedCollector);
					}

					if (bl2) {
						this.debugBlockIntersection((ServerLevel)this.level(), blockPos.immutable(), bl3, bl4);
					}

					return true;
				} else {
					return true;
				}
			}
		});
		return atomicInteger.get() + 1;
	}

	private void debugBlockIntersection(ServerLevel serverLevel, BlockPos blockPos, boolean bl, boolean bl2) {
		DebugEntityBlockIntersection debugEntityBlockIntersection;
		if (bl2) {
			debugEntityBlockIntersection = DebugEntityBlockIntersection.IN_FLUID;
		} else if (bl) {
			debugEntityBlockIntersection = DebugEntityBlockIntersection.IN_BLOCK;
		} else {
			debugEntityBlockIntersection = DebugEntityBlockIntersection.IN_AIR;
		}

		serverLevel.debugSynchronizers().sendBlockValue(blockPos, DebugSubscriptions.ENTITY_BLOCK_INTERSECTIONS, debugEntityBlockIntersection);
	}

	public boolean collidedWithFluid(FluidState fluidState, BlockPos blockPos, Vec3 vec3, Vec3 vec32) {
		AABB aABB = fluidState.getAABB(this.level(), blockPos);
		return aABB != null && this.collidedWithShapeMovingFrom(vec3, vec32, List.of(aABB));
	}

	public boolean collidedWithShapeMovingFrom(Vec3 vec3, Vec3 vec32, List<AABB> list) {
		AABB aABB = this.makeBoundingBox(vec3);
		Vec3 vec33 = vec32.subtract(vec3);
		return aABB.collidedAlongVector(vec33, list);
	}

	protected void onInsideBlock(BlockState blockState) {
	}

	public BlockPos adjustSpawnLocation(ServerLevel serverLevel, BlockPos blockPos) {
		BlockPos blockPos2 = serverLevel.getRespawnData().pos();
		Vec3 vec3 = blockPos2.getCenter();
		int i = serverLevel.getChunkAt(blockPos2).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockPos2.getX(), blockPos2.getZ()) + 1;
		return BlockPos.containing(vec3.x, i, vec3.z);
	}

	public void gameEvent(Holder<GameEvent> holder, @Nullable Entity entity) {
		this.level().gameEvent(entity, holder, this.position);
	}

	public void gameEvent(Holder<GameEvent> holder) {
		this.gameEvent(holder, this);
	}

	private void walkingStepSound(BlockPos blockPos, BlockState blockState) {
		this.playStepSound(blockPos, blockState);
		if (this.shouldPlayAmethystStepSound(blockState)) {
			this.playAmethystStepSound();
		}
	}

	protected void waterSwimSound() {
		Entity entity = Objects.requireNonNullElse(this.getControllingPassenger(), this);
		float f = entity == this ? 0.35F : 0.4F;
		Vec3 vec3 = entity.getDeltaMovement();
		float g = Math.min(1.0F, (float)Math.sqrt(vec3.x * vec3.x * 0.2F + vec3.y * vec3.y + vec3.z * vec3.z * 0.2F) * f);
		this.playSwimSound(g);
	}

	protected BlockPos getPrimaryStepSoundBlockPos(BlockPos blockPos) {
		BlockPos blockPos2 = blockPos.above();
		BlockState blockState = this.level().getBlockState(blockPos2);
		return !blockState.is(BlockTags.INSIDE_STEP_SOUND_BLOCKS) && !blockState.is(BlockTags.COMBINATION_STEP_SOUND_BLOCKS) ? blockPos : blockPos2;
	}

	protected void playCombinationStepSounds(BlockState blockState, BlockState blockState2) {
		SoundType soundType = blockState.getSoundType();
		this.playSound(soundType.getStepSound(), soundType.getVolume() * 0.15F, soundType.getPitch());
		this.playMuffledStepSound(blockState2);
	}

	protected void playMuffledStepSound(BlockState blockState) {
		SoundType soundType = blockState.getSoundType();
		this.playSound(soundType.getStepSound(), soundType.getVolume() * 0.05F, soundType.getPitch() * 0.8F);
	}

	protected void playStepSound(BlockPos blockPos, BlockState blockState) {
		SoundType soundType = blockState.getSoundType();
		this.playSound(soundType.getStepSound(), soundType.getVolume() * 0.15F, soundType.getPitch());
	}

	private boolean shouldPlayAmethystStepSound(BlockState blockState) {
		return blockState.is(BlockTags.CRYSTAL_SOUND_BLOCKS) && this.tickCount >= this.lastCrystalSoundPlayTick + 20;
	}

	private void playAmethystStepSound() {
		this.crystalSoundIntensity = this.crystalSoundIntensity * (float)Math.pow(0.997, this.tickCount - this.lastCrystalSoundPlayTick);
		this.crystalSoundIntensity = Math.min(1.0F, this.crystalSoundIntensity + 0.07F);
		float f = 0.5F + this.crystalSoundIntensity * this.random.nextFloat() * 1.2F;
		float g = 0.1F + this.crystalSoundIntensity * 1.2F;
		this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, g, f);
		this.lastCrystalSoundPlayTick = this.tickCount;
	}

	protected void playSwimSound(float f) {
		this.playSound(this.getSwimSound(), f, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
	}

	protected void onFlap() {
	}

	protected boolean isFlapping() {
		return false;
	}

	public void playSound(SoundEvent soundEvent, float f, float g) {
		if (!this.isSilent()) {
			this.level().playSound(null, this.getX(), this.getY(), this.getZ(), soundEvent, this.getSoundSource(), f, g);
		}
	}

	public void playSound(SoundEvent soundEvent) {
		if (!this.isSilent()) {
			this.playSound(soundEvent, 1.0F, 1.0F);
		}
	}

	public boolean isSilent() {
		return this.entityData.get(DATA_SILENT);
	}

	public void setSilent(boolean bl) {
		this.entityData.set(DATA_SILENT, bl);
	}

	public boolean isNoGravity() {
		return this.entityData.get(DATA_NO_GRAVITY);
	}

	public void setNoGravity(boolean bl) {
		this.entityData.set(DATA_NO_GRAVITY, bl);
	}

	protected double getDefaultGravity() {
		return 0.0;
	}

	public final double getGravity() {
		return this.isNoGravity() ? 0.0 : this.getDefaultGravity();
	}

	protected void applyGravity() {
		double d = this.getGravity();
		if (d != 0.0) {
			this.setDeltaMovement(this.getDeltaMovement().add(0.0, -d, 0.0));
		}
	}

	protected Entity.MovementEmission getMovementEmission() {
		return Entity.MovementEmission.ALL;
	}

	public boolean dampensVibrations() {
		return false;
	}

	public final void doCheckFallDamage(double d, double e, double f, boolean bl) {
		if (!this.touchingUnloadedChunk()) {
			this.checkSupportingBlock(bl, new Vec3(d, e, f));
			BlockPos blockPos = this.getOnPosLegacy();
			BlockState blockState = this.level().getBlockState(blockPos);
			this.checkFallDamage(e, bl, blockState, blockPos);
		}
	}

	protected void checkFallDamage(double d, boolean bl, BlockState blockState, BlockPos blockPos) {
		if (!this.isInWater() && d < 0.0) {
			this.fallDistance -= (float)d;
		}

		if (bl) {
			if (this.fallDistance > 0.0) {
				blockState.getBlock().fallOn(this.level(), blockState, blockPos, this, this.fallDistance);
				this.level()
					.gameEvent(
						GameEvent.HIT_GROUND,
						this.position,
						GameEvent.Context.of(this, this.mainSupportingBlockPos.<BlockState>map(blockPosx -> this.level().getBlockState(blockPosx)).orElse(blockState))
					);
			}

			this.resetFallDistance();
		}
	}

	public boolean fireImmune() {
		return this.getType().fireImmune();
	}

	public boolean causeFallDamage(double d, float f, DamageSource damageSource) {
		if (this.type.is(EntityTypeTags.FALL_DAMAGE_IMMUNE)) {
			return false;
		}

		this.propagateFallToPassengers(d, f, damageSource);
		return false;
	}

	protected void propagateFallToPassengers(double d, float f, DamageSource damageSource) {
		if (this.isVehicle()) {
			for (Entity entity : this.getPassengers()) {
				entity.causeFallDamage(d, f, damageSource);
			}
		}
	}

	public boolean isInWater() {
		return this.wasTouchingWater;
	}

	boolean isInRain() {
		BlockPos blockPos = this.blockPosition();
		return this.level().isRainingAt(blockPos) || this.level().isRainingAt(BlockPos.containing(blockPos.getX(), this.getBoundingBox().maxY, blockPos.getZ()));
	}

	public boolean isInWaterOrRain() {
		return this.isInWater() || this.isInRain();
	}

	public boolean isInLiquid() {
		return this.isInWater() || this.isInLava();
	}

	public boolean isUnderWater() {
		return this.wasEyeInWater && this.isInWater();
	}

	public boolean isInShallowWater() {
		return this.isInWater() && !this.isUnderWater();
	}

	public boolean isInClouds() {
		if (ARGB.alpha(this.level.environmentAttributes().getValue(EnvironmentAttributes.CLOUD_COLOR, this.position())) == 0) {
			return false;
		}

		float f = this.level.environmentAttributes().getValue(EnvironmentAttributes.CLOUD_HEIGHT, this.position());
		if (this.getY() + this.getBbHeight() < f) {
			return false;
		}

		float g = f + 4.0F;
		return this.getY() <= g;
	}

	public void updateSwimming() {
		if (this.isSwimming()) {
			this.setSwimming(this.isSprinting() && this.isInWater() && !this.isPassenger());
		} else {
			this.setSwimming(this.isSprinting() && this.isUnderWater() && !this.isPassenger() && this.level().getFluidState(this.blockPosition).is(FluidTags.WATER));
		}
	}

	protected boolean updateInWaterStateAndDoFluidPushing() {
		this.fluidHeight.clear();
		this.updateInWaterStateAndDoWaterCurrentPushing();
		double d = this.level.environmentAttributes().getDimensionValue(EnvironmentAttributes.FAST_LAVA) ? 0.007 : 0.0023333333333333335;
		boolean bl = this.updateFluidHeightAndDoFluidPushing(FluidTags.LAVA, d);
		return this.isInWater() || bl;
	}

	void updateInWaterStateAndDoWaterCurrentPushing() {
		if (this.getVehicle() instanceof AbstractBoat abstractBoat && !abstractBoat.isUnderWater()) {
			this.wasTouchingWater = false;
		} else if (this.updateFluidHeightAndDoFluidPushing(FluidTags.WATER, 0.014)) {
			if (!this.wasTouchingWater && !this.firstTick) {
				this.doWaterSplashEffect();
			}

			this.resetFallDistance();
			this.wasTouchingWater = true;
		} else {
			this.wasTouchingWater = false;
		}
	}

	private void updateFluidOnEyes() {
		this.wasEyeInWater = this.isEyeInFluid(FluidTags.WATER);
		this.fluidOnEyes.clear();
		double d = this.getEyeY();
		if (!(
			this.getVehicle() instanceof AbstractBoat abstractBoat
				&& !abstractBoat.isUnderWater()
				&& abstractBoat.getBoundingBox().maxY >= d
				&& abstractBoat.getBoundingBox().minY <= d
		)) {
			BlockPos blockPos = BlockPos.containing(this.getX(), d, this.getZ());
			FluidState fluidState = this.level().getFluidState(blockPos);
			double e = blockPos.getY() + fluidState.getHeight(this.level(), blockPos);
			if (e > d) {
				fluidState.getTags().forEach(this.fluidOnEyes::add);
			}
		}
	}

	protected void doWaterSplashEffect() {
		Entity entity = Objects.requireNonNullElse(this.getControllingPassenger(), this);
		float f = entity == this ? 0.2F : 0.9F;
		Vec3 vec3 = entity.getDeltaMovement();
		float g = Math.min(1.0F, (float)Math.sqrt(vec3.x * vec3.x * 0.2F + vec3.y * vec3.y + vec3.z * vec3.z * 0.2F) * f);
		if (g < 0.25F) {
			this.playSound(this.getSwimSplashSound(), g, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
		} else {
			this.playSound(this.getSwimHighSpeedSplashSound(), g, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
		}

		float h = Mth.floor(this.getY());

		for (int i = 0; i < 1.0F + this.dimensions.width() * 20.0F; i++) {
			double d = (this.random.nextDouble() * 2.0 - 1.0) * this.dimensions.width();
			double e = (this.random.nextDouble() * 2.0 - 1.0) * this.dimensions.width();
			this.level().addParticle(ParticleTypes.BUBBLE, this.getX() + d, h + 1.0F, this.getZ() + e, vec3.x, vec3.y - this.random.nextDouble() * 0.2F, vec3.z);
		}

		for (int i = 0; i < 1.0F + this.dimensions.width() * 20.0F; i++) {
			double d = (this.random.nextDouble() * 2.0 - 1.0) * this.dimensions.width();
			double e = (this.random.nextDouble() * 2.0 - 1.0) * this.dimensions.width();
			this.level().addParticle(ParticleTypes.SPLASH, this.getX() + d, h + 1.0F, this.getZ() + e, vec3.x, vec3.y, vec3.z);
		}

		this.gameEvent(GameEvent.SPLASH);
	}

	@Deprecated
	protected BlockState getBlockStateOnLegacy() {
		return this.level().getBlockState(this.getOnPosLegacy());
	}

	public BlockState getBlockStateOn() {
		return this.level().getBlockState(this.getOnPos());
	}

	public boolean canSpawnSprintParticle() {
		return this.isSprinting() && !this.isInWater() && !this.isSpectator() && !this.isCrouching() && !this.isInLava() && this.isAlive();
	}

	protected void spawnSprintParticle() {
		BlockPos blockPos = this.getOnPosLegacy();
		BlockState blockState = this.level().getBlockState(blockPos);
		if (blockState.getRenderShape() != RenderShape.INVISIBLE) {
			Vec3 vec3 = this.getDeltaMovement();
			BlockPos blockPos2 = this.blockPosition();
			double d = this.getX() + (this.random.nextDouble() - 0.5) * this.dimensions.width();
			double e = this.getZ() + (this.random.nextDouble() - 0.5) * this.dimensions.width();
			if (blockPos2.getX() != blockPos.getX()) {
				d = Mth.clamp(d, blockPos.getX(), blockPos.getX() + 1.0);
			}

			if (blockPos2.getZ() != blockPos.getZ()) {
				e = Mth.clamp(e, blockPos.getZ(), blockPos.getZ() + 1.0);
			}

			this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, blockState), d, this.getY() + 0.1, e, vec3.x * -4.0, 1.5, vec3.z * -4.0);
		}
	}

	public boolean isEyeInFluid(TagKey<Fluid> tagKey) {
		return this.fluidOnEyes.contains(tagKey);
	}

	public boolean isInLava() {
		return !this.firstTick && this.fluidHeight.getDouble(FluidTags.LAVA) > 0.0;
	}

	public void moveRelative(float f, Vec3 vec3) {
		Vec3 vec32 = getInputVector(vec3, f, this.getYRot());
		this.setDeltaMovement(this.getDeltaMovement().add(vec32));
	}

	protected static Vec3 getInputVector(Vec3 vec3, float f, float g) {
		double d = vec3.lengthSqr();
		if (d < 1.0E-7) {
			return Vec3.ZERO;
		}

		Vec3 vec32 = (d > 1.0 ? vec3.normalize() : vec3).scale(f);
		float h = Mth.sin(g * (float) (Math.PI / 180.0));
		float i = Mth.cos(g * (float) (Math.PI / 180.0));
		return new Vec3(vec32.x * i - vec32.z * h, vec32.y, vec32.z * i + vec32.x * h);
	}

	@Deprecated
	public float getLightLevelDependentMagicValue() {
		return this.level().hasChunkAt(this.getBlockX(), this.getBlockZ())
			? this.level().getLightLevelDependentMagicValue(BlockPos.containing(this.getX(), this.getEyeY(), this.getZ()))
			: 0.0F;
	}

	public void absSnapTo(double d, double e, double f, float g, float h) {
		this.absSnapTo(d, e, f);
		this.absSnapRotationTo(g, h);
	}

	public void absSnapRotationTo(float f, float g) {
		this.setYRot(f % 360.0F);
		this.setXRot(Mth.clamp(g, -90.0F, 90.0F) % 360.0F);
		this.yRotO = this.getYRot();
		this.xRotO = this.getXRot();
	}

	public void absSnapTo(double d, double e, double f) {
		double g = Mth.clamp(d, -3.0E7, 3.0E7);
		double h = Mth.clamp(f, -3.0E7, 3.0E7);
		this.xo = g;
		this.yo = e;
		this.zo = h;
		this.setPos(g, e, h);
	}

	public void snapTo(Vec3 vec3) {
		this.snapTo(vec3.x, vec3.y, vec3.z);
	}

	public void snapTo(double d, double e, double f) {
		this.snapTo(d, e, f, this.getYRot(), this.getXRot());
	}

	public void snapTo(BlockPos blockPos, float f, float g) {
		this.snapTo(blockPos.getBottomCenter(), f, g);
	}

	public void snapTo(Vec3 vec3, float f, float g) {
		this.snapTo(vec3.x, vec3.y, vec3.z, f, g);
	}

	public void snapTo(double d, double e, double f, float g, float h) {
		this.setPosRaw(d, e, f);
		this.setYRot(g);
		this.setXRot(h);
		this.setOldPosAndRot();
		this.reapplyPosition();
	}

	public final void setOldPosAndRot() {
		this.setOldPos();
		this.setOldRot();
	}

	public final void setOldPosAndRot(Vec3 vec3, float f, float g) {
		this.setOldPos(vec3);
		this.setOldRot(f, g);
	}

	protected void setOldPos() {
		this.setOldPos(this.position);
	}

	public void setOldRot() {
		this.setOldRot(this.getYRot(), this.getXRot());
	}

	private void setOldPos(Vec3 vec3) {
		this.xo = this.xOld = vec3.x;
		this.yo = this.yOld = vec3.y;
		this.zo = this.zOld = vec3.z;
	}

	private void setOldRot(float f, float g) {
		this.yRotO = f;
		this.xRotO = g;
	}

	public final Vec3 oldPosition() {
		return new Vec3(this.xOld, this.yOld, this.zOld);
	}

	public float distanceTo(Entity entity) {
		float f = (float)(this.getX() - entity.getX());
		float g = (float)(this.getY() - entity.getY());
		float h = (float)(this.getZ() - entity.getZ());
		return Mth.sqrt(f * f + g * g + h * h);
	}

	public double distanceToSqr(double d, double e, double f) {
		double g = this.getX() - d;
		double h = this.getY() - e;
		double i = this.getZ() - f;
		return g * g + h * h + i * i;
	}

	public double distanceToSqr(Entity entity) {
		return this.distanceToSqr(entity.position());
	}

	public double distanceToSqr(Vec3 vec3) {
		double d = this.getX() - vec3.x;
		double e = this.getY() - vec3.y;
		double f = this.getZ() - vec3.z;
		return d * d + e * e + f * f;
	}

	public void playerTouch(Player player) {
	}

	public void push(Entity entity) {
		if (!this.isPassengerOfSameVehicle(entity)) {
			if (!entity.noPhysics && !this.noPhysics) {
				double d = entity.getX() - this.getX();
				double e = entity.getZ() - this.getZ();
				double f = Mth.absMax(d, e);
				if (f >= 0.01F) {
					f = Math.sqrt(f);
					d /= f;
					e /= f;
					double g = 1.0 / f;
					if (g > 1.0) {
						g = 1.0;
					}

					d *= g;
					e *= g;
					d *= 0.05F;
					e *= 0.05F;
					if (!this.isVehicle() && this.isPushable()) {
						this.push(-d, 0.0, -e);
					}

					if (!entity.isVehicle() && entity.isPushable()) {
						entity.push(d, 0.0, e);
					}
				}
			}
		}
	}

	public void push(Vec3 vec3) {
		if (vec3.isFinite()) {
			this.push(vec3.x, vec3.y, vec3.z);
		}
	}

	public void push(double d, double e, double f) {
		if (Double.isFinite(d) && Double.isFinite(e) && Double.isFinite(f)) {
			this.setDeltaMovement(this.getDeltaMovement().add(d, e, f));
			this.needsSync = true;
		}
	}

	protected void markHurt() {
		this.hurtMarked = true;
	}

	@Deprecated
	public final void hurt(DamageSource damageSource, float f) {
		if (this.level instanceof ServerLevel serverLevel) {
			this.hurtServer(serverLevel, damageSource, f);
		}
	}

	@Deprecated
	public final boolean hurtOrSimulate(DamageSource damageSource, float f) {
		return this.level instanceof ServerLevel serverLevel ? this.hurtServer(serverLevel, damageSource, f) : this.hurtClient(damageSource);
	}

	public abstract boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float f);

	public boolean hurtClient(DamageSource damageSource) {
		return false;
	}

	public final Vec3 getViewVector(float f) {
		return this.calculateViewVector(this.getViewXRot(f), this.getViewYRot(f));
	}

	public Direction getNearestViewDirection() {
		return Direction.getApproximateNearest(this.getViewVector(1.0F));
	}

	public float getViewXRot(float f) {
		return this.getXRot(f);
	}

	public float getViewYRot(float f) {
		return this.getYRot(f);
	}

	public float getXRot(float f) {
		return f == 1.0F ? this.getXRot() : Mth.lerp(f, this.xRotO, this.getXRot());
	}

	public float getYRot(float f) {
		return f == 1.0F ? this.getYRot() : Mth.rotLerp(f, this.yRotO, this.getYRot());
	}

	public final Vec3 calculateViewVector(float f, float g) {
		float h = f * (float) (Math.PI / 180.0);
		float i = -g * (float) (Math.PI / 180.0);
		float j = Mth.cos(i);
		float k = Mth.sin(i);
		float l = Mth.cos(h);
		float m = Mth.sin(h);
		return new Vec3(k * l, -m, j * l);
	}

	public final Vec3 getUpVector(float f) {
		return this.calculateUpVector(this.getViewXRot(f), this.getViewYRot(f));
	}

	protected final Vec3 calculateUpVector(float f, float g) {
		return this.calculateViewVector(f - 90.0F, g);
	}

	public final Vec3 getEyePosition() {
		return new Vec3(this.getX(), this.getEyeY(), this.getZ());
	}

	public final Vec3 getEyePosition(float f) {
		double d = Mth.lerp(f, this.xo, this.getX());
		double e = Mth.lerp(f, this.yo, this.getY()) + this.getEyeHeight();
		double g = Mth.lerp(f, this.zo, this.getZ());
		return new Vec3(d, e, g);
	}

	public Vec3 getLightProbePosition(float f) {
		return this.getEyePosition(f);
	}

	public final Vec3 getPosition(float f) {
		double d = Mth.lerp(f, this.xo, this.getX());
		double e = Mth.lerp(f, this.yo, this.getY());
		double g = Mth.lerp(f, this.zo, this.getZ());
		return new Vec3(d, e, g);
	}

	public HitResult pick(double d, float f, boolean bl) {
		Vec3 vec3 = this.getEyePosition(f);
		Vec3 vec32 = this.getViewVector(f);
		Vec3 vec33 = vec3.add(vec32.x * d, vec32.y * d, vec32.z * d);
		return this.level().clip(new ClipContext(vec3, vec33, ClipContext.Block.OUTLINE, bl ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, this));
	}

	public boolean canBeHitByProjectile() {
		return this.isAlive() && this.isPickable();
	}

	public boolean isPickable() {
		return false;
	}

	public boolean isPushable() {
		return false;
	}

	public void awardKillScore(Entity entity, DamageSource damageSource) {
		if (entity instanceof ServerPlayer) {
			CriteriaTriggers.ENTITY_KILLED_PLAYER.trigger((ServerPlayer)entity, this, damageSource);
		}
	}

	public boolean shouldRender(double d, double e, double f) {
		double g = this.getX() - d;
		double h = this.getY() - e;
		double i = this.getZ() - f;
		double j = g * g + h * h + i * i;
		return this.shouldRenderAtSqrDistance(j);
	}

	public boolean shouldRenderAtSqrDistance(double d) {
		double e = this.getBoundingBox().getSize();
		if (Double.isNaN(e)) {
			e = 1.0;
		}

		e *= 64.0 * viewScale;
		return d < e * e;
	}

	public boolean saveAsPassenger(ValueOutput valueOutput) {
		if (this.removalReason != null && !this.removalReason.shouldSave()) {
			return false;
		}

		String string = this.getEncodeId();
		if (string == null) {
			return false;
		}

		valueOutput.putString("id", string);
		this.saveWithoutId(valueOutput);
		return true;
	}

	public boolean save(ValueOutput valueOutput) {
		return this.isPassenger() ? false : this.saveAsPassenger(valueOutput);
	}

	public void saveWithoutId(ValueOutput valueOutput) {
		try {
			if (this.vehicle != null) {
				valueOutput.store("Pos", Vec3.CODEC, new Vec3(this.vehicle.getX(), this.getY(), this.vehicle.getZ()));
			} else {
				valueOutput.store("Pos", Vec3.CODEC, this.position());
			}

			valueOutput.store("Motion", Vec3.CODEC, this.getDeltaMovement());
			valueOutput.store("Rotation", Vec2.CODEC, new Vec2(this.getYRot(), this.getXRot()));
			valueOutput.putDouble("fall_distance", this.fallDistance);
			valueOutput.putShort("Fire", (short)this.remainingFireTicks);
			valueOutput.putShort("Air", (short)this.getAirSupply());
			valueOutput.putBoolean("OnGround", this.onGround());
			valueOutput.putBoolean("Invulnerable", this.invulnerable);
			valueOutput.putInt("PortalCooldown", this.portalCooldown);
			valueOutput.store("UUID", UUIDUtil.CODEC, this.getUUID());
			valueOutput.storeNullable("CustomName", ComponentSerialization.CODEC, this.getCustomName());
			if (this.isCustomNameVisible()) {
				valueOutput.putBoolean("CustomNameVisible", this.isCustomNameVisible());
			}

			if (this.isSilent()) {
				valueOutput.putBoolean("Silent", this.isSilent());
			}

			if (this.isNoGravity()) {
				valueOutput.putBoolean("NoGravity", this.isNoGravity());
			}

			if (this.hasGlowingTag) {
				valueOutput.putBoolean("Glowing", true);
			}

			int i = this.getTicksFrozen();
			if (i > 0) {
				valueOutput.putInt("TicksFrozen", this.getTicksFrozen());
			}

			if (this.hasVisualFire) {
				valueOutput.putBoolean("HasVisualFire", this.hasVisualFire);
			}

			if (!this.tags.isEmpty()) {
				valueOutput.store("Tags", TAG_LIST_CODEC, List.copyOf(this.tags));
			}

			if (!this.customData.isEmpty()) {
				valueOutput.store("data", CustomData.CODEC, this.customData);
			}

			this.addAdditionalSaveData(valueOutput);
			if (this.isVehicle()) {
				ValueOutput.ValueOutputList valueOutputList = valueOutput.childrenList("Passengers");

				for (Entity entity : this.getPassengers()) {
					ValueOutput valueOutput2 = valueOutputList.addChild();
					if (!entity.saveAsPassenger(valueOutput2)) {
						valueOutputList.discardLast();
					}
				}

				if (valueOutputList.isEmpty()) {
					valueOutput.discard("Passengers");
				}
			}
		} catch (Throwable throwable) {
			CrashReport crashReport = CrashReport.forThrowable(throwable, "Saving entity NBT");
			CrashReportCategory crashReportCategory = crashReport.addCategory("Entity being saved");
			this.fillCrashReportCategory(crashReportCategory);
			throw new ReportedException(crashReport);
		}
	}

	public void load(ValueInput valueInput) {
		try {
			Vec3 vec3 = valueInput.read("Pos", Vec3.CODEC).orElse(Vec3.ZERO);
			Vec3 vec32 = valueInput.read("Motion", Vec3.CODEC).orElse(Vec3.ZERO);
			Vec2 vec2 = valueInput.read("Rotation", Vec2.CODEC).orElse(Vec2.ZERO);
			this.setDeltaMovement(Math.abs(vec32.x) > 10.0 ? 0.0 : vec32.x, Math.abs(vec32.y) > 10.0 ? 0.0 : vec32.y, Math.abs(vec32.z) > 10.0 ? 0.0 : vec32.z);
			this.needsSync = true;
			double d = 3.0000512E7;
			this.setPosRaw(Mth.clamp(vec3.x, -3.0000512E7, 3.0000512E7), Mth.clamp(vec3.y, -2.0E7, 2.0E7), Mth.clamp(vec3.z, -3.0000512E7, 3.0000512E7));
			this.setYRot(vec2.x);
			this.setXRot(vec2.y);
			this.setOldPosAndRot();
			this.setYHeadRot(this.getYRot());
			this.setYBodyRot(this.getYRot());
			this.fallDistance = valueInput.getDoubleOr("fall_distance", 0.0);
			this.remainingFireTicks = valueInput.getShortOr("Fire", (short)0);
			this.setAirSupply(valueInput.getIntOr("Air", this.getMaxAirSupply()));
			this.onGround = valueInput.getBooleanOr("OnGround", false);
			this.invulnerable = valueInput.getBooleanOr("Invulnerable", false);
			this.portalCooldown = valueInput.getIntOr("PortalCooldown", 0);
			valueInput.read("UUID", UUIDUtil.CODEC).ifPresent(uUID -> {
				this.uuid = uUID;
				this.stringUUID = this.uuid.toString();
			});
			if (!Double.isFinite(this.getX()) || !Double.isFinite(this.getY()) || !Double.isFinite(this.getZ())) {
				throw new IllegalStateException("Entity has invalid position");
			}

			if (Double.isFinite(this.getYRot()) && Double.isFinite(this.getXRot())) {
				this.reapplyPosition();
				this.setRot(this.getYRot(), this.getXRot());
				this.setCustomName(valueInput.read("CustomName", ComponentSerialization.CODEC).orElse(null));
				this.setCustomNameVisible(valueInput.getBooleanOr("CustomNameVisible", false));
				this.setSilent(valueInput.getBooleanOr("Silent", false));
				this.setNoGravity(valueInput.getBooleanOr("NoGravity", false));
				this.setGlowingTag(valueInput.getBooleanOr("Glowing", false));
				this.setTicksFrozen(valueInput.getIntOr("TicksFrozen", 0));
				this.hasVisualFire = valueInput.getBooleanOr("HasVisualFire", false);
				this.customData = valueInput.read("data", CustomData.CODEC).orElse(CustomData.EMPTY);
				this.tags.clear();
				valueInput.read("Tags", TAG_LIST_CODEC).ifPresent(this.tags::addAll);
				this.readAdditionalSaveData(valueInput);
				if (this.repositionEntityAfterLoad()) {
					this.reapplyPosition();
				}
			} else {
				throw new IllegalStateException("Entity has invalid rotation");
			}
		} catch (Throwable throwable) {
			CrashReport crashReport = CrashReport.forThrowable(throwable, "Loading entity NBT");
			CrashReportCategory crashReportCategory = crashReport.addCategory("Entity being loaded");
			this.fillCrashReportCategory(crashReportCategory);
			throw new ReportedException(crashReport);
		}
	}

	protected boolean repositionEntityAfterLoad() {
		return true;
	}

	protected final @Nullable String getEncodeId() {
		EntityType<?> entityType = this.getType();
		Identifier identifier = EntityType.getKey(entityType);
		return !entityType.canSerialize() ? null : identifier.toString();
	}

	protected abstract void readAdditionalSaveData(ValueInput valueInput);

	protected abstract void addAdditionalSaveData(ValueOutput valueOutput);

	public @Nullable ItemEntity spawnAtLocation(ServerLevel serverLevel, ItemLike itemLike) {
		return this.spawnAtLocation(serverLevel, new ItemStack(itemLike), 0.0F);
	}

	public @Nullable ItemEntity spawnAtLocation(ServerLevel serverLevel, ItemStack itemStack) {
		return this.spawnAtLocation(serverLevel, itemStack, 0.0F);
	}

	public @Nullable ItemEntity spawnAtLocation(ServerLevel serverLevel, ItemStack itemStack, Vec3 vec3) {
		if (itemStack.isEmpty()) {
			return null;
		}

		ItemEntity itemEntity = new ItemEntity(serverLevel, this.getX() + vec3.x, this.getY() + vec3.y, this.getZ() + vec3.z, itemStack);
		itemEntity.setDefaultPickUpDelay();
		serverLevel.addFreshEntity(itemEntity);
		return itemEntity;
	}

	public @Nullable ItemEntity spawnAtLocation(ServerLevel serverLevel, ItemStack itemStack, float f) {
		return this.spawnAtLocation(serverLevel, itemStack, new Vec3(0.0, f, 0.0));
	}

	public boolean isAlive() {
		return !this.isRemoved();
	}

	public boolean isInWall() {
		if (this.noPhysics) {
			return false;
		}

		float f = this.dimensions.width() * 0.8F;
		AABB aABB = AABB.ofSize(this.getEyePosition(), f, 1.0E-6, f);
		return BlockPos.betweenClosedStream(aABB)
			.anyMatch(
				blockPos -> {
					BlockState blockState = this.level().getBlockState(blockPos);
					return !blockState.isAir()
						&& blockState.isSuffocating(this.level(), blockPos)
						&& Shapes.joinIsNotEmpty(blockState.getCollisionShape(this.level(), blockPos).move(blockPos), Shapes.create(aABB), BooleanOp.AND);
				}
			);
	}

	public InteractionResult interact(Player player, InteractionHand interactionHand) {
		if (!this.level().isClientSide()
			&& player.isSecondaryUseActive()
			&& this instanceof Leashable leashable
			&& leashable.canBeLeashed()
			&& this.isAlive()
			&& !(this instanceof LivingEntity livingEntity && livingEntity.isBaby())) {
			List<Leashable> list = Leashable.leashableInArea(this, leashablex -> leashablex.getLeashHolder() == player);
			if (!list.isEmpty()) {
				boolean bl = false;

				for (Leashable leashable2 : list) {
					if (leashable2.canHaveALeashAttachedTo(this)) {
						leashable2.setLeashedTo(this, true);
						bl = true;
					}
				}

				if (bl) {
					this.level().gameEvent(GameEvent.ENTITY_ACTION, this.blockPosition(), GameEvent.Context.of(player));
					this.playSound(SoundEvents.LEAD_TIED);
					return InteractionResult.SUCCESS_SERVER.withoutItem();
				}
			}
		}

		ItemStack itemStack = player.getItemInHand(interactionHand);
		if (itemStack.is(Items.SHEARS) && this.shearOffAllLeashConnections(player)) {
			itemStack.hurtAndBreak(1, player, interactionHand);
			return InteractionResult.SUCCESS;
		} else if (this instanceof Mob mob
			&& itemStack.is(Items.SHEARS)
			&& mob.canShearEquipment(player)
			&& !player.isSecondaryUseActive()
			&& this.attemptToShearEquipment(player, interactionHand, itemStack, mob)) {
			return InteractionResult.SUCCESS;
		} else {
			if (this.isAlive() && this instanceof Leashable leashable3) {
				if (leashable3.getLeashHolder() == player) {
					if (!this.level().isClientSide()) {
						if (player.hasInfiniteMaterials()) {
							leashable3.removeLeash();
						} else {
							leashable3.dropLeash();
						}

						this.gameEvent(GameEvent.ENTITY_INTERACT, player);
						this.playSound(SoundEvents.LEAD_UNTIED);
					}

					return InteractionResult.SUCCESS.withoutItem();
				}

				ItemStack itemStack2 = player.getItemInHand(interactionHand);
				if (itemStack2.is(Items.LEAD) && !(leashable3.getLeashHolder() instanceof Player)) {
					if (this.level().isClientSide()) {
						return InteractionResult.CONSUME;
					}

					if (leashable3.canHaveALeashAttachedTo(player)) {
						if (leashable3.isLeashed()) {
							leashable3.dropLeash();
						}

						leashable3.setLeashedTo(player, true);
						this.playSound(SoundEvents.LEAD_TIED);
						itemStack2.shrink(1);
						return InteractionResult.SUCCESS_SERVER;
					}
				}
			}

			return InteractionResult.PASS;
		}
	}

	public boolean shearOffAllLeashConnections(@Nullable Player player) {
		boolean bl = this.dropAllLeashConnections(player);
		if (bl && this.level() instanceof ServerLevel serverLevel) {
			serverLevel.playSound(null, this.blockPosition(), SoundEvents.SHEARS_SNIP, player != null ? player.getSoundSource() : this.getSoundSource());
		}

		return bl;
	}

	public boolean dropAllLeashConnections(@Nullable Player player) {
		List<Leashable> list = Leashable.leashableLeashedTo(this);
		boolean bl = !list.isEmpty();
		if (this instanceof Leashable leashable && leashable.isLeashed()) {
			leashable.dropLeash();
			bl = true;
		}

		for (Leashable leashable2 : list) {
			leashable2.dropLeash();
		}

		if (bl) {
			this.gameEvent(GameEvent.SHEAR, player);
			return true;
		} else {
			return false;
		}
	}

	private boolean attemptToShearEquipment(Player player, InteractionHand interactionHand, ItemStack itemStack, Mob mob) {
		for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
			ItemStack itemStack2 = mob.getItemBySlot(equipmentSlot);
			Equippable equippable = itemStack2.get(DataComponents.EQUIPPABLE);
			if (equippable != null
				&& equippable.canBeSheared()
				&& (!EnchantmentHelper.has(itemStack2, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE) || player.isCreative())) {
				itemStack.hurtAndBreak(1, player, interactionHand.asEquipmentSlot());
				Vec3 vec3 = this.dimensions.attachments().getAverage(EntityAttachment.PASSENGER);
				mob.setItemSlotAndDropWhenKilled(equipmentSlot, ItemStack.EMPTY);
				this.gameEvent(GameEvent.SHEAR, player);
				this.playSound(equippable.shearingSound().value());
				if (this.level() instanceof ServerLevel serverLevel) {
					this.spawnAtLocation(serverLevel, itemStack2, vec3);
					CriteriaTriggers.PLAYER_SHEARED_EQUIPMENT.trigger((ServerPlayer)player, itemStack2, mob);
				}

				return true;
			}
		}

		return false;
	}

	public boolean canCollideWith(Entity entity) {
		return entity.canBeCollidedWith(this) && !this.isPassengerOfSameVehicle(entity);
	}

	public boolean canBeCollidedWith(@Nullable Entity entity) {
		return false;
	}

	public void rideTick() {
		this.setDeltaMovement(Vec3.ZERO);
		this.tick();
		if (this.isPassenger()) {
			this.getVehicle().positionRider(this);
		}
	}

	public final void positionRider(Entity entity) {
		if (this.hasPassenger(entity)) {
			this.positionRider(entity, Entity::setPos);
		}
	}

	protected void positionRider(Entity entity, Entity.MoveFunction moveFunction) {
		Vec3 vec3 = this.getPassengerRidingPosition(entity);
		Vec3 vec32 = entity.getVehicleAttachmentPoint(this);
		moveFunction.accept(entity, vec3.x - vec32.x, vec3.y - vec32.y, vec3.z - vec32.z);
	}

	public void onPassengerTurned(Entity entity) {
	}

	public Vec3 getVehicleAttachmentPoint(Entity entity) {
		return this.getAttachments().get(EntityAttachment.VEHICLE, 0, this.yRot);
	}

	public Vec3 getPassengerRidingPosition(Entity entity) {
		return this.position().add(this.getPassengerAttachmentPoint(entity, this.dimensions, 1.0F));
	}

	protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions entityDimensions, float f) {
		return getDefaultPassengerAttachmentPoint(this, entity, entityDimensions.attachments());
	}

	protected static Vec3 getDefaultPassengerAttachmentPoint(Entity entity, Entity entity2, EntityAttachments entityAttachments) {
		int i = entity.getPassengers().indexOf(entity2);
		return entityAttachments.getClamped(EntityAttachment.PASSENGER, i, entity.yRot);
	}

	public final boolean startRiding(Entity entity) {
		return this.startRiding(entity, false, true);
	}

	public boolean showVehicleHealth() {
		return this instanceof LivingEntity;
	}

	public boolean startRiding(Entity entity, boolean bl, boolean bl2) {
		if (entity == this.vehicle) {
			return false;
		}

		if (!entity.couldAcceptPassenger()) {
			return false;
		}

		if (!this.level().isClientSide() && !entity.type.canSerialize()) {
			return false;
		}

		for (Entity entity2 = entity; entity2.vehicle != null; entity2 = entity2.vehicle) {
			if (entity2.vehicle == this) {
				return false;
			}
		}

		if (bl || this.canRide(entity) && entity.canAddPassenger(this)) {
			if (this.isPassenger()) {
				this.stopRiding();
			}

			this.setPose(Pose.STANDING);
			this.vehicle = entity;
			this.vehicle.addPassenger(this);
			if (bl2) {
				this.level().gameEvent(this, GameEvent.ENTITY_MOUNT, this.vehicle.position);
				entity.getIndirectPassengersStream()
					.filter(entityx -> entityx instanceof ServerPlayer)
					.forEach(entityx -> CriteriaTriggers.START_RIDING_TRIGGER.trigger((ServerPlayer)entityx));
			}

			return true;
		} else {
			return false;
		}
	}

	protected boolean canRide(Entity entity) {
		return !this.isShiftKeyDown() && this.boardingCooldown <= 0;
	}

	public void ejectPassengers() {
		for (int i = this.passengers.size() - 1; i >= 0; i--) {
			this.passengers.get(i).stopRiding();
		}
	}

	public void removeVehicle() {
		if (this.vehicle != null) {
			Entity entity = this.vehicle;
			this.vehicle = null;
			entity.removePassenger(this);
			Entity.RemovalReason removalReason = this.getRemovalReason();
			if (removalReason == null || removalReason.shouldDestroy()) {
				this.level().gameEvent(this, GameEvent.ENTITY_DISMOUNT, entity.position);
			}
		}
	}

	public void stopRiding() {
		this.removeVehicle();
	}

	protected void addPassenger(Entity entity) {
		if (entity.getVehicle() != this) {
			throw new IllegalStateException("Use x.startRiding(y), not y.addPassenger(x)");
		}

		if (this.passengers.isEmpty()) {
			this.passengers = ImmutableList.of(entity);
		} else {
			List<Entity> list = Lists.newArrayList(this.passengers);
			if (!this.level().isClientSide() && entity instanceof Player && !(this.getFirstPassenger() instanceof Player)) {
				list.add(0, entity);
			} else {
				list.add(entity);
			}

			this.passengers = ImmutableList.copyOf(list);
		}
	}

	protected void removePassenger(Entity entity) {
		if (entity.getVehicle() == this) {
			throw new IllegalStateException("Use x.stopRiding(y), not y.removePassenger(x)");
		}

		if (this.passengers.size() == 1 && this.passengers.get(0) == entity) {
			this.passengers = ImmutableList.of();
		} else {
			this.passengers = this.passengers.stream().filter(entity2 -> entity2 != entity).collect(ImmutableList.toImmutableList());
		}

		entity.boardingCooldown = 60;
	}

	protected boolean canAddPassenger(Entity entity) {
		return this.passengers.isEmpty();
	}

	protected boolean couldAcceptPassenger() {
		return true;
	}

	public final boolean isInterpolating() {
		return this.getInterpolation() != null && this.getInterpolation().hasActiveInterpolation();
	}

	public final void moveOrInterpolateTo(Vec3 vec3, float f, float g) {
		this.moveOrInterpolateTo(Optional.of(vec3), Optional.of(f), Optional.of(g));
	}

	public final void moveOrInterpolateTo(float f, float g) {
		this.moveOrInterpolateTo(Optional.empty(), Optional.of(f), Optional.of(g));
	}

	public final void moveOrInterpolateTo(Vec3 vec3) {
		this.moveOrInterpolateTo(Optional.of(vec3), Optional.empty(), Optional.empty());
	}

	public final void moveOrInterpolateTo(Optional<Vec3> optional, Optional<Float> optional2, Optional<Float> optional3) {
		InterpolationHandler interpolationHandler = this.getInterpolation();
		if (interpolationHandler != null) {
			interpolationHandler.interpolateTo(
				optional.orElse(interpolationHandler.position()), optional2.orElse(interpolationHandler.yRot()), optional3.orElse(interpolationHandler.xRot())
			);
		} else {
			optional.ifPresent(this::setPos);
			optional2.ifPresent(float_ -> this.setYRot(float_ % 360.0F));
			optional3.ifPresent(float_ -> this.setXRot(float_ % 360.0F));
		}
	}

	public @Nullable InterpolationHandler getInterpolation() {
		return null;
	}

	public void lerpHeadTo(float f, int i) {
		this.setYHeadRot(f);
	}

	public float getPickRadius() {
		return 0.0F;
	}

	public Vec3 getLookAngle() {
		return this.calculateViewVector(this.getXRot(), this.getYRot());
	}

	public Vec3 getHeadLookAngle() {
		return this.calculateViewVector(this.getXRot(), this.getYHeadRot());
	}

	public Vec3 getHandHoldingItemAngle(Item item) {
		if (!(this instanceof Player player)) {
			return Vec3.ZERO;
		} else {
			boolean bl = player.getOffhandItem().is(item) && !player.getMainHandItem().is(item);
			HumanoidArm humanoidArm = bl ? player.getMainArm().getOpposite() : player.getMainArm();
			return this.calculateViewVector(0.0F, this.getYRot() + (humanoidArm == HumanoidArm.RIGHT ? 80 : -80)).scale(0.5);
		}
	}

	public Vec2 getRotationVector() {
		return new Vec2(this.getXRot(), this.getYRot());
	}

	public Vec3 getForward() {
		return Vec3.directionFromRotation(this.getRotationVector());
	}

	public void setAsInsidePortal(Portal portal, BlockPos blockPos) {
		if (this.isOnPortalCooldown()) {
			this.setPortalCooldown();
		} else {
			if (this.portalProcess == null || !this.portalProcess.isSamePortal(portal)) {
				this.portalProcess = new PortalProcessor(portal, blockPos.immutable());
			} else if (!this.portalProcess.isInsidePortalThisTick()) {
				this.portalProcess.updateEntryPosition(blockPos.immutable());
				this.portalProcess.setAsInsidePortalThisTick(true);
			}
		}
	}

	protected void handlePortal() {
		if (this.level() instanceof ServerLevel serverLevel) {
			this.processPortalCooldown();
			if (this.portalProcess != null) {
				if (this.portalProcess.processPortalTeleportation(serverLevel, this, this.canUsePortal(false))) {
					ProfilerFiller profilerFiller = Profiler.get();
					profilerFiller.push("portal");
					this.setPortalCooldown();
					TeleportTransition teleportTransition = this.portalProcess.getPortalDestination(serverLevel, this);
					if (teleportTransition != null) {
						ServerLevel serverLevel2 = teleportTransition.newLevel();
						if (serverLevel.isAllowedToEnterPortal(serverLevel2)
							&& (serverLevel2.dimension() == serverLevel.dimension() || this.canTeleport(serverLevel, serverLevel2))) {
							this.teleport(teleportTransition);
						}
					}

					profilerFiller.pop();
				} else if (this.portalProcess.hasExpired()) {
					this.portalProcess = null;
				}
			}
		}
	}

	public int getDimensionChangingDelay() {
		Entity entity = this.getFirstPassenger();
		return entity instanceof ServerPlayer ? entity.getDimensionChangingDelay() : 300;
	}

	public void lerpMotion(Vec3 vec3) {
		this.setDeltaMovement(vec3);
	}

	public void handleDamageEvent(DamageSource damageSource) {
	}

	public void handleEntityEvent(byte b) {
		switch (b) {
			case 53:
				HoneyBlock.showSlideParticles(this);
		}
	}

	public void animateHurt(float f) {
	}

	public boolean isOnFire() {
		boolean bl = this.level() != null && this.level().isClientSide();
		return !this.fireImmune() && (this.remainingFireTicks > 0 || bl && this.getSharedFlag(0));
	}

	public boolean isPassenger() {
		return this.getVehicle() != null;
	}

	public boolean isVehicle() {
		return !this.passengers.isEmpty();
	}

	public boolean dismountsUnderwater() {
		return this.getType().is(EntityTypeTags.DISMOUNTS_UNDERWATER);
	}

	public boolean canControlVehicle() {
		return !this.getType().is(EntityTypeTags.NON_CONTROLLING_RIDER);
	}

	public void setShiftKeyDown(boolean bl) {
		this.setSharedFlag(1, bl);
	}

	public boolean isShiftKeyDown() {
		return this.getSharedFlag(1);
	}

	public boolean isSteppingCarefully() {
		return this.isShiftKeyDown();
	}

	public boolean isSuppressingBounce() {
		return this.isShiftKeyDown();
	}

	public boolean isDiscrete() {
		return this.isShiftKeyDown();
	}

	public boolean isDescending() {
		return this.isShiftKeyDown();
	}

	public boolean isCrouching() {
		return this.hasPose(Pose.CROUCHING);
	}

	public boolean isSprinting() {
		return this.getSharedFlag(3);
	}

	public void setSprinting(boolean bl) {
		this.setSharedFlag(3, bl);
	}

	public boolean isSwimming() {
		return this.getSharedFlag(4);
	}

	public boolean isVisuallySwimming() {
		return this.hasPose(Pose.SWIMMING);
	}

	public boolean isVisuallyCrawling() {
		return this.isVisuallySwimming() && !this.isInWater();
	}

	public void setSwimming(boolean bl) {
		this.setSharedFlag(4, bl);
	}

	public final boolean hasGlowingTag() {
		return this.hasGlowingTag;
	}

	public final void setGlowingTag(boolean bl) {
		this.hasGlowingTag = bl;
		this.setSharedFlag(6, this.isCurrentlyGlowing());
	}

	public boolean isCurrentlyGlowing() {
		return this.level().isClientSide() ? this.getSharedFlag(6) : this.hasGlowingTag;
	}

	public boolean isInvisible() {
		return this.getSharedFlag(5);
	}

	public boolean isInvisibleTo(Player player) {
		if (player.isSpectator()) {
			return false;
		}

		Team team = this.getTeam();
		return team != null && player != null && player.getTeam() == team && team.canSeeFriendlyInvisibles() ? false : this.isInvisible();
	}

	public boolean isOnRails() {
		return false;
	}

	public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> biConsumer) {
	}

	public @Nullable PlayerTeam getTeam() {
		return this.level().getScoreboard().getPlayersTeam(this.getScoreboardName());
	}

	public final boolean isAlliedTo(@Nullable Entity entity) {
		return entity == null ? false : this == entity || this.considersEntityAsAlly(entity) || entity.considersEntityAsAlly(this);
	}

	protected boolean considersEntityAsAlly(Entity entity) {
		return this.isAlliedTo(entity.getTeam());
	}

	public boolean isAlliedTo(@Nullable Team team) {
		return this.getTeam() != null ? this.getTeam().isAlliedTo(team) : false;
	}

	public void setInvisible(boolean bl) {
		this.setSharedFlag(5, bl);
	}

	protected boolean getSharedFlag(int i) {
		return (this.entityData.get(DATA_SHARED_FLAGS_ID) & 1 << i) != 0;
	}

	protected void setSharedFlag(int i, boolean bl) {
		byte b = this.entityData.get(DATA_SHARED_FLAGS_ID);
		if (bl) {
			this.entityData.set(DATA_SHARED_FLAGS_ID, (byte)(b | 1 << i));
		} else {
			this.entityData.set(DATA_SHARED_FLAGS_ID, (byte)(b & ~(1 << i)));
		}
	}

	public int getMaxAirSupply() {
		return 300;
	}

	public int getAirSupply() {
		return this.entityData.get(DATA_AIR_SUPPLY_ID);
	}

	public void setAirSupply(int i) {
		this.entityData.set(DATA_AIR_SUPPLY_ID, i);
	}

	public void clearFreeze() {
		this.setTicksFrozen(0);
	}

	public int getTicksFrozen() {
		return this.entityData.get(DATA_TICKS_FROZEN);
	}

	public void setTicksFrozen(int i) {
		this.entityData.set(DATA_TICKS_FROZEN, i);
	}

	public float getPercentFrozen() {
		int i = this.getTicksRequiredToFreeze();
		return (float)Math.min(this.getTicksFrozen(), i) / i;
	}

	public boolean isFullyFrozen() {
		return this.getTicksFrozen() >= this.getTicksRequiredToFreeze();
	}

	public int getTicksRequiredToFreeze() {
		return 140;
	}

	public void thunderHit(ServerLevel serverLevel, LightningBolt lightningBolt) {
		this.setRemainingFireTicks(this.remainingFireTicks + 1);
		if (this.remainingFireTicks == 0) {
			this.igniteForSeconds(8.0F);
		}

		this.hurtServer(serverLevel, this.damageSources().lightningBolt(), 5.0F);
	}

	public void onAboveBubbleColumn(boolean bl, BlockPos blockPos) {
		handleOnAboveBubbleColumn(this, bl, blockPos);
	}

	protected static void handleOnAboveBubbleColumn(Entity entity, boolean bl, BlockPos blockPos) {
		Vec3 vec3 = entity.getDeltaMovement();
		double d;
		if (bl) {
			d = Math.max(-0.9, vec3.y - 0.03);
		} else {
			d = Math.min(1.8, vec3.y + 0.1);
		}

		entity.setDeltaMovement(vec3.x, d, vec3.z);
		sendBubbleColumnParticles(entity.level, blockPos);
	}

	protected static void sendBubbleColumnParticles(Level level, BlockPos blockPos) {
		if (level instanceof ServerLevel serverLevel) {
			for (int i = 0; i < 2; i++) {
				serverLevel.sendParticles(
					ParticleTypes.SPLASH, blockPos.getX() + level.random.nextDouble(), blockPos.getY() + 1, blockPos.getZ() + level.random.nextDouble(), 1, 0.0, 0.0, 0.0, 1.0
				);
				serverLevel.sendParticles(
					ParticleTypes.BUBBLE,
					blockPos.getX() + level.random.nextDouble(),
					blockPos.getY() + 1,
					blockPos.getZ() + level.random.nextDouble(),
					1,
					0.0,
					0.01,
					0.0,
					0.2
				);
			}
		}
	}

	public void onInsideBubbleColumn(boolean bl) {
		handleOnInsideBubbleColumn(this, bl);
	}

	protected static void handleOnInsideBubbleColumn(Entity entity, boolean bl) {
		Vec3 vec3 = entity.getDeltaMovement();
		double d;
		if (bl) {
			d = Math.max(-0.3, vec3.y - 0.03);
		} else {
			d = Math.min(0.7, vec3.y + 0.06);
		}

		entity.setDeltaMovement(vec3.x, d, vec3.z);
		entity.resetFallDistance();
	}

	public boolean killedEntity(ServerLevel serverLevel, LivingEntity livingEntity, DamageSource damageSource) {
		return true;
	}

	public void checkFallDistanceAccumulation() {
		if (this.getDeltaMovement().y() > -0.5 && this.fallDistance > 1.0) {
			this.fallDistance = 1.0;
		}
	}

	public void resetFallDistance() {
		this.fallDistance = 0.0;
	}

	protected void moveTowardsClosestSpace(double d, double e, double f) {
		BlockPos blockPos = BlockPos.containing(d, e, f);
		Vec3 vec3 = new Vec3(d - blockPos.getX(), e - blockPos.getY(), f - blockPos.getZ());
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
		Direction direction = Direction.UP;
		double g = Double.MAX_VALUE;

		for (Direction direction2 : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP}) {
			mutableBlockPos.setWithOffset(blockPos, direction2);
			if (!this.level().getBlockState(mutableBlockPos).isCollisionShapeFullBlock(this.level(), mutableBlockPos)) {
				double h = vec3.get(direction2.getAxis());
				double i = direction2.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 - h : h;
				if (i < g) {
					g = i;
					direction = direction2;
				}
			}
		}

		float j = this.random.nextFloat() * 0.2F + 0.1F;
		float k = direction.getAxisDirection().getStep();
		Vec3 vec32 = this.getDeltaMovement().scale(0.75);
		if (direction.getAxis() == Direction.Axis.X) {
			this.setDeltaMovement(k * j, vec32.y, vec32.z);
		} else if (direction.getAxis() == Direction.Axis.Y) {
			this.setDeltaMovement(vec32.x, k * j, vec32.z);
		} else if (direction.getAxis() == Direction.Axis.Z) {
			this.setDeltaMovement(vec32.x, vec32.y, k * j);
		}
	}

	public void makeStuckInBlock(BlockState blockState, Vec3 vec3) {
		this.resetFallDistance();
		this.stuckSpeedMultiplier = vec3;
	}

	private static Component removeAction(Component component) {
		MutableComponent mutableComponent = component.plainCopy().setStyle(component.getStyle().withClickEvent(null));

		for (Component component2 : component.getSiblings()) {
			mutableComponent.append(removeAction(component2));
		}

		return mutableComponent;
	}

	@Override
	public Component getName() {
		Component component = this.getCustomName();
		return component != null ? removeAction(component) : this.getTypeName();
	}

	protected Component getTypeName() {
		return this.type.getDescription();
	}

	public boolean is(Entity entity) {
		return this == entity;
	}

	public float getYHeadRot() {
		return 0.0F;
	}

	public void setYHeadRot(float f) {
	}

	public void setYBodyRot(float f) {
	}

	public boolean isAttackable() {
		return true;
	}

	public boolean skipAttackInteraction(Entity entity) {
		return false;
	}

	@Override
	public String toString() {
		String string = this.level() == null ? "~NULL~" : this.level().toString();
		return this.removalReason != null
			? String.format(
				Locale.ROOT,
				"%s['%s'/%d, l='%s', x=%.2f, y=%.2f, z=%.2f, removed=%s]",
				this.getClass().getSimpleName(),
				this.getPlainTextName(),
				this.id,
				string,
				this.getX(),
				this.getY(),
				this.getZ(),
				this.removalReason
			)
			: String.format(
				Locale.ROOT,
				"%s['%s'/%d, l='%s', x=%.2f, y=%.2f, z=%.2f]",
				this.getClass().getSimpleName(),
				this.getPlainTextName(),
				this.id,
				string,
				this.getX(),
				this.getY(),
				this.getZ()
			);
	}

	protected final boolean isInvulnerableToBase(DamageSource damageSource) {
		return this.isRemoved()
			|| this.invulnerable && !damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !damageSource.isCreativePlayer()
			|| damageSource.is(DamageTypeTags.IS_FIRE) && this.fireImmune()
			|| damageSource.is(DamageTypeTags.IS_FALL) && this.getType().is(EntityTypeTags.FALL_DAMAGE_IMMUNE);
	}

	public boolean isInvulnerable() {
		return this.invulnerable;
	}

	public void setInvulnerable(boolean bl) {
		this.invulnerable = bl;
	}

	public void copyPosition(Entity entity) {
		this.snapTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
	}

	public void restoreFrom(Entity entity) {
		try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(this.problemPath(), LOGGER)) {
			TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, entity.registryAccess());
			entity.saveWithoutId(tagValueOutput);
			this.load(TagValueInput.create(scopedCollector, this.registryAccess(), tagValueOutput.buildResult()));
		}

		this.portalCooldown = entity.portalCooldown;
		this.portalProcess = entity.portalProcess;
	}

	public @Nullable Entity teleport(TeleportTransition teleportTransition) {
		if (this.level() instanceof ServerLevel serverLevel && !this.isRemoved()) {
			ServerLevel serverLevel2 = teleportTransition.newLevel();
			boolean bl = serverLevel2.dimension() != serverLevel.dimension();
			if (!teleportTransition.asPassenger()) {
				this.stopRiding();
			}

			return bl ? this.teleportCrossDimension(serverLevel, serverLevel2, teleportTransition) : this.teleportSameDimension(serverLevel, teleportTransition);
		} else {
			return null;
		}
	}

	private Entity teleportSameDimension(ServerLevel serverLevel, TeleportTransition teleportTransition) {
		for (Entity entity : this.getPassengers()) {
			entity.teleport(this.calculatePassengerTransition(teleportTransition, entity));
		}

		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push("teleportSameDimension");
		this.teleportSetPosition(PositionMoveRotation.of(teleportTransition), teleportTransition.relatives());
		if (!teleportTransition.asPassenger()) {
			this.sendTeleportTransitionToRidingPlayers(teleportTransition);
		}

		teleportTransition.postTeleportTransition().onTransition(this);
		profilerFiller.pop();
		return this;
	}

	private @Nullable Entity teleportCrossDimension(ServerLevel serverLevel, ServerLevel serverLevel2, TeleportTransition teleportTransition) {
		List<Entity> list = this.getPassengers();
		List<Entity> list2 = new ArrayList<>(list.size());
		this.ejectPassengers();

		for (Entity entity : list) {
			Entity entity2 = entity.teleport(this.calculatePassengerTransition(teleportTransition, entity));
			if (entity2 != null) {
				list2.add(entity2);
			}
		}

		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push("teleportCrossDimension");
		Entity entity = this.getType().create(serverLevel2, EntitySpawnReason.DIMENSION_TRAVEL);
		if (entity == null) {
			profilerFiller.pop();
			return null;
		}

		entity.restoreFrom(this);
		this.removeAfterChangingDimensions();
		entity.teleportSetPosition(PositionMoveRotation.of(this), PositionMoveRotation.of(teleportTransition), teleportTransition.relatives());
		serverLevel2.addDuringTeleport(entity);

		for (Entity entity3 : list2) {
			entity3.startRiding(entity, true, false);
		}

		serverLevel2.resetEmptyTime();
		teleportTransition.postTeleportTransition().onTransition(entity);
		this.teleportSpectators(teleportTransition, serverLevel);
		profilerFiller.pop();
		return entity;
	}

	protected void teleportSpectators(TeleportTransition teleportTransition, ServerLevel serverLevel) {
		for (ServerPlayer serverPlayer : List.copyOf(serverLevel.players())) {
			if (serverPlayer.getCamera() == this) {
				serverPlayer.teleport(teleportTransition);
				serverPlayer.setCamera(null);
			}
		}
	}

	private TeleportTransition calculatePassengerTransition(TeleportTransition teleportTransition, Entity entity) {
		float f = teleportTransition.yRot() + (teleportTransition.relatives().contains(Relative.Y_ROT) ? 0.0F : entity.getYRot() - this.getYRot());
		float g = teleportTransition.xRot() + (teleportTransition.relatives().contains(Relative.X_ROT) ? 0.0F : entity.getXRot() - this.getXRot());
		Vec3 vec3 = entity.position().subtract(this.position());
		Vec3 vec32 = teleportTransition.position()
			.add(
				teleportTransition.relatives().contains(Relative.X) ? 0.0 : vec3.x(),
				teleportTransition.relatives().contains(Relative.Y) ? 0.0 : vec3.y(),
				teleportTransition.relatives().contains(Relative.Z) ? 0.0 : vec3.z()
			);
		return teleportTransition.withPosition(vec32).withRotation(f, g).transitionAsPassenger();
	}

	private void sendTeleportTransitionToRidingPlayers(TeleportTransition teleportTransition) {
		Entity entity = this.getControllingPassenger();

		for (Entity entity2 : this.getIndirectPassengers()) {
			if (entity2 instanceof ServerPlayer serverPlayer) {
				if (entity != null && serverPlayer.getId() == entity.getId()) {
					serverPlayer.connection
						.send(ClientboundTeleportEntityPacket.teleport(this.getId(), PositionMoveRotation.of(teleportTransition), teleportTransition.relatives(), this.onGround));
				} else {
					serverPlayer.connection.send(ClientboundTeleportEntityPacket.teleport(this.getId(), PositionMoveRotation.of(this), Set.of(), this.onGround));
				}
			}
		}
	}

	public void teleportSetPosition(PositionMoveRotation positionMoveRotation, Set<Relative> set) {
		this.teleportSetPosition(PositionMoveRotation.of(this), positionMoveRotation, set);
	}

	public void teleportSetPosition(PositionMoveRotation positionMoveRotation, PositionMoveRotation positionMoveRotation2, Set<Relative> set) {
		PositionMoveRotation positionMoveRotation3 = PositionMoveRotation.calculateAbsolute(positionMoveRotation, positionMoveRotation2, set);
		this.setPosRaw(positionMoveRotation3.position().x, positionMoveRotation3.position().y, positionMoveRotation3.position().z);
		this.setYRot(positionMoveRotation3.yRot());
		this.setYHeadRot(positionMoveRotation3.yRot());
		this.setXRot(positionMoveRotation3.xRot());
		this.reapplyPosition();
		this.setOldPosAndRot();
		this.setDeltaMovement(positionMoveRotation3.deltaMovement());
		this.clearMovementThisTick();
	}

	public void forceSetRotation(float f, boolean bl, float g, boolean bl2) {
		Set<Relative> set = Relative.rotation(bl, bl2);
		PositionMoveRotation positionMoveRotation = PositionMoveRotation.of(this);
		PositionMoveRotation positionMoveRotation2 = positionMoveRotation.withRotation(f, g);
		PositionMoveRotation positionMoveRotation3 = PositionMoveRotation.calculateAbsolute(positionMoveRotation, positionMoveRotation2, set);
		this.setYRot(positionMoveRotation3.yRot());
		this.setYHeadRot(positionMoveRotation3.yRot());
		this.setXRot(positionMoveRotation3.xRot());
		this.setOldRot();
	}

	public void placePortalTicket(BlockPos blockPos) {
		if (this.level() instanceof ServerLevel serverLevel) {
			serverLevel.getChunkSource().addTicketWithRadius(TicketType.PORTAL, new ChunkPos(blockPos), 3);
		}
	}

	protected void removeAfterChangingDimensions() {
		this.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION);
		if (this instanceof Leashable leashable) {
			leashable.removeLeash();
		}

		if (this instanceof WaypointTransmitter waypointTransmitter && this.level instanceof ServerLevel serverLevel) {
			serverLevel.getWaypointManager().untrackWaypoint(waypointTransmitter);
		}
	}

	public Vec3 getRelativePortalPosition(Direction.Axis axis, BlockUtil.FoundRectangle foundRectangle) {
		return PortalShape.getRelativePosition(foundRectangle, axis, this.position(), this.getDimensions(this.getPose()));
	}

	public boolean canUsePortal(boolean bl) {
		return (bl || !this.isPassenger()) && this.isAlive();
	}

	public boolean canTeleport(Level level, Level level2) {
		if (level.dimension() == Level.END && level2.dimension() == Level.OVERWORLD) {
			for (Entity entity : this.getPassengers()) {
				if (entity instanceof ServerPlayer serverPlayer && !serverPlayer.seenCredits) {
					return false;
				}
			}
		}

		return true;
	}

	public float getBlockExplosionResistance(
		Explosion explosion, BlockGetter blockGetter, BlockPos blockPos, BlockState blockState, FluidState fluidState, float f
	) {
		return f;
	}

	public boolean shouldBlockExplode(Explosion explosion, BlockGetter blockGetter, BlockPos blockPos, BlockState blockState, float f) {
		return true;
	}

	public int getMaxFallDistance() {
		return 3;
	}

	public boolean isIgnoringBlockTriggers() {
		return false;
	}

	public void fillCrashReportCategory(CrashReportCategory crashReportCategory) {
		crashReportCategory.setDetail("Entity Type", () -> EntityType.getKey(this.getType()) + " (" + this.getClass().getCanonicalName() + ")");
		crashReportCategory.setDetail("Entity ID", this.id);
		crashReportCategory.setDetail("Entity Name", () -> this.getPlainTextName());
		crashReportCategory.setDetail("Entity's Exact location", String.format(Locale.ROOT, "%.2f, %.2f, %.2f", this.getX(), this.getY(), this.getZ()));
		crashReportCategory.setDetail(
			"Entity's Block location", CrashReportCategory.formatLocation(this.level(), Mth.floor(this.getX()), Mth.floor(this.getY()), Mth.floor(this.getZ()))
		);
		Vec3 vec3 = this.getDeltaMovement();
		crashReportCategory.setDetail("Entity's Momentum", String.format(Locale.ROOT, "%.2f, %.2f, %.2f", vec3.x, vec3.y, vec3.z));
		crashReportCategory.setDetail("Entity's Passengers", () -> this.getPassengers().toString());
		crashReportCategory.setDetail("Entity's Vehicle", () -> String.valueOf(this.getVehicle()));
	}

	public boolean displayFireAnimation() {
		return this.isOnFire() && !this.isSpectator();
	}

	public void setUUID(UUID uUID) {
		this.uuid = uUID;
		this.stringUUID = this.uuid.toString();
	}

	@Override
	public UUID getUUID() {
		return this.uuid;
	}

	public String getStringUUID() {
		return this.stringUUID;
	}

	@Override
	public String getScoreboardName() {
		return this.stringUUID;
	}

	public boolean isPushedByFluid() {
		return true;
	}

	public static double getViewScale() {
		return viewScale;
	}

	public static void setViewScale(double d) {
		viewScale = d;
	}

	@Override
	public Component getDisplayName() {
		return PlayerTeam.formatNameForTeam(this.getTeam(), this.getName())
			.withStyle(style -> style.withHoverEvent(this.createHoverEvent()).withInsertion(this.getStringUUID()));
	}

	public void setCustomName(@Nullable Component component) {
		this.entityData.set(DATA_CUSTOM_NAME, Optional.ofNullable(component));
	}

	@Override
	public @Nullable Component getCustomName() {
		return this.entityData.get(DATA_CUSTOM_NAME).orElse(null);
	}

	@Override
	public boolean hasCustomName() {
		return this.entityData.get(DATA_CUSTOM_NAME).isPresent();
	}

	public void setCustomNameVisible(boolean bl) {
		this.entityData.set(DATA_CUSTOM_NAME_VISIBLE, bl);
	}

	public boolean isCustomNameVisible() {
		return this.entityData.get(DATA_CUSTOM_NAME_VISIBLE);
	}

	public boolean teleportTo(ServerLevel serverLevel, double d, double e, double f, Set<Relative> set, float g, float h, boolean bl) {
		Entity entity = this.teleport(new TeleportTransition(serverLevel, new Vec3(d, e, f), Vec3.ZERO, g, h, set, TeleportTransition.DO_NOTHING));
		return entity != null;
	}

	public void dismountTo(double d, double e, double f) {
		this.teleportTo(d, e, f);
	}

	public void teleportTo(double d, double e, double f) {
		if (this.level() instanceof ServerLevel) {
			this.snapTo(d, e, f, this.getYRot(), this.getXRot());
			this.teleportPassengers();
		}
	}

	private void teleportPassengers() {
		this.getSelfAndPassengers().forEach(entity -> {
			for (Entity entity2 : entity.passengers) {
				entity.positionRider(entity2, Entity::snapTo);
			}
		});
	}

	public void teleportRelative(double d, double e, double f) {
		this.teleportTo(this.getX() + d, this.getY() + e, this.getZ() + f);
	}

	public boolean shouldShowName() {
		return this.isCustomNameVisible();
	}

	@Override
	public void onSyncedDataUpdated(List<SynchedEntityData.DataValue<?>> list) {
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> entityDataAccessor) {
		if (DATA_POSE.equals(entityDataAccessor)) {
			this.refreshDimensions();
		}
	}

	@Deprecated
	protected void fixupDimensions() {
		Pose pose = this.getPose();
		EntityDimensions entityDimensions = this.getDimensions(pose);
		this.dimensions = entityDimensions;
		this.eyeHeight = entityDimensions.eyeHeight();
	}

	public void refreshDimensions() {
		EntityDimensions entityDimensions = this.dimensions;
		Pose pose = this.getPose();
		EntityDimensions entityDimensions2 = this.getDimensions(pose);
		this.dimensions = entityDimensions2;
		this.eyeHeight = entityDimensions2.eyeHeight();
		this.reapplyPosition();
		boolean bl = entityDimensions2.width() <= 4.0F && entityDimensions2.height() <= 4.0F;
		if (!this.level.isClientSide()
			&& !this.firstTick
			&& !this.noPhysics
			&& bl
			&& (entityDimensions2.width() > entityDimensions.width() || entityDimensions2.height() > entityDimensions.height())
			&& !(this instanceof Player)) {
			this.fudgePositionAfterSizeChange(entityDimensions);
		}
	}

	public boolean fudgePositionAfterSizeChange(EntityDimensions entityDimensions) {
		EntityDimensions entityDimensions2 = this.getDimensions(this.getPose());
		Vec3 vec3 = this.position().add(0.0, entityDimensions.height() / 2.0, 0.0);
		double d = Math.max(0.0F, entityDimensions2.width() - entityDimensions.width()) + 1.0E-6;
		double e = Math.max(0.0F, entityDimensions2.height() - entityDimensions.height()) + 1.0E-6;
		VoxelShape voxelShape = Shapes.create(AABB.ofSize(vec3, d, e, d));
		Optional<Vec3> optional = this.level
			.findFreePosition(this, voxelShape, vec3, entityDimensions2.width(), entityDimensions2.height(), entityDimensions2.width());
		if (optional.isPresent()) {
			this.setPos(optional.get().add(0.0, -entityDimensions2.height() / 2.0, 0.0));
			return true;
		}

		if (entityDimensions2.width() > entityDimensions.width() && entityDimensions2.height() > entityDimensions.height()) {
			VoxelShape voxelShape2 = Shapes.create(AABB.ofSize(vec3, d, 1.0E-6, d));
			Optional<Vec3> optional2 = this.level
				.findFreePosition(this, voxelShape2, vec3, entityDimensions2.width(), entityDimensions.height(), entityDimensions2.width());
			if (optional2.isPresent()) {
				this.setPos(optional2.get().add(0.0, -entityDimensions.height() / 2.0 + 1.0E-6, 0.0));
				return true;
			}
		}

		return false;
	}

	public Direction getDirection() {
		return Direction.fromYRot(this.getYRot());
	}

	public Direction getMotionDirection() {
		return this.getDirection();
	}

	protected HoverEvent createHoverEvent() {
		return new HoverEvent.ShowEntity(new HoverEvent.EntityTooltipInfo(this.getType(), this.getUUID(), this.getName()));
	}

	public boolean broadcastToPlayer(ServerPlayer serverPlayer) {
		return true;
	}

	@Override
	public final AABB getBoundingBox() {
		return this.bb;
	}

	public final void setBoundingBox(AABB aABB) {
		this.bb = aABB;
	}

	public final float getEyeHeight(Pose pose) {
		return this.getDimensions(pose).eyeHeight();
	}

	public final float getEyeHeight() {
		return this.eyeHeight;
	}

	@Override
	public @Nullable SlotAccess getSlot(int i) {
		return null;
	}

	public InteractionResult interactAt(Player player, Vec3 vec3, InteractionHand interactionHand) {
		return InteractionResult.PASS;
	}

	public boolean ignoreExplosion(Explosion explosion) {
		return false;
	}

	public void startSeenByPlayer(ServerPlayer serverPlayer) {
	}

	public void stopSeenByPlayer(ServerPlayer serverPlayer) {
	}

	public float rotate(Rotation rotation) {
		float f = Mth.wrapDegrees(this.getYRot());

		return switch (rotation) {
			case CLOCKWISE_180 -> f + 180.0F;
			case COUNTERCLOCKWISE_90 -> f + 270.0F;
			case CLOCKWISE_90 -> f + 90.0F;
			default -> f;
		};
	}

	public float mirror(Mirror mirror) {
		float f = Mth.wrapDegrees(this.getYRot());

		return switch (mirror) {
			case FRONT_BACK -> -f;
			case LEFT_RIGHT -> 180.0F - f;
			default -> f;
		};
	}

	public ProjectileDeflection deflection(Projectile projectile) {
		return this.getType().is(EntityTypeTags.DEFLECTS_PROJECTILES) ? ProjectileDeflection.REVERSE : ProjectileDeflection.NONE;
	}

	public @Nullable LivingEntity getControllingPassenger() {
		return null;
	}

	public final boolean hasControllingPassenger() {
		return this.getControllingPassenger() != null;
	}

	public final List<Entity> getPassengers() {
		return this.passengers;
	}

	public @Nullable Entity getFirstPassenger() {
		return this.passengers.isEmpty() ? null : this.passengers.get(0);
	}

	public boolean hasPassenger(Entity entity) {
		return this.passengers.contains(entity);
	}

	public boolean hasPassenger(Predicate<Entity> predicate) {
		for (Entity entity : this.passengers) {
			if (predicate.test(entity)) {
				return true;
			}
		}

		return false;
	}

	private Stream<Entity> getIndirectPassengersStream() {
		return this.passengers.stream().flatMap(Entity::getSelfAndPassengers);
	}

	@Override
	public Stream<Entity> getSelfAndPassengers() {
		return Stream.concat(Stream.of(this), this.getIndirectPassengersStream());
	}

	@Override
	public Stream<Entity> getPassengersAndSelf() {
		return Stream.concat(this.passengers.stream().flatMap(Entity::getPassengersAndSelf), Stream.of(this));
	}

	public Iterable<Entity> getIndirectPassengers() {
		return () -> this.getIndirectPassengersStream().iterator();
	}

	public int countPlayerPassengers() {
		return (int)this.getIndirectPassengersStream().filter(entity -> entity instanceof Player).count();
	}

	public boolean hasExactlyOnePlayerPassenger() {
		return this.countPlayerPassengers() == 1;
	}

	public Entity getRootVehicle() {
		Entity entity = this;

		while (entity.isPassenger()) {
			entity = entity.getVehicle();
		}

		return entity;
	}

	public boolean isPassengerOfSameVehicle(Entity entity) {
		return this.getRootVehicle() == entity.getRootVehicle();
	}

	public boolean hasIndirectPassenger(Entity entity) {
		if (!entity.isPassenger()) {
			return false;
		}

		Entity entity2 = entity.getVehicle();
		return entity2 == this ? true : this.hasIndirectPassenger(entity2);
	}

	public final boolean isLocalInstanceAuthoritative() {
		return this.level.isClientSide() ? this.isLocalClientAuthoritative() : !this.isClientAuthoritative();
	}

	protected boolean isLocalClientAuthoritative() {
		LivingEntity livingEntity = this.getControllingPassenger();
		return livingEntity != null && livingEntity.isLocalClientAuthoritative();
	}

	public boolean isClientAuthoritative() {
		LivingEntity livingEntity = this.getControllingPassenger();
		return livingEntity != null && livingEntity.isClientAuthoritative();
	}

	public boolean canSimulateMovement() {
		return this.isLocalInstanceAuthoritative();
	}

	public boolean isEffectiveAi() {
		return this.isLocalInstanceAuthoritative();
	}

	protected static Vec3 getCollisionHorizontalEscapeVector(double d, double e, float f) {
		double g = (d + e + 1.0E-5F) / 2.0;
		float h = -Mth.sin(f * (float) (Math.PI / 180.0));
		float i = Mth.cos(f * (float) (Math.PI / 180.0));
		float j = Math.max(Math.abs(h), Math.abs(i));
		return new Vec3(h * g / j, 0.0, i * g / j);
	}

	public Vec3 getDismountLocationForPassenger(LivingEntity livingEntity) {
		return new Vec3(this.getX(), this.getBoundingBox().maxY, this.getZ());
	}

	public @Nullable Entity getVehicle() {
		return this.vehicle;
	}

	public @Nullable Entity getControlledVehicle() {
		return this.vehicle != null && this.vehicle.getControllingPassenger() == this ? this.vehicle : null;
	}

	public PushReaction getPistonPushReaction() {
		return PushReaction.NORMAL;
	}

	public SoundSource getSoundSource() {
		return SoundSource.NEUTRAL;
	}

	protected int getFireImmuneTicks() {
		return 0;
	}

	public CommandSourceStack createCommandSourceStackForNameResolution(ServerLevel serverLevel) {
		return new CommandSourceStack(
			CommandSource.NULL,
			this.position(),
			this.getRotationVector(),
			serverLevel,
			PermissionSet.NO_PERMISSIONS,
			this.getPlainTextName(),
			this.getDisplayName(),
			serverLevel.getServer(),
			this
		);
	}

	public void lookAt(EntityAnchorArgument.Anchor anchor, Vec3 vec3) {
		Vec3 vec32 = anchor.apply(this);
		double d = vec3.x - vec32.x;
		double e = vec3.y - vec32.y;
		double f = vec3.z - vec32.z;
		double g = Math.sqrt(d * d + f * f);
		this.setXRot(Mth.wrapDegrees((float)(-(Mth.atan2(e, g) * 180.0F / (float)Math.PI))));
		this.setYRot(Mth.wrapDegrees((float)(Mth.atan2(f, d) * 180.0F / (float)Math.PI) - 90.0F));
		this.setYHeadRot(this.getYRot());
		this.xRotO = this.getXRot();
		this.yRotO = this.getYRot();
	}

	public float getPreciseBodyRotation(float f) {
		return Mth.lerp(f, this.yRotO, this.yRot);
	}

	public boolean updateFluidHeightAndDoFluidPushing(TagKey<Fluid> tagKey, double d) {
		if (this.touchingUnloadedChunk()) {
			return false;
		}

		AABB aABB = this.getBoundingBox().deflate(0.001);
		int i = Mth.floor(aABB.minX);
		int j = Mth.ceil(aABB.maxX);
		int k = Mth.floor(aABB.minY);
		int l = Mth.ceil(aABB.maxY);
		int m = Mth.floor(aABB.minZ);
		int n = Mth.ceil(aABB.maxZ);
		double e = 0.0;
		boolean bl = this.isPushedByFluid();
		boolean bl2 = false;
		Vec3 vec3 = Vec3.ZERO;
		int o = 0;
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

		for (int p = i; p < j; p++) {
			for (int q = k; q < l; q++) {
				for (int r = m; r < n; r++) {
					mutableBlockPos.set(p, q, r);
					FluidState fluidState = this.level().getFluidState(mutableBlockPos);
					if (fluidState.is(tagKey)) {
						double f = q + fluidState.getHeight(this.level(), mutableBlockPos);
						if (f >= aABB.minY) {
							bl2 = true;
							e = Math.max(f - aABB.minY, e);
							if (bl) {
								Vec3 vec32 = fluidState.getFlow(this.level(), mutableBlockPos);
								if (e < 0.4) {
									vec32 = vec32.scale(e);
								}

								vec3 = vec3.add(vec32);
								o++;
							}
						}
					}
				}
			}
		}

		if (vec3.length() > 0.0) {
			if (o > 0) {
				vec3 = vec3.scale(1.0 / o);
			}

			if (!(this instanceof Player)) {
				vec3 = vec3.normalize();
			}

			Vec3 vec33 = this.getDeltaMovement();
			vec3 = vec3.scale(d);
			double g = 0.003;
			if (Math.abs(vec33.x) < 0.003 && Math.abs(vec33.z) < 0.003 && vec3.length() < 0.0045000000000000005) {
				vec3 = vec3.normalize().scale(0.0045000000000000005);
			}

			this.setDeltaMovement(this.getDeltaMovement().add(vec3));
		}

		this.fluidHeight.put(tagKey, e);
		return bl2;
	}

	public boolean touchingUnloadedChunk() {
		AABB aABB = this.getBoundingBox().inflate(1.0);
		int i = Mth.floor(aABB.minX);
		int j = Mth.ceil(aABB.maxX);
		int k = Mth.floor(aABB.minZ);
		int l = Mth.ceil(aABB.maxZ);
		return !this.level().hasChunksAt(i, k, j, l);
	}

	public double getFluidHeight(TagKey<Fluid> tagKey) {
		return this.fluidHeight.getDouble(tagKey);
	}

	public double getFluidJumpThreshold() {
		return this.getEyeHeight() < 0.4 ? 0.0 : 0.4;
	}

	public final float getBbWidth() {
		return this.dimensions.width();
	}

	public final float getBbHeight() {
		return this.dimensions.height();
	}

	public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
		return new ClientboundAddEntityPacket(this, serverEntity);
	}

	public EntityDimensions getDimensions(Pose pose) {
		return this.type.getDimensions();
	}

	public final EntityAttachments getAttachments() {
		return this.dimensions.attachments();
	}

	@Override
	public Vec3 position() {
		return this.position;
	}

	public Vec3 trackingPosition() {
		return this.position();
	}

	@Override
	public BlockPos blockPosition() {
		return this.blockPosition;
	}

	public BlockState getInBlockState() {
		if (this.inBlockState == null) {
			this.inBlockState = this.level().getBlockState(this.blockPosition());
		}

		return this.inBlockState;
	}

	public ChunkPos chunkPosition() {
		return this.chunkPosition;
	}

	public Vec3 getDeltaMovement() {
		return this.deltaMovement;
	}

	public void setDeltaMovement(Vec3 vec3) {
		if (vec3.isFinite()) {
			this.deltaMovement = vec3;
		}
	}

	public void addDeltaMovement(Vec3 vec3) {
		if (vec3.isFinite()) {
			this.setDeltaMovement(this.getDeltaMovement().add(vec3));
		}
	}

	public void setDeltaMovement(double d, double e, double f) {
		this.setDeltaMovement(new Vec3(d, e, f));
	}

	public final int getBlockX() {
		return this.blockPosition.getX();
	}

	public final double getX() {
		return this.position.x;
	}

	public double getX(double d) {
		return this.position.x + this.getBbWidth() * d;
	}

	public double getRandomX(double d) {
		return this.getX((2.0 * this.random.nextDouble() - 1.0) * d);
	}

	public final int getBlockY() {
		return this.blockPosition.getY();
	}

	public final double getY() {
		return this.position.y;
	}

	public double getY(double d) {
		return this.position.y + this.getBbHeight() * d;
	}

	public double getRandomY() {
		return this.getY(this.random.nextDouble());
	}

	public double getEyeY() {
		return this.position.y + this.eyeHeight;
	}

	public final int getBlockZ() {
		return this.blockPosition.getZ();
	}

	public final double getZ() {
		return this.position.z;
	}

	public double getZ(double d) {
		return this.position.z + this.getBbWidth() * d;
	}

	public double getRandomZ(double d) {
		return this.getZ((2.0 * this.random.nextDouble() - 1.0) * d);
	}

	public final void setPosRaw(double d, double e, double f) {
		if (this.position.x != d || this.position.y != e || this.position.z != f) {
			this.position = new Vec3(d, e, f);
			int i = Mth.floor(d);
			int j = Mth.floor(e);
			int k = Mth.floor(f);
			if (i != this.blockPosition.getX() || j != this.blockPosition.getY() || k != this.blockPosition.getZ()) {
				this.blockPosition = new BlockPos(i, j, k);
				this.inBlockState = null;
				if (SectionPos.blockToSectionCoord(i) != this.chunkPosition.x || SectionPos.blockToSectionCoord(k) != this.chunkPosition.z) {
					this.chunkPosition = new ChunkPos(this.blockPosition);
				}
			}

			this.levelCallback.onMove();
			if (!this.firstTick && this.level instanceof ServerLevel serverLevel && !this.isRemoved()) {
				if (this instanceof WaypointTransmitter waypointTransmitter && waypointTransmitter.isTransmittingWaypoint()) {
					serverLevel.getWaypointManager().updateWaypoint(waypointTransmitter);
				}

				if (this instanceof ServerPlayer serverPlayer && serverPlayer.isReceivingWaypoints() && serverPlayer.connection != null) {
					serverLevel.getWaypointManager().updatePlayer(serverPlayer);
				}
			}
		}
	}

	public void checkDespawn() {
	}

	public Vec3[] getQuadLeashHolderOffsets() {
		return Leashable.createQuadLeashOffsets(this, 0.0, 0.5, 0.5, 0.0);
	}

	public boolean supportQuadLeashAsHolder() {
		return false;
	}

	public void notifyLeashHolder(Leashable leashable) {
	}

	public void notifyLeasheeRemoved(Leashable leashable) {
	}

	public Vec3 getRopeHoldPosition(float f) {
		return this.getPosition(f).add(0.0, this.eyeHeight * 0.7, 0.0);
	}

	public void recreateFromPacket(ClientboundAddEntityPacket clientboundAddEntityPacket) {
		int i = clientboundAddEntityPacket.getId();
		double d = clientboundAddEntityPacket.getX();
		double e = clientboundAddEntityPacket.getY();
		double f = clientboundAddEntityPacket.getZ();
		this.syncPacketPositionCodec(d, e, f);
		this.snapTo(d, e, f, clientboundAddEntityPacket.getYRot(), clientboundAddEntityPacket.getXRot());
		this.setId(i);
		this.setUUID(clientboundAddEntityPacket.getUUID());
		this.setDeltaMovement(clientboundAddEntityPacket.getMovement());
	}

	public @Nullable ItemStack getPickResult() {
		return null;
	}

	public void setIsInPowderSnow(boolean bl) {
		this.isInPowderSnow = bl;
	}

	public boolean canFreeze() {
		return !this.getType().is(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES);
	}

	public boolean isFreezing() {
		return this.getTicksFrozen() > 0;
	}

	public float getYRot() {
		return this.yRot;
	}

	@Override
	public float getVisualRotationYInDegrees() {
		return this.getYRot();
	}

	public void setYRot(float f) {
		if (!Float.isFinite(f)) {
			Util.logAndPauseIfInIde("Invalid entity rotation: " + f + ", discarding.");
		} else {
			this.yRot = f;
		}
	}

	public float getXRot() {
		return this.xRot;
	}

	public void setXRot(float f) {
		if (!Float.isFinite(f)) {
			Util.logAndPauseIfInIde("Invalid entity rotation: " + f + ", discarding.");
		} else {
			this.xRot = Math.clamp(f % 360.0F, -90.0F, 90.0F);
		}
	}

	public boolean canSprint() {
		return false;
	}

	public float maxUpStep() {
		return 0.0F;
	}

	public void onExplosionHit(@Nullable Entity entity) {
	}

	@Override
	public final boolean isRemoved() {
		return this.removalReason != null;
	}

	public Entity.@Nullable RemovalReason getRemovalReason() {
		return this.removalReason;
	}

	@Override
	public final void setRemoved(Entity.RemovalReason removalReason) {
		if (this.removalReason == null) {
			this.removalReason = removalReason;
		}

		if (this.removalReason.shouldDestroy()) {
			this.stopRiding();
		}

		this.getPassengers().forEach(Entity::stopRiding);
		this.levelCallback.onRemove(removalReason);
		this.onRemoval(removalReason);
	}

	protected void unsetRemoved() {
		this.removalReason = null;
	}

	@Override
	public void setLevelCallback(EntityInLevelCallback entityInLevelCallback) {
		this.levelCallback = entityInLevelCallback;
	}

	@Override
	public boolean shouldBeSaved() {
		if (this.removalReason != null && !this.removalReason.shouldSave()) {
			return false;
		} else {
			return this.isPassenger() ? false : !this.isVehicle() || !this.hasExactlyOnePlayerPassenger();
		}
	}

	@Override
	public boolean isAlwaysTicking() {
		return false;
	}

	public boolean mayInteract(ServerLevel serverLevel, BlockPos blockPos) {
		return true;
	}

	public boolean isFlyingVehicle() {
		return false;
	}

	@Override
	public Level level() {
		return this.level;
	}

	protected void setLevel(Level level) {
		this.level = level;
	}

	public DamageSources damageSources() {
		return this.level().damageSources();
	}

	public RegistryAccess registryAccess() {
		return this.level().registryAccess();
	}

	protected void lerpPositionAndRotationStep(int i, double d, double e, double f, double g, double h) {
		double j = 1.0 / i;
		double k = Mth.lerp(j, this.getX(), d);
		double l = Mth.lerp(j, this.getY(), e);
		double m = Mth.lerp(j, this.getZ(), f);
		float n = (float)Mth.rotLerp(j, this.getYRot(), g);
		float o = (float)Mth.lerp(j, this.getXRot(), h);
		this.setPos(k, l, m);
		this.setRot(n, o);
	}

	public RandomSource getRandom() {
		return this.random;
	}

	public Vec3 getKnownMovement() {
		return this.getControllingPassenger() instanceof Player player && this.isAlive() ? player.getKnownMovement() : this.getDeltaMovement();
	}

	public Vec3 getKnownSpeed() {
		return this.getControllingPassenger() instanceof Player player && this.isAlive() ? player.getKnownSpeed() : this.lastKnownSpeed;
	}

	public @Nullable ItemStack getWeaponItem() {
		return null;
	}

	public Optional<ResourceKey<LootTable>> getLootTable() {
		return this.type.getDefaultLootTable();
	}

	protected void applyImplicitComponents(DataComponentGetter dataComponentGetter) {
		this.applyImplicitComponentIfPresent(dataComponentGetter, DataComponents.CUSTOM_NAME);
		this.applyImplicitComponentIfPresent(dataComponentGetter, DataComponents.CUSTOM_DATA);
	}

	public final void applyComponentsFromItemStack(ItemStack itemStack) {
		this.applyImplicitComponents(itemStack.getComponents());
	}

	@Override
	public <T> @Nullable T get(DataComponentType<? extends T> dataComponentType) {
		if (dataComponentType == DataComponents.CUSTOM_NAME) {
			return castComponentValue((DataComponentType<T>)dataComponentType, this.getCustomName());
		} else {
			return dataComponentType == DataComponents.CUSTOM_DATA ? castComponentValue((DataComponentType<T>)dataComponentType, this.customData) : null;
		}
	}

	@Contract("_,!null->!null;_,_->_")
	protected static <T> @Nullable T castComponentValue(DataComponentType<T> dataComponentType, @Nullable Object object) {
		return (T)object;
	}

	public <T> void setComponent(DataComponentType<T> dataComponentType, T object) {
		this.applyImplicitComponent(dataComponentType, object);
	}

	protected <T> boolean applyImplicitComponent(DataComponentType<T> dataComponentType, T object) {
		if (dataComponentType == DataComponents.CUSTOM_NAME) {
			this.setCustomName(castComponentValue(DataComponents.CUSTOM_NAME, object));
			return true;
		} else if (dataComponentType == DataComponents.CUSTOM_DATA) {
			this.customData = castComponentValue(DataComponents.CUSTOM_DATA, object);
			return true;
		} else {
			return false;
		}
	}

	protected <T> boolean applyImplicitComponentIfPresent(DataComponentGetter dataComponentGetter, DataComponentType<T> dataComponentType) {
		T object = dataComponentGetter.get(dataComponentType);
		return object != null ? this.applyImplicitComponent(dataComponentType, object) : false;
	}

	public ProblemReporter.PathElement problemPath() {
		return new Entity.EntityPathElement(this);
	}

	@Override
	public void registerDebugValues(ServerLevel serverLevel, DebugValueSource.Registration registration) {
	}

	record EntityPathElement(Entity entity) implements ProblemReporter.PathElement {
		@Override
		public String get() {
			return this.entity.toString();
		}
	}

	@FunctionalInterface
	public interface MoveFunction {
		void accept(Entity entity, double d, double e, double f);
	}

	record Movement(Vec3 from, Vec3 to, Optional<Vec3> axisDependentOriginalMovement) {

		public Movement(Vec3 vec3, Vec3 vec32, Vec3 vec33) {
			this(vec3, vec32, Optional.of(vec33));
		}

		public Movement(Vec3 vec3, Vec3 vec32) {
			this(vec3, vec32, Optional.empty());
		}
	}

	public enum MovementEmission {
		NONE(false, false),
		SOUNDS(true, false),
		EVENTS(false, true),
		ALL(true, true);

		final boolean sounds;
		final boolean events;

		MovementEmission(final boolean bl, final boolean bl2) {
			this.sounds = bl;
			this.events = bl2;
		}

		public boolean emitsAnything() {
			return this.events || this.sounds;
		}

		public boolean emitsEvents() {
			return this.events;
		}

		public boolean emitsSounds() {
			return this.sounds;
		}
	}

	public enum RemovalReason {
		KILLED(true, false),
		DISCARDED(true, false),
		UNLOADED_TO_CHUNK(false, true),
		UNLOADED_WITH_PLAYER(false, false),
		CHANGED_DIMENSION(false, false);

		private final boolean destroy;
		private final boolean save;

		RemovalReason(final boolean bl, final boolean bl2) {
			this.destroy = bl;
			this.save = bl2;
		}

		public boolean shouldDestroy() {
			return this.destroy;
		}

		public boolean shouldSave() {
			return this.save;
		}
	}
}
