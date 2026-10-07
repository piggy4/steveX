package com.mojang.blaze3d.vertex;

import com.mojang.jtracy.MemoryPool;
import com.mojang.jtracy.TracyClient;
import com.mojang.logging.LogUtils;
import java.nio.ByteBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.MemoryUtil.MemoryAllocator;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ByteBufferBuilder implements AutoCloseable {
	private static final MemoryPool MEMORY_POOL = TracyClient.createMemoryPool("ByteBufferBuilder");
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final MemoryAllocator ALLOCATOR = MemoryUtil.getAllocator(false);
	private static final long DEFAULT_MAX_CAPACITY = 4294967295L;
	private static final int MAX_GROWTH_SIZE = 2097152;
	private static final int BUFFER_FREED_GENERATION = -1;
	long pointer;
	private long capacity;
	private final long maxCapacity;
	private long writeOffset;
	private long nextResultOffset;
	private int resultCount;
	private int generation;

	public ByteBufferBuilder(int i, long l) {
		this.capacity = i;
		this.maxCapacity = l;
		this.pointer = ALLOCATOR.malloc(i);
		MEMORY_POOL.malloc(this.pointer, i);
		if (this.pointer == 0L) {
			throw new OutOfMemoryError("Failed to allocate " + i + " bytes");
		}
	}

	public ByteBufferBuilder(int i) {
		this(i, 4294967295L);
	}

	public static ByteBufferBuilder exactlySized(int i) {
		return new ByteBufferBuilder(i, i);
	}

	public long reserve(int i) {
		long l = this.writeOffset;
		long m = Math.addExact(l, i);
		this.ensureCapacity(m);
		this.writeOffset = m;
		return Math.addExact(this.pointer, l);
	}

	private void ensureCapacity(long l) {
		if (l > this.capacity) {
			if (l > this.maxCapacity) {
				throw new IllegalArgumentException("Maximum capacity of ByteBufferBuilder (" + this.maxCapacity + ") exceeded, required " + l);
			}

			long m = Math.min(this.capacity, 2097152L);
			long n = Mth.clamp(this.capacity + m, l, this.maxCapacity);
			this.resize(n);
		}
	}

	private void resize(long l) {
		MEMORY_POOL.free(this.pointer);
		this.pointer = ALLOCATOR.realloc(this.pointer, l);
		MEMORY_POOL.malloc(this.pointer, (int)Math.min(l, 2147483647L));
		LOGGER.debug("Needed to grow BufferBuilder buffer: Old size {} bytes, new size {} bytes.", this.capacity, l);
		if (this.pointer == 0L) {
			throw new OutOfMemoryError("Failed to resize buffer from " + this.capacity + " bytes to " + l + " bytes");
		}

		this.capacity = l;
	}

	public ByteBufferBuilder.@Nullable Result build() {
		this.checkOpen();
		long l = this.nextResultOffset;
		long m = this.writeOffset - l;
		if (m == 0L) {
			return null;
		}

		if (m > 2147483647L) {
			throw new IllegalStateException("Cannot build buffer larger than 2147483647 bytes (was " + m + ")");
		}

		this.nextResultOffset = this.writeOffset;
		this.resultCount++;
		return new ByteBufferBuilder.Result(l, (int)m, this.generation);
	}

	public void clear() {
		if (this.resultCount > 0) {
			LOGGER.warn("Clearing BufferBuilder with unused batches");
		}

		this.discard();
	}

	public void discard() {
		this.checkOpen();
		if (this.resultCount > 0) {
			this.discardResults();
			this.resultCount = 0;
		}
	}

	boolean isValid(int i) {
		return i == this.generation;
	}

	void freeResult() {
		if (--this.resultCount <= 0) {
			this.discardResults();
		}
	}

	private void discardResults() {
		long l = this.writeOffset - this.nextResultOffset;
		if (l > 0L) {
			MemoryUtil.memCopy(this.pointer + this.nextResultOffset, this.pointer, l);
		}

		this.writeOffset = l;
		this.nextResultOffset = 0L;
		this.generation++;
	}

	@Override
	public void close() {
		if (this.pointer != 0L) {
			MEMORY_POOL.free(this.pointer);
			ALLOCATOR.free(this.pointer);
			this.pointer = 0L;
			this.generation = -1;
		}
	}

	private void checkOpen() {
		if (this.pointer == 0L) {
			throw new IllegalStateException("Buffer has been freed");
		}
	}

	@Environment(EnvType.CLIENT)
	public class Result implements AutoCloseable {
		private final long offset;
		private final int capacity;
		private final int generation;
		private boolean closed;

		Result(final long l, final int i, final int j) {
			this.offset = l;
			this.capacity = i;
			this.generation = j;
		}

		public ByteBuffer byteBuffer() {
			if (!ByteBufferBuilder.this.isValid(this.generation)) {
				throw new IllegalStateException("Buffer is no longer valid");
			} else {
				return MemoryUtil.memByteBuffer(ByteBufferBuilder.this.pointer + this.offset, this.capacity);
			}
		}

		@Override
		public void close() {
			if (!this.closed) {
				this.closed = true;
				if (ByteBufferBuilder.this.isValid(this.generation)) {
					ByteBufferBuilder.this.freeResult();
				}
			}
		}
	}
}
