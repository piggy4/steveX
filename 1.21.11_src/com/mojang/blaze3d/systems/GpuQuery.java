package com.mojang.blaze3d.systems;

import com.mojang.blaze3d.DontObfuscate;
import java.util.OptionalLong;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
@DontObfuscate
public interface GpuQuery extends AutoCloseable {
	OptionalLong getValue();

	@Override
	void close();
}
