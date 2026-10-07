package net.minecraft.server.level;

import net.minecraft.world.level.TicketStorage;

class LoadingChunkTracker extends ChunkTracker {
	private static final int MAX_LEVEL = ChunkLevel.MAX_LEVEL + 1;
	private final DistanceManager distanceManager;
	private final TicketStorage ticketStorage;

	public LoadingChunkTracker(DistanceManager distanceManager, TicketStorage ticketStorage) {
		super(MAX_LEVEL + 1, 16, 256);
		this.distanceManager = distanceManager;
		this.ticketStorage = ticketStorage;
		ticketStorage.setLoadingChunkUpdatedListener(this::update);
	}

	@Override
	protected int getLevelFromSource(long l) {
		return this.ticketStorage.getTicketLevelAt(l, false);
	}

	@Override
	protected int getLevel(long l) {
		if (!this.distanceManager.isChunkToRemove(l)) {
			ChunkHolder chunkHolder = this.distanceManager.getChunk(l);
			if (chunkHolder != null) {
				return chunkHolder.getTicketLevel();
			}
		}

		return MAX_LEVEL;
	}

	@Override
	protected void setLevel(long l, int i) {
		ChunkHolder chunkHolder = this.distanceManager.getChunk(l);
		int j = chunkHolder == null ? MAX_LEVEL : chunkHolder.getTicketLevel();
		if (j != i) {
			chunkHolder = this.distanceManager.updateChunkScheduling(l, i, chunkHolder, j);
			if (chunkHolder != null) {
				this.distanceManager.chunksToUpdateFutures.add(chunkHolder);
			}
		}
	}

	public int runDistanceUpdates(int i) {
		return this.runUpdates(i);
	}
}
