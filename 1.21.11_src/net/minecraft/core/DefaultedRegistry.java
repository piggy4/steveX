package net.minecraft.core;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public interface DefaultedRegistry<T> extends Registry<T> {
	@Override
	@NonNull Identifier getKey(T object);

	@Override
	@NonNull T getValue(@Nullable Identifier identifier);

	@Override
	@NonNull T byId(int i);

	Identifier getDefaultKey();
}
