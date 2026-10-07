package com.mojang.blaze3d.buffers;

import com.mojang.blaze3d.DontObfuscate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
@DontObfuscate
public record GpuBufferSlice(GpuBuffer buffer, long offset, long length) {
	public GpuBufferSlice slice(long l, long m) {
		if (l >= 0L && m >= 0L && l + m <= this.length) {
			return new GpuBufferSlice(this.buffer, this.offset + l, m);
		} else {
			throw new IllegalArgumentException(
				"Offset of " + l + " and length " + m + " would put new slice outside existing slice's range (of " + this.offset + "," + this.length + ")"
			);
		}
	}
}
