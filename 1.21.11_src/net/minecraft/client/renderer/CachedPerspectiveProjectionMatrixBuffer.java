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
public class CachedPerspectiveProjectionMatrixBuffer implements AutoCloseable {
	private final GpuBuffer buffer;
	private final GpuBufferSlice bufferSlice;
	private final float zNear;
	private final float zFar;
	private int width;
	private int height;
	private float fov;

	public CachedPerspectiveProjectionMatrixBuffer(String string, float f, float g) {
		this.zNear = f;
		this.zFar = g;
		GpuDevice gpuDevice = RenderSystem.getDevice();
		this.buffer = gpuDevice.createBuffer(() -> "Projection matrix UBO " + string, 136, RenderSystem.PROJECTION_MATRIX_UBO_SIZE);
		this.bufferSlice = this.buffer.slice(0L, RenderSystem.PROJECTION_MATRIX_UBO_SIZE);
	}

	public GpuBufferSlice getBuffer(int i, int j, float f) {
		if (this.width != i || this.height != j || this.fov != f) {
			Matrix4f matrix4f = this.createProjectionMatrix(i, j, f);

			try (MemoryStack memoryStack = MemoryStack.stackPush()) {
				ByteBuffer byteBuffer = Std140Builder.onStack(memoryStack, RenderSystem.PROJECTION_MATRIX_UBO_SIZE).putMat4f(matrix4f).get();
				RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.buffer.slice(), byteBuffer);
			}

			this.width = i;
			this.height = j;
			this.fov = f;
		}

		return this.bufferSlice;
	}

	private Matrix4f createProjectionMatrix(int i, int j, float f) {
		return new Matrix4f().perspective(f * (float) (Math.PI / 180.0), (float)i / j, this.zNear, this.zFar);
	}

	@Override
	public void close() {
		this.buffer.close();
	}
}
