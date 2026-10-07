package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public class GlShaderModule implements AutoCloseable {
	private static final int NOT_ALLOCATED = -1;
	public static final GlShaderModule INVALID_SHADER = new GlShaderModule(-1, Identifier.withDefaultNamespace("invalid"), ShaderType.VERTEX);
	private final Identifier id;
	private int shaderId;
	private final ShaderType type;

	public GlShaderModule(int i, Identifier identifier, ShaderType shaderType) {
		this.id = identifier;
		this.shaderId = i;
		this.type = shaderType;
	}

	@Override
	public void close() {
		if (this.shaderId == -1) {
			throw new IllegalStateException("Already closed");
		}

		RenderSystem.assertOnRenderThread();
		GlStateManager.glDeleteShader(this.shaderId);
		this.shaderId = -1;
	}

	public Identifier getId() {
		return this.id;
	}

	public int getShaderId() {
		return this.shaderId;
	}

	public String getDebugLabel() {
		return this.type.idConverter().idToFile(this.id).toString();
	}
}
