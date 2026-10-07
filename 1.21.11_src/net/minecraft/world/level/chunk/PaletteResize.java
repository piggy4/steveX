package net.minecraft.world.level.chunk;

public interface PaletteResize<T> {
	int onResize(int i, T object);

	static <T> PaletteResize<T> noResizeExpected() {
		return (i, object) -> {
			throw new IllegalArgumentException("Unexpected palette resize, bits = " + i + ", added value = " + object);
		};
	}
}
