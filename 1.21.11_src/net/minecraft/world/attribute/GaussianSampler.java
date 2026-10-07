package net.minecraft.world.attribute;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class GaussianSampler {
	private static final int GAUSSIAN_SAMPLE_RADIUS = 2;
	private static final int GAUSSIAN_SAMPLE_BREADTH = 6;
	private static final double[] GAUSSIAN_SAMPLE_KERNEL = new double[]{0.0, 1.0, 4.0, 6.0, 4.0, 1.0, 0.0};

	public static <V> void sample(Vec3 vec3, GaussianSampler.Sampler<V> sampler, GaussianSampler.Accumulator<V> accumulator) {
		vec3 = vec3.subtract(0.5, 0.5, 0.5);
		int i = Mth.floor(vec3.x());
		int j = Mth.floor(vec3.y());
		int k = Mth.floor(vec3.z());
		double d = vec3.x() - i;
		double e = vec3.y() - j;
		double f = vec3.z() - k;

		for (int l = 0; l < 6; l++) {
			double g = Mth.lerp(f, GAUSSIAN_SAMPLE_KERNEL[l + 1], GAUSSIAN_SAMPLE_KERNEL[l]);
			int m = k - 2 + l;

			for (int n = 0; n < 6; n++) {
				double h = Mth.lerp(d, GAUSSIAN_SAMPLE_KERNEL[n + 1], GAUSSIAN_SAMPLE_KERNEL[n]);
				int o = i - 2 + n;

				for (int p = 0; p < 6; p++) {
					double q = Mth.lerp(e, GAUSSIAN_SAMPLE_KERNEL[p + 1], GAUSSIAN_SAMPLE_KERNEL[p]);
					int r = j - 2 + p;
					double s = h * q * g;
					V object = sampler.get(o, r, m);
					accumulator.accumulate(s, object);
				}
			}
		}
	}

	@FunctionalInterface
	public interface Accumulator<V> {
		void accumulate(double d, V object);
	}

	@FunctionalInterface
	public interface Sampler<V> {
		V get(int i, int j, int k);
	}
}
