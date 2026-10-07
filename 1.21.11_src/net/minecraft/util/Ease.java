package net.minecraft.util;

public class Ease {
	public static float inBack(float f) {
		float g = 1.70158F;
		float h = 2.70158F;
		return Mth.square(f) * (2.70158F * f - 1.70158F);
	}

	public static float inBounce(float f) {
		return 1.0F - outBounce(1.0F - f);
	}

	public static float inCubic(float f) {
		return Mth.cube(f);
	}

	public static float inElastic(float f) {
		if (f == 0.0F) {
			return 0.0F;
		}

		if (f == 1.0F) {
			return 1.0F;
		}

		float g = (float) (Math.PI * 2.0 / 3.0);
		return (float)(-Math.pow(2.0, 10.0 * f - 10.0) * Math.sin((f * 10.0 - 10.75) * (float) (Math.PI * 2.0 / 3.0)));
	}

	public static float inExpo(float f) {
		return f == 0.0F ? 0.0F : (float)Math.pow(2.0, 10.0 * f - 10.0);
	}

	public static float inQuart(float f) {
		return Mth.square(Mth.square(f));
	}

	public static float inQuint(float f) {
		return Mth.square(Mth.square(f)) * f;
	}

	public static float inSine(float f) {
		return 1.0F - Mth.cos(f * (float) (Math.PI / 2));
	}

	public static float inOutBounce(float f) {
		return f < 0.5F ? (1.0F - outBounce(1.0F - 2.0F * f)) / 2.0F : (1.0F + outBounce(2.0F * f - 1.0F)) / 2.0F;
	}

	public static float inOutCirc(float f) {
		return f < 0.5F ? (float)((1.0 - Math.sqrt(1.0 - Math.pow(2.0 * f, 2.0))) / 2.0) : (float)((Math.sqrt(1.0 - Math.pow(-2.0 * f + 2.0, 2.0)) + 1.0) / 2.0);
	}

	public static float inOutCubic(float f) {
		return f < 0.5F ? 4.0F * Mth.cube(f) : (float)(1.0 - Math.pow(-2.0 * f + 2.0, 3.0) / 2.0);
	}

	public static float inOutQuad(float f) {
		return f < 0.5F ? 2.0F * Mth.square(f) : (float)(1.0 - Math.pow(-2.0 * f + 2.0, 2.0) / 2.0);
	}

	public static float inOutQuart(float f) {
		return f < 0.5F ? 8.0F * Mth.square(Mth.square(f)) : (float)(1.0 - Math.pow(-2.0 * f + 2.0, 4.0) / 2.0);
	}

	public static float inOutQuint(float f) {
		return f < 0.5 ? 16.0F * f * f * f * f * f : (float)(1.0 - Math.pow(-2.0 * f + 2.0, 5.0) / 2.0);
	}

	public static float outBounce(float f) {
		float g = 7.5625F;
		float h = 2.75F;
		if (f < 0.36363637F) {
			return 7.5625F * Mth.square(f);
		} else if (f < 0.72727275F) {
			return 7.5625F * Mth.square(f - 0.54545456F) + 0.75F;
		} else {
			return f < 0.9090909090909091 ? 7.5625F * Mth.square(f - 0.8181818F) + 0.9375F : 7.5625F * Mth.square(f - 0.95454544F) + 0.984375F;
		}
	}

	public static float outElastic(float f) {
		float g = (float) (Math.PI * 2.0 / 3.0);
		if (f == 0.0F) {
			return 0.0F;
		} else {
			return f == 1.0F ? 1.0F : (float)(Math.pow(2.0, -10.0 * f) * Math.sin((f * 10.0 - 0.75) * (float) (Math.PI * 2.0 / 3.0)) + 1.0);
		}
	}

	public static float outExpo(float f) {
		return f == 1.0F ? 1.0F : 1.0F - (float)Math.pow(2.0, -10.0 * f);
	}

	public static float outQuad(float f) {
		return 1.0F - Mth.square(1.0F - f);
	}

	public static float outQuint(float f) {
		return 1.0F - (float)Math.pow(1.0 - f, 5.0);
	}

	public static float outSine(float f) {
		return Mth.sin(f * (float) (Math.PI / 2));
	}

	public static float inOutSine(float f) {
		return -(Mth.cos((float) Math.PI * f) - 1.0F) / 2.0F;
	}

	public static float outBack(float f) {
		float g = 1.70158F;
		float h = 2.70158F;
		return 1.0F + 2.70158F * Mth.cube(f - 1.0F) + 1.70158F * Mth.square(f - 1.0F);
	}

	public static float outQuart(float f) {
		return 1.0F - Mth.square(Mth.square(1.0F - f));
	}

	public static float outCubic(float f) {
		return 1.0F - Mth.cube(1.0F - f);
	}

	public static float inOutExpo(float f) {
		if (f < 0.5F) {
			return f == 0.0F ? 0.0F : (float)(Math.pow(2.0, 20.0 * f - 10.0) / 2.0);
		} else {
			return f == 1.0F ? 1.0F : (float)((2.0 - Math.pow(2.0, -20.0 * f + 10.0)) / 2.0);
		}
	}

	public static float inQuad(float f) {
		return f * f;
	}

	public static float outCirc(float f) {
		return (float)Math.sqrt(1.0F - Mth.square(f - 1.0F));
	}

	public static float inOutElastic(float f) {
		float g = (float) Math.PI * 4.0F / 9.0F;
		if (f == 0.0F) {
			return 0.0F;
		}

		if (f == 1.0F) {
			return 1.0F;
		}

		double d = Math.sin((20.0 * f - 11.125) * (float) Math.PI * 4.0F / 9.0F);
		return f < 0.5F ? (float)(-(Math.pow(2.0, 20.0 * f - 10.0) * d) / 2.0) : (float)(Math.pow(2.0, -20.0 * f + 10.0) * d / 2.0 + 1.0);
	}

	public static float inCirc(float f) {
		return (float)(-Math.sqrt(1.0F - f * f)) + 1.0F;
	}

	public static float inOutBack(float f) {
		float g = 1.70158F;
		float h = 2.5949094F;
		if (f < 0.5F) {
			return 4.0F * f * f * (7.189819F * f - 2.5949094F) / 2.0F;
		}

		float i = 2.0F * f - 2.0F;
		return (i * i * (3.5949094F * i + 2.5949094F) + 2.0F) / 2.0F;
	}
}
