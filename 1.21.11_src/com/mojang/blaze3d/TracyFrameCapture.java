package com.mojang.blaze3d;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.jtracy.TracyClient;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;

@Environment(EnvType.CLIENT)
public class TracyFrameCapture implements AutoCloseable {
	private static final int MAX_WIDTH = 320;
	private static final int MAX_HEIGHT = 180;
	private static final long BYTES_PER_PIXEL = 4L;
	private int targetWidth;
	private int targetHeight;
	private int width;
	private int height;
	private GpuTexture frameBuffer;
	private GpuTextureView frameBufferView;
	private GpuBuffer pixelbuffer;
	private int lastCaptureDelay;
	private boolean capturedThisFrame;
	private TracyFrameCapture.Status status = TracyFrameCapture.Status.WAITING_FOR_CAPTURE;

	public TracyFrameCapture() {
		this.width = 320;
		this.height = 180;
		GpuDevice gpuDevice = RenderSystem.getDevice();
		this.frameBuffer = gpuDevice.createTexture("Tracy Frame Capture", 10, TextureFormat.RGBA8, this.width, this.height, 1, 1);
		this.frameBufferView = gpuDevice.createTextureView(this.frameBuffer);
		this.pixelbuffer = gpuDevice.createBuffer(() -> "Tracy Frame Capture buffer", 9, this.width * this.height * 4L);
	}

	private void resize(int i, int j) {
		float f = (float)i / j;
		if (i > 320) {
			i = 320;
			j = (int)(320.0F / f);
		}

		if (j > 180) {
			i = (int)(180.0F * f);
			j = 180;
		}

		i = i / 4 * 4;
		j = j / 4 * 4;
		if (this.width != i || this.height != j) {
			this.width = i;
			this.height = j;
			GpuDevice gpuDevice = RenderSystem.getDevice();
			this.frameBuffer.close();
			this.frameBuffer = gpuDevice.createTexture("Tracy Frame Capture", 10, TextureFormat.RGBA8, i, j, 1, 1);
			this.frameBufferView.close();
			this.frameBufferView = gpuDevice.createTextureView(this.frameBuffer);
			this.pixelbuffer.close();
			this.pixelbuffer = gpuDevice.createBuffer(() -> "Tracy Frame Capture buffer", 9, i * j * 4L);
		}
	}

	public void capture(RenderTarget renderTarget) {
		if (this.status == TracyFrameCapture.Status.WAITING_FOR_CAPTURE && !this.capturedThisFrame && renderTarget.getColorTexture() != null) {
			this.capturedThisFrame = true;
			if (renderTarget.width != this.targetWidth || renderTarget.height != this.targetHeight) {
				this.targetWidth = renderTarget.width;
				this.targetHeight = renderTarget.height;
				this.resize(this.targetWidth, this.targetHeight);
			}

			this.status = TracyFrameCapture.Status.WAITING_FOR_COPY;
			CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();

			try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Tracy blit", this.frameBufferView, OptionalInt.empty())) {
				renderPass.setPipeline(RenderPipelines.TRACY_BLIT);
				renderPass.bindTexture("InSampler", renderTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
				renderPass.draw(0, 3);
			}

			commandEncoder.copyTextureToBuffer(this.frameBuffer, this.pixelbuffer, 0L, () -> this.status = TracyFrameCapture.Status.WAITING_FOR_UPLOAD, 0);
			this.lastCaptureDelay = 0;
		}
	}

	public void upload() {
		if (this.status == TracyFrameCapture.Status.WAITING_FOR_UPLOAD) {
			this.status = TracyFrameCapture.Status.WAITING_FOR_CAPTURE;

			try (GpuBuffer.MappedView mappedView = RenderSystem.getDevice().createCommandEncoder().mapBuffer(this.pixelbuffer, true, false)) {
				TracyClient.frameImage(mappedView.data(), this.width, this.height, this.lastCaptureDelay, true);
			}
		}
	}

	public void endFrame() {
		this.lastCaptureDelay++;
		this.capturedThisFrame = false;
		TracyClient.markFrame();
	}

	@Override
	public void close() {
		this.frameBuffer.close();
		this.frameBufferView.close();
		this.pixelbuffer.close();
	}

	@Environment(EnvType.CLIENT)
	enum Status {
		WAITING_FOR_CAPTURE,
		WAITING_FOR_COPY,
		WAITING_FOR_UPLOAD;
	}
}
