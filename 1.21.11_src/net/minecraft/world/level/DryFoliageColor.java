package net.minecraft.world.level;

public class DryFoliageColor {
	public static final int FOLIAGE_DRY_DEFAULT = -10732494;
	private static int[] pixels = new int[65536];

	public static void init(int[] is) {
		pixels = is;
	}

	public static int get(double d, double e) {
		return ColorMapColorUtil.get(d, e, pixels, -10732494);
	}
}
