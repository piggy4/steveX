package com.mojang.blaze3d.systems;

import com.mojang.blaze3d.DontObfuscate;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
@DontObfuscate
public interface GpuDevice {
	CommandEncoder createCommandEncoder();

	GpuSampler createSampler(
		AddressMode addressMode, AddressMode addressMode2, FilterMode filterMode, FilterMode filterMode2, int i, OptionalDouble optionalDouble
	);

	GpuTexture createTexture(@Nullable Supplier<String> supplier, @GpuTexture.Usage int i, TextureFormat textureFormat, int j, int k, int l, int m);

	GpuTexture createTexture(@Nullable String string, @GpuTexture.Usage int i, TextureFormat textureFormat, int j, int k, int l, int m);

	GpuTextureView createTextureView(GpuTexture gpuTexture);

	GpuTextureView createTextureView(GpuTexture gpuTexture, int i, int j);

	GpuBuffer createBuffer(@Nullable Supplier<String> supplier, @GpuBuffer.Usage int i, long l);

	GpuBuffer createBuffer(@Nullable Supplier<String> supplier, @GpuBuffer.Usage int i, ByteBuffer byteBuffer);

	String getImplementationInformation();

	List<String> getLastDebugMessages();

	boolean isDebuggingEnabled();

	String getVendor();

	String getBackendName();

	String getVersion();

	String getRenderer();

	int getMaxTextureSize();

	int getUniformOffsetAlignment();

	default CompiledRenderPipeline precompilePipeline(RenderPipeline renderPipeline) {
		return this.precompilePipeline(renderPipeline, null);
	}

	CompiledRenderPipeline precompilePipeline(RenderPipeline renderPipeline, @Nullable ShaderSource shaderSource);

	void clearPipelineCache();

	List<String> getEnabledExtensions();

	int getMaxSupportedAnisotropy();

	void close();
}
