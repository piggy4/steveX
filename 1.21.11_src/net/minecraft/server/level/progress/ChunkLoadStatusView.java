package net.minecraft.server.level.progress;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

public interface ChunkLoadStatusView {
	void moveTo(ResourceKey<Level> resourceKey, ChunkPos chunkPos);

	@Nullable ChunkStatus get(int i, int j);

	int radius();
}
