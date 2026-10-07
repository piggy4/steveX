package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

@Environment(EnvType.CLIENT)
public class MipmapGenerator {
	private static final String ITEM_PREFIX = "item/";
	private static final float ALPHA_CUTOFF = 0.5F;
	private static final float STRICT_ALPHA_CUTOFF = 0.3F;

	private MipmapGenerator() {
	}

	private static float alphaTestCoverage(NativeImage nativeImage, float f, float g) {
		int i = nativeImage.getWidth();
		int j = nativeImage.getHeight();
		float h = 0.0F;
		int k = 4;

		for (int l = 0; l < j - 1; l++) {
			for (int m = 0; m < i - 1; m++) {
				float n = Math.clamp(ARGB.alphaFloat(nativeImage.getPixel(m, l)) * g, 0.0F, 1.0F);
				float o = Math.clamp(ARGB.alphaFloat(nativeImage.getPixel(m + 1, l)) * g, 0.0F, 1.0F);
				float p = Math.clamp(ARGB.alphaFloat(nativeImage.getPixel(m, l + 1)) * g, 0.0F, 1.0F);
				float q = Math.clamp(ARGB.alphaFloat(nativeImage.getPixel(m + 1, l + 1)) * g, 0.0F, 1.0F);
				float r = 0.0F;

				for (int s = 0; s < 4; s++) {
					float t = (s + 0.5F) / 4.0F;

					for (int u = 0; u < 4; u++) {
						float v = (u + 0.5F) / 4.0F;
						float w = n * (1.0F - v) * (1.0F - t) + o * v * (1.0F - t) + p * (1.0F - v) * t + q * v * t;
						if (w > f) {
							r++;
						}
					}
				}

				h += r / 16.0F;
			}
		}

		return h / ((i - 1) * (j - 1));
	}

	private static void scaleAlphaToCoverage(NativeImage nativeImage, float f, float g, float h) {
		float i = 0.0F;
		float j = 4.0F;
		float k = 1.0F;
		float l = 1.0F;
		float m = Float.MAX_VALUE;
		int n = nativeImage.getWidth();
		int o = nativeImage.getHeight();

		for (int p = 0; p < 5; p++) {
			float q = alphaTestCoverage(nativeImage, g, k);
			float r = Math.abs(q - f);
			if (r < m) {
				m = r;
				l = k;
			}

			if (q < f) {
				i = k;
			} else {
				if (!(q > f)) {
					break;
				}

				j = k;
			}

			k = (i + j) * 0.5F;
		}

		for (int p = 0; p < o; p++) {
			for (int s = 0; s < n; s++) {
				int t = nativeImage.getPixel(s, p);
				float u = ARGB.alphaFloat(t);
				u = u * l + h + 0.025F;
				u = Math.clamp(u, 0.0F, 1.0F);
				nativeImage.setPixel(s, p, ARGB.color(u, t));
			}
		}
	}

	public static NativeImage[] generateMipLevels(Identifier identifier, NativeImage[] nativeImages, int i, MipmapStrategy mipmapStrategy, float f) {
		if (mipmapStrategy == MipmapStrategy.AUTO) {
			mipmapStrategy = hasTransparentPixel(nativeImages[0]) ? MipmapStrategy.CUTOUT : MipmapStrategy.MEAN;
		}

		if (nativeImages.length == 1 && !identifier.getPath().startsWith("item/")) {
			if (mipmapStrategy == MipmapStrategy.CUTOUT || mipmapStrategy == MipmapStrategy.STRICT_CUTOUT) {
				TextureUtil.solidify(nativeImages[0]);
			} else if (mipmapStrategy == MipmapStrategy.DARK_CUTOUT) {
				TextureUtil.fillEmptyAreasWithDarkColor(nativeImages[0]);
			}
		}

		if (i + 1 <= nativeImages.length) {
			return nativeImages;
		}

		NativeImage[] nativeImages2 = new NativeImage[i + 1];
		nativeImages2[0] = nativeImages[0];
		boolean bl = mipmapStrategy == MipmapStrategy.CUTOUT || mipmapStrategy == MipmapStrategy.STRICT_CUTOUT || mipmapStrategy == MipmapStrategy.DARK_CUTOUT;
		float g = mipmapStrategy == MipmapStrategy.STRICT_CUTOUT ? 0.3F : 0.5F;
		float h = bl ? alphaTestCoverage(nativeImages[0], g, 1.0F) : 0.0F;

		for (int j = 1; j <= i; j++) {
			if (j < nativeImages.length) {
				nativeImages2[j] = nativeImages[j];
			} else {
				NativeImage nativeImage = nativeImages2[j - 1];
				NativeImage nativeImage2 = new NativeImage(nativeImage.getWidth() >> 1, nativeImage.getHeight() >> 1, false);
				int k = nativeImage2.getWidth();
				int l = nativeImage2.getHeight();

				for (int m = 0; m < k; m++) {
					for (int n = 0; n < l; n++) {
						int o = nativeImage.getPixel(m * 2 + 0, n * 2 + 0);
						int p = nativeImage.getPixel(m * 2 + 1, n * 2 + 0);
						int q = nativeImage.getPixel(m * 2 + 0, n * 2 + 1);
						int r = nativeImage.getPixel(m * 2 + 1, n * 2 + 1);
						int s;
						if (mipmapStrategy == MipmapStrategy.DARK_CUTOUT) {
							s = darkenedAlphaBlend(o, p, q, r);
						} else {
							s = ARGB.meanLinear(o, p, q, r);
						}

						nativeImage2.setPixel(m, n, s);
					}
				}

				nativeImages2[j] = nativeImage2;
			}

			if (bl) {
				scaleAlphaToCoverage(nativeImages2[j], h, g, f);
			}
		}

		return nativeImages2;
	}

	private static boolean hasTransparentPixel(NativeImage nativeImage) {
		for (int i = 0; i < nativeImage.getWidth(); i++) {
			for (int j = 0; j < nativeImage.getHeight(); j++) {
				if (ARGB.alpha(nativeImage.getPixel(i, j)) == 0) {
					return true;
				}
			}
		}

		return false;
	}

	private static int darkenedAlphaBlend(int i, int j, int k, int l) {
		float f = 0.0F;
		float g = 0.0F;
		float h = 0.0F;
		float m = 0.0F;
		if (ARGB.alpha(i) != 0) {
			f += ARGB.srgbToLinearChannel(ARGB.alpha(i));
			g += ARGB.srgbToLinearChannel(ARGB.red(i));
			h += ARGB.srgbToLinearChannel(ARGB.green(i));
			m += ARGB.srgbToLinearChannel(ARGB.blue(i));
		}

		if (ARGB.alpha(j) != 0) {
			f += ARGB.srgbToLinearChannel(ARGB.alpha(j));
			g += ARGB.srgbToLinearChannel(ARGB.red(j));
			h += ARGB.srgbToLinearChannel(ARGB.green(j));
			m += ARGB.srgbToLinearChannel(ARGB.blue(j));
		}

		if (ARGB.alpha(k) != 0) {
			f += ARGB.srgbToLinearChannel(ARGB.alpha(k));
			g += ARGB.srgbToLinearChannel(ARGB.red(k));
			h += ARGB.srgbToLinearChannel(ARGB.green(k));
			m += ARGB.srgbToLinearChannel(ARGB.blue(k));
		}

		if (ARGB.alpha(l) != 0) {
			f += ARGB.srgbToLinearChannel(ARGB.alpha(l));
			g += ARGB.srgbToLinearChannel(ARGB.red(l));
			h += ARGB.srgbToLinearChannel(ARGB.green(l));
			m += ARGB.srgbToLinearChannel(ARGB.blue(l));
		}

		f /= 4.0F;
		g /= 4.0F;
		h /= 4.0F;
		m /= 4.0F;
		return ARGB.color(ARGB.linearToSrgbChannel(f), ARGB.linearToSrgbChannel(g), ARGB.linearToSrgbChannel(h), ARGB.linearToSrgbChannel(m));
	}
}
