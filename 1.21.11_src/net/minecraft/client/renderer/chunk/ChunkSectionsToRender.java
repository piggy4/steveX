package net.minecraft.client.renderer.chunk;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.EnumMap;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;

@Environment(EnvType.CLIENT)
public record ChunkSectionsToRender(
	GpuTextureView textureView,
	EnumMap<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> drawsPerLayer,
	int maxIndicesRequired,
	GpuBufferSlice[] chunkSectionInfos
) {
	public void renderGroup(ChunkSectionLayerGroup chunkSectionLayerGroup, GpuSampler gpuSampler) {
		RenderSystem.AutoStorageIndexBuffer autoStorageIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		GpuBuffer gpuBuffer = this.maxIndicesRequired == 0 ? null : autoStorageIndexBuffer.getBuffer(this.maxIndicesRequired);
		VertexFormat.IndexType indexType = this.maxIndicesRequired == 0 ? null : autoStorageIndexBuffer.type();
		ChunkSectionLayer[] chunkSectionLayers = chunkSectionLayerGroup.layers();
		Minecraft minecraft = Minecraft.getInstance();
		boolean bl = SharedConstants.DEBUG_HOTKEYS && minecraft.wireframe;
		RenderTarget renderTarget = chunkSectionLayerGroup.outputTarget();

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(
					() -> "Section layers for " + chunkSectionLayerGroup.label(),
					renderTarget.getColorTextureView(),
					OptionalInt.empty(),
					renderTarget.getDepthTextureView(),
					OptionalDouble.empty()
				)) {
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.bindTexture("Sampler2", minecraft.gameRenderer.lightTexture().getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));

			for (ChunkSectionLayer chunkSectionLayer : chunkSectionLayers) {
				List<RenderPass.Draw<GpuBufferSlice[]>> list = this.drawsPerLayer.get(chunkSectionLayer);
				if (!list.isEmpty()) {
					if (chunkSectionLayer == ChunkSectionLayer.TRANSLUCENT) {
						list = list.reversed();
					}

					renderPass.setPipeline(bl ? RenderPipelines.WIREFRAME : chunkSectionLayer.pipeline());
					renderPass.bindTexture("Sampler0", this.textureView, gpuSampler);
					renderPass.drawMultipleIndexed(list, gpuBuffer, indexType, List.of("ChunkSection"), this.chunkSectionInfos);
				}
			}
		}
	}
}
