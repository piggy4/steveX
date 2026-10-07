package com.mojang.blaze3d.textures;

import com.mojang.blaze3d.DontObfuscate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
@DontObfuscate
public enum FilterMode {
	NEAREST,
	LINEAR;
}
