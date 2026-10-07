package com.mojang.blaze3d.opengl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.ShaderManager;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL31;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class GlProgram implements AutoCloseable {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static Set<String> BUILT_IN_UNIFORMS = Sets.newHashSet("Projection", "Lighting", "Fog", "Globals");
	public static GlProgram INVALID_PROGRAM = new GlProgram(-1, "invalid");
	private final Map<String, Uniform> uniformsByName = new HashMap<>();
	private final int programId;
	private final String debugLabel;

	private GlProgram(int i, String string) {
		this.programId = i;
		this.debugLabel = string;
	}

	public static GlProgram link(GlShaderModule glShaderModule, GlShaderModule glShaderModule2, VertexFormat vertexFormat, String string) throws ShaderManager.CompilationException {
		int i = GlStateManager.glCreateProgram();
		if (i <= 0) {
			throw new ShaderManager.CompilationException("Could not create shader program (returned program ID " + i + ")");
		}

		int j = 0;

		for (String string2 : vertexFormat.getElementAttributeNames()) {
			GlStateManager._glBindAttribLocation(i, j, string2);
			j++;
		}

		GlStateManager.glAttachShader(i, glShaderModule.getShaderId());
		GlStateManager.glAttachShader(i, glShaderModule2.getShaderId());
		GlStateManager.glLinkProgram(i);
		int k = GlStateManager.glGetProgrami(i, 35714);
		String string2 = GlStateManager.glGetProgramInfoLog(i, 32768);
		if (k != 0 && !string2.contains("Failed for unknown reason")) {
			if (!string2.isEmpty()) {
				LOGGER.info("Info log when linking program containing VS {} and FS {}. Log output: {}", glShaderModule.getId(), glShaderModule2.getId(), string2);
			}

			return new GlProgram(i, string);
		} else {
			throw new ShaderManager.CompilationException(
				"Error encountered when linking program containing VS " + glShaderModule.getId() + " and FS " + glShaderModule2.getId() + ". Log output: " + string2
			);
		}
	}

	public void setupUniforms(List<RenderPipeline.UniformDescription> list, List<String> list2) {
		int i = 0;
		int j = 0;

		for (RenderPipeline.UniformDescription uniformDescription : list) {
			String string = uniformDescription.name();

			Uniform uniform = switch (uniformDescription.type()) {
				case UNIFORM_BUFFER -> {
					int k = GL31.glGetUniformBlockIndex(this.programId, string);
					if (k == -1) {
						yield null;
					} else {
						int l = i++;
						GL31.glUniformBlockBinding(this.programId, k, l);
						yield new Uniform.Ubo(l);
					}
				}
				case TEXEL_BUFFER -> {
					int k = GlStateManager._glGetUniformLocation(this.programId, string);
					if (k == -1) {
						LOGGER.warn("{} shader program does not use utb {} defined in the pipeline. This might be a bug.", this.debugLabel, string);
						yield null;
					} else {
						int l = j++;
						yield new Uniform.Utb(k, l, Objects.requireNonNull(uniformDescription.textureFormat()));
					}
				}
			};
			if (uniform != null) {
				this.uniformsByName.put(string, uniform);
			}
		}

		for (String string2 : list2) {
			int m = GlStateManager._glGetUniformLocation(this.programId, string2);
			if (m == -1) {
				LOGGER.warn("{} shader program does not use sampler {} defined in the pipeline. This might be a bug.", this.debugLabel, string2);
			} else {
				int n = j++;
				this.uniformsByName.put(string2, new Uniform.Sampler(m, n));
			}
		}

		int o = GlStateManager.glGetProgrami(this.programId, 35382);

		for (int p = 0; p < o; p++) {
			String string = GL31.glGetActiveUniformBlockName(this.programId, p);
			if (!this.uniformsByName.containsKey(string)) {
				if (!list2.contains(string) && BUILT_IN_UNIFORMS.contains(string)) {
					int n = i++;
					GL31.glUniformBlockBinding(this.programId, p, n);
					this.uniformsByName.put(string, new Uniform.Ubo(n));
				} else {
					LOGGER.warn("Found unknown and unsupported uniform {} in {}", string, this.debugLabel);
				}
			}
		}
	}

	@Override
	public void close() {
		this.uniformsByName.values().forEach(Uniform::close);
		GlStateManager.glDeleteProgram(this.programId);
	}

	public @Nullable Uniform getUniform(String string) {
		RenderSystem.assertOnRenderThread();
		return this.uniformsByName.get(string);
	}

	@VisibleForTesting
	public int getProgramId() {
		return this.programId;
	}

	@Override
	public String toString() {
		return this.debugLabel;
	}

	public String getDebugLabel() {
		return this.debugLabel;
	}

	public Map<String, Uniform> getUniforms() {
		return this.uniformsByName;
	}
}
