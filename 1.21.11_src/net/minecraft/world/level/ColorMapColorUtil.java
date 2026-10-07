package net.minecraft.world.level;

public interface ColorMapColorUtil {
	static int get(double d, double e, int[] is, int i) {
		e *= d;
		int j = (int)((1.0 - d) * 255.0);
		int k = (int)((1.0 - e) * 255.0);
		int l = k << 8 | j;
		return l >= is.length ? i : is[l];
	}
}
