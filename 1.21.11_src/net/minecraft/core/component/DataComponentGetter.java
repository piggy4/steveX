package net.minecraft.core.component;

import org.jspecify.annotations.Nullable;

public interface DataComponentGetter {
	<T> @Nullable T get(DataComponentType<? extends T> dataComponentType);

	default <T> T getOrDefault(DataComponentType<? extends T> dataComponentType, T object) {
		T object2 = this.get(dataComponentType);
		return object2 != null ? object2 : object;
	}

	default <T> @Nullable TypedDataComponent<T> getTyped(DataComponentType<T> dataComponentType) {
		T object = this.get(dataComponentType);
		return object != null ? new TypedDataComponent<>(dataComponentType, object) : null;
	}
}
