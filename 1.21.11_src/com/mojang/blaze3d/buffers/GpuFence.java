package com.mojang.blaze3d.buffers;

import com.mojang.blaze3d.DontObfuscate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
@DontObfuscate
public interface GpuFence extends AutoCloseable {
	@Override
	void close();

	boolean awaitCompletion(long l);
}
