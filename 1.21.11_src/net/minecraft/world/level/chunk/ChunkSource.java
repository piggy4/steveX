package net.minecraft.world.level.chunk;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.Nullable;

public abstract class ChunkSource implements LightChunkGetter, AutoCloseable {
	public @Nullable LevelChunk getChunk(int i, int j, boolean bl) {
		return (LevelChunk)this.getChunk(i, j, ChunkStatus.FULL, bl);
	}

	public @Nullable LevelChunk getChunkNow(int i, int j) {
		return this.getChunk(i, j, false);
	}

	@Override
	public @Nullable LightChunk getChunkForLighting(int i, int j) {
		return this.getChunk(i, j, ChunkStatus.EMPTY, false);
	}

	public boolean hasChunk(int i, int j) {
		return this.getChunk(i, j, ChunkStatus.FULL, false) != null;
	}

	public abstract @Nullable ChunkAccess getChunk(int i, int j, ChunkStatus chunkStatus, boolean bl);

	public abstract void tick(BooleanSupplier booleanSupplier, boolean bl);

	public void onSectionEmptinessChanged(int i, int j, int k, boolean bl) {
	}

	public abstract String gatherStats();

	public abstract int getLoadedChunksCount();

	@Override
	public void close() throws IOException {
	}

	public abstract LevelLightEngine getLightEngine();

	public void setSpawnSettings(boolean bl) {
	}

	public boolean updateChunkForced(ChunkPos chunkPos, boolean bl) {
		return false;
	}

	public LongSet getForceLoadedChunks() {
		return LongSet.of();
	}
}
