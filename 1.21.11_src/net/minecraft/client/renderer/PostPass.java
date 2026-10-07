package net.minecraft.client.renderer;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.SamplerCache;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

@Environment(EnvType.CLIENT)
public class PostPass implements AutoCloseable {
	private static final int UBO_SIZE_PER_SAMPLER = new Std140SizeCalculator().putVec2().get();
	private final String name;
	private final RenderPipeline pipeline;
	private final Identifier outputTargetId;
	private final Map<String, GpuBuffer> customUniforms = new HashMap<>();
	private final MappableRingBuffer infoUbo;
	private final List<PostPass.Input> inputs;

	public PostPass(RenderPipeline renderPipeline, Identifier identifier, Map<String, List<UniformValue>> map, List<PostPass.Input> list) {
		this.pipeline = renderPipeline;
		this.name = renderPipeline.getLocation().toString();
		this.outputTargetId = identifier;
		this.inputs = list;

		for (Entry<String, List<UniformValue>> entry : map.entrySet()) {
			List<UniformValue> list2 = entry.getValue();
			if (!list2.isEmpty()) {
				Std140SizeCalculator std140SizeCalculator = new Std140SizeCalculator();

				for (UniformValue uniformValue : list2) {
					uniformValue.addSize(std140SizeCalculator);
				}

				int i = std140SizeCalculator.get();

				try (MemoryStack memoryStack = MemoryStack.stackPush()) {
					Std140Builder std140Builder = Std140Builder.onStack(memoryStack, i);

					for (UniformValue uniformValue2 : list2) {
						uniformValue2.writeTo(std140Builder);
					}

					this.customUniforms.put(entry.getKey(), RenderSystem.getDevice().createBuffer(() -> this.name + " / " + entry.getKey(), 128, std140Builder.get()));
				}
			}
		}

		this.infoUbo = new MappableRingBuffer(() -> this.name + " SamplerInfo", 130, (list.size() + 1) * UBO_SIZE_PER_SAMPLER);
	}

	public void addToFrame(FrameGraphBuilder frameGraphBuilder, Map<Identifier, ResourceHandle<RenderTarget>> map, GpuBufferSlice gpuBufferSlice) {
		FramePass framePass = frameGraphBuilder.addPass(this.name);

		for (PostPass.Input input : this.inputs) {
			input.addToPass(framePass, map);
		}

		ResourceHandle<RenderTarget> resourceHandle = map.computeIfPresent(
			this.outputTargetId, (identifier, resourceHandlex) -> framePass.readsAndWrites((ResourceHandle<RenderTarget>)resourceHandlex)
		);
		if (resourceHandle == null) {
			throw new IllegalStateException("Missing handle for target " + this.outputTargetId);
		}

		framePass.executes(
			() -> {
				RenderTarget renderTarget = resourceHandle.get();
				RenderSystem.backupProjectionMatrix();
				RenderSystem.setProjectionMatrix(gpuBufferSlice, ProjectionType.ORTHOGRAPHIC);
				CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
				SamplerCache samplerCache = RenderSystem.getSamplerCache();
				List<PostPass.InputTexture> list = this.inputs
					.stream()
					.map(
						inputxx -> new PostPass.InputTexture(
							inputxx.samplerName(), inputxx.texture(map), samplerCache.getClampToEdge(inputxx.bilinear() ? FilterMode.LINEAR : FilterMode.NEAREST)
						)
					)
					.toList();

				try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(this.infoUbo.currentBuffer(), false, true)) {
					Std140Builder std140Builder = Std140Builder.intoBuffer(mappedView.data());
					std140Builder.putVec2(renderTarget.width, renderTarget.height);

					for (PostPass.InputTexture inputTexture : list) {
						std140Builder.putVec2(inputTexture.view.getWidth(0), inputTexture.view.getHeight(0));
					}
				}

				try (RenderPass renderPass = commandEncoder.createRenderPass(
						() -> "Post pass " + this.name,
						renderTarget.getColorTextureView(),
						OptionalInt.empty(),
						renderTarget.useDepth ? renderTarget.getDepthTextureView() : null,
						OptionalDouble.empty()
					)) {
					renderPass.setPipeline(this.pipeline);
					RenderSystem.bindDefaultUniforms(renderPass);
					renderPass.setUniform("SamplerInfo", this.infoUbo.currentBuffer());

					for (Entry<String, GpuBuffer> entry : this.customUniforms.entrySet()) {
						renderPass.setUniform(entry.getKey(), entry.getValue());
					}

					for (PostPass.InputTexture inputTexture2 : list) {
						renderPass.bindTexture(inputTexture2.samplerName() + "Sampler", inputTexture2.view(), inputTexture2.sampler());
					}

					renderPass.draw(0, 3);
				}

				this.infoUbo.rotate();
				RenderSystem.restoreProjectionMatrix();

				for (PostPass.Input inputx : this.inputs) {
					inputx.cleanup(map);
				}
			}
		);
	}

	@Override
	public void close() {
		for (GpuBuffer gpuBuffer : this.customUniforms.values()) {
			gpuBuffer.close();
		}

		this.infoUbo.close();
	}

	@Environment(EnvType.CLIENT)
	public interface Input {
		void addToPass(FramePass framePass, Map<Identifier, ResourceHandle<RenderTarget>> map);

		default void cleanup(Map<Identifier, ResourceHandle<RenderTarget>> map) {
		}

		GpuTextureView texture(Map<Identifier, ResourceHandle<RenderTarget>> map);

		String samplerName();

		boolean bilinear();
	}

	@Environment(EnvType.CLIENT)
	record InputTexture(String samplerName, GpuTextureView view, GpuSampler sampler) {
	}

	@Environment(EnvType.CLIENT)
	public record TargetInput(String samplerName, Identifier targetId, boolean depthBuffer, boolean bilinear) implements PostPass.Input {
		private ResourceHandle<RenderTarget> getHandle(Map<Identifier, ResourceHandle<RenderTarget>> map) {
			ResourceHandle<RenderTarget> resourceHandle = map.get(this.targetId);
			if (resourceHandle == null) {
				throw new IllegalStateException("Missing handle for target " + this.targetId);
			} else {
				return resourceHandle;
			}
		}

		@Override
		public void addToPass(FramePass framePass, Map<Identifier, ResourceHandle<RenderTarget>> map) {
			framePass.reads(this.getHandle(map));
		}

		@Override
		public GpuTextureView texture(Map<Identifier, ResourceHandle<RenderTarget>> map) {
			ResourceHandle<RenderTarget> resourceHandle = this.getHandle(map);
			RenderTarget renderTarget = resourceHandle.get();
			GpuTextureView gpuTextureView = this.depthBuffer ? renderTarget.getDepthTextureView() : renderTarget.getColorTextureView();
			if (gpuTextureView == null) {
				throw new IllegalStateException("Missing " + (this.depthBuffer ? "depth" : "color") + "texture for target " + this.targetId);
			} else {
				return gpuTextureView;
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public record TextureInput(String samplerName, AbstractTexture texture, int width, int height, boolean bilinear) implements PostPass.Input {
		@Override
		public void addToPass(FramePass framePass, Map<Identifier, ResourceHandle<RenderTarget>> map) {
		}

		@Override
		public GpuTextureView texture(Map<Identifier, ResourceHandle<RenderTarget>> map) {
			return this.texture.getTextureView();
		}
	}
}
