package net.minecraft.server.level;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMaps;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.util.TriState;
import net.minecraft.util.thread.TaskScheduler;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public abstract class DistanceManager {
	private static final Logger LOGGER = LogUtils.getLogger();
	static final int PLAYER_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
	final Long2ObjectMap<ObjectSet<ServerPlayer>> playersPerChunk = new Long2ObjectOpenHashMap<>();
	private final LoadingChunkTracker loadingChunkTracker;
	private final SimulationChunkTracker simulationChunkTracker;
	final TicketStorage ticketStorage;
	private final DistanceManager.FixedPlayerDistanceChunkTracker naturalSpawnChunkCounter = new DistanceManager.FixedPlayerDistanceChunkTracker(8);
	private final DistanceManager.PlayerTicketTracker playerTicketManager = new DistanceManager.PlayerTicketTracker(32);
	protected final Set<ChunkHolder> chunksToUpdateFutures = new ReferenceOpenHashSet<>();
	final ThrottlingChunkTaskDispatcher ticketDispatcher;
	final LongSet ticketsToRelease = new LongOpenHashSet();
	final Executor mainThreadExecutor;
	private int simulationDistance = 10;

	protected DistanceManager(TicketStorage ticketStorage, Executor executor, Executor executor2) {
		this.ticketStorage = ticketStorage;
		this.loadingChunkTracker = new LoadingChunkTracker(this, ticketStorage);
		this.simulationChunkTracker = new SimulationChunkTracker(ticketStorage);
		TaskScheduler<Runnable> taskScheduler = TaskScheduler.wrapExecutor("player ticket throttler", executor2);
		this.ticketDispatcher = new ThrottlingChunkTaskDispatcher(taskScheduler, executor, 4);
		this.mainThreadExecutor = executor2;
	}

	protected abstract boolean isChunkToRemove(long l);

	protected abstract @Nullable ChunkHolder getChunk(long l);

	protected abstract @Nullable ChunkHolder updateChunkScheduling(long l, int i, @Nullable ChunkHolder chunkHolder, int j);

	public boolean runAllUpdates(ChunkMap chunkMap) {
		this.naturalSpawnChunkCounter.runAllUpdates();
		this.simulationChunkTracker.runAllUpdates();
		this.playerTicketManager.runAllUpdates();
		int i = Integer.MAX_VALUE - this.loadingChunkTracker.runDistanceUpdates(Integer.MAX_VALUE);
		boolean bl = i != 0;
		if (bl && SharedConstants.DEBUG_VERBOSE_SERVER_EVENTS) {
			LOGGER.debug("DMU {}", i);
		}

		if (!this.chunksToUpdateFutures.isEmpty()) {
			for (ChunkHolder chunkHolder : this.chunksToUpdateFutures) {
				chunkHolder.updateHighestAllowedStatus(chunkMap);
			}

			for (ChunkHolder chunkHolder : this.chunksToUpdateFutures) {
				chunkHolder.updateFutures(chunkMap, this.mainThreadExecutor);
			}

			this.chunksToUpdateFutures.clear();
			return true;
		} else {
			if (!this.ticketsToRelease.isEmpty()) {
				LongIterator longIterator = this.ticketsToRelease.iterator();

				while (longIterator.hasNext()) {
					long l = longIterator.nextLong();
					if (this.ticketStorage.getTickets(l).stream().anyMatch(ticket -> ticket.getType() == TicketType.PLAYER_LOADING)) {
						ChunkHolder chunkHolder2 = chunkMap.getUpdatingChunkIfPresent(l);
						if (chunkHolder2 == null) {
							throw new IllegalStateException();
						}

						CompletableFuture<ChunkResult<LevelChunk>> completableFuture = chunkHolder2.getEntityTickingChunkFuture();
						completableFuture.thenAccept(chunkResult -> this.mainThreadExecutor.execute(() -> this.ticketDispatcher.release(l, () -> {}, false)));
					}
				}

				this.ticketsToRelease.clear();
			}

			return bl;
		}
	}

	public void addPlayer(SectionPos sectionPos, ServerPlayer serverPlayer) {
		ChunkPos chunkPos = sectionPos.chunk();
		long l = chunkPos.toLong();
		this.playersPerChunk.computeIfAbsent(l, lx -> new ObjectOpenHashSet<>()).add(serverPlayer);
		this.naturalSpawnChunkCounter.update(l, 0, true);
		this.playerTicketManager.update(l, 0, true);
		this.ticketStorage.addTicket(new Ticket(TicketType.PLAYER_SIMULATION, this.getPlayerTicketLevel()), chunkPos);
	}

	public void removePlayer(SectionPos sectionPos, ServerPlayer serverPlayer) {
		ChunkPos chunkPos = sectionPos.chunk();
		long l = chunkPos.toLong();
		ObjectSet<ServerPlayer> objectSet = this.playersPerChunk.get(l);
		objectSet.remove(serverPlayer);
		if (objectSet.isEmpty()) {
			this.playersPerChunk.remove(l);
			this.naturalSpawnChunkCounter.update(l, Integer.MAX_VALUE, false);
			this.playerTicketManager.update(l, Integer.MAX_VALUE, false);
			this.ticketStorage.removeTicket(new Ticket(TicketType.PLAYER_SIMULATION, this.getPlayerTicketLevel()), chunkPos);
		}
	}

	private int getPlayerTicketLevel() {
		return Math.max(0, ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING) - this.simulationDistance);
	}

	public boolean inEntityTickingRange(long l) {
		return ChunkLevel.isEntityTicking(this.simulationChunkTracker.getLevel(l));
	}

	public boolean inBlockTickingRange(long l) {
		return ChunkLevel.isBlockTicking(this.simulationChunkTracker.getLevel(l));
	}

	public int getChunkLevel(long l, boolean bl) {
		return bl ? this.simulationChunkTracker.getLevel(l) : this.loadingChunkTracker.getLevel(l);
	}

	protected void updatePlayerTickets(int i) {
		this.playerTicketManager.updateViewDistance(i);
	}

	public void updateSimulationDistance(int i) {
		if (i != this.simulationDistance) {
			this.simulationDistance = i;
			this.ticketStorage.replaceTicketLevelOfType(this.getPlayerTicketLevel(), TicketType.PLAYER_SIMULATION);
		}
	}

	public int getNaturalSpawnChunkCount() {
		this.naturalSpawnChunkCounter.runAllUpdates();
		return this.naturalSpawnChunkCounter.chunks.size();
	}

	public TriState hasPlayersNearby(long l) {
		this.naturalSpawnChunkCounter.runAllUpdates();
		int i = this.naturalSpawnChunkCounter.getLevel(l);
		if (i <= NaturalSpawner.INSCRIBED_SQUARE_SPAWN_DISTANCE_CHUNK) {
			return TriState.TRUE;
		} else {
			return i > 8 ? TriState.FALSE : TriState.DEFAULT;
		}
	}

	public void forEachEntityTickingChunk(LongConsumer longConsumer) {
		for (Entry entry : Long2ByteMaps.fastIterable(this.simulationChunkTracker.chunks)) {
			byte b = entry.getByteValue();
			long l = entry.getLongKey();
			if (ChunkLevel.isEntityTicking(b)) {
				longConsumer.accept(l);
			}
		}
	}

	public LongIterator getSpawnCandidateChunks() {
		this.naturalSpawnChunkCounter.runAllUpdates();
		return this.naturalSpawnChunkCounter.chunks.keySet().iterator();
	}

	public String getDebugStatus() {
		return this.ticketDispatcher.getDebugStatus();
	}

	public boolean hasTickets() {
		return this.ticketStorage.hasTickets();
	}

	class FixedPlayerDistanceChunkTracker extends ChunkTracker {
		protected final Long2ByteMap chunks = new Long2ByteOpenHashMap();
		protected final int maxDistance;

		protected FixedPlayerDistanceChunkTracker(final int i) {
			super(i + 2, 16, 256);
			this.maxDistance = i;
			this.chunks.defaultReturnValue((byte)(i + 2));
		}

		@Override
		protected int getLevel(long l) {
			return this.chunks.get(l);
		}

		@Override
		protected void setLevel(long l, int i) {
			byte b;
			if (i > this.maxDistance) {
				b = this.chunks.remove(l);
			} else {
				b = this.chunks.put(l, (byte)i);
			}

			this.onLevelChange(l, b, i);
		}

		protected void onLevelChange(long l, int i, int j) {
		}

		@Override
		protected int getLevelFromSource(long l) {
			return this.havePlayer(l) ? 0 : Integer.MAX_VALUE;
		}

		private boolean havePlayer(long l) {
			ObjectSet<ServerPlayer> objectSet = DistanceManager.this.playersPerChunk.get(l);
			return objectSet != null && !objectSet.isEmpty();
		}

		public void runAllUpdates() {
			this.runUpdates(Integer.MAX_VALUE);
		}
	}

	class PlayerTicketTracker extends DistanceManager.FixedPlayerDistanceChunkTracker {
		private int viewDistance;
		private final Long2IntMap queueLevels = Long2IntMaps.synchronize(new Long2IntOpenHashMap());
		private final LongSet toUpdate = new LongOpenHashSet();

		protected PlayerTicketTracker(final int i) {
			super(i);
			this.viewDistance = 0;
			this.queueLevels.defaultReturnValue(i + 2);
		}

		@Override
		protected void onLevelChange(long l, int i, int j) {
			this.toUpdate.add(l);
		}

		public void updateViewDistance(int i) {
			for (Entry entry : this.chunks.long2ByteEntrySet()) {
				byte b = entry.getByteValue();
				long l = entry.getLongKey();
				this.onLevelChange(l, b, this.haveTicketFor(b), b <= i);
			}

			this.viewDistance = i;
		}

		private void onLevelChange(long l, int i, boolean bl, boolean bl2) {
			if (bl != bl2) {
				Ticket ticket = new Ticket(TicketType.PLAYER_LOADING, DistanceManager.PLAYER_TICKET_LEVEL);
				if (bl2) {
					DistanceManager.this.ticketDispatcher.submit(() -> DistanceManager.this.mainThreadExecutor.execute(() -> {
						if (this.haveTicketFor(this.getLevel(l))) {
							DistanceManager.this.ticketStorage.addTicket(l, ticket);
							DistanceManager.this.ticketsToRelease.add(l);
						} else {
							DistanceManager.this.ticketDispatcher.release(l, () -> {}, false);
						}
					}), l, () -> i);
				} else {
					DistanceManager.this.ticketDispatcher
						.release(l, () -> DistanceManager.this.mainThreadExecutor.execute(() -> DistanceManager.this.ticketStorage.removeTicket(l, ticket)), true);
				}
			}
		}

		@Override
		public void runAllUpdates() {
			super.runAllUpdates();
			if (!this.toUpdate.isEmpty()) {
				LongIterator longIterator = this.toUpdate.iterator();

				while (longIterator.hasNext()) {
					long l = longIterator.nextLong();
					int i = this.queueLevels.get(l);
					int j = this.getLevel(l);
					if (i != j) {
						DistanceManager.this.ticketDispatcher.onLevelChange(new ChunkPos(l), () -> this.queueLevels.get(l), j, ix -> {
							if (ix >= this.queueLevels.defaultReturnValue()) {
								this.queueLevels.remove(l);
							} else {
								this.queueLevels.put(l, ix);
							}
						});
						this.onLevelChange(l, j, this.haveTicketFor(i), this.haveTicketFor(j));
					}
				}

				this.toUpdate.clear();
			}
		}

		private boolean haveTicketFor(int i) {
			return i <= this.viewDistance;
		}
	}
}
