package net.minecraft.util.debug;

import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

public interface DebugValueSource {
	void registerDebugValues(ServerLevel serverLevel, DebugValueSource.Registration registration);

	interface Registration {
		<T> void register(DebugSubscription<T> debugSubscription, DebugValueSource.ValueGetter<T> valueGetter);
	}

	interface ValueGetter<T> {
		@Nullable T get();
	}
}
