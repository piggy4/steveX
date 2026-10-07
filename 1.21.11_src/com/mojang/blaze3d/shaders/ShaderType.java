package com.mojang.blaze3d.shaders;

import com.mojang.blaze3d.DontObfuscate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
@DontObfuscate
public enum ShaderType {
	VERTEX("vertex", ".vsh"),
	FRAGMENT("fragment", ".fsh");

	private static final ShaderType[] TYPES = values();
	private final String name;
	private final String extension;

	ShaderType(final String string2, final String string3) {
		this.name = string2;
		this.extension = string3;
	}

	public static @Nullable ShaderType byLocation(Identifier identifier) {
		for (ShaderType shaderType : TYPES) {
			if (identifier.getPath().endsWith(shaderType.extension)) {
				return shaderType;
			}
		}

		return null;
	}

	public String getName() {
		return this.name;
	}

	public FileToIdConverter idConverter() {
		return new FileToIdConverter("shaders", this.extension);
	}
}
