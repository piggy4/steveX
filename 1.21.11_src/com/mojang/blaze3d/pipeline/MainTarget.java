package com.mojang.blaze3d.pipeline;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.GpuOutOfMemoryException;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import java.util.List;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class MainTarget extends RenderTarget {
	public static final int DEFAULT_WIDTH = 854;
	public static final int DEFAULT_HEIGHT = 480;
	static final MainTarget.Dimension DEFAULT_DIMENSIONS = new MainTarget.Dimension(854, 480);

	public MainTarget(int i, int j) {
		super("Main", true);
		this.createFrameBuffer(i, j);
	}

	private void createFrameBuffer(int i, int j) {
		MainTarget.Dimension dimension = this.allocateAttachments(i, j);
		if (this.colorTexture != null && this.depthTexture != null) {
			this.width = dimension.width;
			this.height = dimension.height;
		} else {
			throw new IllegalStateException("Missing color and/or depth textures");
		}
	}

	private MainTarget.Dimension allocateAttachments(int i, int j) {
		RenderSystem.assertOnRenderThread();

		for (MainTarget.Dimension dimension : MainTarget.Dimension.listWithFallback(i, j)) {
			if (this.colorTexture != null) {
				this.colorTexture.close();
				this.colorTexture = null;
			}

			if (this.colorTextureView != null) {
				this.colorTextureView.close();
				this.colorTextureView = null;
			}

			if (this.depthTexture != null) {
				this.depthTexture.close();
				this.depthTexture = null;
			}

			if (this.depthTextureView != null) {
				this.depthTextureView.close();
				this.depthTextureView = null;
			}

			this.colorTexture = this.allocateColorAttachment(dimension);
			this.depthTexture = this.allocateDepthAttachment(dimension);
			if (this.colorTexture != null && this.depthTexture != null) {
				this.colorTextureView = RenderSystem.getDevice().createTextureView(this.colorTexture);
				this.depthTextureView = RenderSystem.getDevice().createTextureView(this.depthTexture);
				return dimension;
			}
		}

		throw new RuntimeException(
			"Unrecoverable GL_OUT_OF_MEMORY ("
				+ (this.colorTexture == null ? "missing color" : "have color")
				+ ", "
				+ (this.depthTexture == null ? "missing depth" : "have depth")
				+ ")"
		);
	}

	private @Nullable GpuTexture allocateColorAttachment(MainTarget.Dimension dimension) {
		try {
			return RenderSystem.getDevice().createTexture(() -> this.label + " / Color", 15, TextureFormat.RGBA8, dimension.width, dimension.height, 1, 1);
		} catch (GpuOutOfMemoryException gpuOutOfMemoryException) {
			return null;
		}
	}

	private @Nullable GpuTexture allocateDepthAttachment(MainTarget.Dimension dimension) {
		try {
			return RenderSystem.getDevice().createTexture(() -> this.label + " / Depth", 15, TextureFormat.DEPTH32, dimension.width, dimension.height, 1, 1);
		} catch (GpuOutOfMemoryException gpuOutOfMemoryException) {
			return null;
		}
	}

	@Environment(EnvType.CLIENT)
	static class Dimension {
		public final int width;
		public final int height;

		Dimension(int i, int j) {
			this.width = i;
			this.height = j;
		}

		static List<MainTarget.Dimension> listWithFallback(int i, int j) {
			RenderSystem.assertOnRenderThread();
			int k = RenderSystem.getDevice().getMaxTextureSize();
			return i > 0 && i <= k && j > 0 && j <= k
				? ImmutableList.of(new MainTarget.Dimension(i, j), MainTarget.DEFAULT_DIMENSIONS)
				: ImmutableList.of(MainTarget.DEFAULT_DIMENSIONS);
		}

		@Override
		public boolean equals(Object object) {
			if (this == object) {
				return true;
			} else if (object != null && this.getClass() == object.getClass()) {
				MainTarget.Dimension dimension = (MainTarget.Dimension)object;
				return this.width == dimension.width && this.height == dimension.height;
			} else {
				return false;
			}
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.width, this.height);
		}

		@Override
		public String toString() {
			return this.width + "x" + this.height;
		}
	}
}
