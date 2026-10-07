package com.mojang.blaze3d.textures;

import com.mojang.blaze3d.DontObfuscate;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
@DontObfuscate
public abstract class GpuTexture implements AutoCloseable {
	public static final int USAGE_COPY_DST = 1;
	public static final int USAGE_COPY_SRC = 2;
	public static final int USAGE_TEXTURE_BINDING = 4;
	public static final int USAGE_RENDER_ATTACHMENT = 8;
	public static final int USAGE_CUBEMAP_COMPATIBLE = 16;
	private final TextureFormat format;
	private final int width;
	private final int height;
	private final int depthOrLayers;
	private final int mipLevels;
	@GpuTexture.Usage
	private final int usage;
	private final String label;

	public GpuTexture(@GpuTexture.Usage int i, String string, TextureFormat textureFormat, int j, int k, int l, int m) {
		this.usage = i;
		this.label = string;
		this.format = textureFormat;
		this.width = j;
		this.height = k;
		this.depthOrLayers = l;
		this.mipLevels = m;
	}

	public int getWidth(int i) {
		return this.width >> i;
	}

	public int getHeight(int i) {
		return this.height >> i;
	}

	public int getDepthOrLayers() {
		return this.depthOrLayers;
	}

	public int getMipLevels() {
		return this.mipLevels;
	}

	public TextureFormat getFormat() {
		return this.format;
	}

	@GpuTexture.Usage
	public int usage() {
		return this.usage;
	}

	public String getLabel() {
		return this.label;
	}

	@Override
	public abstract void close();

	public abstract boolean isClosed();

	@Retention(RetentionPolicy.CLASS)
	@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.METHOD, ElementType.TYPE_USE})
	@Environment(EnvType.CLIENT)
	public @interface Usage {
	}
}
