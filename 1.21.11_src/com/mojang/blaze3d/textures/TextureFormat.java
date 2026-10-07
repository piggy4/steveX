package com.mojang.blaze3d.textures;

import com.mojang.blaze3d.DontObfuscate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
@DontObfuscate
public enum TextureFormat {
	RGBA8(4),
	RED8(1),
	RED8I(1),
	DEPTH32(4);

	private final int pixelSize;

	TextureFormat(final int j) {
		this.pixelSize = j;
	}

	public int pixelSize() {
		return this.pixelSize;
	}

	public boolean hasColorAspect() {
		return this == RGBA8 || this == RED8;
	}

	public boolean hasDepthAspect() {
		return this == DEPTH32;
	}
}
