package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.textures.TextureFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public sealed interface Uniform extends AutoCloseable permits Uniform.Ubo, Uniform.Utb, Uniform.Sampler {
	@Override
	default void close() {
	}

	@Environment(EnvType.CLIENT)
	record Sampler(int location, int samplerIndex) implements Uniform {
	}

	@Environment(EnvType.CLIENT)
	record Ubo(int blockBinding) implements Uniform {
	}

	@Environment(EnvType.CLIENT)
	record Utb(int location, int samplerIndex, TextureFormat format, int texture) implements Uniform {
		public Utb(int i, int j, TextureFormat textureFormat) {
			this(i, j, textureFormat, GlStateManager._genTexture());
		}

		@Override
		public void close() {
			GlStateManager._deleteTexture(this.texture);
		}
	}
}
