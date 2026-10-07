package net.minecraft.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class MappableRingBuffer implements AutoCloseable {
	private static final int BUFFER_COUNT = 3;
	private final GpuBuffer[] buffers = new GpuBuffer[3];
	private final @Nullable GpuFence[] fences = new GpuFence[3];
	private final int size;
	private int current = 0;

	public MappableRingBuffer(Supplier<String> supplier, @GpuBuffer.Usage int i, int j) {
		GpuDevice gpuDevice = RenderSystem.getDevice();
		if ((i & 1) == 0 && (i & 2) == 0) {
			throw new IllegalArgumentException("MappableRingBuffer requires at least one of USAGE_MAP_READ or USAGE_MAP_WRITE");
		}

		for (int k = 0; k < 3; k++) {
			int l = k;
			this.buffers[k] = gpuDevice.createBuffer(() -> supplier.get() + " #" + l, i, j);
			this.fences[k] = null;
		}

		this.size = j;
	}

	public int size() {
		return this.size;
	}

	public GpuBuffer currentBuffer() {
		GpuFence gpuFence = this.fences[this.current];
		if (gpuFence != null) {
			gpuFence.awaitCompletion(Long.MAX_VALUE);
			gpuFence.close();
			this.fences[this.current] = null;
		}

		return this.buffers[this.current];
	}

	public void rotate() {
		if (this.fences[this.current] != null) {
			this.fences[this.current].close();
		}

		this.fences[this.current] = RenderSystem.getDevice().createCommandEncoder().createFence();
		this.current = (this.current + 1) % 3;
	}

	@Override
	public void close() {
		for (int i = 0; i < 3; i++) {
			this.buffers[i].close();
			if (this.fences[i] != null) {
				this.fences[i].close();
			}
		}
	}
}
