package net.minecraft.client.gui.render.pip;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.state.pip.GuiSkinRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4fStack;

@Environment(EnvType.CLIENT)
public class GuiSkinRenderer extends PictureInPictureRenderer<GuiSkinRenderState> {
	public GuiSkinRenderer(MultiBufferSource.BufferSource bufferSource) {
		super(bufferSource);
	}

	@Override
	public Class<GuiSkinRenderState> getRenderStateClass() {
		return GuiSkinRenderState.class;
	}

	protected void renderToTexture(GuiSkinRenderState guiSkinRenderState, PoseStack poseStack) {
		Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.PLAYER_SKIN);
		int i = Minecraft.getInstance().getWindow().getGuiScale();
		Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
		matrix4fStack.pushMatrix();
		float f = guiSkinRenderState.scale() * i;
		matrix4fStack.rotateAround(Axis.XP.rotationDegrees(guiSkinRenderState.rotationX()), 0.0F, f * -guiSkinRenderState.pivotY(), 0.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(-guiSkinRenderState.rotationY()));
		poseStack.translate(0.0F, -1.6010001F, 0.0F);
		RenderType renderType = guiSkinRenderState.playerModel().renderType(guiSkinRenderState.texture());
		guiSkinRenderState.playerModel().renderToBuffer(poseStack, this.bufferSource.getBuffer(renderType), 15728880, OverlayTexture.NO_OVERLAY);
		this.bufferSource.endBatch();
		matrix4fStack.popMatrix();
	}

	@Override
	protected String getTextureLabel() {
		return "player skin";
	}
}
