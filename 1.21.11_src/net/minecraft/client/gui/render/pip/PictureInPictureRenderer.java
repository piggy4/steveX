package net.minecraft.client.gui.render.pip;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class PictureInPictureRenderer<T extends PictureInPictureRenderState> implements AutoCloseable {
	protected final MultiBufferSource.BufferSource bufferSource;
	private @Nullable GpuTexture texture;
	private @Nullable GpuTextureView textureView;
	private @Nullable GpuTexture depthTexture;
	private @Nullable GpuTextureView depthTextureView;
	private final CachedOrthoProjectionMatrixBuffer projectionMatrixBuffer = new CachedOrthoProjectionMatrixBuffer(
		"PIP - " + this.getClass().getSimpleName(), -1000.0F, 1000.0F, true
	);

	protected PictureInPictureRenderer(MultiBufferSource.BufferSource bufferSource) {
		this.bufferSource = bufferSource;
	}

	public void prepare(T pictureInPictureRenderState, GuiRenderState guiRenderState, int i) {
		int j = (pictureInPictureRenderState.x1() - pictureInPictureRenderState.x0()) * i;
		int k = (pictureInPictureRenderState.y1() - pictureInPictureRenderState.y0()) * i;
		boolean bl = this.texture == null || this.texture.getWidth(0) != j || this.texture.getHeight(0) != k;
		if (!bl && this.textureIsReadyToBlit(pictureInPictureRenderState)) {
			this.blitTexture(pictureInPictureRenderState, guiRenderState);
		} else {
			this.prepareTexturesAndProjection(bl, j, k);
			RenderSystem.outputColorTextureOverride = this.textureView;
			RenderSystem.outputDepthTextureOverride = this.depthTextureView;
			PoseStack poseStack = new PoseStack();
			poseStack.translate(j / 2.0F, this.getTranslateY(k, i), 0.0F);
			float f = i * pictureInPictureRenderState.scale();
			poseStack.scale(f, f, -f);
			this.renderToTexture(pictureInPictureRenderState, poseStack);
			this.bufferSource.endBatch();
			RenderSystem.outputColorTextureOverride = null;
			RenderSystem.outputDepthTextureOverride = null;
			this.blitTexture(pictureInPictureRenderState, guiRenderState);
		}
	}

	protected void blitTexture(T pictureInPictureRenderState, GuiRenderState guiRenderState) {
		guiRenderState.submitBlitToCurrentLayer(
			new BlitRenderState(
				RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
				TextureSetup.singleTexture(this.textureView, RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
				pictureInPictureRenderState.pose(),
				pictureInPictureRenderState.x0(),
				pictureInPictureRenderState.y0(),
				pictureInPictureRenderState.x1(),
				pictureInPictureRenderState.y1(),
				0.0F,
				1.0F,
				1.0F,
				0.0F,
				-1,
				pictureInPictureRenderState.scissorArea(),
				null
			)
		);
	}

	private void prepareTexturesAndProjection(boolean bl, int i, int j) {
		if (this.texture != null && bl) {
			this.texture.close();
			this.texture = null;
			this.textureView.close();
			this.textureView = null;
			this.depthTexture.close();
			this.depthTexture = null;
			this.depthTextureView.close();
			this.depthTextureView = null;
		}

		GpuDevice gpuDevice = RenderSystem.getDevice();
		if (this.texture == null) {
			this.texture = gpuDevice.createTexture(() -> "UI " + this.getTextureLabel() + " texture", 12, TextureFormat.RGBA8, i, j, 1, 1);
			this.textureView = gpuDevice.createTextureView(this.texture);
			this.depthTexture = gpuDevice.createTexture(() -> "UI " + this.getTextureLabel() + " depth texture", 8, TextureFormat.DEPTH32, i, j, 1, 1);
			this.depthTextureView = gpuDevice.createTextureView(this.depthTexture);
		}

		gpuDevice.createCommandEncoder().clearColorAndDepthTextures(this.texture, 0, this.depthTexture, 1.0);
		RenderSystem.setProjectionMatrix(this.projectionMatrixBuffer.getBuffer(i, j), ProjectionType.ORTHOGRAPHIC);
	}

	protected boolean textureIsReadyToBlit(T pictureInPictureRenderState) {
		return false;
	}

	protected float getTranslateY(int i, int j) {
		return i;
	}

	@Override
	public void close() {
		if (this.texture != null) {
			this.texture.close();
		}

		if (this.textureView != null) {
			this.textureView.close();
		}

		if (this.depthTexture != null) {
			this.depthTexture.close();
		}

		if (this.depthTextureView != null) {
			this.depthTextureView.close();
		}

		this.projectionMatrixBuffer.close();
	}

	public abstract Class<T> getRenderStateClass();

	protected abstract void renderToTexture(T pictureInPictureRenderState, PoseStack poseStack);

	protected abstract String getTextureLabel();
}
