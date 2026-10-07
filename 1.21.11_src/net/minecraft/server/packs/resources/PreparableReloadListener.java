package net.minecraft.server.packs.resources;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@FunctionalInterface
public interface PreparableReloadListener {
	CompletableFuture<Void> reload(
		PreparableReloadListener.SharedState sharedState, Executor executor, PreparableReloadListener.PreparationBarrier preparationBarrier, Executor executor2
	);

	default void prepareSharedState(PreparableReloadListener.SharedState sharedState) {
	}

	default String getName() {
		return this.getClass().getSimpleName();
	}

	@FunctionalInterface
	interface PreparationBarrier {
		<T> CompletableFuture<T> wait(T object);
	}

	final class SharedState {
		private final ResourceManager manager;
		private final Map<PreparableReloadListener.StateKey<?>, Object> state = new IdentityHashMap<>();

		public SharedState(ResourceManager resourceManager) {
			this.manager = resourceManager;
		}

		public ResourceManager resourceManager() {
			return this.manager;
		}

		public <T> void set(PreparableReloadListener.StateKey<T> stateKey, T object) {
			this.state.put(stateKey, object);
		}

		public <T> T get(PreparableReloadListener.StateKey<T> stateKey) {
			return Objects.requireNonNull((T)this.state.get(stateKey));
		}
	}

	final class StateKey<T> {
	}
}
