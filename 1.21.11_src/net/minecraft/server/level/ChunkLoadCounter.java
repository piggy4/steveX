package net.minecraft.server.level;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class ChunkLoadCounter {
	private final List<ChunkHolder> pendingChunks = new ArrayList<>();
	private int totalChunks;

	public void track(ServerLevel serverLevel, Runnable runnable) {
		ServerChunkCache serverChunkCache = serverLevel.getChunkSource();
		LongSet longSet = new LongOpenHashSet();
		serverChunkCache.runDistanceManagerUpdates();
		serverChunkCache.chunkMap.allChunksWithAtLeastStatus(ChunkStatus.FULL).forEach(chunkHolder -> longSet.add(chunkHolder.getPos().toLong()));
		runnable.run();
		serverChunkCache.runDistanceManagerUpdates();
		serverChunkCache.chunkMap.allChunksWithAtLeastStatus(ChunkStatus.FULL).forEach(chunkHolder -> {
			if (!longSet.contains(chunkHolder.getPos().toLong())) {
				this.pendingChunks.add(chunkHolder);
				this.totalChunks++;
			}
		});
	}

	public int readyChunks() {
		return this.totalChunks - this.pendingChunks();
	}

	public int pendingChunks() {
		this.pendingChunks.removeIf(chunkHolder -> chunkHolder.getLatestStatus() == ChunkStatus.FULL);
		return this.pendingChunks.size();
	}

	public int totalChunks() {
		return this.totalChunks;
	}
}
