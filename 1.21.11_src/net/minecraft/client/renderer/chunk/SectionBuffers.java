package net.minecraft.client.renderer.chunk;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public final class SectionBuffers implements AutoCloseable {
	private GpuBuffer vertexBuffer;
	private @Nullable GpuBuffer indexBuffer;
	private int indexCount;
	private VertexFormat.IndexType indexType;

	public SectionBuffers(GpuBuffer gpuBuffer, @Nullable GpuBuffer gpuBuffer2, int i, VertexFormat.IndexType indexType) {
		this.vertexBuffer = gpuBuffer;
		this.indexBuffer = gpuBuffer2;
		this.indexCount = i;
		this.indexType = indexType;
	}

	public GpuBuffer getVertexBuffer() {
		return this.vertexBuffer;
	}

	public @Nullable GpuBuffer getIndexBuffer() {
		return this.indexBuffer;
	}

	public void setIndexBuffer(@Nullable GpuBuffer gpuBuffer) {
		this.indexBuffer = gpuBuffer;
	}

	public int getIndexCount() {
		return this.indexCount;
	}

	public VertexFormat.IndexType getIndexType() {
		return this.indexType;
	}

	public void setIndexType(VertexFormat.IndexType indexType) {
		this.indexType = indexType;
	}

	public void setIndexCount(int i) {
		this.indexCount = i;
	}

	public void setVertexBuffer(GpuBuffer gpuBuffer) {
		this.vertexBuffer = gpuBuffer;
	}

	@Override
	public void close() {
		this.vertexBuffer.close();
		if (this.indexBuffer != null) {
			this.indexBuffer.close();
		}
	}
}
