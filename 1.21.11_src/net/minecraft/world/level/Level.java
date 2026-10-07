package net.minecraft.world.level;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.Nullable;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.attachment.v1.AttachmentTarget} injected by mod fabric-data-attachment-api-v1</p>
 */
public abstract class Level implements LevelAccessor, AutoCloseable, AttachmentTarget {
	public static final Codec<ResourceKey<Level>> RESOURCE_KEY_CODEC = ResourceKey.codec(Registries.DIMENSION);
	public static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
	public static final ResourceKey<Level> NETHER = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether"));
	public static final ResourceKey<Level> END = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_end"));
	public static final int MAX_LEVEL_SIZE = 30000000;
	public static final int LONG_PARTICLE_CLIP_RANGE = 512;
	public static final int SHORT_PARTICLE_CLIP_RANGE = 32;
	public static final int MAX_BRIGHTNESS = 15;
	public static final int MAX_ENTITY_SPAWN_Y = 20000000;
	public static final int MIN_ENTITY_SPAWN_Y = -20000000;
	private static final WeightedList<ExplosionParticleInfo> DEFAULT_EXPLOSION_BLOCK_PARTICLES = WeightedList.<ExplosionParticleInfo>builder()
		.add(new ExplosionParticleInfo(ParticleTypes.POOF, 0.5F, 1.0F))
		.add(new ExplosionParticleInfo(ParticleTypes.SMOKE, 1.0F, 1.0F))
		.build();
	protected final List<TickingBlockEntity> blockEntityTickers = Lists.newArrayList();
	protected final CollectingNeighborUpdater neighborUpdater;
	private final List<TickingBlockEntity> pendingBlockEntityTickers = Lists.newArrayList();
	private boolean tickingBlockEntities;
	private final Thread thread;
	private final boolean isDebug;
	private int skyDarken;
	protected int randValue = RandomSource.create().nextInt();
	protected final int addend = 1013904223;
	protected float oRainLevel;
	protected float rainLevel;
	protected float oThunderLevel;
	protected float thunderLevel;
	public final RandomSource random = RandomSource.create();
	@Deprecated
	private final RandomSource threadSafeRandom = RandomSource.createThreadSafe();
	private final Holder<DimensionType> dimensionTypeRegistration;
	protected final WritableLevelData levelData;
	private final boolean isClientSide;
	private final BiomeManager biomeManager;
	private final ResourceKey<Level> dimension;
	private final RegistryAccess registryAccess;
	private final DamageSources damageSources;
	private final PalettedContainerFactory palettedContainerFactory;
	private long subTickCount;

	protected Level(
		WritableLevelData writableLevelData,
		ResourceKey<Level> resourceKey,
		RegistryAccess registryAccess,
		Holder<DimensionType> holder,
		boolean bl,
		boolean bl2,
		long l,
		int i
	) {
		this.levelData = writableLevelData;
		this.dimensionTypeRegistration = holder;
		this.dimension = resourceKey;
		this.isClientSide = bl;
		this.thread = Thread.currentThread();
		this.biomeManager = new BiomeManager(this, l);
		this.isDebug = bl2;
		this.neighborUpdater = new CollectingNeighborUpdater(this, i);
		this.registryAccess = registryAccess;
		this.palettedContainerFactory = PalettedContainerFactory.create(registryAccess);
		this.damageSources = new DamageSources(registryAccess);
	}

	@Override
	public boolean isClientSide() {
		return this.isClientSide;
	}

	@Override
	public @Nullable MinecraftServer getServer() {
		return null;
	}

	public boolean isInWorldBounds(BlockPos blockPos) {
		return !this.isOutsideBuildHeight(blockPos) && isInWorldBoundsHorizontal(blockPos);
	}

	public boolean isInValidBounds(BlockPos blockPos) {
		return !this.isOutsideBuildHeight(blockPos) && isInValidBoundsHorizontal(blockPos);
	}

	public static boolean isInSpawnableBounds(BlockPos blockPos) {
		return !isOutsideSpawnableHeight(blockPos.getY()) && isInWorldBoundsHorizontal(blockPos);
	}

	private static boolean isInWorldBoundsHorizontal(BlockPos blockPos) {
		return blockPos.getX() >= -30000000 && blockPos.getZ() >= -30000000 && blockPos.getX() < 30000000 && blockPos.getZ() < 30000000;
	}

	private static boolean isInValidBoundsHorizontal(BlockPos blockPos) {
		int i = SectionPos.blockToSectionCoord(blockPos.getX());
		int j = SectionPos.blockToSectionCoord(blockPos.getZ());
		return ChunkPos.isValid(i, j);
	}

	private static boolean isOutsideSpawnableHeight(int i) {
		return i < -20000000 || i >= 20000000;
	}

	public LevelChunk getChunkAt(BlockPos blockPos) {
		return this.getChunk(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()));
	}

	public LevelChunk getChunk(int i, int j) {
		return (LevelChunk)this.getChunk(i, j, ChunkStatus.FULL);
	}

	@Override
	public @Nullable ChunkAccess getChunk(int i, int j, ChunkStatus chunkStatus, boolean bl) {
		ChunkAccess chunkAccess = this.getChunkSource().getChunk(i, j, chunkStatus, bl);
		if (chunkAccess == null && bl) {
			throw new IllegalStateException("Should always be able to create a chunk!");
		} else {
			return chunkAccess;
		}
	}

	@Override
	public boolean setBlock(BlockPos blockPos, BlockState blockState, @Block.UpdateFlags int i) {
		return this.setBlock(blockPos, blockState, i, 512);
	}

	@Override
	public boolean setBlock(BlockPos blockPos, BlockState blockState, @Block.UpdateFlags int i, int j) {
		if (!this.isInValidBounds(blockPos)) {
			return false;
		}

		if (!this.isClientSide() && this.isDebug()) {
			return false;
		}

		LevelChunk levelChunk = this.getChunkAt(blockPos);
		Block block = blockState.getBlock();
		BlockState blockState2 = levelChunk.setBlockState(blockPos, blockState, i);
		if (blockState2 == null) {
			return false;
		}

		BlockState blockState3 = this.getBlockState(blockPos);
		if (blockState3 == blockState) {
			if (blockState2 != blockState3) {
				this.setBlocksDirty(blockPos, blockState2, blockState3);
			}

			if ((i & 2) != 0
				&& (!this.isClientSide() || (i & 4) == 0)
				&& (this.isClientSide() || levelChunk.getFullStatus() != null && levelChunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING))) {
				this.sendBlockUpdated(blockPos, blockState2, blockState, i);
			}

			if ((i & 1) != 0) {
				this.updateNeighborsAt(blockPos, blockState2.getBlock());
				if (!this.isClientSide() && blockState.hasAnalogOutputSignal()) {
					this.updateNeighbourForOutputSignal(blockPos, block);
				}
			}

			if ((i & 16) == 0 && j > 0) {
				int k = i & -34;
				blockState2.updateIndirectNeighbourShapes(this, blockPos, k, j - 1);
				blockState.updateNeighbourShapes(this, blockPos, k, j - 1);
				blockState.updateIndirectNeighbourShapes(this, blockPos, k, j - 1);
			}

			this.updatePOIOnBlockStateChange(blockPos, blockState2, blockState3);
		}

		return true;
	}

	public void updatePOIOnBlockStateChange(BlockPos blockPos, BlockState blockState, BlockState blockState2) {
	}

	@Override
	public boolean removeBlock(BlockPos blockPos, boolean bl) {
		FluidState fluidState = this.getFluidState(blockPos);
		return this.setBlock(blockPos, fluidState.createLegacyBlock(), 3 | (bl ? 64 : 0));
	}

	@Override
	public boolean destroyBlock(BlockPos blockPos, boolean bl, @Nullable Entity entity, int i) {
		BlockState blockState = this.getBlockState(blockPos);
		if (blockState.isAir()) {
			return false;
		}

		FluidState fluidState = this.getFluidState(blockPos);
		if (!(blockState.getBlock() instanceof BaseFireBlock)) {
			this.levelEvent(2001, blockPos, Block.getId(blockState));
		}

		if (bl) {
			BlockEntity blockEntity = blockState.hasBlockEntity() ? this.getBlockEntity(blockPos) : null;
			Block.dropResources(blockState, this, blockPos, blockEntity, entity, ItemStack.EMPTY);
		}

		boolean bl2 = this.setBlock(blockPos, fluidState.createLegacyBlock(), 3, i);
		if (bl2) {
			this.gameEvent(GameEvent.BLOCK_DESTROY, blockPos, GameEvent.Context.of(entity, blockState));
		}

		return bl2;
	}

	public void addDestroyBlockEffect(BlockPos blockPos, BlockState blockState) {
	}

	public boolean setBlockAndUpdate(BlockPos blockPos, BlockState blockState) {
		return this.setBlock(blockPos, blockState, 3);
	}

	public abstract void sendBlockUpdated(BlockPos blockPos, BlockState blockState, BlockState blockState2, @Block.UpdateFlags int i);

	public void setBlocksDirty(BlockPos blockPos, BlockState blockState, BlockState blockState2) {
	}

	public void updateNeighborsAt(BlockPos blockPos, Block block, @Nullable Orientation orientation) {
	}

	public void updateNeighborsAtExceptFromFacing(BlockPos blockPos, Block block, Direction direction, @Nullable Orientation orientation) {
	}

	public void neighborChanged(BlockPos blockPos, Block block, @Nullable Orientation orientation) {
	}

	public void neighborChanged(BlockState blockState, BlockPos blockPos, Block block, @Nullable Orientation orientation, boolean bl) {
	}

	@Override
	public void neighborShapeChanged(Direction direction, BlockPos blockPos, BlockPos blockPos2, BlockState blockState, @Block.UpdateFlags int i, int j) {
		this.neighborUpdater.shapeUpdate(direction, blockState, blockPos, blockPos2, i, j);
	}

	@Override
	public int getHeight(Heightmap.Types types, int i, int j) {
		int k;
		if (i >= -30000000 && j >= -30000000 && i < 30000000 && j < 30000000) {
			if (this.hasChunk(SectionPos.blockToSectionCoord(i), SectionPos.blockToSectionCoord(j))) {
				k = this.getChunk(SectionPos.blockToSectionCoord(i), SectionPos.blockToSectionCoord(j)).getHeight(types, i & 15, j & 15) + 1;
			} else {
				k = this.getMinY();
			}
		} else {
			k = this.getSeaLevel() + 1;
		}

		return k;
	}

	@Override
	public LevelLightEngine getLightEngine() {
		return this.getChunkSource().getLightEngine();
	}

	@Override
	public BlockState getBlockState(BlockPos blockPos) {
		if (!this.isInValidBounds(blockPos)) {
			return Blocks.VOID_AIR.defaultBlockState();
		}

		LevelChunk levelChunk = this.getChunk(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()));
		return levelChunk.getBlockState(blockPos);
	}

	@Override
	public FluidState getFluidState(BlockPos blockPos) {
		if (!this.isInValidBounds(blockPos)) {
			return Fluids.EMPTY.defaultFluidState();
		}

		LevelChunk levelChunk = this.getChunkAt(blockPos);
		return levelChunk.getFluidState(blockPos);
	}

	public boolean isBrightOutside() {
		return !this.dimensionType().hasFixedTime() && this.skyDarken < 4;
	}

	public boolean isDarkOutside() {
		return !this.dimensionType().hasFixedTime() && !this.isBrightOutside();
	}

	@Override
	public void playSound(@Nullable Entity entity, BlockPos blockPos, SoundEvent soundEvent, SoundSource soundSource, float f, float g) {
		this.playSound(entity, blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5, soundEvent, soundSource, f, g);
	}

	public abstract void playSeededSound(
		@Nullable Entity entity, double d, double e, double f, Holder<SoundEvent> holder, SoundSource soundSource, float g, float h, long l
	);

	public void playSeededSound(@Nullable Entity entity, double d, double e, double f, SoundEvent soundEvent, SoundSource soundSource, float g, float h, long l) {
		this.playSeededSound(entity, d, e, f, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(soundEvent), soundSource, g, h, l);
	}

	public abstract void playSeededSound(@Nullable Entity entity, Entity entity2, Holder<SoundEvent> holder, SoundSource soundSource, float f, float g, long l);

	public void playSound(@Nullable Entity entity, double d, double e, double f, SoundEvent soundEvent, SoundSource soundSource) {
		this.playSound(entity, d, e, f, soundEvent, soundSource, 1.0F, 1.0F);
	}

	public void playSound(@Nullable Entity entity, double d, double e, double f, SoundEvent soundEvent, SoundSource soundSource, float g, float h) {
		this.playSeededSound(entity, d, e, f, soundEvent, soundSource, g, h, this.threadSafeRandom.nextLong());
	}

	public void playSound(@Nullable Entity entity, double d, double e, double f, Holder<SoundEvent> holder, SoundSource soundSource, float g, float h) {
		this.playSeededSound(entity, d, e, f, holder, soundSource, g, h, this.threadSafeRandom.nextLong());
	}

	public void playSound(@Nullable Entity entity, Entity entity2, SoundEvent soundEvent, SoundSource soundSource, float f, float g) {
		this.playSeededSound(entity, entity2, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(soundEvent), soundSource, f, g, this.threadSafeRandom.nextLong());
	}

	public void playLocalSound(BlockPos blockPos, SoundEvent soundEvent, SoundSource soundSource, float f, float g, boolean bl) {
		this.playLocalSound(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5, soundEvent, soundSource, f, g, bl);
	}

	public void playLocalSound(Entity entity, SoundEvent soundEvent, SoundSource soundSource, float f, float g) {
	}

	public void playLocalSound(double d, double e, double f, SoundEvent soundEvent, SoundSource soundSource, float g, float h, boolean bl) {
	}

	public void playPlayerSound(SoundEvent soundEvent, SoundSource soundSource, float f, float g) {
	}

	@Override
	public void addParticle(ParticleOptions particleOptions, double d, double e, double f, double g, double h, double i) {
	}

	public void addParticle(ParticleOptions particleOptions, boolean bl, boolean bl2, double d, double e, double f, double g, double h, double i) {
	}

	public void addAlwaysVisibleParticle(ParticleOptions particleOptions, double d, double e, double f, double g, double h, double i) {
	}

	public void addAlwaysVisibleParticle(ParticleOptions particleOptions, boolean bl, double d, double e, double f, double g, double h, double i) {
	}

	public void addBlockEntityTicker(TickingBlockEntity tickingBlockEntity) {
		(this.tickingBlockEntities ? this.pendingBlockEntityTickers : this.blockEntityTickers).add(tickingBlockEntity);
	}

	public void tickBlockEntities() {
		this.tickingBlockEntities = true;
		if (!this.pendingBlockEntityTickers.isEmpty()) {
			this.blockEntityTickers.addAll(this.pendingBlockEntityTickers);
			this.pendingBlockEntityTickers.clear();
		}

		Iterator<TickingBlockEntity> iterator = this.blockEntityTickers.iterator();
		boolean bl = this.tickRateManager().runsNormally();

		while (iterator.hasNext()) {
			TickingBlockEntity tickingBlockEntity = iterator.next();
			if (tickingBlockEntity.isRemoved()) {
				iterator.remove();
			} else if (bl && this.shouldTickBlocksAt(tickingBlockEntity.getPos())) {
				tickingBlockEntity.tick();
			}
		}

		this.tickingBlockEntities = false;
	}

	public <T extends Entity> void guardEntityTick(Consumer<T> consumer, T entity) {
		try {
			consumer.accept(entity);
		} catch (Throwable throwable) {
			CrashReport crashReport = CrashReport.forThrowable(throwable, "Ticking entity");
			CrashReportCategory crashReportCategory = crashReport.addCategory("Entity being ticked");
			entity.fillCrashReportCategory(crashReportCategory);
			throw new ReportedException(crashReport);
		}
	}

	public boolean shouldTickDeath(Entity entity) {
		return true;
	}

	public boolean shouldTickBlocksAt(long l) {
		return true;
	}

	public boolean shouldTickBlocksAt(BlockPos blockPos) {
		return this.shouldTickBlocksAt(ChunkPos.asLong(blockPos));
	}

	public void explode(@Nullable Entity entity, double d, double e, double f, float g, Level.ExplosionInteraction explosionInteraction) {
		this.explode(
			entity,
			Explosion.getDefaultDamageSource(this, entity),
			null,
			d,
			e,
			f,
			g,
			false,
			explosionInteraction,
			ParticleTypes.EXPLOSION,
			ParticleTypes.EXPLOSION_EMITTER,
			DEFAULT_EXPLOSION_BLOCK_PARTICLES,
			SoundEvents.GENERIC_EXPLODE
		);
	}

	public void explode(@Nullable Entity entity, double d, double e, double f, float g, boolean bl, Level.ExplosionInteraction explosionInteraction) {
		this.explode(
			entity,
			Explosion.getDefaultDamageSource(this, entity),
			null,
			d,
			e,
			f,
			g,
			bl,
			explosionInteraction,
			ParticleTypes.EXPLOSION,
			ParticleTypes.EXPLOSION_EMITTER,
			DEFAULT_EXPLOSION_BLOCK_PARTICLES,
			SoundEvents.GENERIC_EXPLODE
		);
	}

	public void explode(
		@Nullable Entity entity,
		@Nullable DamageSource damageSource,
		@Nullable ExplosionDamageCalculator explosionDamageCalculator,
		Vec3 vec3,
		float f,
		boolean bl,
		Level.ExplosionInteraction explosionInteraction
	) {
		this.explode(
			entity,
			damageSource,
			explosionDamageCalculator,
			vec3.x(),
			vec3.y(),
			vec3.z(),
			f,
			bl,
			explosionInteraction,
			ParticleTypes.EXPLOSION,
			ParticleTypes.EXPLOSION_EMITTER,
			DEFAULT_EXPLOSION_BLOCK_PARTICLES,
			SoundEvents.GENERIC_EXPLODE
		);
	}

	public void explode(
		@Nullable Entity entity,
		@Nullable DamageSource damageSource,
		@Nullable ExplosionDamageCalculator explosionDamageCalculator,
		double d,
		double e,
		double f,
		float g,
		boolean bl,
		Level.ExplosionInteraction explosionInteraction
	) {
		this.explode(
			entity,
			damageSource,
			explosionDamageCalculator,
			d,
			e,
			f,
			g,
			bl,
			explosionInteraction,
			ParticleTypes.EXPLOSION,
			ParticleTypes.EXPLOSION_EMITTER,
			DEFAULT_EXPLOSION_BLOCK_PARTICLES,
			SoundEvents.GENERIC_EXPLODE
		);
	}

	public abstract void explode(
		@Nullable Entity entity,
		@Nullable DamageSource damageSource,
		@Nullable ExplosionDamageCalculator explosionDamageCalculator,
		double d,
		double e,
		double f,
		float g,
		boolean bl,
		Level.ExplosionInteraction explosionInteraction,
		ParticleOptions particleOptions,
		ParticleOptions particleOptions2,
		WeightedList<ExplosionParticleInfo> weightedList,
		Holder<SoundEvent> holder
	);

	public abstract String gatherChunkSourceStats();

	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos blockPos) {
		if (!this.isInValidBounds(blockPos)) {
			return null;
		} else {
			return !this.isClientSide() && Thread.currentThread() != this.thread
				? null
				: this.getChunkAt(blockPos).getBlockEntity(blockPos, LevelChunk.EntityCreationType.IMMEDIATE);
		}
	}

	public void setBlockEntity(BlockEntity blockEntity) {
		BlockPos blockPos = blockEntity.getBlockPos();
		if (this.isInValidBounds(blockPos)) {
			this.getChunkAt(blockPos).addAndRegisterBlockEntity(blockEntity);
		}
	}

	public void removeBlockEntity(BlockPos blockPos) {
		if (this.isInValidBounds(blockPos)) {
			this.getChunkAt(blockPos).removeBlockEntity(blockPos);
		}
	}

	public boolean isLoaded(BlockPos blockPos) {
		return !this.isInValidBounds(blockPos)
			? false
			: this.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()));
	}

	public boolean loadedAndEntityCanStandOnFace(BlockPos blockPos, Entity entity, Direction direction) {
		if (!this.isInValidBounds(blockPos)) {
			return false;
		}

		ChunkAccess chunkAccess = this.getChunk(
			SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()), ChunkStatus.FULL, false
		);
		return chunkAccess == null ? false : chunkAccess.getBlockState(blockPos).entityCanStandOnFace(this, blockPos, entity, direction);
	}

	public boolean loadedAndEntityCanStandOn(BlockPos blockPos, Entity entity) {
		return this.loadedAndEntityCanStandOnFace(blockPos, entity, Direction.UP);
	}

	public void updateSkyBrightness() {
		this.skyDarken = (int)(15.0F - this.environmentAttributes().getDimensionValue(EnvironmentAttributes.SKY_LIGHT_LEVEL));
	}

	public void setSpawnSettings(boolean bl) {
		this.getChunkSource().setSpawnSettings(bl);
	}

	public abstract void setRespawnData(LevelData.RespawnData respawnData);

	public abstract LevelData.RespawnData getRespawnData();

	public LevelData.RespawnData getWorldBorderAdjustedRespawnData(LevelData.RespawnData respawnData) {
		WorldBorder worldBorder = this.getWorldBorder();
		if (!worldBorder.isWithinBounds(respawnData.pos())) {
			BlockPos blockPos = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(worldBorder.getCenterX(), 0.0, worldBorder.getCenterZ()));
			return LevelData.RespawnData.of(respawnData.dimension(), blockPos, respawnData.yaw(), respawnData.pitch());
		} else {
			return respawnData;
		}
	}

	protected void prepareWeather() {
		if (this.levelData.isRaining()) {
			this.rainLevel = 1.0F;
			if (this.levelData.isThundering()) {
				this.thunderLevel = 1.0F;
			}
		}
	}

	@Override
	public void close() throws IOException {
		this.getChunkSource().close();
	}

	@Override
	public @Nullable BlockGetter getChunkForCollisions(int i, int j) {
		return this.getChunk(i, j, ChunkStatus.FULL, false);
	}

	@Override
	public List<Entity> getEntities(@Nullable Entity entity, AABB aABB, Predicate<? super Entity> predicate) {
		Profiler.get().incrementCounter("getEntities");
		List<Entity> list = Lists.newArrayList();
		this.getEntities().get(aABB, entity2 -> {
			if (entity2 != entity && predicate.test(entity2)) {
				list.add(entity2);
			}
		});

		for (EnderDragonPart enderDragonPart : this.dragonParts()) {
			if (enderDragonPart != entity && enderDragonPart.parentMob != entity && predicate.test(enderDragonPart) && aABB.intersects(enderDragonPart.getBoundingBox())
				)
			 {
				list.add(enderDragonPart);
			}
		}

		return list;
	}

	@Override
	public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> entityTypeTest, AABB aABB, Predicate<? super T> predicate) {
		List<T> list = Lists.newArrayList();
		this.getEntities(entityTypeTest, aABB, predicate, list);
		return list;
	}

	public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> entityTypeTest, AABB aABB, Predicate<? super T> predicate, List<? super T> list) {
		this.getEntities(entityTypeTest, aABB, predicate, list, Integer.MAX_VALUE);
	}

	public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> entityTypeTest, AABB aABB, Predicate<? super T> predicate, List<? super T> list, int i) {
		Profiler.get().incrementCounter("getEntities");
		this.getEntities().get(entityTypeTest, aABB, entity -> {
			if (predicate.test(entity)) {
				list.add(entity);
				if (list.size() >= i) {
					return AbortableIterationConsumer.Continuation.ABORT;
				}
			}

			if (entity instanceof EnderDragon enderDragon) {
				for (EnderDragonPart enderDragonPart : enderDragon.getSubEntities()) {
					T entity2 = entityTypeTest.tryCast(enderDragonPart);
					if (entity2 != null && predicate.test(entity2)) {
						list.add(entity2);
						if (list.size() >= i) {
							return AbortableIterationConsumer.Continuation.ABORT;
						}
					}
				}
			}

			return AbortableIterationConsumer.Continuation.CONTINUE;
		});
	}

	public <T extends Entity> boolean hasEntities(EntityTypeTest<Entity, T> entityTypeTest, AABB aABB, Predicate<? super T> predicate) {
		Profiler.get().incrementCounter("hasEntities");
		MutableBoolean mutableBoolean = new MutableBoolean();
		this.getEntities().get(entityTypeTest, aABB, entity -> {
			if (predicate.test(entity)) {
				mutableBoolean.setTrue();
				return AbortableIterationConsumer.Continuation.ABORT;
			}

			if (entity instanceof EnderDragon enderDragon) {
				for (EnderDragonPart enderDragonPart : enderDragon.getSubEntities()) {
					T entity2 = entityTypeTest.tryCast(enderDragonPart);
					if (entity2 != null && predicate.test(entity2)) {
						mutableBoolean.setTrue();
						return AbortableIterationConsumer.Continuation.ABORT;
					}
				}
			}

			return AbortableIterationConsumer.Continuation.CONTINUE;
		});
		return mutableBoolean.isTrue();
	}

	public List<Entity> getPushableEntities(Entity entity, AABB aABB) {
		return this.getEntities(entity, aABB, EntitySelector.pushableBy(entity));
	}

	public abstract @Nullable Entity getEntity(int i);

	public @Nullable Entity getEntity(UUID uUID) {
		return this.getEntities().get(uUID);
	}

	public @Nullable Entity getEntityInAnyDimension(UUID uUID) {
		return this.getEntity(uUID);
	}

	public @Nullable Player getPlayerInAnyDimension(UUID uUID) {
		return this.getPlayerByUUID(uUID);
	}

	public abstract Collection<EnderDragonPart> dragonParts();

	public void blockEntityChanged(BlockPos blockPos) {
		if (this.hasChunkAt(blockPos)) {
			this.getChunkAt(blockPos).markUnsaved();
		}
	}

	public void onBlockEntityAdded(BlockEntity blockEntity) {
	}

	public long getDayTime() {
		return this.levelData.getDayTime();
	}

	public boolean mayInteract(Entity entity, BlockPos blockPos) {
		return true;
	}

	public void broadcastEntityEvent(Entity entity, byte b) {
	}

	public void broadcastDamageEvent(Entity entity, DamageSource damageSource) {
	}

	public void blockEvent(BlockPos blockPos, Block block, int i, int j) {
		this.getBlockState(blockPos).triggerEvent(this, blockPos, i, j);
	}

	@Override
	public LevelData getLevelData() {
		return this.levelData;
	}

	public abstract TickRateManager tickRateManager();

	public float getThunderLevel(float f) {
		return Mth.lerp(f, this.oThunderLevel, this.thunderLevel) * this.getRainLevel(f);
	}

	public void setThunderLevel(float f) {
		float g = Mth.clamp(f, 0.0F, 1.0F);
		this.oThunderLevel = g;
		this.thunderLevel = g;
	}

	public float getRainLevel(float f) {
		return Mth.lerp(f, this.oRainLevel, this.rainLevel);
	}

	public void setRainLevel(float f) {
		float g = Mth.clamp(f, 0.0F, 1.0F);
		this.oRainLevel = g;
		this.rainLevel = g;
	}

	public boolean canHaveWeather() {
		return this.dimensionType().hasSkyLight() && !this.dimensionType().hasCeiling() && this.dimension() != END;
	}

	public boolean isThundering() {
		return this.canHaveWeather() && this.getThunderLevel(1.0F) > 0.9;
	}

	public boolean isRaining() {
		return this.canHaveWeather() && this.getRainLevel(1.0F) > 0.2;
	}

	public boolean isRainingAt(BlockPos blockPos) {
		return this.precipitationAt(blockPos) == Biome.Precipitation.RAIN;
	}

	public Biome.Precipitation precipitationAt(BlockPos blockPos) {
		if (!this.isRaining()) {
			return Biome.Precipitation.NONE;
		}

		if (!this.canSeeSky(blockPos)) {
			return Biome.Precipitation.NONE;
		}

		if (this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockPos).getY() > blockPos.getY()) {
			return Biome.Precipitation.NONE;
		}

		Biome biome = this.getBiome(blockPos).value();
		return biome.getPrecipitationAt(blockPos, this.getSeaLevel());
	}

	public abstract @Nullable MapItemSavedData getMapData(MapId mapId);

	public void globalLevelEvent(int i, BlockPos blockPos, int j) {
	}

	public CrashReportCategory fillReportDetails(CrashReport crashReport) {
		CrashReportCategory crashReportCategory = crashReport.addCategory("Affected level", 1);
		crashReportCategory.setDetail("All players", () -> {
			List<? extends Player> list = this.players();
			return list.size() + " total; " + list.stream().map(Player::debugInfo).collect(Collectors.joining(", "));
		});
		crashReportCategory.setDetail("Chunk stats", this.getChunkSource()::gatherStats);
		crashReportCategory.setDetail("Level dimension", () -> this.dimension().identifier().toString());

		try {
			this.levelData.fillCrashReportCategory(crashReportCategory, this);
		} catch (Throwable throwable) {
			crashReportCategory.setDetailError("Level Data Unobtainable", throwable);
		}

		return crashReportCategory;
	}

	public abstract void destroyBlockProgress(int i, BlockPos blockPos, int j);

	public void createFireworks(double d, double e, double f, double g, double h, double i, List<FireworkExplosion> list) {
	}

	public abstract Scoreboard getScoreboard();

	public void updateNeighbourForOutputSignal(BlockPos blockPos, Block block) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos blockPos2 = blockPos.relative(direction);
			if (this.hasChunkAt(blockPos2)) {
				BlockState blockState = this.getBlockState(blockPos2);
				if (blockState.is(Blocks.COMPARATOR)) {
					this.neighborChanged(blockState, blockPos2, block, null, false);
				} else if (blockState.isRedstoneConductor(this, blockPos2)) {
					blockPos2 = blockPos2.relative(direction);
					blockState = this.getBlockState(blockPos2);
					if (blockState.is(Blocks.COMPARATOR)) {
						this.neighborChanged(blockState, blockPos2, block, null, false);
					}
				}
			}
		}
	}

	@Override
	public int getSkyDarken() {
		return this.skyDarken;
	}

	public void setSkyFlashTime(int i) {
	}

	public void sendPacketToServer(Packet<?> packet) {
		throw new UnsupportedOperationException("Can't send packets to server unless you're on the client.");
	}

	@Override
	public DimensionType dimensionType() {
		return this.dimensionTypeRegistration.value();
	}

	public Holder<DimensionType> dimensionTypeRegistration() {
		return this.dimensionTypeRegistration;
	}

	public ResourceKey<Level> dimension() {
		return this.dimension;
	}

	@Override
	public RandomSource getRandom() {
		return this.random;
	}

	@Override
	public boolean isStateAtPosition(BlockPos blockPos, Predicate<BlockState> predicate) {
		return predicate.test(this.getBlockState(blockPos));
	}

	@Override
	public boolean isFluidAtPosition(BlockPos blockPos, Predicate<FluidState> predicate) {
		return predicate.test(this.getFluidState(blockPos));
	}

	public abstract RecipeAccess recipeAccess();

	public BlockPos getBlockRandomPos(int i, int j, int k, int l) {
		this.randValue = this.randValue * 3 + 1013904223;
		int m = this.randValue >> 2;
		return new BlockPos(i + (m & 15), j + (m >> 16 & l), k + (m >> 8 & 15));
	}

	public boolean noSave() {
		return false;
	}

	@Override
	public BiomeManager getBiomeManager() {
		return this.biomeManager;
	}

	public final boolean isDebug() {
		return this.isDebug;
	}

	protected abstract LevelEntityGetter<Entity> getEntities();

	@Override
	public long nextSubTickCount() {
		return this.subTickCount++;
	}

	@Override
	public RegistryAccess registryAccess() {
		return this.registryAccess;
	}

	public DamageSources damageSources() {
		return this.damageSources;
	}

	public abstract EnvironmentAttributeSystem environmentAttributes();

	public abstract PotionBrewing potionBrewing();

	public abstract FuelValues fuelValues();

	public int getClientLeafTintColor(BlockPos blockPos) {
		return 0;
	}

	public PalettedContainerFactory palettedContainerFactory() {
		return this.palettedContainerFactory;
	}

	public enum ExplosionInteraction implements StringRepresentable {
		NONE("none"),
		BLOCK("block"),
		MOB("mob"),
		TNT("tnt"),
		TRIGGER("trigger");

		public static final Codec<Level.ExplosionInteraction> CODEC = StringRepresentable.fromEnum(Level.ExplosionInteraction::values);
		private final String id;

		ExplosionInteraction(final String string2) {
			this.id = string2;
		}

		@Override
		public String getSerializedName() {
			return this.id;
		}
	}
}
