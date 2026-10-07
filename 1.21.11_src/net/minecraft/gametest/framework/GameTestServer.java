package net.minecraft.gametest.framework;

import com.google.common.base.Stopwatch;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ServicesKeySet;
import com.mojang.brigadier.StringReader;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Lifecycle;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import net.minecraft.SystemReport;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceSelectorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gizmos.GizmoCollector;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LoggingLevelLoadListener;
import net.minecraft.server.notifications.EmptyNotificationService;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.ProfileResolver;
import net.minecraft.server.players.UserNameToIdResolver;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.debugchart.LocalSampleLogger;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class GameTestServer extends MinecraftServer {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int PROGRESS_REPORT_INTERVAL = 20;
	private static final int TEST_POSITION_RANGE = 14999992;
	private static final Services NO_SERVICES = new Services(
		null, ServicesKeySet.EMPTY, null, new GameTestServer.MockUserNameToIdResolver(), new GameTestServer.MockProfileResolver()
	);
	private static final FeatureFlagSet ENABLED_FEATURES = FeatureFlags.REGISTRY
		.allFlags()
		.subtract(FeatureFlagSet.of(FeatureFlags.REDSTONE_EXPERIMENTS, FeatureFlags.MINECART_IMPROVEMENTS));
	private final LocalSampleLogger sampleLogger = new LocalSampleLogger(4);
	private final Optional<String> testSelection;
	private final boolean verify;
	private List<GameTestBatch> testBatches = new ArrayList<>();
	private final Stopwatch stopwatch = Stopwatch.createUnstarted();
	private static final WorldOptions WORLD_OPTIONS = new WorldOptions(0L, false, false);
	private @Nullable MultipleTestTracker testTracker;

	public static GameTestServer create(
		Thread thread, LevelStorageSource.LevelStorageAccess levelStorageAccess, PackRepository packRepository, Optional<String> optional, boolean bl
	) {
		packRepository.reload();
		ArrayList<String> arrayList = new ArrayList<>(packRepository.getAvailableIds());
		arrayList.remove("vanilla");
		arrayList.addFirst("vanilla");
		WorldDataConfiguration worldDataConfiguration = new WorldDataConfiguration(new DataPackConfig(arrayList, List.of()), ENABLED_FEATURES);
		LevelSettings levelSettings = new LevelSettings(
			"Test Level", GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(ENABLED_FEATURES), worldDataConfiguration
		);
		WorldLoader.PackConfig packConfig = new WorldLoader.PackConfig(packRepository, worldDataConfiguration, false, true);
		WorldLoader.InitConfig initConfig = new WorldLoader.InitConfig(packConfig, Commands.CommandSelection.DEDICATED, LevelBasedPermissionSet.OWNER);

		try {
			LOGGER.debug("Starting resource loading");
			Stopwatch stopwatch = Stopwatch.createStarted();
			WorldStem worldStem = Util.<WorldStem>blockUntilDone(
					executor -> WorldLoader.load(
						initConfig,
						dataLoadContext -> {
							Registry<LevelStem> registry = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable()).freeze();
							WorldDimensions.Complete complete = dataLoadContext.datapackWorldgen()
								.lookupOrThrow(Registries.WORLD_PRESET)
								.getOrThrow(WorldPresets.FLAT)
								.value()
								.createWorldDimensions()
								.bake(registry);
							return new WorldLoader.DataLoadOutput<>(
								new PrimaryLevelData(levelSettings, WORLD_OPTIONS, complete.specialWorldProperty(), complete.lifecycle()), complete.dimensionsRegistryAccess()
							);
						},
						WorldStem::new,
						Util.backgroundExecutor(),
						executor
					)
				)
				.get();
			stopwatch.stop();
			LOGGER.debug("Finished resource loading after {} ms", stopwatch.elapsed(TimeUnit.MILLISECONDS));
			return new GameTestServer(thread, levelStorageAccess, packRepository, worldStem, optional, bl);
		} catch (Exception exception) {
			LOGGER.warn("Failed to load vanilla datapack, bit oops", exception);
			System.exit(-1);
			throw new IllegalStateException();
		}
	}

	private GameTestServer(
		Thread thread,
		LevelStorageSource.LevelStorageAccess levelStorageAccess,
		PackRepository packRepository,
		WorldStem worldStem,
		Optional<String> optional,
		boolean bl
	) {
		super(
			thread, levelStorageAccess, packRepository, worldStem, Proxy.NO_PROXY, DataFixers.getDataFixer(), NO_SERVICES, LoggingLevelLoadListener.forDedicatedServer()
		);
		this.testSelection = optional;
		this.verify = bl;
	}

	@Override
	public boolean initServer() {
		this.setPlayerList(new PlayerList(this, this.registries(), this.playerDataStorage, new EmptyNotificationService()) {});
		Gizmos.withCollector(GizmoCollector.NOOP);
		this.loadLevel();
		ServerLevel serverLevel = this.overworld();
		this.testBatches = this.evaluateTestsToRun(serverLevel);
		LOGGER.info("Started game test server");
		return true;
	}

	private List<GameTestBatch> evaluateTestsToRun(ServerLevel serverLevel) {
		Registry<GameTestInstance> registry = serverLevel.registryAccess().lookupOrThrow(Registries.TEST_INSTANCE);
		Collection<Holder.Reference<GameTestInstance>> collection;
		GameTestBatchFactory.TestDecorator testDecorator;
		if (this.testSelection.isPresent()) {
			collection = getTestsForSelection(serverLevel.registryAccess(), this.testSelection.get()).filter(reference -> !reference.value().manualOnly()).toList();
			if (this.verify) {
				testDecorator = GameTestServer::rotateAndMultiply;
				LOGGER.info("Verify requested. Will run each test that matches {} {} times", this.testSelection.get(), 100 * Rotation.values().length);
			} else {
				testDecorator = GameTestBatchFactory.DIRECT;
				LOGGER.info("Will run tests matching {} ({} tests)", this.testSelection.get(), collection.size());
			}
		} else {
			collection = registry.listElements().filter(reference -> !reference.value().manualOnly()).toList();
			testDecorator = GameTestBatchFactory.DIRECT;
		}

		return GameTestBatchFactory.divideIntoBatches(collection, testDecorator, serverLevel);
	}

	private static Stream<GameTestInfo> rotateAndMultiply(Holder.Reference<GameTestInstance> reference, ServerLevel serverLevel) {
		Builder<GameTestInfo> builder = Stream.builder();

		for (Rotation rotation : Rotation.values()) {
			for (int i = 0; i < 100; i++) {
				builder.add(new GameTestInfo(reference, rotation, serverLevel, RetryOptions.noRetries()));
			}
		}

		return builder.build();
	}

	public static Stream<Holder.Reference<GameTestInstance>> getTestsForSelection(RegistryAccess registryAccess, String string) {
		return ResourceSelectorArgument.parse(new StringReader(string), registryAccess.lookupOrThrow(Registries.TEST_INSTANCE)).stream();
	}

	@Override
	public void tickServer(BooleanSupplier booleanSupplier) {
		super.tickServer(booleanSupplier);
		ServerLevel serverLevel = this.overworld();
		if (!this.haveTestsStarted()) {
			this.startTests(serverLevel);
		}

		if (serverLevel.getGameTime() % 20L == 0L) {
			LOGGER.info(this.testTracker.getProgressBar());
		}

		if (this.testTracker.isDone()) {
			this.halt(false);
			LOGGER.info(this.testTracker.getProgressBar());
			GlobalTestReporter.finish();
			LOGGER.info("========= {} GAME TESTS COMPLETE IN {} ======================", this.testTracker.getTotalCount(), this.stopwatch.stop());
			if (this.testTracker.hasFailedRequired()) {
				LOGGER.info("{} required tests failed :(", this.testTracker.getFailedRequiredCount());
				this.testTracker.getFailedRequired().forEach(GameTestServer::logFailedTest);
			} else {
				LOGGER.info("All {} required tests passed :)", this.testTracker.getTotalCount());
			}

			if (this.testTracker.hasFailedOptional()) {
				LOGGER.info("{} optional tests failed", this.testTracker.getFailedOptionalCount());
				this.testTracker.getFailedOptional().forEach(GameTestServer::logFailedTest);
			}

			LOGGER.info("====================================================");
		}
	}

	private static void logFailedTest(GameTestInfo gameTestInfo) {
		if (gameTestInfo.getRotation() != Rotation.NONE) {
			LOGGER.info(
				"   - {} with rotation {}: {}", gameTestInfo.id(), gameTestInfo.getRotation().getSerializedName(), gameTestInfo.getError().getDescription().getString()
			);
		} else {
			LOGGER.info("   - {}: {}", gameTestInfo.id(), gameTestInfo.getError().getDescription().getString());
		}
	}

	@Override
	public SampleLogger getTickTimeLogger() {
		return this.sampleLogger;
	}

	@Override
	public boolean isTickTimeLoggingEnabled() {
		return false;
	}

	@Override
	public void waitUntilNextTick() {
		this.runAllTasks();
	}

	@Override
	public SystemReport fillServerSystemReport(SystemReport systemReport) {
		systemReport.setDetail("Type", "Game test server");
		return systemReport;
	}

	@Override
	public void onServerExit() {
		super.onServerExit();
		LOGGER.info("Game test server shutting down");
		System.exit(this.testTracker != null ? this.testTracker.getFailedRequiredCount() : -1);
	}

	@Override
	public void onServerCrash(CrashReport crashReport) {
		super.onServerCrash(crashReport);
		LOGGER.error("Game test server crashed\n{}", crashReport.getFriendlyReport(ReportType.CRASH));
		System.exit(1);
	}

	private void startTests(ServerLevel serverLevel) {
		BlockPos blockPos = new BlockPos(
			serverLevel.random.nextIntBetweenInclusive(-14999992, 14999992), -59, serverLevel.random.nextIntBetweenInclusive(-14999992, 14999992)
		);
		serverLevel.setRespawnData(LevelData.RespawnData.of(serverLevel.dimension(), blockPos, 0.0F, 0.0F));
		GameTestRunner gameTestRunner = GameTestRunner.Builder.fromBatches(this.testBatches, serverLevel)
			.newStructureSpawner(new StructureGridSpawner(blockPos, 8, false))
			.build();
		Collection<GameTestInfo> collection = gameTestRunner.getTestInfos();
		this.testTracker = new MultipleTestTracker(collection);
		LOGGER.info("{} tests are now running at position {}!", this.testTracker.getTotalCount(), blockPos.toShortString());
		this.stopwatch.reset();
		this.stopwatch.start();
		gameTestRunner.start();
	}

	private boolean haveTestsStarted() {
		return this.testTracker != null;
	}

	@Override
	public boolean isHardcore() {
		return false;
	}

	@Override
	public LevelBasedPermissionSet operatorUserPermissions() {
		return LevelBasedPermissionSet.ALL;
	}

	@Override
	public PermissionSet getFunctionCompilationPermissions() {
		return LevelBasedPermissionSet.OWNER;
	}

	@Override
	public boolean shouldRconBroadcast() {
		return false;
	}

	@Override
	public boolean isDedicatedServer() {
		return false;
	}

	@Override
	public int getRateLimitPacketsPerSecond() {
		return 0;
	}

	@Override
	public boolean useNativeTransport() {
		return false;
	}

	@Override
	public boolean isPublished() {
		return false;
	}

	@Override
	public boolean shouldInformAdmins() {
		return false;
	}

	@Override
	public boolean isSingleplayerOwner(NameAndId nameAndId) {
		return false;
	}

	@Override
	public int getMaxPlayers() {
		return 1;
	}

	static class MockProfileResolver implements ProfileResolver {
		@Override
		public Optional<GameProfile> fetchByName(String string) {
			return Optional.empty();
		}

		@Override
		public Optional<GameProfile> fetchById(UUID uUID) {
			return Optional.empty();
		}
	}

	static class MockUserNameToIdResolver implements UserNameToIdResolver {
		private final Set<NameAndId> savedIds = new HashSet<>();

		@Override
		public void add(NameAndId nameAndId) {
			this.savedIds.add(nameAndId);
		}

		@Override
		public Optional<NameAndId> get(String string) {
			return this.savedIds.stream().filter(nameAndId -> nameAndId.name().equals(string)).findFirst().or(() -> Optional.of(NameAndId.createOffline(string)));
		}

		@Override
		public Optional<NameAndId> get(UUID uUID) {
			return this.savedIds.stream().filter(nameAndId -> nameAndId.id().equals(uUID)).findFirst();
		}

		@Override
		public void resolveOfflineUsers(boolean bl) {
		}

		@Override
		public void save() {
		}
	}
}
