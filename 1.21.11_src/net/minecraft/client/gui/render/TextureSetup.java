package net.minecraft.client.gui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record TextureSetup(
	@Nullable GpuTextureView texure0,
	@Nullable GpuTextureView texure1,
	@Nullable GpuTextureView texure2,
	@Nullable GpuSampler sampler0,
	@Nullable GpuSampler sampler1,
	@Nullable GpuSampler sampler2
) {
	private static final TextureSetup NO_TEXTURE_SETUP = new TextureSetup(null, null, null, null, null, null);
	private static int sortKeySeed;

	public static TextureSetup singleTexture(GpuTextureView gpuTextureView, GpuSampler gpuSampler) {
		return new TextureSetup(gpuTextureView, null, null, gpuSampler, null, null);
	}

	public static TextureSetup singleTextureWithLightmap(GpuTextureView gpuTextureView, GpuSampler gpuSampler) {
		return new TextureSetup(
			gpuTextureView,
			null,
			Minecraft.getInstance().gameRenderer.lightTexture().getTextureView(),
			gpuSampler,
			null,
			RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
		);
	}

	public static TextureSetup doubleTexture(GpuTextureView gpuTextureView, GpuSampler gpuSampler, GpuTextureView gpuTextureView2, GpuSampler gpuSampler2) {
		return new TextureSetup(gpuTextureView, gpuTextureView2, null, gpuSampler, gpuSampler2, null);
	}

	public static TextureSetup noTexture() {
		return NO_TEXTURE_SETUP;
	}

	public int getSortKey() {
		return SharedConstants.DEBUG_SHUFFLE_UI_RENDERING_ORDER ? this.hashCode() * (sortKeySeed + 1) : this.hashCode();
	}

	public static void updateSortKeySeed() {
		sortKeySeed = Math.round(100000.0F * (float)Math.random());
	}
}
