package net.minecraft.client.renderer.feature;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Queue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureManager;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ParticleFeatureRenderer implements AutoCloseable {
	private final Queue<ParticleFeatureRenderer.ParticleBufferCache> availableBuffers = new ArrayDeque<>();
	private final List<ParticleFeatureRenderer.ParticleBufferCache> usedBuffers = new ArrayList<>();

	public void render(SubmitNodeCollection submitNodeCollection) {
		if (!submitNodeCollection.getParticleGroupRenderers().isEmpty()) {
			GpuDevice gpuDevice = RenderSystem.getDevice();
			Minecraft minecraft = Minecraft.getInstance();
			TextureManager textureManager = minecraft.getTextureManager();
			RenderTarget renderTarget = minecraft.getMainRenderTarget();
			RenderTarget renderTarget2 = minecraft.levelRenderer.getParticlesTarget();

			for (SubmitNodeCollector.ParticleGroupRenderer particleGroupRenderer : submitNodeCollection.getParticleGroupRenderers()) {
				ParticleFeatureRenderer.ParticleBufferCache particleBufferCache = this.availableBuffers.poll();
				if (particleBufferCache == null) {
					particleBufferCache = new ParticleFeatureRenderer.ParticleBufferCache();
				}

				this.usedBuffers.add(particleBufferCache);
				QuadParticleRenderState.PreparedBuffers preparedBuffers = particleGroupRenderer.prepare(particleBufferCache);
				if (preparedBuffers != null) {
					try (RenderPass renderPass = gpuDevice.createCommandEncoder()
							.createRenderPass(
								() -> "Particles - Main", renderTarget.getColorTextureView(), OptionalInt.empty(), renderTarget.getDepthTextureView(), OptionalDouble.empty()
							)) {
						this.prepareRenderPass(renderPass);
						particleGroupRenderer.render(preparedBuffers, particleBufferCache, renderPass, textureManager, false);
						if (renderTarget2 == null) {
							particleGroupRenderer.render(preparedBuffers, particleBufferCache, renderPass, textureManager, true);
						}
					}

					if (renderTarget2 != null) {
						try (RenderPass renderPass = gpuDevice.createCommandEncoder()
								.createRenderPass(
									() -> "Particles - Transparent", renderTarget2.getColorTextureView(), OptionalInt.empty(), renderTarget2.getDepthTextureView(), OptionalDouble.empty()
								)) {
							this.prepareRenderPass(renderPass);
							particleGroupRenderer.render(preparedBuffers, particleBufferCache, renderPass, textureManager, true);
						}
					}
				}
			}
		}
	}

	public void endFrame() {
		for (ParticleFeatureRenderer.ParticleBufferCache particleBufferCache : this.usedBuffers) {
			particleBufferCache.rotate();
		}

		this.availableBuffers.addAll(this.usedBuffers);
		this.usedBuffers.clear();
	}

	private void prepareRenderPass(RenderPass renderPass) {
		renderPass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
		renderPass.setUniform("Fog", RenderSystem.getShaderFog());
		renderPass.bindTexture(
			"Sampler2", Minecraft.getInstance().gameRenderer.lightTexture().getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
		);
	}

	@Override
	public void close() {
		this.availableBuffers.forEach(ParticleFeatureRenderer.ParticleBufferCache::close);
	}

	@Environment(EnvType.CLIENT)
	public static class ParticleBufferCache implements AutoCloseable {
		private @Nullable MappableRingBuffer ringBuffer;

		public void write(ByteBuffer byteBuffer) {
			if (this.ringBuffer == null || this.ringBuffer.size() < byteBuffer.remaining()) {
				if (this.ringBuffer != null) {
					this.ringBuffer.close();
				}

				this.ringBuffer = new MappableRingBuffer(() -> "Particle Vertices", 34, byteBuffer.remaining());
			}

			try (GpuBuffer.MappedView mappedView = RenderSystem.getDevice().createCommandEncoder().mapBuffer(this.ringBuffer.currentBuffer().slice(), false, true)) {
				mappedView.data().put(byteBuffer);
			}
		}

		public GpuBuffer get() {
			if (this.ringBuffer == null) {
				throw new IllegalStateException("Can't get buffer before it's made");
			} else {
				return this.ringBuffer.currentBuffer();
			}
		}

		void rotate() {
			if (this.ringBuffer != null) {
				this.ringBuffer.rotate();
			}
		}

		@Override
		public void close() {
			if (this.ringBuffer != null) {
				this.ringBuffer.close();
			}
		}
	}
}
