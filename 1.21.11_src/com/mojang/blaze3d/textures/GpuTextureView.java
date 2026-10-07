package com.mojang.blaze3d.textures;

import com.mojang.blaze3d.DontObfuscate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
@DontObfuscate
public abstract class GpuTextureView implements AutoCloseable {
	private final GpuTexture texture;
	private final int baseMipLevel;
	private final int mipLevels;

	public GpuTextureView(GpuTexture gpuTexture, int i, int j) {
		this.texture = gpuTexture;
		this.baseMipLevel = i;
		this.mipLevels = j;
	}

	@Override
	public abstract void close();

	public GpuTexture texture() {
		return this.texture;
	}

	public int baseMipLevel() {
		return this.baseMipLevel;
	}

	public int mipLevels() {
		return this.mipLevels;
	}

	public int getWidth(int i) {
		return this.texture.getWidth(i + this.baseMipLevel);
	}

	public int getHeight(int i) {
		return this.texture.getHeight(i + this.baseMipLevel);
	}

	public abstract boolean isClosed();
}
