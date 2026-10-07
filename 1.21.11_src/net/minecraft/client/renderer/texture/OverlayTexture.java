package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.ARGB;

@Environment(EnvType.CLIENT)
public class OverlayTexture implements AutoCloseable {
	private static final int SIZE = 16;
	public static final int NO_WHITE_U = 0;
	public static final int RED_OVERLAY_V = 3;
	public static final int WHITE_OVERLAY_V = 10;
	public static final int NO_OVERLAY = pack(0, 10);
	private final DynamicTexture texture = new DynamicTexture("Entity Color Overlay", 16, 16, false);

	public OverlayTexture() {
		NativeImage nativeImage = this.texture.getPixels();

		for (int i = 0; i < 16; i++) {
			for (int j = 0; j < 16; j++) {
				if (i < 8) {
					nativeImage.setPixel(j, i, -1291911168);
				} else {
					int k = (int)((1.0F - j / 15.0F * 0.75F) * 255.0F);
					nativeImage.setPixel(j, i, ARGB.white(k));
				}
			}
		}

		this.texture.upload();
	}

	@Override
	public void close() {
		this.texture.close();
	}

	public static int u(float f) {
		return (int)(f * 15.0F);
	}

	public static int v(boolean bl) {
		return bl ? 3 : 10;
	}

	public static int pack(int i, int j) {
		return i | j << 16;
	}

	public static int pack(float f, boolean bl) {
		return pack(u(f), v(bl));
	}

	public GpuTextureView getTextureView() {
		return this.texture.getTextureView();
	}
}
