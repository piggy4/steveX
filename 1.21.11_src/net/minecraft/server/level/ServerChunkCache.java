package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FileUtil;
import net.minecraft.util.Util;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ServerChunkCache extends ChunkSource {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final DistanceManager distanceManager;
	private final ServerLevel level;
	final Thread mainThread;
	final ThreadedLevelLightEngine lightEngine;
	private final ServerChunkCache.MainThreadExecutor mainThreadProcessor;
	public final ChunkMap chunkMap;
	private final DimensionDataStorage dataStorage;
	private final TicketStorage ticketStorage;
	private long lastInhabitedUpdate;
	private boolean spawnEnemies = true;
	private static final int CACHE_SIZE = 4;
	private final long[] lastChunkPos = new long[4];
	private final @Nullable ChunkStatus[] lastChunkStatus = new ChunkStatus[4];
	private final @Nullable ChunkAccess[] lastChunk = new ChunkAccess[4];
	private final List<LevelChunk> spawningChunks = new ObjectArrayList<>();
	private final Set<ChunkHolder> chunkHoldersToBroadcast = new ReferenceOpenHashSet<>();
	@VisibleForDebug
	private NaturalSpawner.@Nullable SpawnState lastSpawnState;

	public ServerChunkCache(
		ServerLevel serverLevel,
		LevelStorageSource.LevelStorageAccess levelStorageAccess,
		DataFixer dataFixer,
		StructureTemplateManager structureTemplateManager,
		Executor executor,
		ChunkGenerator chunkGenerator,
		int i,
		int j,
		boolean bl,
		ChunkStatusUpdateListener chunkStatusUpdateListener,
		Supplier<DimensionDataStorage> supplier
	) {
		this.level = serverLevel;
		this.mainThreadProcessor = new ServerChunkCache.MainThreadExecutor(serverLevel);
		this.mainThread = Thread.currentThread();
		Path path = levelStorageAccess.getDimensionPath(serverLevel.dimension()).resolve("data");

		try {
			FileUtil.createDirectoriesSafe(path);
		} catch (IOException iOException) {
			LOGGER.error("Failed to create dimension data storage directory", iOException);
		}

		this.dataStorage = new DimensionDataStorage(path, dataFixer, serverLevel.registryAccess());
		this.ticketStorage = this.dataStorage.computeIfAbsent(TicketStorage.TYPE);
		this.chunkMap = new ChunkMap(
			serverLevel,
			levelStorageAccess,
			dataFixer,
			structureTemplateManager,
			executor,
			this.mainThreadProcessor,
			this,
			chunkGenerator,
			chunkStatusUpdateListener,
			supplier,
			this.ticketStorage,
			i,
			bl
		);
		this.lightEngine = this.chunkMap.getLightEngine();
		this.distanceManager = this.chunkMap.getDistanceManager();
		this.distanceManager.updateSimulationDistance(j);
		this.clearCache();
	}

	public ThreadedLevelLightEngine getLightEngine() {
		return this.lightEngine;
	}

	private @Nullable ChunkHolder getVisibleChunkIfPresent(long l) {
		return this.chunkMap.getVisibleChunkIfPresent(l);
	}

	private void storeInCache(long l, @Nullable ChunkAccess chunkAccess, ChunkStatus chunkStatus) {
		for (int i = 3; i > 0; i--) {
			this.lastChunkPos[i] = this.lastChunkPos[i - 1];
			this.lastChunkStatus[i] = this.lastChunkStatus[i - 1];
			this.lastChunk[i] = this.lastChunk[i - 1];
		}

		this.lastChunkPos[0] = l;
		this.lastChunkStatus[0] = chunkStatus;
		this.lastChunk[0] = chunkAccess;
	}

	@Override
	public @Nullable ChunkAccess getChunk(int i, int j, ChunkStatus chunkStatus, boolean bl) {
		if (Thread.currentThread() != this.mainThread) {
			return CompletableFuture.<ChunkAccess>supplyAsync(() -> this.getChunk(i, j, chunkStatus, bl), this.mainThreadProcessor).join();
		}

		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.incrementCounter("getChunk");
		long l = ChunkPos.asLong(i, j);

		for (int k = 0; k < 4; k++) {
			if (l == this.lastChunkPos[k] && chunkStatus == this.lastChunkStatus[k]) {
				ChunkAccess chunkAccess = this.lastChunk[k];
				if (chunkAccess != null || !bl) {
					return chunkAccess;
				}
			}
		}

		profilerFiller.incrementCounter("getChunkCacheMiss");
		CompletableFuture<ChunkResult<ChunkAccess>> completableFuture = this.getChunkFutureMainThread(i, j, chunkStatus, bl);
		this.mainThreadProcessor.managedBlock(completableFuture::isDone);
		ChunkResult<ChunkAccess> chunkResult = completableFuture.join();
		ChunkAccess chunkAccess2 = chunkResult.orElse(null);
		if (chunkAccess2 == null && bl) {
			throw (IllegalStateException)Util.pauseInIde(new IllegalStateException("Chunk not there when requested: " + chunkResult.getError()));
		}

		this.storeInCache(l, chunkAccess2, chunkStatus);
		return chunkAccess2;
	}

	@Override
	public @Nullable LevelChunk getChunkNow(int i, int j) {
		if (Thread.currentThread() != this.mainThread) {
			return null;
		}

		Profiler.get().incrementCounter("getChunkNow");
		long l = ChunkPos.asLong(i, j);

		for (int k = 0; k < 4; k++) {
			if (l == this.lastChunkPos[k] && this.lastChunkStatus[k] == ChunkStatus.FULL) {
				ChunkAccess chunkAccess = this.lastChunk[k];
				return chunkAccess instanceof LevelChunk ? (LevelChunk)chunkAccess : null;
			}
		}

		ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(l);
		if (chunkHolder == null) {
			return null;
		}

		ChunkAccess chunkAccess = chunkHolder.getChunkIfPresent(ChunkStatus.FULL);
		if (chunkAccess != null) {
			this.storeInCache(l, chunkAccess, ChunkStatus.FULL);
			if (chunkAccess instanceof LevelChunk) {
				return (LevelChunk)chunkAccess;
			}
		}

		return null;
	}

	private void clearCache() {
		Arrays.fill(this.lastChunkPos, ChunkPos.INVALID_CHUNK_POS);
		Arrays.fill(this.lastChunkStatus, null);
		Arrays.fill(this.lastChunk, null);
	}

	public CompletableFuture<ChunkResult<ChunkAccess>> getChunkFuture(int i, int j, ChunkStatus chunkStatus, boolean bl) {
		boolean bl2 = Thread.currentThread() == this.mainThread;
		CompletableFuture<ChunkResult<ChunkAccess>> completableFuture;
		if (bl2) {
			completableFuture = this.getChunkFutureMainThread(i, j, chunkStatus, bl);
			this.mainThreadProcessor.managedBlock(completableFuture::isDone);
		} else {
			completableFuture = CompletableFuture.<CompletableFuture<ChunkResult<ChunkAccess>>>supplyAsync(
					() -> this.getChunkFutureMainThread(i, j, chunkStatus, bl), this.mainThreadProcessor
				)
				.thenCompose(completableFuturex -> completableFuturex);
		}

		return completableFuture;
	}

	private CompletableFuture<ChunkResult<ChunkAccess>> getChunkFutureMainThread(int i, int j, ChunkStatus chunkStatus, boolean bl) {
		ChunkPos chunkPos = new ChunkPos(i, j);
		long l = chunkPos.toLong();
		int k = ChunkLevel.byStatus(chunkStatus);
		ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(l);
		if (bl) {
			this.addTicket(new Ticket(TicketType.UNKNOWN, k), chunkPos);
			if (this.chunkAbsent(chunkHolder, k)) {
				ProfilerFiller profilerFiller = Profiler.get();
				profilerFiller.push("chunkLoad");
				this.runDistanceManagerUpdates();
				chunkHolder = this.getVisibleChunkIfPresent(l);
				profilerFiller.pop();
				if (this.chunkAbsent(chunkHolder, k)) {
					throw (IllegalStateException)Util.pauseInIde(new IllegalStateException("No chunk holder after ticket has been added"));
				}
			}
		}

		return this.chunkAbsent(chunkHolder, k) ? GenerationChunkHolder.UNLOADED_CHUNK_FUTURE : chunkHolder.scheduleChunkGenerationTask(chunkStatus, this.chunkMap);
	}

	private boolean chunkAbsent(@Nullable ChunkHolder chunkHolder, int i) {
		return chunkHolder == null || chunkHolder.getTicketLevel() > i;
	}

	@Override
	public boolean hasChunk(int i, int j) {
		ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(new ChunkPos(i, j).toLong());
		int k = ChunkLevel.byStatus(ChunkStatus.FULL);
		return !this.chunkAbsent(chunkHolder, k);
	}

	@Override
	public @Nullable LightChunk getChunkForLighting(int i, int j) {
		long l = ChunkPos.asLong(i, j);
		ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(l);
		return chunkHolder == null ? null : chunkHolder.getChunkIfPresentUnchecked(ChunkStatus.INITIALIZE_LIGHT.getParent());
	}

	public Level getLevel() {
		return this.level;
	}

	public boolean pollTask() {
		return this.mainThreadProcessor.pollTask();
	}

	boolean runDistanceManagerUpdates() {
		boolean bl = this.distanceManager.runAllUpdates(this.chunkMap);
		boolean bl2 = this.chunkMap.promoteChunkMap();
		this.chunkMap.runGenerationTasks();
		if (!bl && !bl2) {
			return false;
		}

		this.clearCache();
		return true;
	}

	public boolean isPositionTicking(long l) {
		if (!this.level.shouldTickBlocksAt(l)) {
			return false;
		}

		ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(l);
		return chunkHolder == null ? false : chunkHolder.getTickingChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).isSuccess();
	}

	public void save(boolean bl) {
		this.runDistanceManagerUpdates();
		this.chunkMap.saveAllChunks(bl);
	}

	@Override
	public void close() throws IOException {
		this.save(true);
		this.dataStorage.close();
		this.lightEngine.close();
		this.chunkMap.close();
	}

	@Override
	public void tick(BooleanSupplier booleanSupplier, boolean bl) {
		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push("purge");
		if (this.level.tickRateManager().runsNormally() || !bl) {
			this.ticketStorage.purgeStaleTickets(this.chunkMap);
		}

		this.runDistanceManagerUpdates();
		profilerFiller.popPush("chunks");
		if (bl) {
			this.tickChunks();
			this.chunkMap.tick();
		}

		profilerFiller.popPush("unload");
		this.chunkMap.tick(booleanSupplier);
		profilerFiller.pop();
		this.clearCache();
	}

	private void tickChunks() {
		long l = this.level.getGameTime();
		long m = l - this.lastInhabitedUpdate;
		this.lastInhabitedUpdate = l;
		if (!this.level.isDebug()) {
			ProfilerFiller profilerFiller = Profiler.get();
			profilerFiller.push("pollingChunks");
			if (this.level.tickRateManager().runsNormally()) {
				profilerFiller.push("tickingChunks");
				this.tickChunks(profilerFiller, m);
				profilerFiller.pop();
			}

			this.broadcastChangedChunks(profilerFiller);
			profilerFiller.pop();
		}
	}

	private void broadcastChangedChunks(ProfilerFiller profilerFiller) {
		profilerFiller.push("broadcast");

		for (ChunkHolder chunkHolder : this.chunkHoldersToBroadcast) {
			LevelChunk levelChunk = chunkHolder.getTickingChunk();
			if (levelChunk != null) {
				chunkHolder.broadcastChanges(levelChunk);
			}
		}

		this.chunkHoldersToBroadcast.clear();
		profilerFiller.pop();
	}

	private void tickChunks(ProfilerFiller profilerFiller, long l) {
		profilerFiller.push("naturalSpawnCount");
		int i = this.distanceManager.getNaturalSpawnChunkCount();
		NaturalSpawner.SpawnState spawnState = NaturalSpawner.createState(
			i, this.level.getAllEntities(), this::getFullChunk, new LocalMobCapCalculator(this.chunkMap)
		);
		this.lastSpawnState = spawnState;
		boolean bl = this.level.getGameRules().get(GameRules.SPAWN_MOBS);
		int j = this.level.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
		List<MobCategory> list;
		if (bl) {
			boolean bl2 = this.level.getGameTime() % 400L == 0L;
			list = NaturalSpawner.getFilteredSpawningCategories(spawnState, true, this.spawnEnemies, bl2);
		} else {
			list = List.of();
		}

		List<LevelChunk> list2 = this.spawningChunks;

		try {
			profilerFiller.popPush("filteringSpawningChunks");
			this.chunkMap.collectSpawningChunks(list2);
			profilerFiller.popPush("shuffleSpawningChunks");
			Util.shuffle(list2, this.level.random);
			profilerFiller.popPush("tickSpawningChunks");

			for (LevelChunk levelChunk : list2) {
				this.tickSpawningChunk(levelChunk, l, list, spawnState);
			}
		} finally {
			list2.clear();
		}

		profilerFiller.popPush("tickTickingChunks");
		this.chunkMap.forEachBlockTickingChunk(levelChunkx -> this.level.tickChunk(levelChunkx, j));
		if (bl) {
			profilerFiller.popPush("customSpawners");
			this.level.tickCustomSpawners(this.spawnEnemies);
		}

		profilerFiller.pop();
	}

	private void tickSpawningChunk(LevelChunk levelChunk, long l, List<MobCategory> list, NaturalSpawner.SpawnState spawnState) {
		ChunkPos chunkPos = levelChunk.getPos();
		levelChunk.incrementInhabitedTime(l);
		if (this.distanceManager.inEntityTickingRange(chunkPos.toLong())) {
			this.level.tickThunder(levelChunk);
		}

		if (!list.isEmpty()) {
			if (this.level.canSpawnEntitiesInChunk(chunkPos)) {
				NaturalSpawner.spawnForChunk(this.level, levelChunk, spawnState, list);
			}
		}
	}

	private void getFullChunk(long l, Consumer<LevelChunk> consumer) {
		ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(l);
		if (chunkHolder != null) {
			chunkHolder.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).ifSuccess(consumer);
		}
	}

	@Override
	public String gatherStats() {
		return Integer.toString(this.getLoadedChunksCount());
	}

	@VisibleForTesting
	public int getPendingTasksCount() {
		return this.mainThreadProcessor.getPendingTasksCount();
	}

	public ChunkGenerator getGenerator() {
		return this.chunkMap.generator();
	}

	public ChunkGeneratorStructureState getGeneratorState() {
		return this.chunkMap.generatorState();
	}

	public RandomState randomState() {
		return this.chunkMap.randomState();
	}

	@Override
	public int getLoadedChunksCount() {
		return this.chunkMap.size();
	}

	public void blockChanged(BlockPos blockPos) {
		int i = SectionPos.blockToSectionCoord(blockPos.getX());
		int j = SectionPos.blockToSectionCoord(blockPos.getZ());
		ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(ChunkPos.asLong(i, j));
		if (chunkHolder != null && chunkHolder.blockChanged(blockPos)) {
			this.chunkHoldersToBroadcast.add(chunkHolder);
		}
	}

	@Override
	public void onLightUpdate(LightLayer lightLayer, SectionPos sectionPos) {
		this.mainThreadProcessor.execute(() -> {
			ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(sectionPos.chunk().toLong());
			if (chunkHolder != null && chunkHolder.sectionLightChanged(lightLayer, sectionPos.y())) {
				this.chunkHoldersToBroadcast.add(chunkHolder);
			}
		});
	}

	public boolean hasActiveTickets() {
		return this.ticketStorage.shouldKeepDimensionActive();
	}

	public void addTicket(Ticket ticket, ChunkPos chunkPos) {
		this.ticketStorage.addTicket(ticket, chunkPos);
	}

	public CompletableFuture<?> addTicketAndLoadWithRadius(TicketType ticketType, ChunkPos chunkPos, int i) {
		if (!ticketType.doesLoad()) {
			throw new IllegalStateException("Ticket type " + ticketType + " does not trigger chunk loading");
		}

		if (ticketType.canExpireIfUnloaded()) {
			throw new IllegalStateException("Ticket type " + ticketType + " can expire before it loads, cannot fetch asynchronously");
		}

		this.addTicketWithRadius(ticketType, chunkPos, i);
		this.runDistanceManagerUpdates();
		ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(chunkPos.toLong());
		Objects.requireNonNull(chunkHolder, "No chunk was scheduled for loading");
		return this.chunkMap.getChunkRangeFuture(chunkHolder, i, ix -> ChunkStatus.FULL);
	}

	public void addTicketWithRadius(TicketType ticketType, ChunkPos chunkPos, int i) {
		this.ticketStorage.addTicketWithRadius(ticketType, chunkPos, i);
	}

	public void removeTicketWithRadius(TicketType ticketType, ChunkPos chunkPos, int i) {
		this.ticketStorage.removeTicketWithRadius(ticketType, chunkPos, i);
	}

	@Override
	public boolean updateChunkForced(ChunkPos chunkPos, boolean bl) {
		return this.ticketStorage.updateChunkForced(chunkPos, bl);
	}

	@Override
	public LongSet getForceLoadedChunks() {
		return this.ticketStorage.getForceLoadedChunks();
	}

	public void move(ServerPlayer serverPlayer) {
		if (!serverPlayer.isRemoved()) {
			this.chunkMap.move(serverPlayer);
			if (serverPlayer.isReceivingWaypoints()) {
				this.level.getWaypointManager().updatePlayer(serverPlayer);
			}
		}
	}

	public void removeEntity(Entity entity) {
		this.chunkMap.removeEntity(entity);
	}

	public void addEntity(Entity entity) {
		this.chunkMap.addEntity(entity);
	}

	public void sendToTrackingPlayersAndSelf(Entity entity, Packet<? super ClientGamePacketListener> packet) {
		this.chunkMap.sendToTrackingPlayersAndSelf(entity, packet);
	}

	public void sendToTrackingPlayers(Entity entity, Packet<? super ClientGamePacketListener> packet) {
		this.chunkMap.sendToTrackingPlayers(entity, packet);
	}

	public void setViewDistance(int i) {
		this.chunkMap.setServerViewDistance(i);
	}

	public void setSimulationDistance(int i) {
		this.distanceManager.updateSimulationDistance(i);
	}

	@Override
	public void setSpawnSettings(boolean bl) {
		this.spawnEnemies = bl;
	}

	public String getChunkDebugData(ChunkPos chunkPos) {
		return this.chunkMap.getChunkDebugData(chunkPos);
	}

	public DimensionDataStorage getDataStorage() {
		return this.dataStorage;
	}

	public PoiManager getPoiManager() {
		return this.chunkMap.getPoiManager();
	}

	public ChunkScanAccess chunkScanner() {
		return this.chunkMap.chunkScanner();
	}

	@VisibleForDebug
	public NaturalSpawner.@Nullable SpawnState getLastSpawnState() {
		return this.lastSpawnState;
	}

	public void deactivateTicketsOnClosing() {
		this.ticketStorage.deactivateTicketsOnClosing();
	}

	public void onChunkReadyToSend(ChunkHolder chunkHolder) {
		if (chunkHolder.hasChangesToBroadcast()) {
			this.chunkHoldersToBroadcast.add(chunkHolder);
		}
	}

	final class MainThreadExecutor extends BlockableEventLoop<Runnable> {
		MainThreadExecutor(final Level level) {
			super("Chunk source main thread executor for " + level.dimension().identifier());
		}

		@Override
		public void managedBlock(BooleanSupplier booleanSupplier) {
			super.managedBlock(() -> MinecraftServer.throwIfFatalException() && booleanSupplier.getAsBoolean());
		}

		@Override
		public Runnable wrapRunnable(Runnable runnable) {
			return runnable;
		}

		@Override
		protected boolean shouldRun(Runnable runnable) {
			return true;
		}

		@Override
		protected boolean scheduleExecutables() {
			return true;
		}

		@Override
		protected Thread getRunningThread() {
			return ServerChunkCache.this.mainThread;
		}

		@Override
		protected void doRunTask(Runnable runnable) {
			Profiler.get().incrementCounter("runTask");
			super.doRunTask(runnable);
		}

		@Override
		protected boolean pollTask() {
			if (ServerChunkCache.this.runDistanceManagerUpdates()) {
				return true;
			}

			ServerChunkCache.this.lightEngine.tryScheduleUpdate();
			return super.pollTask();
		}
	}
}
