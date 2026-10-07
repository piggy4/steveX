package net.minecraft.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class DynamicUniformStorage<T extends DynamicUniformStorage.DynamicUniform> implements AutoCloseable {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final List<MappableRingBuffer> oldBuffers = new ArrayList<>();
	private final int blockSize;
	private MappableRingBuffer ringBuffer;
	private int nextBlock;
	private int capacity;
	private @Nullable T lastUniform;
	private final String label;

	public DynamicUniformStorage(String string, int i, int j) {
		GpuDevice gpuDevice = RenderSystem.getDevice();
		this.blockSize = Mth.roundToward(i, gpuDevice.getUniformOffsetAlignment());
		this.capacity = Mth.smallestEncompassingPowerOfTwo(j);
		this.nextBlock = 0;
		this.ringBuffer = new MappableRingBuffer(() -> string + " x" + this.blockSize, 130, this.blockSize * this.capacity);
		this.label = string;
	}

	public void endFrame() {
		this.nextBlock = 0;
		this.lastUniform = null;
		this.ringBuffer.rotate();
		if (!this.oldBuffers.isEmpty()) {
			for (MappableRingBuffer mappableRingBuffer : this.oldBuffers) {
				mappableRingBuffer.close();
			}

			this.oldBuffers.clear();
		}
	}

	private void resizeBuffers(int i) {
		this.capacity = i;
		this.nextBlock = 0;
		this.lastUniform = null;
		this.oldBuffers.add(this.ringBuffer);
		this.ringBuffer = new MappableRingBuffer(() -> this.label + " x" + this.blockSize, 130, this.blockSize * this.capacity);
	}

	public GpuBufferSlice writeUniform(T dynamicUniform) {
		if (this.lastUniform != null && this.lastUniform.equals(dynamicUniform)) {
			return this.ringBuffer.currentBuffer().slice((this.nextBlock - 1) * this.blockSize, this.blockSize);
		}

		if (this.nextBlock >= this.capacity) {
			int i = this.capacity * 2;
			LOGGER.info("Resizing {}, capacity limit of {} reached during a single frame. New capacity will be {}.", this.label, this.capacity, i);
			this.resizeBuffers(i);
		}

		int i = this.nextBlock * this.blockSize;

		try (GpuBuffer.MappedView mappedView = RenderSystem.getDevice()
				.createCommandEncoder()
				.mapBuffer(this.ringBuffer.currentBuffer().slice(i, this.blockSize), false, true)) {
			dynamicUniform.write(mappedView.data());
		}

		this.nextBlock++;
		this.lastUniform = dynamicUniform;
		return this.ringBuffer.currentBuffer().slice(i, this.blockSize);
	}

	public GpuBufferSlice[] writeUniforms(T[] dynamicUniforms) {
		if (dynamicUniforms.length == 0) {
			return new GpuBufferSlice[0];
		}

		if (this.nextBlock + dynamicUniforms.length > this.capacity) {
			int i = Mth.smallestEncompassingPowerOfTwo(Math.max(this.capacity + 1, dynamicUniforms.length));
			LOGGER.info("Resizing {}, capacity limit of {} reached during a single frame. New capacity will be {}.", this.label, this.capacity, i);
			this.resizeBuffers(i);
		}

		int i = this.nextBlock * this.blockSize;
		GpuBufferSlice[] gpuBufferSlices = new GpuBufferSlice[dynamicUniforms.length];

		try (GpuBuffer.MappedView mappedView = RenderSystem.getDevice()
				.createCommandEncoder()
				.mapBuffer(this.ringBuffer.currentBuffer().slice(i, dynamicUniforms.length * this.blockSize), false, true)) {
			ByteBuffer byteBuffer = mappedView.data();

			for (int j = 0; j < dynamicUniforms.length; j++) {
				T dynamicUniform = dynamicUniforms[j];
				gpuBufferSlices[j] = this.ringBuffer.currentBuffer().slice(i + j * this.blockSize, this.blockSize);
				byteBuffer.position(j * this.blockSize);
				dynamicUniform.write(byteBuffer);
			}
		}

		this.nextBlock += dynamicUniforms.length;
		this.lastUniform = dynamicUniforms[dynamicUniforms.length - 1];
		return gpuBufferSlices;
	}

	@Override
	public void close() {
		for (MappableRingBuffer mappableRingBuffer : this.oldBuffers) {
			mappableRingBuffer.close();
		}

		this.ringBuffer.close();
	}

	@Environment(EnvType.CLIENT)
	public interface DynamicUniform {
		void write(ByteBuffer byteBuffer);
	}
}
