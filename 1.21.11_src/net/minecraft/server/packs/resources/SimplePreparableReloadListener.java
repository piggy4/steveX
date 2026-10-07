package net.minecraft.server.packs.resources;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

public abstract class SimplePreparableReloadListener<T> implements PreparableReloadListener {
	@Override
	public final CompletableFuture<Void> reload(
		PreparableReloadListener.SharedState sharedState, Executor executor, PreparableReloadListener.PreparationBarrier preparationBarrier, Executor executor2
	) {
		ResourceManager resourceManager = sharedState.resourceManager();
		return CompletableFuture.<T>supplyAsync(() -> this.prepare(resourceManager, Profiler.get()), executor)
			.thenCompose(preparationBarrier::wait)
			.thenAcceptAsync(object -> this.apply((T)object, resourceManager, Profiler.get()), executor2);
	}

	protected abstract T prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller);

	protected abstract void apply(T object, ResourceManager resourceManager, ProfilerFiller profilerFiller);
}
