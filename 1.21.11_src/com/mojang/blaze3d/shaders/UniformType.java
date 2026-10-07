package com.mojang.blaze3d.shaders;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum UniformType {
	UNIFORM_BUFFER("ubo"),
	TEXEL_BUFFER("utb");

	final String name;

	UniformType(final String string2) {
		this.name = string2;
	}
}
