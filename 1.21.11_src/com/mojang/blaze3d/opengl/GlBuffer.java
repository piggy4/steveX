package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.jtracy.MemoryPool;
import com.mojang.jtracy.TracyClient;
import java.nio.ByteBuffer;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class GlBuffer extends GpuBuffer {
	protected static final MemoryPool MEMORY_POOl = TracyClient.createMemoryPool("GPU Buffers");
	protected boolean closed;
	protected final @Nullable Supplier<String> label;
	private final DirectStateAccess dsa;
	protected final int handle;
	protected @Nullable ByteBuffer persistentBuffer;

	protected GlBuffer(
		@Nullable Supplier<String> supplier, DirectStateAccess directStateAccess, @GpuBuffer.Usage int i, long l, int j, @Nullable ByteBuffer byteBuffer
	) {
		super(i, l);
		this.label = supplier;
		this.dsa = directStateAccess;
		this.handle = j;
		this.persistentBuffer = byteBuffer;
		int k = (int)Math.min(l, 2147483647L);
		MEMORY_POOl.malloc(j, k);
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	@Override
	public void close() {
		if (!this.closed) {
			this.closed = true;
			if (this.persistentBuffer != null) {
				this.dsa.unmapBuffer(this.handle, this.usage());
				this.persistentBuffer = null;
			}

			GlStateManager._glDeleteBuffers(this.handle);
			MEMORY_POOl.free(this.handle);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class GlMappedView implements GpuBuffer.MappedView {
		private final Runnable unmap;
		private final GlBuffer buffer;
		private final ByteBuffer data;
		private boolean closed;

		protected GlMappedView(Runnable runnable, GlBuffer glBuffer, ByteBuffer byteBuffer) {
			this.unmap = runnable;
			this.buffer = glBuffer;
			this.data = byteBuffer;
		}

		@Override
		public ByteBuffer data() {
			return this.data;
		}

		@Override
		public void close() {
			if (!this.closed) {
				this.closed = true;
				this.unmap.run();
			}
		}
	}
}
