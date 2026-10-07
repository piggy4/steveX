package net.minecraft.world.level;

public class GrassColor {
	private static int[] pixels = new int[65536];

	public static void init(int[] is) {
		pixels = is;
	}

	public static int get(double d, double e) {
		return ColorMapColorUtil.get(d, e, pixels, -65281);
	}

	public static int getDefaultColor() {
		return get(0.5, 1.0);
	}
}
