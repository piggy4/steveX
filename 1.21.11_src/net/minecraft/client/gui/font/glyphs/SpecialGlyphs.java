package net.minecraft.client.gui.font.glyphs;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.font.GlyphStitcher;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public enum SpecialGlyphs implements GlyphInfo {
	WHITE(() -> generate(5, 8, (i, j) -> -1)),
	MISSING(() -> {
		int i = 5;
		int j = 8;
		return generate(5, 8, (ix, jx) -> {
			boolean bl = ix == 0 || ix + 1 == 5 || jx == 0 || jx + 1 == 8;
			return bl ? -1 : 0;
		});
	});

	final NativeImage image;

	private static NativeImage generate(int i, int j, SpecialGlyphs.PixelProvider pixelProvider) {
		NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, i, j, false);

		for (int k = 0; k < j; k++) {
			for (int l = 0; l < i; l++) {
				nativeImage.setPixel(l, k, pixelProvider.getColor(l, k));
			}
		}

		nativeImage.untrack();
		return nativeImage;
	}

	SpecialGlyphs(final Supplier<NativeImage> supplier) {
		this.image = supplier.get();
	}

	@Override
	public float getAdvance() {
		return this.image.getWidth() + 1;
	}

	public @Nullable BakedSheetGlyph bake(GlyphStitcher glyphStitcher) {
		return glyphStitcher.stitch(
			this,
			new GlyphBitmap() {
				@Override
				public int getPixelWidth() {
					return SpecialGlyphs.this.image.getWidth();
				}

				@Override
				public int getPixelHeight() {
					return SpecialGlyphs.this.image.getHeight();
				}

				@Override
				public float getOversample() {
					return 1.0F;
				}

				@Override
				public void upload(int i, int j, GpuTexture gpuTexture) {
					RenderSystem.getDevice()
						.createCommandEncoder()
						.writeToTexture(gpuTexture, SpecialGlyphs.this.image, 0, 0, i, j, SpecialGlyphs.this.image.getWidth(), SpecialGlyphs.this.image.getHeight(), 0, 0);
				}

				@Override
				public boolean isColored() {
					return true;
				}
			}
		);
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	interface PixelProvider {
		int getColor(int i, int j);
	}
}
