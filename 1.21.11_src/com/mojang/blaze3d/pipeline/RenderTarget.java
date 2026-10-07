package com.mojang.blaze3d.pipeline;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class RenderTarget {
	private static int UNNAMED_RENDER_TARGETS = 0;
	public int width;
	public int height;
	protected final String label;
	public final boolean useDepth;
	protected @Nullable GpuTexture colorTexture;
	protected @Nullable GpuTextureView colorTextureView;
	protected @Nullable GpuTexture depthTexture;
	protected @Nullable GpuTextureView depthTextureView;

	public RenderTarget(@Nullable String string, boolean bl) {
		this.label = string == null ? "FBO " + UNNAMED_RENDER_TARGETS++ : string;
		this.useDepth = bl;
	}

	public void resize(int i, int j) {
		RenderSystem.assertOnRenderThread();
		this.destroyBuffers();
		this.createBuffers(i, j);
	}

	public void destroyBuffers() {
		RenderSystem.assertOnRenderThread();
		if (this.depthTexture != null) {
			this.depthTexture.close();
			this.depthTexture = null;
		}

		if (this.depthTextureView != null) {
			this.depthTextureView.close();
			this.depthTextureView = null;
		}

		if (this.colorTexture != null) {
			this.colorTexture.close();
			this.colorTexture = null;
		}

		if (this.colorTextureView != null) {
			this.colorTextureView.close();
			this.colorTextureView = null;
		}
	}

	public void copyDepthFrom(RenderTarget renderTarget) {
		RenderSystem.assertOnRenderThread();
		if (this.depthTexture == null) {
			throw new IllegalStateException("Trying to copy depth texture to a RenderTarget without a depth texture");
		}

		if (renderTarget.depthTexture == null) {
			throw new IllegalStateException("Trying to copy depth texture from a RenderTarget without a depth texture");
		}

		RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(renderTarget.depthTexture, this.depthTexture, 0, 0, 0, 0, 0, this.width, this.height);
	}

	public void createBuffers(int i, int j) {
		RenderSystem.assertOnRenderThread();
		GpuDevice gpuDevice = RenderSystem.getDevice();
		int k = gpuDevice.getMaxTextureSize();
		if (i > 0 && i <= k && j > 0 && j <= k) {
			this.width = i;
			this.height = j;
			if (this.useDepth) {
				this.depthTexture = gpuDevice.createTexture(() -> this.label + " / Depth", 15, TextureFormat.DEPTH32, i, j, 1, 1);
				this.depthTextureView = gpuDevice.createTextureView(this.depthTexture);
			}

			this.colorTexture = gpuDevice.createTexture(() -> this.label + " / Color", 15, TextureFormat.RGBA8, i, j, 1, 1);
			this.colorTextureView = gpuDevice.createTextureView(this.colorTexture);
		} else {
			throw new IllegalArgumentException("Window " + i + "x" + j + " size out of bounds (max. size: " + k + ")");
		}
	}

	public void blitToScreen() {
		if (this.colorTexture == null) {
			throw new IllegalStateException("Can't blit to screen, color texture doesn't exist yet");
		}

		RenderSystem.getDevice().createCommandEncoder().presentTexture(this.colorTextureView);
	}

	public void blitAndBlendToTexture(GpuTextureView gpuTextureView) {
		RenderSystem.assertOnRenderThread();

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "Blit render target", gpuTextureView, OptionalInt.empty())) {
			renderPass.setPipeline(RenderPipelines.ENTITY_OUTLINE_BLIT);
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.bindTexture("InSampler", this.colorTextureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			renderPass.draw(0, 3);
		}
	}

	public @Nullable GpuTexture getColorTexture() {
		return this.colorTexture;
	}

	public @Nullable GpuTextureView getColorTextureView() {
		return this.colorTextureView;
	}

	public @Nullable GpuTexture getDepthTexture() {
		return this.depthTexture;
	}

	public @Nullable GpuTextureView getDepthTextureView() {
		return this.depthTextureView;
	}
}
