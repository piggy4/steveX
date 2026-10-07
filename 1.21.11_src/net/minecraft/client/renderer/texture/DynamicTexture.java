package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class DynamicTexture extends AbstractTexture implements Dumpable {
	private static final Logger LOGGER = LogUtils.getLogger();
	private @Nullable NativeImage pixels;

	public DynamicTexture(Supplier<String> supplier, NativeImage nativeImage) {
		this.pixels = nativeImage;
		this.createTexture(supplier);
		this.upload();
	}

	public DynamicTexture(String string, int i, int j, boolean bl) {
		this.pixels = new NativeImage(i, j, bl);
		this.createTexture(string);
	}

	public DynamicTexture(Supplier<String> supplier, int i, int j, boolean bl) {
		this.pixels = new NativeImage(i, j, bl);
		this.createTexture(supplier);
	}

	private void createTexture(Supplier<String> supplier) {
		GpuDevice gpuDevice = RenderSystem.getDevice();
		this.texture = gpuDevice.createTexture(supplier, 5, TextureFormat.RGBA8, this.pixels.getWidth(), this.pixels.getHeight(), 1, 1);
		this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
		this.textureView = gpuDevice.createTextureView(this.texture);
	}

	private void createTexture(String string) {
		GpuDevice gpuDevice = RenderSystem.getDevice();
		this.texture = gpuDevice.createTexture(string, 5, TextureFormat.RGBA8, this.pixels.getWidth(), this.pixels.getHeight(), 1, 1);
		this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
		this.textureView = gpuDevice.createTextureView(this.texture);
	}

	public void upload() {
		if (this.pixels != null && this.texture != null) {
			RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, this.pixels);
		} else {
			LOGGER.warn("Trying to upload disposed texture {}", this.getTexture().getLabel());
		}
	}

	public @Nullable NativeImage getPixels() {
		return this.pixels;
	}

	public void setPixels(NativeImage nativeImage) {
		if (this.pixels != null) {
			this.pixels.close();
		}

		this.pixels = nativeImage;
	}

	@Override
	public void close() {
		if (this.pixels != null) {
			this.pixels.close();
			this.pixels = null;
		}

		super.close();
	}

	@Override
	public void dumpContents(Identifier identifier, Path path) throws IOException {
		if (this.pixels != null) {
			String string = identifier.toDebugFileName() + ".png";
			Path path2 = path.resolve(string);
			this.pixels.writeToFile(path2);
		}
	}
}
