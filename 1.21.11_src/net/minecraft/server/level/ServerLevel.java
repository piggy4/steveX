package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.Mth;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.LevelDebugSynchronizers;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ReputationEventHandler;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelCallback;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.level.portal.PortalForcer;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapIndex;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.waypoints.WaypointTransmitter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ServerLevel extends Level implements ServerEntityGetter, WorldGenLevel {
	public static final BlockPos END_SPAWN_POINT = new BlockPos(100, 50, 0);
	public static final IntProvider RAIN_DELAY = UniformInt.of(12000, 180000);
	public static final IntProvider RAIN_DURATION = UniformInt.of(12000, 24000);
	private static final IntProvider THUNDER_DELAY = UniformInt.of(12000, 180000);
	public static final IntProvider THUNDER_DURATION = UniformInt.of(3600, 15600);
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int EMPTY_TIME_NO_TICK = 300;
	private static final int MAX_SCHEDULED_TICKS_PER_TICK = 65536;
	final List<ServerPlayer> players = Lists.newArrayList();
	private final ServerChunkCache chunkSource;
	private final MinecraftServer server;
	private final ServerLevelData serverLevelData;
	final EntityTickList entityTickList = new EntityTickList();
	private final ServerWaypointManager waypointManager;
	private final EnvironmentAttributeSystem environmentAttributes;
	private final PersistentEntitySectionManager<Entity> entityManager;
	private final GameEventDispatcher gameEventDispatcher;
	public boolean noSave;
	private final SleepStatus sleepStatus;
	private int emptyTime;
	private final PortalForcer portalForcer;
	private final LevelTicks<Block> blockTicks = new LevelTicks<>(this::isPositionTickingWithEntitiesLoaded);
	private final LevelTicks<Fluid> fluidTicks = new LevelTicks<>(this::isPositionTickingWithEntitiesLoaded);
	private final PathTypeCache pathTypesByPosCache = new PathTypeCache();
	final Set<Mob> navigatingMobs = new ObjectOpenHashSet<>();
	volatile boolean isUpdatingNavigations;
	protected final Raids raids;
	private final ObjectLinkedOpenHashSet<BlockEventData> blockEvents = new ObjectLinkedOpenHashSet<>();
	private final List<BlockEventData> blockEventsToReschedule = new ArrayList<>(64);
	private boolean handlingTick;
	private final List<CustomSpawner> customSpawners;
	private @Nullable EndDragonFight dragonFight;
	final Int2ObjectMap<EnderDragonPart> dragonParts = new Int2ObjectOpenHashMap<>();
	private final StructureManager structureManager;
	private final StructureCheck structureCheck;
	private final boolean tickTime;
	private final RandomSequences randomSequences;
	final LevelDebugSynchronizers debugSynchronizers = new LevelDebugSynchronizers(this);

	public ServerLevel(
		MinecraftServer minecraftServer,
		Executor executor,
		LevelStorageSource.LevelStorageAccess levelStorageAccess,
		ServerLevelData serverLevelData,
		ResourceKey<Level> resourceKey,
		LevelStem levelStem,
		boolean bl,
		long l,
		List<CustomSpawner> list,
		boolean bl2,
		@Nullable RandomSequences randomSequences
	) {
		super(serverLevelData, resourceKey, minecraftServer.registryAccess(), levelStem.type(), false, bl, l, minecraftServer.getMaxChainedNeighborUpdates());
		this.tickTime = bl2;
		this.server = minecraftServer;
		this.customSpawners = list;
		this.serverLevelData = serverLevelData;
		ChunkGenerator chunkGenerator = levelStem.generator();
		boolean bl3 = minecraftServer.forceSynchronousWrites();
		DataFixer dataFixer = minecraftServer.getFixerUpper();
		EntityPersistentStorage<Entity> entityPersistentStorage = new EntityStorage(
			new SimpleRegionStorage(
				new RegionStorageInfo(levelStorageAccess.getLevelId(), resourceKey, "entities"),
				levelStorageAccess.getDimensionPath(resourceKey).resolve("entities"),
				dataFixer,
				bl3,
				DataFixTypes.ENTITY_CHUNK
			),
			this,
			minecraftServer
		);
		this.entityManager = new PersistentEntitySectionManager<>(Entity.class, new ServerLevel.EntityCallbacks(), entityPersistentStorage);
		this.chunkSource = new ServerChunkCache(
			this,
			levelStorageAccess,
			dataFixer,
			minecraftServer.getStructureManager(),
			executor,
			chunkGenerator,
			minecraftServer.getPlayerList().getViewDistance(),
			minecraftServer.getPlayerList().getSimulationDistance(),
			bl3,
			this.entityManager::updateChunkStatus,
			() -> minecraftServer.overworld().getDataStorage()
		);
		this.chunkSource.getGeneratorState().ensureStructuresGenerated();
		this.portalForcer = new PortalForcer(this);
		if (this.canHaveWeather()) {
			this.prepareWeather();
		}

		this.raids = this.getDataStorage().computeIfAbsent(Raids.getType(this.dimensionTypeRegistration()));
		if (!minecraftServer.isSingleplayer()) {
			serverLevelData.setGameType(minecraftServer.getDefaultGameType());
		}

		long m = minecraftServer.getWorldData().worldGenOptions().seed();
		this.structureCheck = new StructureCheck(
			this.chunkSource.chunkScanner(),
			this.registryAccess(),
			minecraftServer.getStructureManager(),
			resourceKey,
			chunkGenerator,
			this.chunkSource.randomState(),
			this,
			chunkGenerator.getBiomeSource(),
			m,
			dataFixer
		);
		this.structureManager = new StructureManager(this, minecraftServer.getWorldData().worldGenOptions(), this.structureCheck);
		if (this.dimension() == Level.END && this.dimensionTypeRegistration().is(BuiltinDimensionTypes.END)) {
			this.dragonFight = new EndDragonFight(this, m, minecraftServer.getWorldData().endDragonFightData());
		} else {
			this.dragonFight = null;
		}

		this.sleepStatus = new SleepStatus();
		this.gameEventDispatcher = new GameEventDispatcher(this);
		this.randomSequences = Objects.requireNonNullElseGet(randomSequences, () -> this.getDataStorage().computeIfAbsent(RandomSequences.TYPE));
		this.waypointManager = new ServerWaypointManager();
		this.environmentAttributes = EnvironmentAttributeSystem.builder().addDefaultLayers(this).build();
		this.updateSkyBrightness();
	}

	@Deprecated
	@VisibleForTesting
	public void setDragonFight(@Nullable EndDragonFight endDragonFight) {
		this.dragonFight = endDragonFight;
	}

	public void setWeatherParameters(int i, int j, boolean bl, boolean bl2) {
		this.serverLevelData.setClearWeatherTime(i);
		this.serverLevelData.setRainTime(j);
		this.serverLevelData.setThunderTime(j);
		this.serverLevelData.setRaining(bl);
		this.serverLevelData.setThundering(bl2);
	}

	@Override
	public Holder<Biome> getUncachedNoiseBiome(int i, int j, int k) {
		return this.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(i, j, k, this.getChunkSource().randomState().sampler());
	}

	public StructureManager structureManager() {
		return this.structureManager;
	}

	@Override
	public EnvironmentAttributeSystem environmentAttributes() {
		return this.environmentAttributes;
	}

	public void tick(BooleanSupplier booleanSupplier) {
		ProfilerFiller profilerFiller = Profiler.get();
		this.handlingTick = true;
		TickRateManager tickRateManager = this.tickRateManager();
		boolean bl = tickRateManager.runsNormally();
		if (bl) {
			profilerFiller.push("world border");
			this.getWorldBorder().tick();
			profilerFiller.popPush("weather");
			this.advanceWeatherCycle();
			profilerFiller.pop();
		}

		int i = this.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
		if (this.sleepStatus.areEnoughSleeping(i) && this.sleepStatus.areEnoughDeepSleeping(i, this.players)) {
			if (this.getGameRules().get(GameRules.ADVANCE_TIME)) {
				long l = this.levelData.getDayTime() + 24000L;
				this.setDayTime(l - l % 24000L);
			}

			this.wakeUpAllPlayers();
			if (this.getGameRules().get(GameRules.ADVANCE_WEATHER) && this.isRaining()) {
				this.resetWeatherCycle();
			}
		}

		this.updateSkyBrightness();
		if (bl) {
			this.tickTime();
		}

		profilerFiller.push("tickPending");
		if (!this.isDebug() && bl) {
			long l = this.getGameTime();
			profilerFiller.push("blockTicks");
			this.blockTicks.tick(l, 65536, this::tickBlock);
			profilerFiller.popPush("fluidTicks");
			this.fluidTicks.tick(l, 65536, this::tickFluid);
			profilerFiller.pop();
		}

		profilerFiller.popPush("raid");
		if (bl) {
			this.raids.tick(this);
		}

		profilerFiller.popPush("chunkSource");
		this.getChunkSource().tick(booleanSupplier, true);
		profilerFiller.popPush("blockEvents");
		if (bl) {
			this.runBlockEvents();
		}

		this.handlingTick = false;
		profilerFiller.pop();
		boolean bl2 = this.chunkSource.hasActiveTickets();
		if (bl2) {
			this.resetEmptyTime();
		}

		if (bl) {
			this.emptyTime++;
		}

		if (this.emptyTime < 300) {
			profilerFiller.push("entities");
			if (this.dragonFight != null && bl) {
				profilerFiller.push("dragonFight");
				this.dragonFight.tick();
				profilerFiller.pop();
			}

			this.entityTickList.forEach(entity -> {
				if (!entity.isRemoved()) {
					if (!tickRateManager.isEntityFrozen(entity)) {
						profilerFiller.push("checkDespawn");
						entity.checkDespawn();
						profilerFiller.pop();
						if (entity instanceof ServerPlayer || this.chunkSource.chunkMap.getDistanceManager().inEntityTickingRange(entity.chunkPosition().toLong())) {
							Entity entity2 = entity.getVehicle();
							if (entity2 != null) {
								if (!entity2.isRemoved() && entity2.hasPassenger(entity)) {
									return;
								}

								entity.stopRiding();
							}

							profilerFiller.push("tick");
							this.guardEntityTick(this::tickNonPassenger, entity);
							profilerFiller.pop();
						}
					}
				}
			});
			profilerFiller.popPush("blockEntities");
			this.tickBlockEntities();
			profilerFiller.pop();
		}

		profilerFiller.push("entityManagement");
		this.entityManager.tick();
		profilerFiller.pop();
		profilerFiller.push("debugSynchronizers");
		if (this.debugSynchronizers.hasAnySubscriberFor(DebugSubscriptions.NEIGHBOR_UPDATES)) {
			this.neighborUpdater.setDebugListener(blockPos -> this.debugSynchronizers.broadcastEventToTracking(blockPos, DebugSubscriptions.NEIGHBOR_UPDATES, blockPos));
		} else {
			this.neighborUpdater.setDebugListener(null);
		}

		this.debugSynchronizers.tick(this.server.debugSubscribers());
		profilerFiller.pop();
		this.environmentAttributes().invalidateTickCache();
	}

	@Override
	public boolean shouldTickBlocksAt(long l) {
		return this.chunkSource.chunkMap.getDistanceManager().inBlockTickingRange(l);
	}

	protected void tickTime() {
		if (this.tickTime) {
			long l = this.levelData.getGameTime() + 1L;
			this.serverLevelData.setGameTime(l);
			Profiler.get().push("scheduledFunctions");
			this.serverLevelData.getScheduledEvents().tick(this.server, l);
			Profiler.get().pop();
			if (this.getGameRules().get(GameRules.ADVANCE_TIME)) {
				this.setDayTime(this.levelData.getDayTime() + 1L);
			}
		}
	}

	public void setDayTime(long l) {
		this.serverLevelData.setDayTime(l);
	}

	public long getDayCount() {
		return this.getDayTime() / 24000L;
	}

	public void tickCustomSpawners(boolean bl) {
		for (CustomSpawner customSpawner : this.customSpawners) {
			customSpawner.tick(this, bl);
		}
	}

	private void wakeUpAllPlayers() {
		this.sleepStatus.removeAllSleepers();
		this.players.stream().filter(LivingEntity::isSleeping).collect(Collectors.toList()).forEach(serverPlayer -> serverPlayer.stopSleepInBed(false, false));
	}

	public void tickChunk(LevelChunk levelChunk, int i) {
		ChunkPos chunkPos = levelChunk.getPos();
		int j = chunkPos.getMinBlockX();
		int k = chunkPos.getMinBlockZ();
		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push("iceandsnow");

		for (int l = 0; l < i; l++) {
			if (this.random.nextInt(48) == 0) {
				this.tickPrecipitation(this.getBlockRandomPos(j, 0, k, 15));
			}
		}

		profilerFiller.popPush("tickBlocks");
		if (i > 0) {
			LevelChunkSection[] levelChunkSections = levelChunk.getSections();

			for (int m = 0; m < levelChunkSections.length; m++) {
				LevelChunkSection levelChunkSection = levelChunkSections[m];
				if (levelChunkSection.isRandomlyTicking()) {
					int n = levelChunk.getSectionYFromSectionIndex(m);
					int o = SectionPos.sectionToBlockCoord(n);

					for (int p = 0; p < i; p++) {
						BlockPos blockPos = this.getBlockRandomPos(j, o, k, 15);
						profilerFiller.push("randomTick");
						BlockState blockState = levelChunkSection.getBlockState(blockPos.getX() - j, blockPos.getY() - o, blockPos.getZ() - k);
						if (blockState.isRandomlyTicking()) {
							blockState.randomTick(this, blockPos, this.random);
						}

						FluidState fluidState = blockState.getFluidState();
						if (fluidState.isRandomlyTicking()) {
							fluidState.randomTick(this, blockPos, this.random);
						}

						profilerFiller.pop();
					}
				}
			}
		}

		profilerFiller.pop();
	}

	public void tickThunder(LevelChunk levelChunk) {
		ChunkPos chunkPos = levelChunk.getPos();
		boolean bl = this.isRaining();
		int i = chunkPos.getMinBlockX();
		int j = chunkPos.getMinBlockZ();
		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push("thunder");
		if (bl && this.isThundering() && this.random.nextInt(100000) == 0) {
			BlockPos blockPos = this.findLightningTargetAround(this.getBlockRandomPos(i, 0, j, 15));
			if (this.isRainingAt(blockPos)) {
				DifficultyInstance difficultyInstance = this.getCurrentDifficultyAt(blockPos);
				boolean bl2 = this.getGameRules().get(GameRules.SPAWN_MOBS)
					&& this.random.nextDouble() < difficultyInstance.getEffectiveDifficulty() * 0.01
					&& !this.getBlockState(blockPos.below()).is(BlockTags.LIGHTNING_RODS);
				if (bl2) {
					SkeletonHorse skeletonHorse = EntityType.SKELETON_HORSE.create(this, EntitySpawnReason.EVENT);
					if (skeletonHorse != null) {
						skeletonHorse.setTrap(true);
						skeletonHorse.setAge(0);
						skeletonHorse.setPos(blockPos.getX(), blockPos.getY(), blockPos.getZ());
						this.addFreshEntity(skeletonHorse);
					}
				}

				LightningBolt lightningBolt = EntityType.LIGHTNING_BOLT.create(this, EntitySpawnReason.EVENT);
				if (lightningBolt != null) {
					lightningBolt.snapTo(Vec3.atBottomCenterOf(blockPos));
					lightningBolt.setVisualOnly(bl2);
					this.addFreshEntity(lightningBolt);
				}
			}
		}

		profilerFiller.pop();
	}

	@VisibleForTesting
	public void tickPrecipitation(BlockPos blockPos) {
		BlockPos blockPos2 = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockPos);
		BlockPos blockPos3 = blockPos2.below();
		Biome biome = this.getBiome(blockPos2).value();
		if (biome.shouldFreeze(this, blockPos3)) {
			this.setBlockAndUpdate(blockPos3, Blocks.ICE.defaultBlockState());
		}

		if (this.isRaining()) {
			int i = this.getGameRules().get(GameRules.MAX_SNOW_ACCUMULATION_HEIGHT);
			if (i > 0 && biome.shouldSnow(this, blockPos2)) {
				BlockState blockState = this.getBlockState(blockPos2);
				if (blockState.is(Blocks.SNOW)) {
					int j = blockState.getValue(SnowLayerBlock.LAYERS);
					if (j < Math.min(i, 8)) {
						BlockState blockState2 = blockState.setValue(SnowLayerBlock.LAYERS, j + 1);
						Block.pushEntitiesUp(blockState, blockState2, this, blockPos2);
						this.setBlockAndUpdate(blockPos2, blockState2);
					}
				} else {
					this.setBlockAndUpdate(blockPos2, Blocks.SNOW.defaultBlockState());
				}
			}

			Biome.Precipitation precipitation = biome.getPrecipitationAt(blockPos3, this.getSeaLevel());
			if (precipitation != Biome.Precipitation.NONE) {
				BlockState blockState3 = this.getBlockState(blockPos3);
				blockState3.getBlock().handlePrecipitation(blockState3, this, blockPos3, precipitation);
			}
		}
	}

	private Optional<BlockPos> findLightningRod(BlockPos blockPos) {
		Optional<BlockPos> optional = this.getPoiManager()
			.findClosest(
				holder -> holder.is(PoiTypes.LIGHTNING_ROD),
				blockPosx -> blockPosx.getY() == this.getHeight(Heightmap.Types.WORLD_SURFACE, blockPosx.getX(), blockPosx.getZ()) - 1,
				blockPos,
				128,
				PoiManager.Occupancy.ANY
			);
		return optional.map(blockPosx -> blockPosx.above(1));
	}

	protected BlockPos findLightningTargetAround(BlockPos blockPos) {
		BlockPos blockPos2 = this.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockPos);
		Optional<BlockPos> optional = this.findLightningRod(blockPos2);
		if (optional.isPresent()) {
			return optional.get();
		}

		AABB aABB = AABB.encapsulatingFullBlocks(blockPos2, blockPos2.atY(this.getMaxY() + 1)).inflate(3.0);
		List<LivingEntity> list = this.getEntitiesOfClass(
			LivingEntity.class, aABB, livingEntity -> livingEntity.isAlive() && this.canSeeSky(livingEntity.blockPosition())
		);
		if (!list.isEmpty()) {
			return list.get(this.random.nextInt(list.size())).blockPosition();
		}

		if (blockPos2.getY() == this.getMinY() - 1) {
			blockPos2 = blockPos2.above(2);
		}

		return blockPos2;
	}

	public boolean isHandlingTick() {
		return this.handlingTick;
	}

	public boolean canSleepThroughNights() {
		return this.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE) <= 100;
	}

	private void announceSleepStatus() {
		if (this.canSleepThroughNights()) {
			if (!this.getServer().isSingleplayer() || this.getServer().isPublished()) {
				int i = this.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
				Component component;
				if (this.sleepStatus.areEnoughSleeping(i)) {
					component = Component.translatable("sleep.skipping_night");
				} else {
					component = Component.translatable("sleep.players_sleeping", this.sleepStatus.amountSleeping(), this.sleepStatus.sleepersNeeded(i));
				}

				for (ServerPlayer serverPlayer : this.players) {
					serverPlayer.displayClientMessage(component, true);
				}
			}
		}
	}

	public void updateSleepingPlayerList() {
		if (!this.players.isEmpty() && this.sleepStatus.update(this.players)) {
			this.announceSleepStatus();
		}
	}

	public ServerScoreboard getScoreboard() {
		return this.server.getScoreboard();
	}

	public ServerWaypointManager getWaypointManager() {
		return this.waypointManager;
	}

	@Override
	public DifficultyInstance getCurrentDifficultyAt(BlockPos blockPos) {
		long l = 0L;
		float f = 0.0F;
		ChunkAccess chunkAccess = this.getChunk(
			SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()), ChunkStatus.FULL, false
		);
		if (chunkAccess != null) {
			l = chunkAccess.getInhabitedTime();
			f = this.getMoonBrightness(blockPos);
		}

		return new DifficultyInstance(this.getDifficulty(), this.getDayTime(), l, f);
	}

	public float getMoonBrightness(BlockPos blockPos) {
		MoonPhase moonPhase = this.environmentAttributes.getValue(EnvironmentAttributes.MOON_PHASE, blockPos);
		return DimensionType.MOON_BRIGHTNESS_PER_PHASE[moonPhase.index()];
	}

	private void advanceWeatherCycle() {
		boolean bl = this.isRaining();
		if (this.canHaveWeather()) {
			if (this.getGameRules().get(GameRules.ADVANCE_WEATHER)) {
				int i = this.serverLevelData.getClearWeatherTime();
				int j = this.serverLevelData.getThunderTime();
				int k = this.serverLevelData.getRainTime();
				boolean bl2 = this.levelData.isThundering();
				boolean bl3 = this.levelData.isRaining();
				if (i > 0) {
					i--;
					j = bl2 ? 0 : 1;
					k = bl3 ? 0 : 1;
					bl2 = false;
					bl3 = false;
				} else {
					if (j > 0) {
						if (--j == 0) {
							bl2 = !bl2;
						}
					} else if (bl2) {
						j = THUNDER_DURATION.sample(this.random);
					} else {
						j = THUNDER_DELAY.sample(this.random);
					}

					if (k > 0) {
						if (--k == 0) {
							bl3 = !bl3;
						}
					} else if (bl3) {
						k = RAIN_DURATION.sample(this.random);
					} else {
						k = RAIN_DELAY.sample(this.random);
					}
				}

				this.serverLevelData.setThunderTime(j);
				this.serverLevelData.setRainTime(k);
				this.serverLevelData.setClearWeatherTime(i);
				this.serverLevelData.setThundering(bl2);
				this.serverLevelData.setRaining(bl3);
			}

			this.oThunderLevel = this.thunderLevel;
			if (this.levelData.isThundering()) {
				this.thunderLevel += 0.01F;
			} else {
				this.thunderLevel -= 0.01F;
			}

			this.thunderLevel = Mth.clamp(this.thunderLevel, 0.0F, 1.0F);
			this.oRainLevel = this.rainLevel;
			if (this.levelData.isRaining()) {
				this.rainLevel += 0.01F;
			} else {
				this.rainLevel -= 0.01F;
			}

			this.rainLevel = Mth.clamp(this.rainLevel, 0.0F, 1.0F);
		}

		if (this.oRainLevel != this.rainLevel) {
			this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, this.rainLevel), this.dimension());
		}

		if (this.oThunderLevel != this.thunderLevel) {
			this.server
				.getPlayerList()
				.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, this.thunderLevel), this.dimension());
		}

		if (bl != this.isRaining()) {
			if (bl) {
				this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0.0F));
			} else {
				this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0F));
			}

			this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, this.rainLevel));
			this.server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, this.thunderLevel));
		}
	}

	@VisibleForTesting
	public void resetWeatherCycle() {
		this.serverLevelData.setRainTime(0);
		this.serverLevelData.setRaining(false);
		this.serverLevelData.setThunderTime(0);
		this.serverLevelData.setThundering(false);
	}

	public void resetEmptyTime() {
		this.emptyTime = 0;
	}

	private void tickFluid(BlockPos blockPos, Fluid fluid) {
		BlockState blockState = this.getBlockState(blockPos);
		FluidState fluidState = blockState.getFluidState();
		if (fluidState.is(fluid)) {
			fluidState.tick(this, blockPos, blockState);
		}
	}

	private void tickBlock(BlockPos blockPos, Block block) {
		BlockState blockState = this.getBlockState(blockPos);
		if (blockState.is(block)) {
			blockState.tick(this, blockPos, this.random);
		}
	}

	public void tickNonPassenger(Entity entity) {
		entity.setOldPosAndRot();
		ProfilerFiller profilerFiller = Profiler.get();
		entity.tickCount++;
		profilerFiller.push(() -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
		profilerFiller.incrementCounter("tickNonPassenger");
		entity.tick();
		profilerFiller.pop();

		for (Entity entity2 : entity.getPassengers()) {
			this.tickPassenger(entity, entity2);
		}
	}

	private void tickPassenger(Entity entity, Entity entity2) {
		if (entity2.isRemoved() || entity2.getVehicle() != entity) {
			entity2.stopRiding();
		} else if (entity2 instanceof Player || this.entityTickList.contains(entity2)) {
			entity2.setOldPosAndRot();
			entity2.tickCount++;
			ProfilerFiller profilerFiller = Profiler.get();
			profilerFiller.push(() -> BuiltInRegistries.ENTITY_TYPE.getKey(entity2.getType()).toString());
			profilerFiller.incrementCounter("tickPassenger");
			entity2.rideTick();
			profilerFiller.pop();

			for (Entity entity3 : entity2.getPassengers()) {
				this.tickPassenger(entity2, entity3);
			}
		}
	}

	public void updateNeighboursOnBlockSet(BlockPos blockPos, BlockState blockState) {
		BlockState blockState2 = this.getBlockState(blockPos);
		Block block = blockState2.getBlock();
		boolean bl = !blockState.is(block);
		if (bl) {
			blockState.affectNeighborsAfterRemoval(this, blockPos, false);
		}

		this.updateNeighborsAt(blockPos, blockState2.getBlock());
		if (blockState2.hasAnalogOutputSignal()) {
			this.updateNeighbourForOutputSignal(blockPos, block);
		}
	}

	@Override
	public boolean mayInteract(Entity entity, BlockPos blockPos) {
		return !(entity instanceof Player player && (this.server.isUnderSpawnProtection(this, blockPos, player) || !this.getWorldBorder().isWithinBounds(blockPos)));
	}

	public void save(@Nullable ProgressListener progressListener, boolean bl, boolean bl2) {
		ServerChunkCache serverChunkCache = this.getChunkSource();
		if (!bl2) {
			if (progressListener != null) {
				progressListener.progressStartNoAbort(Component.translatable("menu.savingLevel"));
			}

			this.saveLevelData(bl);
			if (progressListener != null) {
				progressListener.progressStage(Component.translatable("menu.savingChunks"));
			}

			serverChunkCache.save(bl);
			if (bl) {
				this.entityManager.saveAll();
			} else {
				this.entityManager.autoSave();
			}
		}
	}

	private void saveLevelData(boolean bl) {
		if (this.dragonFight != null) {
			this.server.getWorldData().setEndDragonFightData(this.dragonFight.saveData());
		}

		DimensionDataStorage dimensionDataStorage = this.getChunkSource().getDataStorage();
		if (bl) {
			dimensionDataStorage.saveAndJoin();
		} else {
			dimensionDataStorage.scheduleSave();
		}
	}

	public <T extends Entity> List<? extends T> getEntities(EntityTypeTest<Entity, T> entityTypeTest, Predicate<? super T> predicate) {
		List<T> list = Lists.newArrayList();
		this.getEntities(entityTypeTest, predicate, list);
		return list;
	}

	public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> entityTypeTest, Predicate<? super T> predicate, List<? super T> list) {
		this.getEntities(entityTypeTest, predicate, list, Integer.MAX_VALUE);
	}

	public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> entityTypeTest, Predicate<? super T> predicate, List<? super T> list, int i) {
		this.getEntities().get(entityTypeTest, entity -> {
			if (predicate.test(entity)) {
				list.add(entity);
				if (list.size() >= i) {
					return AbortableIterationConsumer.Continuation.ABORT;
				}
			}

			return AbortableIterationConsumer.Continuation.CONTINUE;
		});
	}

	public List<? extends EnderDragon> getDragons() {
		return this.getEntities(EntityType.ENDER_DRAGON, LivingEntity::isAlive);
	}

	public List<ServerPlayer> getPlayers(Predicate<? super ServerPlayer> predicate) {
		return this.getPlayers(predicate, Integer.MAX_VALUE);
	}

	public List<ServerPlayer> getPlayers(Predicate<? super ServerPlayer> predicate, int i) {
		List<ServerPlayer> list = Lists.newArrayList();

		for (ServerPlayer serverPlayer : this.players) {
			if (predicate.test(serverPlayer)) {
				list.add(serverPlayer);
				if (list.size() >= i) {
					return list;
				}
			}
		}

		return list;
	}

	public @Nullable ServerPlayer getRandomPlayer() {
		List<ServerPlayer> list = this.getPlayers(LivingEntity::isAlive);
		return list.isEmpty() ? null : list.get(this.random.nextInt(list.size()));
	}

	@Override
	public boolean addFreshEntity(Entity entity) {
		return this.addEntity(entity);
	}

	public boolean addWithUUID(Entity entity) {
		return this.addEntity(entity);
	}

	public void addDuringTeleport(Entity entity) {
		if (entity instanceof ServerPlayer serverPlayer) {
			this.addPlayer(serverPlayer);
		} else {
			this.addEntity(entity);
		}
	}

	public void addNewPlayer(ServerPlayer serverPlayer) {
		this.addPlayer(serverPlayer);
	}

	public void addRespawnedPlayer(ServerPlayer serverPlayer) {
		this.addPlayer(serverPlayer);
	}

	private void addPlayer(ServerPlayer serverPlayer) {
		Entity entity = this.getEntity(serverPlayer.getUUID());
		if (entity != null) {
			LOGGER.warn("Force-added player with duplicate UUID {}", serverPlayer.getUUID());
			entity.unRide();
			this.removePlayerImmediately((ServerPlayer)entity, Entity.RemovalReason.DISCARDED);
		}

		this.entityManager.addNewEntity(serverPlayer);
	}

	private boolean addEntity(Entity entity) {
		if (entity.isRemoved()) {
			LOGGER.warn("Tried to add entity {} but it was marked as removed already", EntityType.getKey(entity.getType()));
			return false;
		} else {
			return this.entityManager.addNewEntity(entity);
		}
	}

	public boolean tryAddFreshEntityWithPassengers(Entity entity) {
		if (entity.getSelfAndPassengers().map(Entity::getUUID).anyMatch(this.entityManager::isLoaded)) {
			return false;
		}

		this.addFreshEntityWithPassengers(entity);
		return true;
	}

	public void unload(LevelChunk levelChunk) {
		levelChunk.clearAllBlockEntities();
		levelChunk.unregisterTickContainerFromLevel(this);
		this.debugSynchronizers.dropChunk(levelChunk.getPos());
	}

	public void removePlayerImmediately(ServerPlayer serverPlayer, Entity.RemovalReason removalReason) {
		serverPlayer.remove(removalReason);
	}

	@Override
	public void destroyBlockProgress(int i, BlockPos blockPos, int j) {
		for (ServerPlayer serverPlayer : this.server.getPlayerList().getPlayers()) {
			if (serverPlayer.level() == this && serverPlayer.getId() != i) {
				double d = blockPos.getX() - serverPlayer.getX();
				double e = blockPos.getY() - serverPlayer.getY();
				double f = blockPos.getZ() - serverPlayer.getZ();
				if (d * d + e * e + f * f < 1024.0) {
					serverPlayer.connection.send(new ClientboundBlockDestructionPacket(i, blockPos, j));
				}
			}
		}
	}

	@Override
	public void playSeededSound(
		@Nullable Entity entity, double d, double e, double f, Holder<SoundEvent> holder, SoundSource soundSource, float g, float h, long l
	) {
		this.server
			.getPlayerList()
			.broadcast(
				entity instanceof Player player ? player : null,
				d,
				e,
				f,
				holder.value().getRange(g),
				this.dimension(),
				new ClientboundSoundPacket(holder, soundSource, d, e, f, g, h, l)
			);
	}

	@Override
	public void playSeededSound(@Nullable Entity entity, Entity entity2, Holder<SoundEvent> holder, SoundSource soundSource, float f, float g, long l) {
		this.server
			.getPlayerList()
			.broadcast(
				entity instanceof Player player ? player : null,
				entity2.getX(),
				entity2.getY(),
				entity2.getZ(),
				holder.value().getRange(f),
				this.dimension(),
				new ClientboundSoundEntityPacket(holder, soundSource, entity2, f, g, l)
			);
	}

	@Override
	public void globalLevelEvent(int i, BlockPos blockPos, int j) {
		if (this.getGameRules().get(GameRules.GLOBAL_SOUND_EVENTS)) {
			this.server.getPlayerList().getPlayers().forEach(serverPlayer -> {
				Vec3 vec32;
				if (serverPlayer.level() == this) {
					Vec3 vec3 = Vec3.atCenterOf(blockPos);
					if (serverPlayer.distanceToSqr(vec3) < Mth.square(32)) {
						vec32 = vec3;
					} else {
						Vec3 vec33 = vec3.subtract(serverPlayer.position()).normalize();
						vec32 = serverPlayer.position().add(vec33.scale(32.0));
					}
				} else {
					vec32 = serverPlayer.position();
				}

				serverPlayer.connection.send(new ClientboundLevelEventPacket(i, BlockPos.containing(vec32), j, true));
			});
		} else {
			this.levelEvent(null, i, blockPos, j);
		}
	}

	@Override
	public void levelEvent(@Nullable Entity entity, int i, BlockPos blockPos, int j) {
		this.server
			.getPlayerList()
			.broadcast(
				entity instanceof Player player ? player : null,
				blockPos.getX(),
				blockPos.getY(),
				blockPos.getZ(),
				64.0,
				this.dimension(),
				new ClientboundLevelEventPacket(i, blockPos, j, false)
			);
	}

	public int getLogicalHeight() {
		return this.dimensionType().logicalHeight();
	}

	@Override
	public void gameEvent(Holder<GameEvent> holder, Vec3 vec3, GameEvent.Context context) {
		this.gameEventDispatcher.post(holder, vec3, context);
	}

	@Override
	public void sendBlockUpdated(BlockPos blockPos, BlockState blockState, BlockState blockState2, int i) {
		if (this.isUpdatingNavigations) {
			String string = "recursive call to sendBlockUpdated";
			Util.logAndPauseIfInIde("recursive call to sendBlockUpdated", new IllegalStateException("recursive call to sendBlockUpdated"));
		}

		this.getChunkSource().blockChanged(blockPos);
		this.pathTypesByPosCache.invalidate(blockPos);
		VoxelShape voxelShape = blockState.getCollisionShape(this, blockPos);
		VoxelShape voxelShape2 = blockState2.getCollisionShape(this, blockPos);
		if (Shapes.joinIsNotEmpty(voxelShape, voxelShape2, BooleanOp.NOT_SAME)) {
			List<PathNavigation> list = new ObjectArrayList<>();

			for (Mob mob : this.navigatingMobs) {
				PathNavigation pathNavigation = mob.getNavigation();
				if (pathNavigation.shouldRecomputePath(blockPos)) {
					list.add(pathNavigation);
				}
			}

			try {
				this.isUpdatingNavigations = true;

				for (PathNavigation pathNavigation2 : list) {
					pathNavigation2.recomputePath();
				}
			} finally {
				this.isUpdatingNavigations = false;
			}
		}
	}

	@Override
	public void updateNeighborsAt(BlockPos blockPos, Block block) {
		this.updateNeighborsAt(blockPos, block, ExperimentalRedstoneUtils.initialOrientation(this, null, null));
	}

	@Override
	public void updateNeighborsAt(BlockPos blockPos, Block block, @Nullable Orientation orientation) {
		this.neighborUpdater.updateNeighborsAtExceptFromFacing(blockPos, block, null, orientation);
	}

	@Override
	public void updateNeighborsAtExceptFromFacing(BlockPos blockPos, Block block, Direction direction, @Nullable Orientation orientation) {
		this.neighborUpdater.updateNeighborsAtExceptFromFacing(blockPos, block, direction, orientation);
	}

	@Override
	public void neighborChanged(BlockPos blockPos, Block block, @Nullable Orientation orientation) {
		this.neighborUpdater.neighborChanged(blockPos, block, orientation);
	}

	@Override
	public void neighborChanged(BlockState blockState, BlockPos blockPos, Block block, @Nullable Orientation orientation, boolean bl) {
		this.neighborUpdater.neighborChanged(blockState, blockPos, block, orientation, bl);
	}

	@Override
	public void broadcastEntityEvent(Entity entity, byte b) {
		this.getChunkSource().sendToTrackingPlayersAndSelf(entity, new ClientboundEntityEventPacket(entity, b));
	}

	@Override
	public void broadcastDamageEvent(Entity entity, DamageSource damageSource) {
		this.getChunkSource().sendToTrackingPlayersAndSelf(entity, new ClientboundDamageEventPacket(entity, damageSource));
	}

	public ServerChunkCache getChunkSource() {
		return this.chunkSource;
	}

	@Override
	public void explode(
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
	) {
		Explosion.BlockInteraction blockInteraction = switch (explosionInteraction) {
			case NONE -> Explosion.BlockInteraction.KEEP;
			case BLOCK -> this.getDestroyType(GameRules.BLOCK_EXPLOSION_DROP_DECAY);
			case MOB -> this.getGameRules().get(GameRules.MOB_GRIEFING) ? this.getDestroyType(GameRules.MOB_EXPLOSION_DROP_DECAY) : Explosion.BlockInteraction.KEEP;
			case TNT -> this.getDestroyType(GameRules.TNT_EXPLOSION_DROP_DECAY);
			case TRIGGER -> Explosion.BlockInteraction.TRIGGER_BLOCK;
		};
		Vec3 vec3 = new Vec3(d, e, f);
		ServerExplosion serverExplosion = new ServerExplosion(this, entity, damageSource, explosionDamageCalculator, vec3, g, bl, blockInteraction);
		int i = serverExplosion.explode();
		ParticleOptions particleOptions3 = serverExplosion.isSmall() ? particleOptions : particleOptions2;

		for (ServerPlayer serverPlayer : this.players) {
			if (serverPlayer.distanceToSqr(vec3) < 4096.0) {
				Optional<Vec3> optional = Optional.ofNullable(serverExplosion.getHitPlayers().get(serverPlayer));
				serverPlayer.connection.send(new ClientboundExplodePacket(vec3, g, i, optional, particleOptions3, holder, weightedList));
			}
		}
	}

	private Explosion.BlockInteraction getDestroyType(GameRule<Boolean> gameRule) {
		return this.getGameRules().get(gameRule) ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.DESTROY;
	}

	@Override
	public void blockEvent(BlockPos blockPos, Block block, int i, int j) {
		this.blockEvents.add(new BlockEventData(blockPos, block, i, j));
	}

	private void runBlockEvents() {
		this.blockEventsToReschedule.clear();

		while (!this.blockEvents.isEmpty()) {
			BlockEventData blockEventData = this.blockEvents.removeFirst();
			if (this.shouldTickBlocksAt(blockEventData.pos())) {
				if (this.doBlockEvent(blockEventData)) {
					this.server
						.getPlayerList()
						.broadcast(
							null,
							blockEventData.pos().getX(),
							blockEventData.pos().getY(),
							blockEventData.pos().getZ(),
							64.0,
							this.dimension(),
							new ClientboundBlockEventPacket(blockEventData.pos(), blockEventData.block(), blockEventData.paramA(), blockEventData.paramB())
						);
				}
			} else {
				this.blockEventsToReschedule.add(blockEventData);
			}
		}

		this.blockEvents.addAll(this.blockEventsToReschedule);
	}

	private boolean doBlockEvent(BlockEventData blockEventData) {
		BlockState blockState = this.getBlockState(blockEventData.pos());
		return blockState.is(blockEventData.block()) ? blockState.triggerEvent(this, blockEventData.pos(), blockEventData.paramA(), blockEventData.paramB()) : false;
	}

	public LevelTicks<Block> getBlockTicks() {
		return this.blockTicks;
	}

	public LevelTicks<Fluid> getFluidTicks() {
		return this.fluidTicks;
	}

	@Override
	public MinecraftServer getServer() {
		return this.server;
	}

	public PortalForcer getPortalForcer() {
		return this.portalForcer;
	}

	public StructureTemplateManager getStructureManager() {
		return this.server.getStructureManager();
	}

	public <T extends ParticleOptions> int sendParticles(T particleOptions, double d, double e, double f, int i, double g, double h, double j, double k) {
		return this.sendParticles(particleOptions, false, false, d, e, f, i, g, h, j, k);
	}

	public <T extends ParticleOptions> int sendParticles(
		T particleOptions, boolean bl, boolean bl2, double d, double e, double f, int i, double g, double h, double j, double k
	) {
		ClientboundLevelParticlesPacket clientboundLevelParticlesPacket = new ClientboundLevelParticlesPacket(
			particleOptions, bl, bl2, d, e, f, (float)g, (float)h, (float)j, (float)k, i
		);
		int l = 0;

		for (int m = 0; m < this.players.size(); m++) {
			ServerPlayer serverPlayer = this.players.get(m);
			if (this.sendParticles(serverPlayer, bl, d, e, f, clientboundLevelParticlesPacket)) {
				l++;
			}
		}

		return l;
	}

	public <T extends ParticleOptions> boolean sendParticles(
		ServerPlayer serverPlayer, T particleOptions, boolean bl, boolean bl2, double d, double e, double f, int i, double g, double h, double j, double k
	) {
		Packet<?> packet = new ClientboundLevelParticlesPacket(particleOptions, bl, bl2, d, e, f, (float)g, (float)h, (float)j, (float)k, i);
		return this.sendParticles(serverPlayer, bl, d, e, f, packet);
	}

	public final boolean sendParticles(ServerPlayer serverPlayer, boolean bl, double d, double e, double f, Packet<?> packet) {
		if (serverPlayer.level() != this) {
			return false;
		} else {
			BlockPos blockPos = serverPlayer.blockPosition();
			if (blockPos.closerToCenterThan(new Vec3(d, e, f), bl ? 512.0 : 32.0)) {
				serverPlayer.connection.send(packet);
				return true;
			} else {
				return false;
			}
		}
	}

	@Override
	public @Nullable Entity getEntity(int i) {
		return this.getEntities().get(i);
	}

	@Override
	public @Nullable Entity getEntityInAnyDimension(UUID uUID) {
		Entity entity = this.getEntity(uUID);
		if (entity != null) {
			return entity;
		}

		for (ServerLevel serverLevel : this.getServer().getAllLevels()) {
			if (serverLevel != this) {
				Entity entity2 = serverLevel.getEntity(uUID);
				if (entity2 != null) {
					return entity2;
				}
			}
		}

		return null;
	}

	@Override
	public @Nullable Player getPlayerInAnyDimension(UUID uUID) {
		return this.getServer().getPlayerList().getPlayer(uUID);
	}

	@Deprecated
	public @Nullable Entity getEntityOrPart(int i) {
		Entity entity = this.getEntities().get(i);
		return entity != null ? entity : this.dragonParts.get(i);
	}

	@Override
	public Collection<EnderDragonPart> dragonParts() {
		return this.dragonParts.values();
	}

	public @Nullable BlockPos findNearestMapStructure(TagKey<Structure> tagKey, BlockPos blockPos, int i, boolean bl) {
		if (!this.server.getWorldData().worldGenOptions().generateStructures()) {
			return null;
		}

		Optional<HolderSet.Named<Structure>> optional = this.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(tagKey);
		if (optional.isEmpty()) {
			return null;
		}

		Pair<BlockPos, Holder<Structure>> pair = this.getChunkSource().getGenerator().findNearestMapStructure(this, optional.get(), blockPos, i, bl);
		return pair != null ? pair.getFirst() : null;
	}

	public @Nullable Pair<BlockPos, Holder<Biome>> findClosestBiome3d(Predicate<Holder<Biome>> predicate, BlockPos blockPos, int i, int j, int k) {
		return this.getChunkSource()
			.getGenerator()
			.getBiomeSource()
			.findClosestBiome3d(blockPos, i, j, k, predicate, this.getChunkSource().randomState().sampler(), this);
	}

	@Override
	public WorldBorder getWorldBorder() {
		WorldBorder worldBorder = this.getDataStorage().computeIfAbsent(WorldBorder.TYPE);
		worldBorder.applyInitialSettings(this.levelData.getGameTime());
		return worldBorder;
	}

	public RecipeManager recipeAccess() {
		return this.server.getRecipeManager();
	}

	@Override
	public TickRateManager tickRateManager() {
		return this.server.tickRateManager();
	}

	@Override
	public boolean noSave() {
		return this.noSave;
	}

	public DimensionDataStorage getDataStorage() {
		return this.getChunkSource().getDataStorage();
	}

	@Override
	public @Nullable MapItemSavedData getMapData(MapId mapId) {
		return this.getServer().overworld().getDataStorage().get(MapItemSavedData.type(mapId));
	}

	public void setMapData(MapId mapId, MapItemSavedData mapItemSavedData) {
		this.getServer().overworld().getDataStorage().set(MapItemSavedData.type(mapId), mapItemSavedData);
	}

	public MapId getFreeMapId() {
		return this.getServer().overworld().getDataStorage().computeIfAbsent(MapIndex.TYPE).getNextMapId();
	}

	@Override
	public void setRespawnData(LevelData.RespawnData respawnData) {
		this.getServer().setRespawnData(respawnData);
	}

	@Override
	public LevelData.RespawnData getRespawnData() {
		return this.getServer().getRespawnData();
	}

	public LongSet getForceLoadedChunks() {
		return this.chunkSource.getForceLoadedChunks();
	}

	public boolean setChunkForced(int i, int j, boolean bl) {
		boolean bl2 = this.chunkSource.updateChunkForced(new ChunkPos(i, j), bl);
		if (bl && bl2) {
			this.getChunk(i, j);
		}

		return bl2;
	}

	@Override
	public List<ServerPlayer> players() {
		return this.players;
	}

	@Override
	public void updatePOIOnBlockStateChange(BlockPos blockPos, BlockState blockState, BlockState blockState2) {
		Optional<Holder<PoiType>> optional = PoiTypes.forState(blockState);
		Optional<Holder<PoiType>> optional2 = PoiTypes.forState(blockState2);
		if (!Objects.equals(optional, optional2)) {
			BlockPos blockPos2 = blockPos.immutable();
			optional.ifPresent(holder -> this.getServer().execute(() -> {
				this.getPoiManager().remove(blockPos2);
				this.debugSynchronizers.dropPoi(blockPos2);
			}));
			optional2.ifPresent(holder -> this.getServer().execute(() -> {
				PoiRecord poiRecord = this.getPoiManager().add(blockPos2, (Holder<PoiType>)holder);
				if (poiRecord != null) {
					this.debugSynchronizers.registerPoi(poiRecord);
				}
			}));
		}
	}

	public PoiManager getPoiManager() {
		return this.getChunkSource().getPoiManager();
	}

	public boolean isVillage(BlockPos blockPos) {
		return this.isCloseToVillage(blockPos, 1);
	}

	public boolean isVillage(SectionPos sectionPos) {
		return this.isVillage(sectionPos.center());
	}

	public boolean isCloseToVillage(BlockPos blockPos, int i) {
		return i > 6 ? false : this.sectionsToVillage(SectionPos.of(blockPos)) <= i;
	}

	public int sectionsToVillage(SectionPos sectionPos) {
		return this.getPoiManager().sectionsToVillage(sectionPos);
	}

	public Raids getRaids() {
		return this.raids;
	}

	public @Nullable Raid getRaidAt(BlockPos blockPos) {
		return this.raids.getNearbyRaid(blockPos, 9216);
	}

	public boolean isRaided(BlockPos blockPos) {
		return this.getRaidAt(blockPos) != null;
	}

	public void onReputationEvent(ReputationEventType reputationEventType, Entity entity, ReputationEventHandler reputationEventHandler) {
		reputationEventHandler.onReputationEventFrom(reputationEventType, entity);
	}

	public void saveDebugReport(Path path) throws IOException {
		ChunkMap chunkMap = this.getChunkSource().chunkMap;

		try (Writer writer = Files.newBufferedWriter(path.resolve("stats.txt"))) {
			writer.write(String.format(Locale.ROOT, "spawning_chunks: %d\n", chunkMap.getDistanceManager().getNaturalSpawnChunkCount()));
			NaturalSpawner.SpawnState spawnState = this.getChunkSource().getLastSpawnState();
			if (spawnState != null) {
				for (Entry<MobCategory> entry : spawnState.getMobCategoryCounts().object2IntEntrySet()) {
					writer.write(String.format(Locale.ROOT, "spawn_count.%s: %d\n", entry.getKey().getName(), entry.getIntValue()));
				}
			}

			writer.write(String.format(Locale.ROOT, "entities: %s\n", this.entityManager.gatherStats()));
			writer.write(String.format(Locale.ROOT, "block_entity_tickers: %d\n", this.blockEntityTickers.size()));
			writer.write(String.format(Locale.ROOT, "block_ticks: %d\n", this.getBlockTicks().count()));
			writer.write(String.format(Locale.ROOT, "fluid_ticks: %d\n", this.getFluidTicks().count()));
			writer.write("distance_manager: " + chunkMap.getDistanceManager().getDebugStatus() + "\n");
			writer.write(String.format(Locale.ROOT, "pending_tasks: %d\n", this.getChunkSource().getPendingTasksCount()));
		}

		CrashReport crashReport = new CrashReport("Level dump", new Exception("dummy"));
		this.fillReportDetails(crashReport);

		try (Writer writer2 = Files.newBufferedWriter(path.resolve("example_crash.txt"))) {
			writer2.write(crashReport.getFriendlyReport(ReportType.TEST));
		}

		Path path2 = path.resolve("chunks.csv");

		try (Writer writer3 = Files.newBufferedWriter(path2)) {
			chunkMap.dumpChunks(writer3);
		}

		Path path3 = path.resolve("entity_chunks.csv");

		try (Writer writer4 = Files.newBufferedWriter(path3)) {
			this.entityManager.dumpSections(writer4);
		}

		Path path4 = path.resolve("entities.csv");

		try (Writer writer5 = Files.newBufferedWriter(path4)) {
			dumpEntities(writer5, this.getEntities().getAll());
		}

		Path path5 = path.resolve("block_entities.csv");

		try (Writer writer6 = Files.newBufferedWriter(path5)) {
			this.dumpBlockEntityTickers(writer6);
		}
	}

	private static void dumpEntities(Writer writer, Iterable<Entity> iterable) throws IOException {
		CsvOutput csvOutput = CsvOutput.builder()
			.addColumn("x")
			.addColumn("y")
			.addColumn("z")
			.addColumn("uuid")
			.addColumn("type")
			.addColumn("alive")
			.addColumn("display_name")
			.addColumn("custom_name")
			.build(writer);

		for (Entity entity : iterable) {
			Component component = entity.getCustomName();
			Component component2 = entity.getDisplayName();
			csvOutput.writeRow(
				entity.getX(),
				entity.getY(),
				entity.getZ(),
				entity.getUUID(),
				BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
				entity.isAlive(),
				component2.getString(),
				component != null ? component.getString() : null
			);
		}
	}

	private void dumpBlockEntityTickers(Writer writer) throws IOException {
		CsvOutput csvOutput = CsvOutput.builder().addColumn("x").addColumn("y").addColumn("z").addColumn("type").build(writer);

		for (TickingBlockEntity tickingBlockEntity : this.blockEntityTickers) {
			BlockPos blockPos = tickingBlockEntity.getPos();
			csvOutput.writeRow(blockPos.getX(), blockPos.getY(), blockPos.getZ(), tickingBlockEntity.getType());
		}
	}

	@VisibleForTesting
	public void clearBlockEvents(BoundingBox boundingBox) {
		this.blockEvents.removeIf(blockEventData -> boundingBox.isInside(blockEventData.pos()));
	}

	@Override
	public float getShade(Direction direction, boolean bl) {
		return 1.0F;
	}

	public Iterable<Entity> getAllEntities() {
		return this.getEntities().getAll();
	}

	@Override
	public String toString() {
		return "ServerLevel[" + this.serverLevelData.getLevelName() + "]";
	}

	public boolean isFlat() {
		return this.server.getWorldData().isFlatWorld();
	}

	@Override
	public long getSeed() {
		return this.server.getWorldData().worldGenOptions().seed();
	}

	public @Nullable EndDragonFight getDragonFight() {
		return this.dragonFight;
	}

	@Override
	public ServerLevel getLevel() {
		return this;
	}

	@VisibleForTesting
	public String getWatchdogStats() {
		return String.format(
			Locale.ROOT,
			"players: %s, entities: %s [%s], block_entities: %d [%s], block_ticks: %d, fluid_ticks: %d, chunk_source: %s",
			this.players.size(),
			this.entityManager.gatherStats(),
			getTypeCount(this.entityManager.getEntityGetter().getAll(), entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()),
			this.blockEntityTickers.size(),
			getTypeCount(this.blockEntityTickers, TickingBlockEntity::getType),
			this.getBlockTicks().count(),
			this.getFluidTicks().count(),
			this.gatherChunkSourceStats()
		);
	}

	private static <T> String getTypeCount(Iterable<T> iterable, Function<T, String> function) {
		try {
			Object2IntOpenHashMap<String> object2IntOpenHashMap = new Object2IntOpenHashMap<>();

			for (T object : iterable) {
				String string = function.apply(object);
				object2IntOpenHashMap.addTo(string, 1);
			}

			return object2IntOpenHashMap.object2IntEntrySet()
				.stream()
				.sorted(Comparator.comparing(Entry::getIntValue).reversed())
				.limit(5L)
				.map(entry -> entry.getKey() + ":" + entry.getIntValue())
				.collect(Collectors.joining(","));
		} catch (Exception exception) {
			return "";
		}
	}

	@Override
	protected LevelEntityGetter<Entity> getEntities() {
		return this.entityManager.getEntityGetter();
	}

	public void addLegacyChunkEntities(Stream<Entity> stream) {
		this.entityManager.addLegacyChunkEntities(stream);
	}

	public void addWorldGenChunkEntities(Stream<Entity> stream) {
		this.entityManager.addWorldGenChunkEntities(stream);
	}

	public void startTickingChunk(LevelChunk levelChunk) {
		levelChunk.unpackTicks(this.getGameTime());
	}

	public void onStructureStartsAvailable(ChunkAccess chunkAccess) {
		this.server.execute(() -> this.structureCheck.onStructureLoad(chunkAccess.getPos(), chunkAccess.getAllStarts()));
	}

	public PathTypeCache getPathTypeCache() {
		return this.pathTypesByPosCache;
	}

	public void waitForEntities(ChunkPos chunkPos, int i) {
		List<ChunkPos> list = ChunkPos.rangeClosed(chunkPos, i).toList();
		this.server.managedBlock(() -> {
			this.entityManager.processPendingLoads();

			for (ChunkPos chunkPosx : list) {
				if (!this.areEntitiesLoaded(chunkPosx.toLong())) {
					return false;
				}
			}

			return true;
		});
	}

	public boolean isSpawningMonsters() {
		return this.getLevelData().getDifficulty() != Difficulty.PEACEFUL
			&& this.getGameRules().get(GameRules.SPAWN_MOBS)
			&& this.getGameRules().get(GameRules.SPAWN_MONSTERS);
	}

	@Override
	public void close() throws IOException {
		super.close();
		this.entityManager.close();
	}

	@Override
	public String gatherChunkSourceStats() {
		return "Chunks[S] W: " + this.chunkSource.gatherStats() + " E: " + this.entityManager.gatherStats();
	}

	public boolean areEntitiesLoaded(long l) {
		return this.entityManager.areEntitiesLoaded(l);
	}

	public boolean isPositionTickingWithEntitiesLoaded(long l) {
		return this.areEntitiesLoaded(l) && this.chunkSource.isPositionTicking(l);
	}

	public boolean isPositionEntityTicking(BlockPos blockPos) {
		return this.entityManager.canPositionTick(blockPos) && this.chunkSource.chunkMap.getDistanceManager().inEntityTickingRange(ChunkPos.asLong(blockPos));
	}

	public boolean areEntitiesActuallyLoadedAndTicking(ChunkPos chunkPos) {
		return this.entityManager.isTicking(chunkPos) && this.entityManager.areEntitiesLoaded(chunkPos.toLong());
	}

	public boolean anyPlayerCloseEnoughForSpawning(BlockPos blockPos) {
		return this.anyPlayerCloseEnoughForSpawning(new ChunkPos(blockPos));
	}

	public boolean anyPlayerCloseEnoughForSpawning(ChunkPos chunkPos) {
		return this.chunkSource.chunkMap.anyPlayerCloseEnoughForSpawning(chunkPos);
	}

	public boolean canSpreadFireAround(BlockPos blockPos) {
		int i = this.getGameRules().get(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER);
		return i == -1 || this.chunkSource.chunkMap.anyPlayerCloseEnoughTo(blockPos, i);
	}

	public boolean canSpawnEntitiesInChunk(ChunkPos chunkPos) {
		return this.entityManager.canPositionTick(chunkPos) && this.getWorldBorder().isWithinBounds(chunkPos);
	}

	@Override
	public FeatureFlagSet enabledFeatures() {
		return this.server.getWorldData().enabledFeatures();
	}

	@Override
	public PotionBrewing potionBrewing() {
		return this.server.potionBrewing();
	}

	@Override
	public FuelValues fuelValues() {
		return this.server.fuelValues();
	}

	public RandomSource getRandomSequence(Identifier identifier) {
		return this.randomSequences.get(identifier, this.getSeed());
	}

	public RandomSequences getRandomSequences() {
		return this.randomSequences;
	}

	public GameRules getGameRules() {
		return this.serverLevelData.getGameRules();
	}

	@Override
	public CrashReportCategory fillReportDetails(CrashReport crashReport) {
		CrashReportCategory crashReportCategory = super.fillReportDetails(crashReport);
		crashReportCategory.setDetail("Loaded entity count", () -> String.valueOf(this.entityManager.count()));
		return crashReportCategory;
	}

	@Override
	public int getSeaLevel() {
		return this.chunkSource.getGenerator().getSeaLevel();
	}

	@Override
	public void onBlockEntityAdded(BlockEntity blockEntity) {
		super.onBlockEntityAdded(blockEntity);
		this.debugSynchronizers.registerBlockEntity(blockEntity);
	}

	public LevelDebugSynchronizers debugSynchronizers() {
		return this.debugSynchronizers;
	}

	public boolean isAllowedToEnterPortal(Level level) {
		return level.dimension() == Level.NETHER ? this.getGameRules().get(GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS) : true;
	}

	public boolean isPvpAllowed() {
		return this.getGameRules().get(GameRules.PVP);
	}

	public boolean isCommandBlockEnabled() {
		return this.getGameRules().get(GameRules.COMMAND_BLOCKS_WORK);
	}

	public boolean isSpawnerBlockEnabled() {
		return this.getGameRules().get(GameRules.SPAWNER_BLOCKS_WORK);
	}

	final class EntityCallbacks implements LevelCallback<Entity> {
		public void onCreated(Entity entity) {
			if (entity instanceof WaypointTransmitter waypointTransmitter && waypointTransmitter.isTransmittingWaypoint()) {
				ServerLevel.this.getWaypointManager().trackWaypoint(waypointTransmitter);
			}
		}

		public void onDestroyed(Entity entity) {
			if (entity instanceof WaypointTransmitter waypointTransmitter) {
				ServerLevel.this.getWaypointManager().untrackWaypoint(waypointTransmitter);
			}

			ServerLevel.this.getScoreboard().entityRemoved(entity);
		}

		public void onTickingStart(Entity entity) {
			ServerLevel.this.entityTickList.add(entity);
		}

		public void onTickingEnd(Entity entity) {
			ServerLevel.this.entityTickList.remove(entity);
		}

		public void onTrackingStart(Entity entity) {
			ServerLevel.this.getChunkSource().addEntity(entity);
			if (entity instanceof ServerPlayer serverPlayer) {
				ServerLevel.this.players.add(serverPlayer);
				if (serverPlayer.isReceivingWaypoints()) {
					ServerLevel.this.getWaypointManager().addPlayer(serverPlayer);
				}

				ServerLevel.this.updateSleepingPlayerList();
			}

			if (entity instanceof WaypointTransmitter waypointTransmitter && waypointTransmitter.isTransmittingWaypoint()) {
				ServerLevel.this.getWaypointManager().trackWaypoint(waypointTransmitter);
			}

			if (entity instanceof Mob mob) {
				if (ServerLevel.this.isUpdatingNavigations) {
					String string = "onTrackingStart called during navigation iteration";
					Util.logAndPauseIfInIde(
						"onTrackingStart called during navigation iteration", new IllegalStateException("onTrackingStart called during navigation iteration")
					);
				}

				ServerLevel.this.navigatingMobs.add(mob);
			}

			if (entity instanceof EnderDragon enderDragon) {
				for (EnderDragonPart enderDragonPart : enderDragon.getSubEntities()) {
					ServerLevel.this.dragonParts.put(enderDragonPart.getId(), enderDragonPart);
				}
			}

			entity.updateDynamicGameEventListener(DynamicGameEventListener::add);
		}

		public void onTrackingEnd(Entity entity) {
			ServerLevel.this.getChunkSource().removeEntity(entity);
			if (entity instanceof ServerPlayer serverPlayer) {
				ServerLevel.this.players.remove(serverPlayer);
				ServerLevel.this.getWaypointManager().removePlayer(serverPlayer);
				ServerLevel.this.updateSleepingPlayerList();
			}

			if (entity instanceof Mob mob) {
				if (ServerLevel.this.isUpdatingNavigations) {
					String string = "onTrackingStart called during navigation iteration";
					Util.logAndPauseIfInIde(
						"onTrackingStart called during navigation iteration", new IllegalStateException("onTrackingStart called during navigation iteration")
					);
				}

				ServerLevel.this.navigatingMobs.remove(mob);
			}

			if (entity instanceof EnderDragon enderDragon) {
				for (EnderDragonPart enderDragonPart : enderDragon.getSubEntities()) {
					ServerLevel.this.dragonParts.remove(enderDragonPart.getId());
				}
			}

			entity.updateDynamicGameEventListener(DynamicGameEventListener::remove);
			ServerLevel.this.debugSynchronizers.dropEntity(entity);
		}

		public void onSectionChange(Entity entity) {
			entity.updateDynamicGameEventListener(DynamicGameEventListener::move);
		}
	}
}
