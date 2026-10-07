package com.mojang.blaze3d.systems;

import com.mojang.blaze3d.DontObfuscate;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
@DontObfuscate
public interface RenderPass extends AutoCloseable {
	void pushDebugGroup(Supplier<String> supplier);

	void popDebugGroup();

	void setPipeline(RenderPipeline renderPipeline);

	void bindTexture(String string, @Nullable GpuTextureView gpuTextureView, @Nullable GpuSampler gpuSampler);

	void setUniform(String string, GpuBuffer gpuBuffer);

	void setUniform(String string, GpuBufferSlice gpuBufferSlice);

	void enableScissor(int i, int j, int k, int l);

	void disableScissor();

	void setVertexBuffer(int i, GpuBuffer gpuBuffer);

	void setIndexBuffer(GpuBuffer gpuBuffer, VertexFormat.IndexType indexType);

	void drawIndexed(int i, int j, int k, int l);

	<T> void drawMultipleIndexed(
		Collection<RenderPass.Draw<T>> collection,
		@Nullable GpuBuffer gpuBuffer,
		VertexFormat.@Nullable IndexType indexType,
		Collection<String> collection2,
		T object
	);

	void draw(int i, int j);

	@Override
	void close();

	@Environment(EnvType.CLIENT)
	record Draw<T>(
		int slot,
		GpuBuffer vertexBuffer,
		@Nullable GpuBuffer indexBuffer,
		VertexFormat.@Nullable IndexType indexType,
		int firstIndex,
		int indexCount,
		@Nullable BiConsumer<T, RenderPass.UniformUploader> uniformUploaderConsumer
	) {
		public Draw(int i, GpuBuffer gpuBuffer, GpuBuffer gpuBuffer2, VertexFormat.IndexType indexType, int j, int k) {
			this(i, gpuBuffer, gpuBuffer2, indexType, j, k, null);
		}
	}

	@Environment(EnvType.CLIENT)
	interface UniformUploader {
		void upload(String string, GpuBufferSlice gpuBufferSlice);
	}
}
