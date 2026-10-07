package com.mojang.blaze3d.buffers;

import com.mojang.blaze3d.DontObfuscate;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
@DontObfuscate
public abstract class GpuBuffer implements AutoCloseable {
	public static final int USAGE_MAP_READ = 1;
	public static final int USAGE_MAP_WRITE = 2;
	public static final int USAGE_HINT_CLIENT_STORAGE = 4;
	public static final int USAGE_COPY_DST = 8;
	public static final int USAGE_COPY_SRC = 16;
	public static final int USAGE_VERTEX = 32;
	public static final int USAGE_INDEX = 64;
	public static final int USAGE_UNIFORM = 128;
	public static final int USAGE_UNIFORM_TEXEL_BUFFER = 256;
	@GpuBuffer.Usage
	private final int usage;
	private final long size;

	public GpuBuffer(@GpuBuffer.Usage int i, long l) {
		this.size = l;
		this.usage = i;
	}

	public long size() {
		return this.size;
	}

	@GpuBuffer.Usage
	public int usage() {
		return this.usage;
	}

	public abstract boolean isClosed();

	@Override
	public abstract void close();

	public GpuBufferSlice slice(long l, long m) {
		if (l >= 0L && m >= 0L && l + m <= this.size) {
			return new GpuBufferSlice(this, l, m);
		} else {
			throw new IllegalArgumentException("Offset of " + l + " and length " + m + " would put new slice outside buffer's range (of 0," + m + ")");
		}
	}

	public GpuBufferSlice slice() {
		return new GpuBufferSlice(this, 0L, this.size);
	}

	@Environment(EnvType.CLIENT)
	@DontObfuscate
	public interface MappedView extends AutoCloseable {
		ByteBuffer data();

		@Override
		void close();
	}

	@Retention(RetentionPolicy.CLASS)
	@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.METHOD, ElementType.TYPE_USE})
	@Environment(EnvType.CLIENT)
	public @interface Usage {
	}
}
