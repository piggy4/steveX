package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

@Environment(EnvType.CLIENT)
public abstract class BufferStorage {
	public static BufferStorage create(GLCapabilities gLCapabilities, Set<String> set) {
		if (gLCapabilities.GL_ARB_buffer_storage && GlDevice.USE_GL_ARB_buffer_storage) {
			set.add("GL_ARB_buffer_storage");
			return new BufferStorage.Immutable();
		} else {
			return new BufferStorage.Mutable();
		}
	}

	public abstract GlBuffer createBuffer(DirectStateAccess directStateAccess, @Nullable Supplier<String> supplier, @GpuBuffer.Usage int i, long l);

	public abstract GlBuffer createBuffer(DirectStateAccess directStateAccess, @Nullable Supplier<String> supplier, @GpuBuffer.Usage int i, ByteBuffer byteBuffer);

	public abstract GlBuffer.GlMappedView mapBuffer(DirectStateAccess directStateAccess, GlBuffer glBuffer, long l, long m, int i);

	@Environment(EnvType.CLIENT)
	static class Immutable extends BufferStorage {
		@Override
		public GlBuffer createBuffer(DirectStateAccess directStateAccess, @Nullable Supplier<String> supplier, @GpuBuffer.Usage int i, long l) {
			int j = directStateAccess.createBuffer();
			directStateAccess.bufferStorage(j, l, i);
			ByteBuffer byteBuffer = this.tryMapBufferPersistent(directStateAccess, i, j, l);
			return new GlBuffer(supplier, directStateAccess, i, l, j, byteBuffer);
		}

		@Override
		public GlBuffer createBuffer(DirectStateAccess directStateAccess, @Nullable Supplier<String> supplier, @GpuBuffer.Usage int i, ByteBuffer byteBuffer) {
			int j = directStateAccess.createBuffer();
			int k = byteBuffer.remaining();
			directStateAccess.bufferStorage(j, byteBuffer, i);
			ByteBuffer byteBuffer2 = this.tryMapBufferPersistent(directStateAccess, i, j, k);
			return new GlBuffer(supplier, directStateAccess, i, k, j, byteBuffer2);
		}

		private @Nullable ByteBuffer tryMapBufferPersistent(DirectStateAccess directStateAccess, @GpuBuffer.Usage int i, int j, long l) {
			int k = 0;
			if ((i & 1) != 0) {
				k |= 1;
			}

			if ((i & 2) != 0) {
				k |= 18;
			}

			ByteBuffer byteBuffer;
			if (k != 0) {
				GlStateManager.clearGlErrors();
				byteBuffer = directStateAccess.mapBufferRange(j, 0L, l, k | 64, i);
				if (byteBuffer == null) {
					throw new IllegalStateException("Can't persistently map buffer, opengl error " + GlStateManager._getError());
				}
			} else {
				byteBuffer = null;
			}

			return byteBuffer;
		}

		@Override
		public GlBuffer.GlMappedView mapBuffer(DirectStateAccess directStateAccess, GlBuffer glBuffer, long l, long m, int i) {
			if (glBuffer.persistentBuffer == null) {
				throw new IllegalStateException("Somehow trying to map an unmappable buffer");
			} else if (l > 2147483647L || m > 2147483647L) {
				throw new IllegalArgumentException("Mapping buffers larger than 2GB is not supported");
			} else if (l >= 0L && m >= 0L) {
				return new GlBuffer.GlMappedView(() -> {
					if ((i & 2) != 0) {
						directStateAccess.flushMappedBufferRange(glBuffer.handle, l, m, glBuffer.usage());
					}
				}, glBuffer, MemoryUtil.memSlice(glBuffer.persistentBuffer, (int)l, (int)m));
			} else {
				throw new IllegalArgumentException("Offset or length must be positive integer values");
			}
		}
	}

	@Environment(EnvType.CLIENT)
	static class Mutable extends BufferStorage {
		@Override
		public GlBuffer createBuffer(DirectStateAccess directStateAccess, @Nullable Supplier<String> supplier, @GpuBuffer.Usage int i, long l) {
			int j = directStateAccess.createBuffer();
			directStateAccess.bufferData(j, l, i);
			return new GlBuffer(supplier, directStateAccess, i, l, j, null);
		}

		@Override
		public GlBuffer createBuffer(DirectStateAccess directStateAccess, @Nullable Supplier<String> supplier, @GpuBuffer.Usage int i, ByteBuffer byteBuffer) {
			int j = directStateAccess.createBuffer();
			int k = byteBuffer.remaining();
			directStateAccess.bufferData(j, byteBuffer, i);
			return new GlBuffer(supplier, directStateAccess, i, k, j, null);
		}

		@Override
		public GlBuffer.GlMappedView mapBuffer(DirectStateAccess directStateAccess, GlBuffer glBuffer, long l, long m, int i) {
			GlStateManager.clearGlErrors();
			ByteBuffer byteBuffer = directStateAccess.mapBufferRange(glBuffer.handle, l, m, i, glBuffer.usage());
			if (byteBuffer == null) {
				throw new IllegalStateException("Can't map buffer, opengl error " + GlStateManager._getError());
			} else {
				return new GlBuffer.GlMappedView(() -> directStateAccess.unmapBuffer(glBuffer.handle, glBuffer.usage()), glBuffer, byteBuffer);
			}
		}
	}
}
