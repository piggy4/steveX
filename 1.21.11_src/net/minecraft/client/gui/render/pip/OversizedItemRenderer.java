package net.minecraft.client.gui.render.pip;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.pip.OversizedItemRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class OversizedItemRenderer extends PictureInPictureRenderer<OversizedItemRenderState> {
	private boolean usedOnThisFrame;
	private @Nullable Object modelOnTextureIdentity;

	public OversizedItemRenderer(MultiBufferSource.BufferSource bufferSource) {
		super(bufferSource);
	}

	public boolean usedOnThisFrame() {
		return this.usedOnThisFrame;
	}

	public void resetUsedOnThisFrame() {
		this.usedOnThisFrame = false;
	}

	public void invalidateTexture() {
		this.modelOnTextureIdentity = null;
	}

	@Override
	public Class<OversizedItemRenderState> getRenderStateClass() {
		return OversizedItemRenderState.class;
	}

	protected void renderToTexture(OversizedItemRenderState oversizedItemRenderState, PoseStack poseStack) {
		poseStack.scale(1.0F, -1.0F, -1.0F);
		GuiItemRenderState guiItemRenderState = oversizedItemRenderState.guiItemRenderState();
		ScreenRectangle screenRectangle = guiItemRenderState.oversizedItemBounds();
		Objects.requireNonNull(screenRectangle);
		float f = (screenRectangle.left() + screenRectangle.right()) / 2.0F;
		float g = (screenRectangle.top() + screenRectangle.bottom()) / 2.0F;
		float h = guiItemRenderState.x() + 8.0F;
		float i = guiItemRenderState.y() + 8.0F;
		poseStack.translate((h - f) / 16.0F, (g - i) / 16.0F, 0.0F);
		TrackingItemStackRenderState trackingItemStackRenderState = guiItemRenderState.itemStackRenderState();
		boolean bl = !trackingItemStackRenderState.usesBlockLight();
		if (bl) {
			Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_FLAT);
		} else {
			Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
		}

		FeatureRenderDispatcher featureRenderDispatcher = Minecraft.getInstance().gameRenderer.getFeatureRenderDispatcher();
		SubmitNodeStorage submitNodeStorage = featureRenderDispatcher.getSubmitNodeStorage();
		trackingItemStackRenderState.submit(poseStack, submitNodeStorage, 15728880, OverlayTexture.NO_OVERLAY, 0);
		featureRenderDispatcher.renderAllFeatures();
		this.modelOnTextureIdentity = trackingItemStackRenderState.getModelIdentity();
	}

	public void blitTexture(OversizedItemRenderState oversizedItemRenderState, GuiRenderState guiRenderState) {
		super.blitTexture(oversizedItemRenderState, guiRenderState);
		this.usedOnThisFrame = true;
	}

	public boolean textureIsReadyToBlit(OversizedItemRenderState oversizedItemRenderState) {
		TrackingItemStackRenderState trackingItemStackRenderState = oversizedItemRenderState.guiItemRenderState().itemStackRenderState();
		return !trackingItemStackRenderState.isAnimated() && trackingItemStackRenderState.getModelIdentity().equals(this.modelOnTextureIdentity);
	}

	@Override
	protected float getTranslateY(int i, int j) {
		return i / 2.0F;
	}

	@Override
	protected String getTextureLabel() {
		return "oversized_item";
	}
}
