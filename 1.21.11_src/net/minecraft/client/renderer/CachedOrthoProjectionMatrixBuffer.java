package net.minecraft.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

@Environment(EnvType.CLIENT)
public class CachedOrthoProjectionMatrixBuffer implements AutoCloseable {
	private final GpuBuffer buffer;
	private final GpuBufferSlice bufferSlice;
	private final float zNear;
	private final float zFar;
	private final boolean invertY;
	private float width;
	private float height;

	public CachedOrthoProjectionMatrixBuffer(String string, float f, float g, boolean bl) {
		this.zNear = f;
		this.zFar = g;
		this.invertY = bl;
		GpuDevice gpuDevice = RenderSystem.getDevice();
		this.buffer = gpuDevice.createBuffer(() -> "Projection matrix UBO " + string, 136, RenderSystem.PROJECTION_MATRIX_UBO_SIZE);
		this.bufferSlice = this.buffer.slice(0L, RenderSystem.PROJECTION_MATRIX_UBO_SIZE);
	}

	public GpuBufferSlice getBuffer(float f, float g) {
		if (this.width != f || this.height != g) {
			Matrix4f matrix4f = this.createProjectionMatrix(f, g);

			try (MemoryStack memoryStack = MemoryStack.stackPush()) {
				ByteBuffer byteBuffer = Std140Builder.onStack(memoryStack, RenderSystem.PROJECTION_MATRIX_UBO_SIZE).putMat4f(matrix4f).get();
				RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.buffer.slice(), byteBuffer);
			}

			this.width = f;
			this.height = g;
		}

		return this.bufferSlice;
	}

	private Matrix4f createProjectionMatrix(float f, float g) {
		return new Matrix4f().setOrtho(0.0F, f, this.invertY ? g : 0.0F, this.invertY ? 0.0F : g, this.zNear, this.zFar);
	}

	@Override
	public void close() {
		this.buffer.close();
	}
}
