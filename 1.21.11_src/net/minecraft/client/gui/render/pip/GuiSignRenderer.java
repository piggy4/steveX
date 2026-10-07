package net.minecraft.client.gui.render.pip;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.state.pip.GuiSignRenderState;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;

@Environment(EnvType.CLIENT)
public class GuiSignRenderer extends PictureInPictureRenderer<GuiSignRenderState> {
	private final MaterialSet materials;

	public GuiSignRenderer(MultiBufferSource.BufferSource bufferSource, MaterialSet materialSet) {
		super(bufferSource);
		this.materials = materialSet;
	}

	@Override
	public Class<GuiSignRenderState> getRenderStateClass() {
		return GuiSignRenderState.class;
	}

	protected void renderToTexture(GuiSignRenderState guiSignRenderState, PoseStack poseStack) {
		Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_FLAT);
		poseStack.translate(0.0F, -0.75F, 0.0F);
		Material material = Sheets.getSignMaterial(guiSignRenderState.woodType());
		Model.Simple simple = guiSignRenderState.signModel();
		VertexConsumer vertexConsumer = material.buffer(this.materials, this.bufferSource, simple::renderType);
		simple.renderToBuffer(poseStack, vertexConsumer, 15728880, OverlayTexture.NO_OVERLAY);
	}

	@Override
	protected String getTextureLabel() {
		return "sign";
	}
}
