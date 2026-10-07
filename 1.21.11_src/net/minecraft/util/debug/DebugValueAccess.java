package net.minecraft.util.debug;

import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public interface DebugValueAccess {
	<T> void forEachChunk(DebugSubscription<T> debugSubscription, BiConsumer<ChunkPos, T> biConsumer);

	<T> @Nullable T getChunkValue(DebugSubscription<T> debugSubscription, ChunkPos chunkPos);

	<T> void forEachBlock(DebugSubscription<T> debugSubscription, BiConsumer<BlockPos, T> biConsumer);

	<T> @Nullable T getBlockValue(DebugSubscription<T> debugSubscription, BlockPos blockPos);

	<T> void forEachEntity(DebugSubscription<T> debugSubscription, BiConsumer<Entity, T> biConsumer);

	<T> @Nullable T getEntityValue(DebugSubscription<T> debugSubscription, Entity entity);

	<T> void forEachEvent(DebugSubscription<T> debugSubscription, DebugValueAccess.EventVisitor<T> eventVisitor);

	@FunctionalInterface
	interface EventVisitor<T> {
		void accept(T object, int i, int j);
	}
}
