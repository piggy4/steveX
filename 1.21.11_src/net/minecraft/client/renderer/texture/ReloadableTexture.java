package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import java.io.IOException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

@Environment(EnvType.CLIENT)
public abstract class ReloadableTexture extends AbstractTexture {
	private final Identifier resourceId;

	public ReloadableTexture(Identifier identifier) {
		this.resourceId = identifier;
	}

	public Identifier resourceId() {
		return this.resourceId;
	}

	public void apply(TextureContents textureContents) {
		boolean bl = textureContents.clamp();
		boolean bl2 = textureContents.blur();
		AddressMode addressMode = bl ? AddressMode.CLAMP_TO_EDGE : AddressMode.REPEAT;
		FilterMode filterMode = bl2 ? FilterMode.LINEAR : FilterMode.NEAREST;
		this.sampler = RenderSystem.getSamplerCache().getSampler(addressMode, addressMode, filterMode, filterMode, false);

		try (NativeImage nativeImage = textureContents.image()) {
			this.doLoad(nativeImage);
		}
	}

	protected void doLoad(NativeImage nativeImage) {
		GpuDevice gpuDevice = RenderSystem.getDevice();
		this.close();
		this.texture = gpuDevice.createTexture(this.resourceId::toString, 5, TextureFormat.RGBA8, nativeImage.getWidth(), nativeImage.getHeight(), 1, 1);
		this.textureView = gpuDevice.createTextureView(this.texture);
		gpuDevice.createCommandEncoder().writeToTexture(this.texture, nativeImage);
	}

	public abstract TextureContents loadContents(ResourceManager resourceManager) throws IOException;
}
