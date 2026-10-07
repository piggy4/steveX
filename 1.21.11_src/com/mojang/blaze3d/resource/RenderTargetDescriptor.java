package com.mojang.blaze3d.resource;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record RenderTargetDescriptor(int width, int height, boolean useDepth, int clearColor) implements ResourceDescriptor<RenderTarget> {
	public RenderTarget allocate() {
		return new TextureTarget(null, this.width, this.height, this.useDepth);
	}

	public void prepare(RenderTarget renderTarget) {
		if (this.useDepth) {
			RenderSystem.getDevice()
				.createCommandEncoder()
				.clearColorAndDepthTextures(renderTarget.getColorTexture(), this.clearColor, renderTarget.getDepthTexture(), 1.0);
		} else {
			RenderSystem.getDevice().createCommandEncoder().clearColorTexture(renderTarget.getColorTexture(), this.clearColor);
		}
	}

	public void free(RenderTarget renderTarget) {
		renderTarget.destroyBuffers();
	}

	@Override
	public boolean canUsePhysicalResource(ResourceDescriptor<?> resourceDescriptor) {
		return !(resourceDescriptor instanceof RenderTargetDescriptor renderTargetDescriptor)
			? false
			: this.width == renderTargetDescriptor.width && this.height == renderTargetDescriptor.height && this.useDepth == renderTargetDescriptor.useDepth;
	}
}
