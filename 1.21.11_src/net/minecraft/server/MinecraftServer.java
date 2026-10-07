package net.minecraft.server;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.DataFixer;
import com.mojang.jtracy.DiscontinuousFrame;
import com.mojang.jtracy.TracyClient;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.Proxy;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.resource.v1.DataResourceStore;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportType;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.SystemReport;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.nbt.Tag;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.obfuscate.DontObfuscate;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ChunkLoadCounter;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DemoMode;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.level.progress.ChunkLoadStatusView;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.network.ServerConnectionListener;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.notifications.NotificationManager;
import net.minecraft.server.notifications.ServerActivityMonitor;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;
import net.minecraft.util.FileUtil;
import net.minecraft.util.ModCheck;
import net.minecraft.util.Mth;
import net.minecraft.util.NativeModuleLister;
import net.minecraft.util.PngInfo;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.Util;
import net.minecraft.util.debug.ServerDebugSubscribers;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.util.debugchart.TpsDebugDimensions;
import net.minecraft.util.profiling.EmptyProfileResults;
import net.minecraft.util.profiling.ProfileResults;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.ResultField;
import net.minecraft.util.profiling.SingleTickProfiler;
import net.minecraft.util.profiling.jfr.Environment;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.util.profiling.metrics.profiling.ActiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.InactiveMetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.MetricsRecorder;
import net.minecraft.util.profiling.metrics.profiling.ServerMetricsSamplersProvider;
import net.minecraft.util.profiling.metrics.storage.MetricsPersister;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.Stopwatches;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.CommandStorage;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.ScoreboardSaveData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.resource.v1.DataResourceStore} injected by mod fabric-resource-loader-v1</p>
 */
public abstract class MinecraftServer
	extends ReentrantBlockableEventLoop<TickTask>
	implements ServerInfo,
	CommandSource,
	ChunkIOErrorReporter,
	DataResourceStore {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final String VANILLA_BRAND = "vanilla";
	private static final float AVERAGE_TICK_TIME_SMOOTHING = 0.8F;
	private static final int TICK_STATS_SPAN = 100;
	private static final long OVERLOADED_THRESHOLD_NANOS = 20L * TimeUtil.NANOSECONDS_PER_SECOND / 20L;
	private static final int OVERLOADED_TICKS_THRESHOLD = 20;
	private static final long OVERLOADED_WARNING_INTERVAL_NANOS = 10L * TimeUtil.NANOSECONDS_PER_SECOND;
	private static final int OVERLOADED_TICKS_WARNING_INTERVAL = 100;
	private static final long STATUS_EXPIRE_TIME_NANOS = 5L * TimeUtil.NANOSECONDS_PER_SECOND;
	private static final long PREPARE_LEVELS_DEFAULT_DELAY_NANOS = 10L * TimeUtil.NANOSECONDS_PER_MILLISECOND;
	private static final int MAX_STATUS_PLAYER_SAMPLE = 12;
	public static final int SPAWN_POSITION_SEARCH_RADIUS = 5;
	private static final int SERVER_ACTIVITY_MONITOR_SECONDS_BETWEEN_NOTIFICATIONS = 30;
	private static final int AUTOSAVE_INTERVAL = 6000;
	private static final int MIMINUM_AUTOSAVE_TICKS = 100;
	private static final int MAX_TICK_LATENCY = 3;
	public static final int ABSOLUTE_MAX_WORLD_SIZE = 29999984;
	public static final LevelSettings DEMO_SETTINGS = new LevelSettings(
		"Demo World", GameType.SURVIVAL, false, Difficulty.NORMAL, false, new GameRules(FeatureFlags.DEFAULT_FLAGS), WorldDataConfiguration.DEFAULT
	);
	public static final NameAndId ANONYMOUS_PLAYER_PROFILE = new NameAndId(Util.NIL_UUID, "Anonymous Player");
	protected final LevelStorageSource.LevelStorageAccess storageSource;
	protected final PlayerDataStorage playerDataStorage;
	private final List<Runnable> tickables = Lists.newArrayList();
	private MetricsRecorder metricsRecorder = InactiveMetricsRecorder.INSTANCE;
	private Consumer<ProfileResults> onMetricsRecordingStopped = profileResults -> this.stopRecordingMetrics();
	private Consumer<Path> onMetricsRecordingFinished = path -> {};
	private boolean willStartRecordingMetrics;
	private MinecraftServer.@Nullable TimeProfiler debugCommandProfiler;
	private boolean debugCommandProfilerDelayStart;
	private final ServerConnectionListener connection;
	private final LevelLoadListener levelLoadListener;
	private @Nullable ServerStatus status;
	private ServerStatus.@Nullable Favicon statusIcon;
	private final RandomSource random = RandomSource.create();
	private final DataFixer fixerUpper;
	private String localIp;
	private int port = -1;
	private final LayeredRegistryAccess<RegistryLayer> registries;
	private final Map<ResourceKey<Level>, ServerLevel> levels = Maps.newLinkedHashMap();
	private PlayerList playerList;
	private volatile boolean running = true;
	private boolean stopped;
	private int tickCount;
	private int ticksUntilAutosave = 6000;
	protected final Proxy proxy;
	private boolean onlineMode;
	private boolean preventProxyConnections;
	private @Nullable String motd;
	private int playerIdleTimeout;
	private final long[] tickTimesNanos = new long[100];
	private long aggregatedTickTimesNanos = 0L;
	private @Nullable KeyPair keyPair;
	private @Nullable GameProfile singleplayerProfile;
	private boolean isDemo;
	private volatile boolean isReady;
	private long lastOverloadWarningNanos;
	protected final Services services;
	private final NotificationManager notificationManager;
	private final ServerActivityMonitor serverActivityMonitor;
	private long lastServerStatus;
	private final Thread serverThread;
	private long lastTickNanos = Util.getNanos();
	private long taskExecutionStartNanos = Util.getNanos();
	private long idleTimeNanos;
	private long nextTickTimeNanos = Util.getNanos();
	private boolean waitingForNextTick = false;
	private long delayedTasksMaxNextTickTimeNanos;
	private boolean mayHaveDelayedTasks;
	private final PackRepository packRepository;
	private final ServerScoreboard scoreboard = new ServerScoreboard(this);
	private @Nullable Stopwatches stopwatches;
	private @Nullable CommandStorage commandStorage;
	private final CustomBossEvents customBossEvents = new CustomBossEvents();
	private final ServerFunctionManager functionManager;
	private boolean enforceWhitelist;
	private boolean usingWhitelist;
	private float smoothedTickTimeMillis;
	private final Executor executor;
	private @Nullable String serverId;
	private MinecraftServer.ReloadableResources resources;
	private final StructureTemplateManager structureTemplateManager;
	private final ServerTickRateManager tickRateManager;
	private final ServerDebugSubscribers debugSubscribers = new ServerDebugSubscribers(this);
	protected final WorldData worldData;
	private LevelData.RespawnData effectiveRespawnData = LevelData.RespawnData.DEFAULT;
	private final PotionBrewing potionBrewing;
	private FuelValues fuelValues;
	private int emptyTicks;
	private volatile boolean isSaving;
	private static final AtomicReference<@Nullable RuntimeException> fatalException = new AtomicReference<>();
	private final SuppressedExceptionCollector suppressedExceptions = new SuppressedExceptionCollector();
	private final DiscontinuousFrame tickFrame;
	private final PacketProcessor packetProcessor;

	public static <S extends MinecraftServer> S spin(Function<Thread, S> function) {
		AtomicReference<S> atomicReference = new AtomicReference<>();
		Thread thread = new Thread(() -> atomicReference.get().runServer(), "Server thread");
		thread.setUncaughtExceptionHandler((threadx, throwable) -> LOGGER.error("Uncaught exception in server thread", throwable));
		if (Runtime.getRuntime().availableProcessors() > 4) {
			thread.setPriority(8);
		}

		S minecraftServer = (S)function.apply(thread);
		atomicReference.set(minecraftServer);
		thread.start();
		return minecraftServer;
	}

	public MinecraftServer(
		Thread thread,
		LevelStorageSource.LevelStorageAccess levelStorageAccess,
		PackRepository packRepository,
		WorldStem worldStem,
		Proxy proxy,
		DataFixer dataFixer,
		Services services,
		LevelLoadListener levelLoadListener
	) {
		super("Server");
		this.registries = worldStem.registries();
		this.worldData = worldStem.worldData();
		if (!this.registries.compositeAccess().lookupOrThrow(Registries.LEVEL_STEM).containsKey(LevelStem.OVERWORLD)) {
			throw new IllegalStateException("Missing Overworld dimension data");
		}

		this.proxy = proxy;
		this.packRepository = packRepository;
		this.resources = new MinecraftServer.ReloadableResources(worldStem.resourceManager(), worldStem.dataPackResources());
		this.services = services;
		this.connection = new ServerConnectionListener(this);
		this.tickRateManager = new ServerTickRateManager(this);
		this.levelLoadListener = levelLoadListener;
		this.storageSource = levelStorageAccess;
		this.playerDataStorage = levelStorageAccess.createPlayerStorage();
		this.fixerUpper = dataFixer;
		this.functionManager = new ServerFunctionManager(this, this.resources.managers.getFunctionLibrary());
		HolderGetter<Block> holderGetter = this.registries.compositeAccess().lookupOrThrow(Registries.BLOCK).filterFeatures(this.worldData.enabledFeatures());
		this.structureTemplateManager = new StructureTemplateManager(worldStem.resourceManager(), levelStorageAccess, dataFixer, holderGetter);
		this.serverThread = thread;
		this.executor = Util.backgroundExecutor();
		this.potionBrewing = PotionBrewing.bootstrap(this.worldData.enabledFeatures());
		this.resources.managers.getRecipeManager().finalizeRecipeLoading(this.worldData.enabledFeatures());
		this.fuelValues = FuelValues.vanillaBurnTimes(this.registries.compositeAccess(), this.worldData.enabledFeatures());
		this.tickFrame = TracyClient.createDiscontinuousFrame("Server Tick");
		this.notificationManager = new NotificationManager();
		this.serverActivityMonitor = new ServerActivityMonitor(this.notificationManager, 30);
		this.packetProcessor = new PacketProcessor(thread);
	}

	protected abstract boolean initServer() throws IOException;

	public ChunkLoadStatusView createChunkLoadStatusView(int i) {
		return new ChunkLoadStatusView() {
			private @Nullable ChunkMap chunkMap;
			private int centerChunkX;
			private int centerChunkZ;

			@Override
			public void moveTo(ResourceKey<Level> resourceKey, ChunkPos chunkPos) {
				ServerLevel serverLevel = MinecraftServer.this.getLevel(resourceKey);
				this.chunkMap = serverLevel != null ? serverLevel.getChunkSource().chunkMap : null;
				this.centerChunkX = chunkPos.x;
				this.centerChunkZ = chunkPos.z;
			}

			@Override
			public @Nullable ChunkStatus get(int i, int j) {
				return this.chunkMap == null ? null : this.chunkMap.getLatestStatus(ChunkPos.asLong(i + this.centerChunkX - i, j + this.centerChunkZ - i));
			}

			@Override
			public int radius() {
				return i;
			}
		};
	}

	protected void loadLevel() {
		boolean bl = !JvmProfiler.INSTANCE.isRunning()
			&& SharedConstants.DEBUG_JFR_PROFILING_ENABLE_LEVEL_LOADING
			&& JvmProfiler.INSTANCE.start(Environment.from(this));
		ProfiledDuration profiledDuration = JvmProfiler.INSTANCE.onWorldLoadedStarted();
		this.worldData.setModdedInfo(this.getServerModName(), this.getModdedStatus().shouldReportAsModified());
		this.createLevels();
		this.forceDifficulty();
		this.prepareLevels();
		if (profiledDuration != null) {
			profiledDuration.finish(true);
		}

		if (bl) {
			try {
				JvmProfiler.INSTANCE.stop();
			} catch (Throwable throwable) {
				LOGGER.warn("Failed to stop JFR profiling", throwable);
			}
		}
	}

	protected void forceDifficulty() {
	}

	protected void createLevels() {
		ServerLevelData serverLevelData = this.worldData.overworldData();
		boolean bl = this.worldData.isDebugWorld();
		Registry<LevelStem> registry = this.registries.compositeAccess().lookupOrThrow(Registries.LEVEL_STEM);
		WorldOptions worldOptions = this.worldData.worldGenOptions();
		long l = worldOptions.seed();
		long m = BiomeManager.obfuscateSeed(l);
		List<CustomSpawner> list = ImmutableList.of(
			new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(serverLevelData)
		);
		LevelStem levelStem = registry.getValue(LevelStem.OVERWORLD);
		ServerLevel serverLevel = new ServerLevel(this, this.executor, this.storageSource, serverLevelData, Level.OVERWORLD, levelStem, bl, m, list, true, null);
		this.levels.put(Level.OVERWORLD, serverLevel);
		DimensionDataStorage dimensionDataStorage = serverLevel.getDataStorage();
		this.scoreboard.load(dimensionDataStorage.computeIfAbsent(ScoreboardSaveData.TYPE).getData());
		this.commandStorage = new CommandStorage(dimensionDataStorage);
		this.stopwatches = dimensionDataStorage.computeIfAbsent(Stopwatches.TYPE);
		if (!serverLevelData.isInitialized()) {
			try {
				setInitialSpawn(serverLevel, serverLevelData, worldOptions.generateBonusChest(), bl, this.levelLoadListener);
				serverLevelData.setInitialized(true);
				if (bl) {
					this.setupDebugLevel(this.worldData);
				}
			} catch (Throwable throwable) {
				CrashReport crashReport = CrashReport.forThrowable(throwable, "Exception initializing level");

				try {
					serverLevel.fillReportDetails(crashReport);
				} catch (Throwable var27) {
				}

				throw new ReportedException(crashReport);
			}

			serverLevelData.setInitialized(true);
		}

		GlobalPos globalPos = this.selectLevelLoadFocusPos();
		this.levelLoadListener.updateFocus(globalPos.dimension(), new ChunkPos(globalPos.pos()));
		if (this.worldData.getCustomBossEvents() != null) {
			this.getCustomBossEvents().load(this.worldData.getCustomBossEvents(), this.registryAccess());
		}

		RandomSequences randomSequences = serverLevel.getRandomSequences();
		boolean bl2 = false;

		for (Entry<ResourceKey<LevelStem>, LevelStem> entry : registry.entrySet()) {
			ResourceKey<LevelStem> resourceKey = entry.getKey();
			ServerLevel serverLevel2;
			if (resourceKey != LevelStem.OVERWORLD) {
				ResourceKey<Level> resourceKey2 = ResourceKey.create(Registries.DIMENSION, resourceKey.identifier());
				DerivedLevelData derivedLevelData = new DerivedLevelData(this.worldData, serverLevelData);
				serverLevel2 = new ServerLevel(
					this, this.executor, this.storageSource, derivedLevelData, resourceKey2, entry.getValue(), bl, m, ImmutableList.of(), false, randomSequences
				);
				this.levels.put(resourceKey2, serverLevel2);
			} else {
				serverLevel2 = serverLevel;
			}

			Optional<WorldBorder.Settings> optional = serverLevelData.getLegacyWorldBorderSettings();
			if (optional.isPresent()) {
				WorldBorder.Settings settings = optional.get();
				DimensionDataStorage dimensionDataStorage2 = serverLevel2.getDataStorage();
				if (dimensionDataStorage2.get(WorldBorder.TYPE) == null) {
					double d = serverLevel2.dimensionType().coordinateScale();
					WorldBorder.Settings settings2 = new WorldBorder.Settings(
						settings.centerX() / d,
						settings.centerZ() / d,
						settings.damagePerBlock(),
						settings.safeZone(),
						settings.warningBlocks(),
						settings.warningTime(),
						settings.size(),
						settings.lerpTime(),
						settings.lerpTarget()
					);
					WorldBorder worldBorder = new WorldBorder(settings2);
					worldBorder.applyInitialSettings(serverLevel2.getGameTime());
					dimensionDataStorage2.set(WorldBorder.TYPE, worldBorder);
				}

				bl2 = true;
			}

			serverLevel2.getWorldBorder().setAbsoluteMaxSize(this.getAbsoluteMaxWorldSize());
			this.getPlayerList().addWorldborderListener(serverLevel2);
		}

		if (bl2) {
			serverLevelData.setLegacyWorldBorderSettings(Optional.empty());
		}
	}

	private static void setInitialSpawn(ServerLevel serverLevel, ServerLevelData serverLevelData, boolean bl, boolean bl2, LevelLoadListener levelLoadListener) {
		if (SharedConstants.DEBUG_ONLY_GENERATE_HALF_THE_WORLD && SharedConstants.DEBUG_WORLD_RECREATE) {
			serverLevelData.setSpawn(LevelData.RespawnData.of(serverLevel.dimension(), new BlockPos(0, 64, -100), 0.0F, 0.0F));
		} else if (bl2) {
			serverLevelData.setSpawn(LevelData.RespawnData.of(serverLevel.dimension(), BlockPos.ZERO.above(80), 0.0F, 0.0F));
		} else {
			ServerChunkCache serverChunkCache = serverLevel.getChunkSource();
			ChunkPos chunkPos = new ChunkPos(serverChunkCache.randomState().sampler().findSpawnPosition());
			levelLoadListener.start(LevelLoadListener.Stage.PREPARE_GLOBAL_SPAWN, 0);
			levelLoadListener.updateFocus(serverLevel.dimension(), chunkPos);
			int i = serverChunkCache.getGenerator().getSpawnHeight(serverLevel);
			if (i < serverLevel.getMinY()) {
				BlockPos blockPos = chunkPos.getWorldPosition();
				i = serverLevel.getHeight(Heightmap.Types.WORLD_SURFACE, blockPos.getX() + 8, blockPos.getZ() + 8);
			}

			serverLevelData.setSpawn(LevelData.RespawnData.of(serverLevel.dimension(), chunkPos.getWorldPosition().offset(8, i, 8), 0.0F, 0.0F));
			int j = 0;
			int k = 0;
			int l = 0;
			int m = -1;

			for (int n = 0; n < Mth.square(11); n++) {
				if (j >= -5 && j <= 5 && k >= -5 && k <= 5) {
					BlockPos blockPos2 = PlayerSpawnFinder.getSpawnPosInChunk(serverLevel, new ChunkPos(chunkPos.x + j, chunkPos.z + k));
					if (blockPos2 != null) {
						serverLevelData.setSpawn(LevelData.RespawnData.of(serverLevel.dimension(), blockPos2, 0.0F, 0.0F));
						break;
					}
				}

				if (j == k || j < 0 && j == -k || j > 0 && j == 1 - k) {
					int o = l;
					l = -m;
					m = o;
				}

				j += l;
				k += m;
			}

			if (bl) {
				serverLevel.registryAccess()
					.lookup(Registries.CONFIGURED_FEATURE)
					.flatMap(registry -> registry.get(MiscOverworldFeatures.BONUS_CHEST))
					.ifPresent(reference -> reference.value().place(serverLevel, serverChunkCache.getGenerator(), serverLevel.random, serverLevelData.getRespawnData().pos()));
			}

			levelLoadListener.finish(LevelLoadListener.Stage.PREPARE_GLOBAL_SPAWN);
		}
	}

	private void setupDebugLevel(WorldData worldData) {
		worldData.setDifficulty(Difficulty.PEACEFUL);
		worldData.setDifficultyLocked(true);
		ServerLevelData serverLevelData = worldData.overworldData();
		serverLevelData.setRaining(false);
		serverLevelData.setThundering(false);
		serverLevelData.setClearWeatherTime(1000000000);
		serverLevelData.setDayTime(6000L);
		serverLevelData.setGameType(GameType.SPECTATOR);
	}

	private void prepareLevels() {
		ChunkLoadCounter chunkLoadCounter = new ChunkLoadCounter();

		for (ServerLevel serverLevel : this.levels.values()) {
			chunkLoadCounter.track(serverLevel, () -> {
				TicketStorage ticketStorage = serverLevel.getDataStorage().get(TicketStorage.TYPE);
				if (ticketStorage != null) {
					ticketStorage.activateAllDeactivatedTickets();
				}
			});
		}

		this.levelLoadListener.start(LevelLoadListener.Stage.LOAD_INITIAL_CHUNKS, chunkLoadCounter.totalChunks());

		do {
			this.levelLoadListener.update(LevelLoadListener.Stage.LOAD_INITIAL_CHUNKS, chunkLoadCounter.readyChunks(), chunkLoadCounter.totalChunks());
			this.nextTickTimeNanos = Util.getNanos() + PREPARE_LEVELS_DEFAULT_DELAY_NANOS;
			this.waitUntilNextTick();
		} while (chunkLoadCounter.pendingChunks() > 0);

		this.levelLoadListener.finish(LevelLoadListener.Stage.LOAD_INITIAL_CHUNKS);
		this.updateMobSpawningFlags();
		this.updateEffectiveRespawnData();
	}

	protected GlobalPos selectLevelLoadFocusPos() {
		return this.worldData.overworldData().getRespawnData().globalPos();
	}

	public GameType getDefaultGameType() {
		return this.worldData.getGameType();
	}

	public boolean isHardcore() {
		return this.worldData.isHardcore();
	}

	public abstract LevelBasedPermissionSet operatorUserPermissions();

	public abstract PermissionSet getFunctionCompilationPermissions();

	public abstract boolean shouldRconBroadcast();

	public boolean saveAllChunks(boolean bl, boolean bl2, boolean bl3) {
		this.scoreboard.storeToSaveDataIfDirty(this.overworld().getDataStorage().computeIfAbsent(ScoreboardSaveData.TYPE));
		boolean bl4 = false;

		for (ServerLevel serverLevel : this.getAllLevels()) {
			if (!bl) {
				LOGGER.info("Saving chunks for level '{}'/{}", serverLevel, serverLevel.dimension().identifier());
			}

			serverLevel.save(null, bl2, SharedConstants.DEBUG_DONT_SAVE_WORLD || serverLevel.noSave && !bl3);
			bl4 = true;
		}

		this.worldData.setCustomBossEvents(this.getCustomBossEvents().save(this.registryAccess()));
		this.storageSource.saveDataTag(this.registryAccess(), this.worldData, this.getPlayerList().getSingleplayerData());
		if (bl2) {
			for (ServerLevel serverLevel : this.getAllLevels()) {
				LOGGER.info("ThreadedAnvilChunkStorage ({}): All chunks are saved", serverLevel.getChunkSource().chunkMap.getStorageName());
			}

			LOGGER.info("ThreadedAnvilChunkStorage: All dimensions are saved");
		}

		return bl4;
	}

	public boolean saveEverything(boolean bl, boolean bl2, boolean bl3) {
		try {
			this.isSaving = true;
			this.getPlayerList().saveAll();
			return this.saveAllChunks(bl, bl2, bl3);
		} finally {
			this.isSaving = false;
		}
	}

	@Override
	public void close() {
		this.stopServer();
	}

	public void stopServer() {
		this.packetProcessor.close();
		if (this.metricsRecorder.isRecording()) {
			this.cancelRecordingMetrics();
		}

		LOGGER.info("Stopping server");
		this.getConnection().stop();
		this.isSaving = true;
		if (this.playerList != null) {
			LOGGER.info("Saving players");
			this.playerList.saveAll();
			this.playerList.removeAll();
		}

		LOGGER.info("Saving worlds");

		for (ServerLevel serverLevel : this.getAllLevels()) {
			if (serverLevel != null) {
				serverLevel.noSave = false;
			}
		}

		while (this.levels.values().stream().anyMatch(serverLevelx -> serverLevelx.getChunkSource().chunkMap.hasWork())) {
			this.nextTickTimeNanos = Util.getNanos() + TimeUtil.NANOSECONDS_PER_MILLISECOND;

			for (ServerLevel serverLevel : this.getAllLevels()) {
				serverLevel.getChunkSource().deactivateTicketsOnClosing();
				serverLevel.getChunkSource().tick(() -> true, false);
			}

			this.waitUntilNextTick();
		}

		this.saveAllChunks(false, true, false);

		for (ServerLevel serverLevel : this.getAllLevels()) {
			if (serverLevel != null) {
				try {
					serverLevel.close();
				} catch (IOException iOException) {
					LOGGER.error("Exception closing the level", iOException);
				}
			}
		}

		this.isSaving = false;
		this.resources.close();

		try {
			this.storageSource.close();
		} catch (IOException iOException2) {
			LOGGER.error("Failed to unlock level {}", this.storageSource.getLevelId(), iOException2);
		}
	}

	public String getLocalIp() {
		return this.localIp;
	}

	public void setLocalIp(String string) {
		this.localIp = string;
	}

	public boolean isRunning() {
		return this.running;
	}

	public void halt(boolean bl) {
		this.running = false;
		if (bl) {
			try {
				this.serverThread.join();
			} catch (InterruptedException interruptedException) {
				LOGGER.error("Error while shutting down", interruptedException);
			}
		}
	}

	protected void runServer() {
		try {
			if (!this.initServer()) {
				throw new IllegalStateException("Failed to initialize server");
			}

			this.nextTickTimeNanos = Util.getNanos();
			this.statusIcon = this.loadStatusIcon().orElse(null);
			this.status = this.buildServerStatus();

			while (this.running) {
				long l;
				if (!this.isPaused() && this.tickRateManager.isSprinting() && this.tickRateManager.checkShouldSprintThisTick()) {
					l = 0L;
					this.nextTickTimeNanos = Util.getNanos();
					this.lastOverloadWarningNanos = this.nextTickTimeNanos;
				} else {
					l = this.tickRateManager.nanosecondsPerTick();
					long m = Util.getNanos() - this.nextTickTimeNanos;
					if (m > OVERLOADED_THRESHOLD_NANOS + 20L * l && this.nextTickTimeNanos - this.lastOverloadWarningNanos >= OVERLOADED_WARNING_INTERVAL_NANOS + 100L * l) {
						long n = m / l;
						LOGGER.warn("Can't keep up! Is the server overloaded? Running {}ms or {} ticks behind", m / TimeUtil.NANOSECONDS_PER_MILLISECOND, n);
						this.nextTickTimeNanos += n * l;
						this.lastOverloadWarningNanos = this.nextTickTimeNanos;
					}
				}

				boolean bl = l == 0L;
				if (this.debugCommandProfilerDelayStart) {
					this.debugCommandProfilerDelayStart = false;
					this.debugCommandProfiler = new MinecraftServer.TimeProfiler(Util.getNanos(), this.tickCount);
				}

				this.nextTickTimeNanos += l;

				try (Profiler.Scope scope = Profiler.use(this.createProfiler())) {
					this.processPacketsAndTick(bl);
					ProfilerFiller profilerFiller = Profiler.get();
					profilerFiller.push("nextTickWait");
					this.mayHaveDelayedTasks = true;
					this.delayedTasksMaxNextTickTimeNanos = Math.max(Util.getNanos() + l, this.nextTickTimeNanos);
					this.startMeasuringTaskExecutionTime();
					this.waitUntilNextTick();
					this.finishMeasuringTaskExecutionTime();
					if (bl) {
						this.tickRateManager.endTickWork();
					}

					profilerFiller.pop();
					this.logFullTickTime();
				} finally {
					this.endMetricsRecordingTick();
				}

				this.isReady = true;
				JvmProfiler.INSTANCE.onServerTick(this.smoothedTickTimeMillis);
			}
		} catch (Throwable throwable) {
			LOGGER.error("Encountered an unexpected exception", throwable);
			CrashReport crashReport = constructOrExtractCrashReport(throwable);
			this.fillSystemReport(crashReport.getSystemReport());
			Path path = this.getServerDirectory().resolve("crash-reports").resolve("crash-" + Util.getFilenameFormattedDateTime() + "-server.txt");
			if (crashReport.saveToFile(path, ReportType.CRASH)) {
				LOGGER.error("This crash report has been saved to: {}", path.toAbsolutePath());
			} else {
				LOGGER.error("We were unable to save this crash report to disk.");
			}

			this.onServerCrash(crashReport);
		} finally {
			try {
				this.stopped = true;
				this.stopServer();
			} catch (Throwable throwable2) {
				LOGGER.error("Exception stopping the server", throwable2);
			} finally {
				this.onServerExit();
			}
		}
	}

	private void logFullTickTime() {
		long l = Util.getNanos();
		if (this.isTickTimeLoggingEnabled()) {
			this.getTickTimeLogger().logSample(l - this.lastTickNanos);
		}

		this.lastTickNanos = l;
	}

	private void startMeasuringTaskExecutionTime() {
		if (this.isTickTimeLoggingEnabled()) {
			this.taskExecutionStartNanos = Util.getNanos();
			this.idleTimeNanos = 0L;
		}
	}

	private void finishMeasuringTaskExecutionTime() {
		if (this.isTickTimeLoggingEnabled()) {
			SampleLogger sampleLogger = this.getTickTimeLogger();
			sampleLogger.logPartialSample(Util.getNanos() - this.taskExecutionStartNanos - this.idleTimeNanos, TpsDebugDimensions.SCHEDULED_TASKS.ordinal());
			sampleLogger.logPartialSample(this.idleTimeNanos, TpsDebugDimensions.IDLE.ordinal());
		}
	}

	private static CrashReport constructOrExtractCrashReport(Throwable throwable) {
		ReportedException reportedException = null;

		for (Throwable throwable2 = throwable; throwable2 != null; throwable2 = throwable2.getCause()) {
			if (throwable2 instanceof ReportedException reportedException2) {
				reportedException = reportedException2;
			}
		}

		CrashReport crashReport;
		if (reportedException != null) {
			crashReport = reportedException.getReport();
			if (reportedException != throwable) {
				crashReport.addCategory("Wrapped in").setDetailError("Wrapping exception", throwable);
			}
		} else {
			crashReport = new CrashReport("Exception in server tick loop", throwable);
		}

		return crashReport;
	}

	private boolean haveTime() {
		return this.runningTask() || Util.getNanos() < (this.mayHaveDelayedTasks ? this.delayedTasksMaxNextTickTimeNanos : this.nextTickTimeNanos);
	}

	public static boolean throwIfFatalException() {
		RuntimeException runtimeException = fatalException.get();
		if (runtimeException != null) {
			throw runtimeException;
		} else {
			return true;
		}
	}

	public static void setFatalException(RuntimeException runtimeException) {
		fatalException.compareAndSet(null, runtimeException);
	}

	@Override
	public void managedBlock(BooleanSupplier booleanSupplier) {
		super.managedBlock(() -> throwIfFatalException() && booleanSupplier.getAsBoolean());
	}

	public NotificationManager notificationManager() {
		return this.notificationManager;
	}

	protected void waitUntilNextTick() {
		this.runAllTasks();
		this.waitingForNextTick = true;

		try {
			this.managedBlock(() -> !this.haveTime());
		} finally {
			this.waitingForNextTick = false;
		}
	}

	@Override
	public void waitForTasks() {
		boolean bl = this.isTickTimeLoggingEnabled();
		long l = bl ? Util.getNanos() : 0L;
		long m = this.waitingForNextTick ? this.nextTickTimeNanos - Util.getNanos() : 100000L;
		LockSupport.parkNanos("waiting for tasks", m);
		if (bl) {
			this.idleTimeNanos = this.idleTimeNanos + (Util.getNanos() - l);
		}
	}

	public TickTask wrapRunnable(Runnable runnable) {
		return new TickTask(this.tickCount, runnable);
	}

	protected boolean shouldRun(TickTask tickTask) {
		return tickTask.getTick() + 3 < this.tickCount || this.haveTime();
	}

	@Override
	public boolean pollTask() {
		boolean bl = this.pollTaskInternal();
		this.mayHaveDelayedTasks = bl;
		return bl;
	}

	private boolean pollTaskInternal() {
		if (super.pollTask()) {
			return true;
		}

		if (this.tickRateManager.isSprinting() || this.shouldRunAllTasks() || this.haveTime()) {
			for (ServerLevel serverLevel : this.getAllLevels()) {
				if (serverLevel.getChunkSource().pollTask()) {
					return true;
				}
			}
		}

		return false;
	}

	protected void doRunTask(TickTask tickTask) {
		Profiler.get().incrementCounter("runTask");
		super.doRunTask(tickTask);
	}

	private Optional<ServerStatus.Favicon> loadStatusIcon() {
		Optional<Path> optional = Optional.of(this.getFile("server-icon.png"))
			.filter(path -> Files.isRegularFile(path))
			.or(() -> this.storageSource.getIconFile().filter(path -> Files.isRegularFile(path)));
		return optional.flatMap(path -> {
			try {
				byte[] bs = Files.readAllBytes(path);
				PngInfo pngInfo = PngInfo.fromBytes(bs);
				if (pngInfo.width() == 64 && pngInfo.height() == 64) {
					return Optional.of(new ServerStatus.Favicon(bs));
				} else {
					throw new IllegalArgumentException("Invalid world icon size [" + pngInfo.width() + ", " + pngInfo.height() + "], but expected [64, 64]");
				}
			} catch (Exception exception) {
				LOGGER.error("Couldn't load server icon", exception);
				return Optional.empty();
			}
		});
	}

	public Optional<Path> getWorldScreenshotFile() {
		return this.storageSource.getIconFile();
	}

	public Path getServerDirectory() {
		return Path.of("");
	}

	public ServerActivityMonitor getServerActivityMonitor() {
		return this.serverActivityMonitor;
	}

	public void onServerCrash(CrashReport crashReport) {
	}

	public void onServerExit() {
	}

	public boolean isPaused() {
		return false;
	}

	public void tickServer(BooleanSupplier booleanSupplier) {
		long l = Util.getNanos();
		int i = this.pauseWhenEmptySeconds() * 20;
		if (i > 0) {
			if (this.playerList.getPlayerCount() == 0 && !this.tickRateManager.isSprinting()) {
				this.emptyTicks++;
			} else {
				this.emptyTicks = 0;
			}

			if (this.emptyTicks >= i) {
				if (this.emptyTicks == i) {
					LOGGER.info("Server empty for {} seconds, pausing", this.pauseWhenEmptySeconds());
					this.autoSave();
				}

				this.tickConnection();
				return;
			}
		}

		this.tickCount++;
		this.tickRateManager.tick();
		this.tickChildren(booleanSupplier);
		if (l - this.lastServerStatus >= STATUS_EXPIRE_TIME_NANOS) {
			this.lastServerStatus = l;
			this.status = this.buildServerStatus();
		}

		this.ticksUntilAutosave--;
		if (this.ticksUntilAutosave <= 0) {
			this.autoSave();
		}

		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push("tallying");
		long m = Util.getNanos() - l;
		int j = this.tickCount % 100;
		this.aggregatedTickTimesNanos = this.aggregatedTickTimesNanos - this.tickTimesNanos[j];
		this.aggregatedTickTimesNanos += m;
		this.tickTimesNanos[j] = m;
		this.smoothedTickTimeMillis = this.smoothedTickTimeMillis * 0.8F + (float)m / (float)TimeUtil.NANOSECONDS_PER_MILLISECOND * 0.19999999F;
		this.logTickMethodTime(l);
		profilerFiller.pop();
	}

	protected void processPacketsAndTick(boolean bl) {
		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push("tick");
		this.tickFrame.start();
		profilerFiller.push("scheduledPacketProcessing");
		this.packetProcessor.processQueuedPackets();
		profilerFiller.pop();
		this.tickServer(bl ? () -> false : this::haveTime);
		this.tickFrame.end();
		profilerFiller.pop();
	}

	private void autoSave() {
		this.ticksUntilAutosave = this.computeNextAutosaveInterval();
		LOGGER.debug("Autosave started");
		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push("save");
		this.saveEverything(true, false, false);
		profilerFiller.pop();
		LOGGER.debug("Autosave finished");
	}

	private void logTickMethodTime(long l) {
		if (this.isTickTimeLoggingEnabled()) {
			this.getTickTimeLogger().logPartialSample(Util.getNanos() - l, TpsDebugDimensions.TICK_SERVER_METHOD.ordinal());
		}
	}

	private int computeNextAutosaveInterval() {
		float f;
		if (this.tickRateManager.isSprinting()) {
			long l = this.getAverageTickTimeNanos() + 1L;
			f = (float)TimeUtil.NANOSECONDS_PER_SECOND / (float)l;
		} else {
			f = this.tickRateManager.tickrate();
		}

		int i = 300;
		return Math.max(100, (int)(f * 300.0F));
	}

	public void onTickRateChanged() {
		int i = this.computeNextAutosaveInterval();
		if (i < this.ticksUntilAutosave) {
			this.ticksUntilAutosave = i;
		}
	}

	protected abstract SampleLogger getTickTimeLogger();

	public abstract boolean isTickTimeLoggingEnabled();

	private ServerStatus buildServerStatus() {
		ServerStatus.Players players = this.buildPlayerStatus();
		return new ServerStatus(
			Component.nullToEmpty(this.getMotd()),
			Optional.of(players),
			Optional.of(ServerStatus.Version.current()),
			Optional.ofNullable(this.statusIcon),
			this.enforceSecureProfile()
		);
	}

	private ServerStatus.Players buildPlayerStatus() {
		List<ServerPlayer> list = this.playerList.getPlayers();
		int i = this.getMaxPlayers();
		if (this.hidesOnlinePlayers()) {
			return new ServerStatus.Players(i, list.size(), List.of());
		}

		int j = Math.min(list.size(), 12);
		ObjectArrayList<NameAndId> objectArrayList = new ObjectArrayList<>(j);
		int k = Mth.nextInt(this.random, 0, list.size() - j);

		for (int l = 0; l < j; l++) {
			ServerPlayer serverPlayer = list.get(k + l);
			objectArrayList.add(serverPlayer.allowsListing() ? serverPlayer.nameAndId() : ANONYMOUS_PLAYER_PROFILE);
		}

		Util.shuffle(objectArrayList, this.random);
		return new ServerStatus.Players(i, list.size(), objectArrayList);
	}

	protected void tickChildren(BooleanSupplier booleanSupplier) {
		ProfilerFiller profilerFiller = Profiler.get();
		this.getPlayerList().getPlayers().forEach(serverPlayerx -> serverPlayerx.connection.suspendFlushing());
		profilerFiller.push("commandFunctions");
		this.getFunctions().tick();
		profilerFiller.popPush("levels");
		this.updateEffectiveRespawnData();

		for (ServerLevel serverLevel : this.getAllLevels()) {
			profilerFiller.push(() -> serverLevel + " " + serverLevel.dimension().identifier());
			if (this.tickCount % 20 == 0) {
				profilerFiller.push("timeSync");
				this.synchronizeTime(serverLevel);
				profilerFiller.pop();
			}

			profilerFiller.push("tick");

			try {
				serverLevel.tick(booleanSupplier);
			} catch (Throwable throwable) {
				CrashReport crashReport = CrashReport.forThrowable(throwable, "Exception ticking world");
				serverLevel.fillReportDetails(crashReport);
				throw new ReportedException(crashReport);
			}

			profilerFiller.pop();
			profilerFiller.pop();
		}

		profilerFiller.popPush("connection");
		this.tickConnection();
		profilerFiller.popPush("players");
		this.playerList.tick();
		profilerFiller.popPush("debugSubscribers");
		this.debugSubscribers.tick();
		if (this.tickRateManager.runsNormally()) {
			profilerFiller.popPush("gameTests");
			GameTestTicker.SINGLETON.tick();
		}

		profilerFiller.popPush("server gui refresh");

		for (Runnable runnable : this.tickables) {
			runnable.run();
		}

		profilerFiller.popPush("send chunks");

		for (ServerPlayer serverPlayer : this.playerList.getPlayers()) {
			serverPlayer.connection.chunkSender.sendNextChunks(serverPlayer);
			serverPlayer.connection.resumeFlushing();
		}

		profilerFiller.pop();
		this.serverActivityMonitor.tick();
	}

	private void updateEffectiveRespawnData() {
		LevelData.RespawnData respawnData = this.worldData.overworldData().getRespawnData();
		ServerLevel serverLevel = this.findRespawnDimension();
		this.effectiveRespawnData = serverLevel.getWorldBorderAdjustedRespawnData(respawnData);
	}

	public void tickConnection() {
		this.getConnection().tick();
	}

	private void synchronizeTime(ServerLevel serverLevel) {
		this.playerList
			.broadcastAll(
				new ClientboundSetTimePacket(serverLevel.getGameTime(), serverLevel.getDayTime(), serverLevel.getGameRules().get(GameRules.ADVANCE_TIME)),
				serverLevel.dimension()
			);
	}

	public void forceTimeSynchronization() {
		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push("timeSync");

		for (ServerLevel serverLevel : this.getAllLevels()) {
			this.synchronizeTime(serverLevel);
		}

		profilerFiller.pop();
	}

	public void addTickable(Runnable runnable) {
		this.tickables.add(runnable);
	}

	protected void setId(String string) {
		this.serverId = string;
	}

	public boolean isShutdown() {
		return !this.serverThread.isAlive();
	}

	public Path getFile(String string) {
		return this.getServerDirectory().resolve(string);
	}

	public final ServerLevel overworld() {
		return this.levels.get(Level.OVERWORLD);
	}

	public @Nullable ServerLevel getLevel(ResourceKey<Level> resourceKey) {
		return this.levels.get(resourceKey);
	}

	public Set<ResourceKey<Level>> levelKeys() {
		return this.levels.keySet();
	}

	public Iterable<ServerLevel> getAllLevels() {
		return this.levels.values();
	}

	@Override
	public String getServerVersion() {
		return SharedConstants.getCurrentVersion().name();
	}

	@Override
	public int getPlayerCount() {
		return this.playerList.getPlayerCount();
	}

	public String[] getPlayerNames() {
		return this.playerList.getPlayerNamesArray();
	}

	@DontObfuscate
	public String getServerModName() {
		return "vanilla";
	}

	public SystemReport fillSystemReport(SystemReport systemReport) {
		systemReport.setDetail("Server Running", () -> Boolean.toString(this.running));
		if (this.playerList != null) {
			systemReport.setDetail(
				"Player Count", () -> this.playerList.getPlayerCount() + " / " + this.playerList.getMaxPlayers() + "; " + this.playerList.getPlayers()
			);
		}

		systemReport.setDetail("Active Data Packs", () -> PackRepository.displayPackList(this.packRepository.getSelectedPacks()));
		systemReport.setDetail("Available Data Packs", () -> PackRepository.displayPackList(this.packRepository.getAvailablePacks()));
		systemReport.setDetail(
			"Enabled Feature Flags",
			() -> FeatureFlags.REGISTRY.toNames(this.worldData.enabledFeatures()).stream().map(Identifier::toString).collect(Collectors.joining(", "))
		);
		systemReport.setDetail("World Generation", () -> this.worldData.worldGenSettingsLifecycle().toString());
		systemReport.setDetail("World Seed", () -> String.valueOf(this.worldData.worldGenOptions().seed()));
		systemReport.setDetail("Suppressed Exceptions", this.suppressedExceptions::dump);
		if (this.serverId != null) {
			systemReport.setDetail("Server Id", () -> this.serverId);
		}

		return this.fillServerSystemReport(systemReport);
	}

	public abstract SystemReport fillServerSystemReport(SystemReport systemReport);

	public ModCheck getModdedStatus() {
		return ModCheck.identify("vanilla", this::getServerModName, "Server", MinecraftServer.class);
	}

	@Override
	public void sendSystemMessage(Component component) {
		LOGGER.info(component.getString());
	}

	public KeyPair getKeyPair() {
		return Objects.requireNonNull(this.keyPair);
	}

	public int getPort() {
		return this.port;
	}

	public void setPort(int i) {
		this.port = i;
	}

	public @Nullable GameProfile getSingleplayerProfile() {
		return this.singleplayerProfile;
	}

	public void setSingleplayerProfile(@Nullable GameProfile gameProfile) {
		this.singleplayerProfile = gameProfile;
	}

	public boolean isSingleplayer() {
		return this.singleplayerProfile != null;
	}

	protected void initializeKeyPair() {
		LOGGER.info("Generating keypair");

		try {
			this.keyPair = Crypt.generateKeyPair();
		} catch (CryptException cryptException) {
			throw new IllegalStateException("Failed to generate key pair", cryptException);
		}
	}

	public void setDifficulty(Difficulty difficulty, boolean bl) {
		if (bl || !this.worldData.isDifficultyLocked()) {
			this.worldData.setDifficulty(this.worldData.isHardcore() ? Difficulty.HARD : difficulty);
			this.updateMobSpawningFlags();
			this.getPlayerList().getPlayers().forEach(this::sendDifficultyUpdate);
		}
	}

	public int getScaledTrackingDistance(int i) {
		return i;
	}

	public void updateMobSpawningFlags() {
		for (ServerLevel serverLevel : this.getAllLevels()) {
			serverLevel.setSpawnSettings(serverLevel.isSpawningMonsters());
		}
	}

	public void setDifficultyLocked(boolean bl) {
		this.worldData.setDifficultyLocked(bl);
		this.getPlayerList().getPlayers().forEach(this::sendDifficultyUpdate);
	}

	private void sendDifficultyUpdate(ServerPlayer serverPlayer) {
		LevelData levelData = serverPlayer.level().getLevelData();
		serverPlayer.connection.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
	}

	public boolean isDemo() {
		return this.isDemo;
	}

	public void setDemo(boolean bl) {
		this.isDemo = bl;
	}

	public Map<String, String> getCodeOfConducts() {
		return Map.of();
	}

	public Optional<MinecraftServer.ServerResourcePackInfo> getServerResourcePack() {
		return Optional.empty();
	}

	public boolean isResourcePackRequired() {
		return this.getServerResourcePack().filter(MinecraftServer.ServerResourcePackInfo::isRequired).isPresent();
	}

	public abstract boolean isDedicatedServer();

	public abstract int getRateLimitPacketsPerSecond();

	public boolean usesAuthentication() {
		return this.onlineMode;
	}

	public void setUsesAuthentication(boolean bl) {
		this.onlineMode = bl;
	}

	public boolean getPreventProxyConnections() {
		return this.preventProxyConnections;
	}

	public void setPreventProxyConnections(boolean bl) {
		this.preventProxyConnections = bl;
	}

	public abstract boolean useNativeTransport();

	public boolean allowFlight() {
		return true;
	}

	@Override
	public String getMotd() {
		return this.motd;
	}

	public void setMotd(String string) {
		this.motd = string;
	}

	public boolean isStopped() {
		return this.stopped;
	}

	public PlayerList getPlayerList() {
		return this.playerList;
	}

	public void setPlayerList(PlayerList playerList) {
		this.playerList = playerList;
	}

	public abstract boolean isPublished();

	public void setDefaultGameType(GameType gameType) {
		this.worldData.setGameType(gameType);
	}

	public int enforceGameTypeForPlayers(@Nullable GameType gameType) {
		if (gameType == null) {
			return 0;
		}

		int i = 0;

		for (ServerPlayer serverPlayer : this.getPlayerList().getPlayers()) {
			if (serverPlayer.setGameMode(gameType)) {
				i++;
			}
		}

		return i;
	}

	public ServerConnectionListener getConnection() {
		return this.connection;
	}

	public boolean isReady() {
		return this.isReady;
	}

	public boolean publishServer(@Nullable GameType gameType, boolean bl, int i) {
		return false;
	}

	public int getTickCount() {
		return this.tickCount;
	}

	public boolean isUnderSpawnProtection(ServerLevel serverLevel, BlockPos blockPos, Player player) {
		return false;
	}

	public boolean repliesToStatus() {
		return true;
	}

	public boolean hidesOnlinePlayers() {
		return false;
	}

	public Proxy getProxy() {
		return this.proxy;
	}

	public int playerIdleTimeout() {
		return this.playerIdleTimeout;
	}

	public void setPlayerIdleTimeout(int i) {
		this.playerIdleTimeout = i;
	}

	public Services services() {
		return this.services;
	}

	public @Nullable ServerStatus getStatus() {
		return this.status;
	}

	public void invalidateStatus() {
		this.lastServerStatus = 0L;
	}

	public int getAbsoluteMaxWorldSize() {
		return 29999984;
	}

	@Override
	public boolean scheduleExecutables() {
		return super.scheduleExecutables() && !this.isStopped();
	}

	@Override
	public void executeIfPossible(Runnable runnable) {
		if (this.isStopped()) {
			throw new RejectedExecutionException("Server already shutting down");
		}

		super.executeIfPossible(runnable);
	}

	@Override
	public Thread getRunningThread() {
		return this.serverThread;
	}

	public int getCompressionThreshold() {
		return 256;
	}

	public boolean enforceSecureProfile() {
		return false;
	}

	public long getNextTickTime() {
		return this.nextTickTimeNanos;
	}

	public DataFixer getFixerUpper() {
		return this.fixerUpper;
	}

	public ServerAdvancementManager getAdvancements() {
		return this.resources.managers.getAdvancements();
	}

	public ServerFunctionManager getFunctions() {
		return this.functionManager;
	}

	public CompletableFuture<Void> reloadResources(Collection<String> collection) {
		CompletableFuture<Void> completableFuture = CompletableFuture.<ImmutableList>supplyAsync(
				() -> collection.stream().map(this.packRepository::getPack).filter(Objects::nonNull).map(Pack::open).collect(ImmutableList.toImmutableList()), this
			)
			.thenCompose(
				immutableList -> {
					CloseableResourceManager closeableResourceManager = new MultiPackResourceManager(PackType.SERVER_DATA, immutableList);
					List<Registry.PendingTags<?>> list = TagLoader.loadTagsForExistingRegistries(closeableResourceManager, this.registries.compositeAccess());
					return ReloadableServerResources.loadResources(
							closeableResourceManager,
							this.registries,
							list,
							this.worldData.enabledFeatures(),
							this.isDedicatedServer() ? Commands.CommandSelection.DEDICATED : Commands.CommandSelection.INTEGRATED,
							this.getFunctionCompilationPermissions(),
							this.executor,
							this
						)
						.whenComplete((reloadableServerResources, throwable) -> {
							if (throwable != null) {
								closeableResourceManager.close();
							}
						})
						.thenApply(reloadableServerResources -> new MinecraftServer.ReloadableResources(closeableResourceManager, reloadableServerResources));
				}
			)
			.thenAcceptAsync(reloadableResources -> {
				this.resources.close();
				this.resources = reloadableResources;
				this.packRepository.setSelected(collection);
				WorldDataConfiguration worldDataConfiguration = new WorldDataConfiguration(getSelectedPacks(this.packRepository, true), this.worldData.enabledFeatures());
				this.worldData.setDataConfiguration(worldDataConfiguration);
				this.resources.managers.updateStaticRegistryTags();
				this.resources.managers.getRecipeManager().finalizeRecipeLoading(this.worldData.enabledFeatures());
				this.getPlayerList().saveAll();
				this.getPlayerList().reloadResources();
				this.functionManager.replaceLibrary(this.resources.managers.getFunctionLibrary());
				this.structureTemplateManager.onResourceManagerReload(this.resources.resourceManager);
				this.fuelValues = FuelValues.vanillaBurnTimes(this.registries.compositeAccess(), this.worldData.enabledFeatures());
			}, this);
		if (this.isSameThread()) {
			this.managedBlock(completableFuture::isDone);
		}

		return completableFuture;
	}

	public static WorldDataConfiguration configurePackRepository(
		PackRepository packRepository, WorldDataConfiguration worldDataConfiguration, boolean bl, boolean bl2
	) {
		DataPackConfig dataPackConfig = worldDataConfiguration.dataPacks();
		FeatureFlagSet featureFlagSet = bl ? FeatureFlagSet.of() : worldDataConfiguration.enabledFeatures();
		FeatureFlagSet featureFlagSet2 = bl ? FeatureFlags.REGISTRY.allFlags() : worldDataConfiguration.enabledFeatures();
		packRepository.reload();
		if (bl2) {
			return configureRepositoryWithSelection(packRepository, List.of("vanilla"), featureFlagSet, false);
		}

		Set<String> set = Sets.newLinkedHashSet();

		for (String string : dataPackConfig.getEnabled()) {
			if (packRepository.isAvailable(string)) {
				set.add(string);
			} else {
				LOGGER.warn("Missing data pack {}", string);
			}
		}

		for (Pack pack : packRepository.getAvailablePacks()) {
			String string2 = pack.getId();
			if (!dataPackConfig.getDisabled().contains(string2)) {
				FeatureFlagSet featureFlagSet3 = pack.getRequestedFeatures();
				boolean bl3 = set.contains(string2);
				if (!bl3 && pack.getPackSource().shouldAddAutomatically()) {
					if (featureFlagSet3.isSubsetOf(featureFlagSet2)) {
						LOGGER.info("Found new data pack {}, loading it automatically", string2);
						set.add(string2);
					} else {
						LOGGER.info(
							"Found new data pack {}, but can't load it due to missing features {}", string2, FeatureFlags.printMissingFlags(featureFlagSet2, featureFlagSet3)
						);
					}
				}

				if (bl3 && !featureFlagSet3.isSubsetOf(featureFlagSet2)) {
					LOGGER.warn(
						"Pack {} requires features {} that are not enabled for this world, disabling pack.",
						string2,
						FeatureFlags.printMissingFlags(featureFlagSet2, featureFlagSet3)
					);
					set.remove(string2);
				}
			}
		}

		if (set.isEmpty()) {
			LOGGER.info("No datapacks selected, forcing vanilla");
			set.add("vanilla");
		}

		return configureRepositoryWithSelection(packRepository, set, featureFlagSet, true);
	}

	private static WorldDataConfiguration configureRepositoryWithSelection(
		PackRepository packRepository, Collection<String> collection, FeatureFlagSet featureFlagSet, boolean bl
	) {
		packRepository.setSelected(collection);
		enableForcedFeaturePacks(packRepository, featureFlagSet);
		DataPackConfig dataPackConfig = getSelectedPacks(packRepository, bl);
		FeatureFlagSet featureFlagSet2 = packRepository.getRequestedFeatureFlags().join(featureFlagSet);
		return new WorldDataConfiguration(dataPackConfig, featureFlagSet2);
	}

	private static void enableForcedFeaturePacks(PackRepository packRepository, FeatureFlagSet featureFlagSet) {
		FeatureFlagSet featureFlagSet2 = packRepository.getRequestedFeatureFlags();
		FeatureFlagSet featureFlagSet3 = featureFlagSet.subtract(featureFlagSet2);
		if (!featureFlagSet3.isEmpty()) {
			Set<String> set = new ObjectArraySet<>(packRepository.getSelectedIds());

			for (Pack pack : packRepository.getAvailablePacks()) {
				if (featureFlagSet3.isEmpty()) {
					break;
				}

				if (pack.getPackSource() == PackSource.FEATURE) {
					String string = pack.getId();
					FeatureFlagSet featureFlagSet4 = pack.getRequestedFeatures();
					if (!featureFlagSet4.isEmpty() && featureFlagSet4.intersects(featureFlagSet3) && featureFlagSet4.isSubsetOf(featureFlagSet)) {
						if (!set.add(string)) {
							throw new IllegalStateException("Tried to force '" + string + "', but it was already enabled");
						}

						LOGGER.info("Found feature pack ('{}') for requested feature, forcing to enabled", string);
						featureFlagSet3 = featureFlagSet3.subtract(featureFlagSet4);
					}
				}
			}

			packRepository.setSelected(set);
		}
	}

	private static DataPackConfig getSelectedPacks(PackRepository packRepository, boolean bl) {
		Collection<String> collection = packRepository.getSelectedIds();
		List<String> list = ImmutableList.copyOf(collection);
		List<String> list2 = bl ? packRepository.getAvailableIds().stream().filter(string -> !collection.contains(string)).toList() : List.of();
		return new DataPackConfig(list, list2);
	}

	public void kickUnlistedPlayers() {
		if (this.isEnforceWhitelist() && this.isUsingWhitelist()) {
			PlayerList playerList = this.getPlayerList();
			UserWhiteList userWhiteList = playerList.getWhiteList();

			for (ServerPlayer serverPlayer : Lists.newArrayList(playerList.getPlayers())) {
				if (!userWhiteList.isWhiteListed(serverPlayer.nameAndId())) {
					serverPlayer.connection.disconnect(Component.translatable("multiplayer.disconnect.not_whitelisted"));
				}
			}
		}
	}

	public PackRepository getPackRepository() {
		return this.packRepository;
	}

	public Commands getCommands() {
		return this.resources.managers.getCommands();
	}

	public CommandSourceStack createCommandSourceStack() {
		ServerLevel serverLevel = this.findRespawnDimension();
		return new CommandSourceStack(
			this,
			Vec3.atLowerCornerOf(this.getRespawnData().pos()),
			Vec2.ZERO,
			serverLevel,
			LevelBasedPermissionSet.OWNER,
			"Server",
			Component.literal("Server"),
			this,
			null
		);
	}

	public ServerLevel findRespawnDimension() {
		LevelData.RespawnData respawnData = this.getWorldData().overworldData().getRespawnData();
		ResourceKey<Level> resourceKey = respawnData.dimension();
		ServerLevel serverLevel = this.getLevel(resourceKey);
		return serverLevel != null ? serverLevel : this.overworld();
	}

	public void setRespawnData(LevelData.RespawnData respawnData) {
		ServerLevelData serverLevelData = this.worldData.overworldData();
		LevelData.RespawnData respawnData2 = serverLevelData.getRespawnData();
		if (!respawnData2.equals(respawnData)) {
			serverLevelData.setSpawn(respawnData);
			this.getPlayerList().broadcastAll(new ClientboundSetDefaultSpawnPositionPacket(respawnData));
			this.updateEffectiveRespawnData();
		}
	}

	public LevelData.RespawnData getRespawnData() {
		return this.effectiveRespawnData;
	}

	@Override
	public boolean acceptsSuccess() {
		return true;
	}

	@Override
	public boolean acceptsFailure() {
		return true;
	}

	@Override
	public abstract boolean shouldInformAdmins();

	public RecipeManager getRecipeManager() {
		return this.resources.managers.getRecipeManager();
	}

	public ServerScoreboard getScoreboard() {
		return this.scoreboard;
	}

	public CommandStorage getCommandStorage() {
		if (this.commandStorage == null) {
			throw new NullPointerException("Called before server init");
		} else {
			return this.commandStorage;
		}
	}

	public Stopwatches getStopwatches() {
		if (this.stopwatches == null) {
			throw new NullPointerException("Called before server init");
		} else {
			return this.stopwatches;
		}
	}

	public CustomBossEvents getCustomBossEvents() {
		return this.customBossEvents;
	}

	public boolean isEnforceWhitelist() {
		return this.enforceWhitelist;
	}

	public void setEnforceWhitelist(boolean bl) {
		this.enforceWhitelist = bl;
	}

	public boolean isUsingWhitelist() {
		return this.usingWhitelist;
	}

	public void setUsingWhitelist(boolean bl) {
		this.usingWhitelist = bl;
	}

	public float getCurrentSmoothedTickTime() {
		return this.smoothedTickTimeMillis;
	}

	public ServerTickRateManager tickRateManager() {
		return this.tickRateManager;
	}

	public long getAverageTickTimeNanos() {
		return this.aggregatedTickTimesNanos / Math.min(100, Math.max(this.tickCount, 1));
	}

	public long[] getTickTimesNanos() {
		return this.tickTimesNanos;
	}

	public LevelBasedPermissionSet getProfilePermissions(NameAndId nameAndId) {
		if (this.getPlayerList().isOp(nameAndId)) {
			ServerOpListEntry serverOpListEntry = this.getPlayerList().getOps().get(nameAndId);
			if (serverOpListEntry != null) {
				return serverOpListEntry.permissions();
			} else if (this.isSingleplayerOwner(nameAndId)) {
				return LevelBasedPermissionSet.OWNER;
			} else if (this.isSingleplayer()) {
				return this.getPlayerList().isAllowCommandsForAllPlayers() ? LevelBasedPermissionSet.OWNER : LevelBasedPermissionSet.ALL;
			} else {
				return this.operatorUserPermissions();
			}
		} else {
			return LevelBasedPermissionSet.ALL;
		}
	}

	public abstract boolean isSingleplayerOwner(NameAndId nameAndId);

	public void dumpServerProperties(Path path) throws IOException {
	}

	private void saveDebugReport(Path path) {
		Path path2 = path.resolve("levels");

		try {
			for (Entry<ResourceKey<Level>, ServerLevel> entry : this.levels.entrySet()) {
				Identifier identifier = entry.getKey().identifier();
				Path path3 = path2.resolve(identifier.getNamespace()).resolve(identifier.getPath());
				Files.createDirectories(path3);
				entry.getValue().saveDebugReport(path3);
			}

			this.dumpGameRules(path.resolve("gamerules.txt"));
			this.dumpClasspath(path.resolve("classpath.txt"));
			this.dumpMiscStats(path.resolve("stats.txt"));
			this.dumpThreads(path.resolve("threads.txt"));
			this.dumpServerProperties(path.resolve("server.properties.txt"));
			this.dumpNativeModules(path.resolve("modules.txt"));
		} catch (IOException iOException) {
			LOGGER.warn("Failed to save debug report", iOException);
		}
	}

	private void dumpMiscStats(Path path) throws IOException {
		try (Writer writer = Files.newBufferedWriter(path)) {
			writer.write(String.format(Locale.ROOT, "pending_tasks: %d\n", this.getPendingTasksCount()));
			writer.write(String.format(Locale.ROOT, "average_tick_time: %f\n", this.getCurrentSmoothedTickTime()));
			writer.write(String.format(Locale.ROOT, "tick_times: %s\n", Arrays.toString(this.tickTimesNanos)));
			writer.write(String.format(Locale.ROOT, "queue: %s\n", Util.backgroundExecutor()));
		}
	}

	private void dumpGameRules(Path path) throws IOException {
		try (Writer writer = Files.newBufferedWriter(path)) {
			final List<String> list = Lists.newArrayList();
			final GameRules gameRules = this.worldData.getGameRules();
			gameRules.visitGameRuleTypes(new GameRuleTypeVisitor() {
				@Override
				public <T> void visit(GameRule<T> gameRule) {
					list.add(String.format(Locale.ROOT, "%s=%s\n", gameRule.getIdentifier(), gameRules.getAsString(gameRule)));
				}
			});

			for (String string : list) {
				writer.write(string);
			}
		}
	}

	private void dumpClasspath(Path path) throws IOException {
		try (Writer writer = Files.newBufferedWriter(path)) {
			String string = System.getProperty("java.class.path");
			String string2 = File.pathSeparator;

			for (String string3 : Splitter.on(string2).split(string)) {
				writer.write(string3);
				writer.write("\n");
			}
		}
	}

	private void dumpThreads(Path path) throws IOException {
		ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
		ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(true, true);
		Arrays.sort(threadInfos, Comparator.comparing(ThreadInfo::getThreadName));

		try (Writer writer = Files.newBufferedWriter(path)) {
			for (ThreadInfo threadInfo : threadInfos) {
				writer.write(threadInfo.toString());
				writer.write(10);
			}
		}
	}

	private void dumpNativeModules(Path path) throws IOException {
		try (Writer writer = Files.newBufferedWriter(path)) {
			List<NativeModuleLister.NativeModuleInfo> list;
			try {
				list = Lists.newArrayList(NativeModuleLister.listModules());
			} catch (Throwable throwable) {
				LOGGER.warn("Failed to list native modules", throwable);
				return;
			}

			list.sort(Comparator.comparing(nativeModuleInfox -> nativeModuleInfox.name));

			for (NativeModuleLister.NativeModuleInfo nativeModuleInfo : list) {
				writer.write(nativeModuleInfo.toString());
				writer.write(10);
			}
		}
	}

	private ProfilerFiller createProfiler() {
		if (this.willStartRecordingMetrics) {
			this.metricsRecorder = ActiveMetricsRecorder.createStarted(
				new ServerMetricsSamplersProvider(Util.timeSource, this.isDedicatedServer()),
				Util.timeSource,
				Util.ioPool(),
				new MetricsPersister("server"),
				this.onMetricsRecordingStopped,
				path -> {
					this.executeBlocking(() -> this.saveDebugReport(path.resolve("server")));
					this.onMetricsRecordingFinished.accept(path);
				}
			);
			this.willStartRecordingMetrics = false;
		}

		this.metricsRecorder.startTick();
		return SingleTickProfiler.decorateFiller(this.metricsRecorder.getProfiler(), SingleTickProfiler.createTickProfiler("Server"));
	}

	public void endMetricsRecordingTick() {
		this.metricsRecorder.endTick();
	}

	public boolean isRecordingMetrics() {
		return this.metricsRecorder.isRecording();
	}

	public void startRecordingMetrics(Consumer<ProfileResults> consumer, Consumer<Path> consumer2) {
		this.onMetricsRecordingStopped = profileResults -> {
			this.stopRecordingMetrics();
			consumer.accept(profileResults);
		};
		this.onMetricsRecordingFinished = consumer2;
		this.willStartRecordingMetrics = true;
	}

	public void stopRecordingMetrics() {
		this.metricsRecorder = InactiveMetricsRecorder.INSTANCE;
	}

	public void finishRecordingMetrics() {
		this.metricsRecorder.end();
	}

	public void cancelRecordingMetrics() {
		this.metricsRecorder.cancel();
	}

	public Path getWorldPath(LevelResource levelResource) {
		return this.storageSource.getLevelPath(levelResource);
	}

	public boolean forceSynchronousWrites() {
		return true;
	}

	public StructureTemplateManager getStructureManager() {
		return this.structureTemplateManager;
	}

	public WorldData getWorldData() {
		return this.worldData;
	}

	public RegistryAccess.Frozen registryAccess() {
		return this.registries.compositeAccess();
	}

	public LayeredRegistryAccess<RegistryLayer> registries() {
		return this.registries;
	}

	public ReloadableServerRegistries.Holder reloadableRegistries() {
		return this.resources.managers.fullRegistries();
	}

	public TextFilter createTextFilterForPlayer(ServerPlayer serverPlayer) {
		return TextFilter.DUMMY;
	}

	public ServerPlayerGameMode createGameModeForPlayer(ServerPlayer serverPlayer) {
		return this.isDemo() ? new DemoMode(serverPlayer) : new ServerPlayerGameMode(serverPlayer);
	}

	public @Nullable GameType getForcedGameType() {
		return null;
	}

	public ResourceManager getResourceManager() {
		return this.resources.resourceManager;
	}

	public boolean isCurrentlySaving() {
		return this.isSaving;
	}

	public boolean isTimeProfilerRunning() {
		return this.debugCommandProfilerDelayStart || this.debugCommandProfiler != null;
	}

	public void startTimeProfiler() {
		this.debugCommandProfilerDelayStart = true;
	}

	public ProfileResults stopTimeProfiler() {
		if (this.debugCommandProfiler == null) {
			return EmptyProfileResults.EMPTY;
		}

		ProfileResults profileResults = this.debugCommandProfiler.stop(Util.getNanos(), this.tickCount);
		this.debugCommandProfiler = null;
		return profileResults;
	}

	public int getMaxChainedNeighborUpdates() {
		return 1000000;
	}

	public void logChatMessage(Component component, ChatType.Bound bound, @Nullable String string) {
		String string2 = bound.decorate(component).getString();
		if (string != null) {
			LOGGER.info("[{}] {}", string, string2);
		} else {
			LOGGER.info("{}", string2);
		}
	}

	public ChatDecorator getChatDecorator() {
		return ChatDecorator.PLAIN;
	}

	public boolean logIPs() {
		return true;
	}

	public void handleCustomClickAction(Identifier identifier, Optional<Tag> optional) {
		LOGGER.debug("Received custom click action {} with payload {}", identifier, optional.orElse(null));
	}

	public LevelLoadListener getLevelLoadListener() {
		return this.levelLoadListener;
	}

	public boolean setAutoSave(boolean bl) {
		boolean bl2 = false;

		for (ServerLevel serverLevel : this.getAllLevels()) {
			if (serverLevel != null && serverLevel.noSave == bl) {
				serverLevel.noSave = !bl;
				bl2 = true;
			}
		}

		return bl2;
	}

	public boolean isAutoSave() {
		for (ServerLevel serverLevel : this.getAllLevels()) {
			if (serverLevel != null && !serverLevel.noSave) {
				return true;
			}
		}

		return false;
	}

	public <T> void onGameRuleChanged(GameRule<T> gameRule, T object) {
		this.notificationManager().onGameRuleChanged(gameRule, object);
		if (gameRule == GameRules.REDUCED_DEBUG_INFO) {
			byte b = (byte)((Boolean)object ? 22 : 23);

			for (ServerPlayer serverPlayer : this.getPlayerList().getPlayers()) {
				serverPlayer.connection.send(new ClientboundEntityEventPacket(serverPlayer, b));
			}
		} else if (gameRule == GameRules.LIMITED_CRAFTING || gameRule == GameRules.IMMEDIATE_RESPAWN) {
			ClientboundGameEventPacket.Type type = gameRule == GameRules.LIMITED_CRAFTING
				? ClientboundGameEventPacket.LIMITED_CRAFTING
				: ClientboundGameEventPacket.IMMEDIATE_RESPAWN;
			ClientboundGameEventPacket clientboundGameEventPacket = new ClientboundGameEventPacket(type, (Boolean)object ? 1.0F : 0.0F);
			this.getPlayerList().getPlayers().forEach(serverPlayerx -> serverPlayerx.connection.send(clientboundGameEventPacket));
		} else if (gameRule == GameRules.LOCATOR_BAR) {
			this.getAllLevels().forEach(serverLevel -> {
				ServerWaypointManager serverWaypointManager = serverLevel.getWaypointManager();
				if ((Boolean)object) {
					serverLevel.players().forEach(serverWaypointManager::updatePlayer);
				} else {
					serverWaypointManager.breakAllConnections();
				}
			});
		} else if (gameRule == GameRules.SPAWN_MONSTERS) {
			this.updateMobSpawningFlags();
		}
	}

	public boolean acceptsTransfers() {
		return false;
	}

	private void storeChunkIoError(CrashReport crashReport, ChunkPos chunkPos, RegionStorageInfo regionStorageInfo) {
		Util.ioPool().execute(() -> {
			try {
				Path path = this.getFile("debug");
				FileUtil.createDirectoriesSafe(path);
				String string = FileUtil.sanitizeName(regionStorageInfo.level());
				Path path2 = path.resolve("chunk-" + string + "-" + Util.getFilenameFormattedDateTime() + "-server.txt");
				FileStore fileStore = Files.getFileStore(path);
				long l = fileStore.getUsableSpace();
				if (l < 8192L) {
					LOGGER.warn("Not storing chunk IO report due to low space on drive {}", fileStore.name());
					return;
				}

				CrashReportCategory crashReportCategory = crashReport.addCategory("Chunk Info");
				crashReportCategory.setDetail("Level", regionStorageInfo::level);
				crashReportCategory.setDetail("Dimension", () -> regionStorageInfo.dimension().identifier().toString());
				crashReportCategory.setDetail("Storage", regionStorageInfo::type);
				crashReportCategory.setDetail("Position", chunkPos::toString);
				crashReport.saveToFile(path2, ReportType.CHUNK_IO_ERROR);
				LOGGER.info("Saved details to {}", crashReport.getSaveFile());
			} catch (Exception exception) {
				LOGGER.warn("Failed to store chunk IO exception", exception);
			}
		});
	}

	@Override
	public void reportChunkLoadFailure(Throwable throwable, RegionStorageInfo regionStorageInfo, ChunkPos chunkPos) {
		LOGGER.error("Failed to load chunk {},{}", chunkPos.x, chunkPos.z, throwable);
		this.suppressedExceptions.addEntry("chunk/load", throwable);
		this.storeChunkIoError(CrashReport.forThrowable(throwable, "Chunk load failure"), chunkPos, regionStorageInfo);
	}

	@Override
	public void reportChunkSaveFailure(Throwable throwable, RegionStorageInfo regionStorageInfo, ChunkPos chunkPos) {
		LOGGER.error("Failed to save chunk {},{}", chunkPos.x, chunkPos.z, throwable);
		this.suppressedExceptions.addEntry("chunk/save", throwable);
		this.storeChunkIoError(CrashReport.forThrowable(throwable, "Chunk save failure"), chunkPos, regionStorageInfo);
	}

	public void reportPacketHandlingException(Throwable throwable, PacketType<?> packetType) {
		this.suppressedExceptions.addEntry("packet/" + packetType, throwable);
	}

	public PotionBrewing potionBrewing() {
		return this.potionBrewing;
	}

	public FuelValues fuelValues() {
		return this.fuelValues;
	}

	public ServerLinks serverLinks() {
		return ServerLinks.EMPTY;
	}

	protected int pauseWhenEmptySeconds() {
		return 0;
	}

	public PacketProcessor packetProcessor() {
		return this.packetProcessor;
	}

	public ServerDebugSubscribers debugSubscribers() {
		return this.debugSubscribers;
	}

	record ReloadableResources(CloseableResourceManager resourceManager, ReloadableServerResources managers) implements AutoCloseable {

		@Override
		public void close() {
			this.resourceManager.close();
		}
	}

	public record ServerResourcePackInfo(UUID id, String url, String hash, boolean isRequired, @Nullable Component prompt) {
	}

	static class TimeProfiler {
		final long startNanos;
		final int startTick;

		TimeProfiler(long l, int i) {
			this.startNanos = l;
			this.startTick = i;
		}

		ProfileResults stop(long l, int i) {
			return new ProfileResults() {
				@Override
				public List<ResultField> getTimes(String string) {
					return Collections.emptyList();
				}

				@Override
				public boolean saveResults(Path path) {
					return false;
				}

				@Override
				public long getStartTimeNano() {
					return TimeProfiler.this.startNanos;
				}

				@Override
				public int getStartTimeTicks() {
					return TimeProfiler.this.startTick;
				}

				@Override
				public long getEndTimeNano() {
					return l;
				}

				@Override
				public int getEndTimeTicks() {
					return i;
				}

				@Override
				public String getProfilerResults() {
					return "";
				}
			};
		}
	}
}
