package net.minecraft.client.gui.render.pip;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.state.pip.GuiBookModelRenderState;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public class GuiBookModelRenderer extends PictureInPictureRenderer<GuiBookModelRenderState> {
	public GuiBookModelRenderer(MultiBufferSource.BufferSource bufferSource) {
		super(bufferSource);
	}

	@Override
	public Class<GuiBookModelRenderState> getRenderStateClass() {
		return GuiBookModelRenderState.class;
	}

	protected void renderToTexture(GuiBookModelRenderState guiBookModelRenderState, PoseStack poseStack) {
		Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
		poseStack.mulPose(Axis.XP.rotationDegrees(25.0F));
		float f = guiBookModelRenderState.open();
		poseStack.translate((1.0F - f) * 0.2F, (1.0F - f) * 0.1F, (1.0F - f) * 0.25F);
		poseStack.mulPose(Axis.YP.rotationDegrees(-(1.0F - f) * 90.0F - 90.0F));
		poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
		float g = guiBookModelRenderState.flip();
		float h = Mth.clamp(Mth.frac(g + 0.25F) * 1.6F - 0.3F, 0.0F, 1.0F);
		float i = Mth.clamp(Mth.frac(g + 0.75F) * 1.6F - 0.3F, 0.0F, 1.0F);
		BookModel bookModel = guiBookModelRenderState.bookModel();
		bookModel.setupAnim(new BookModel.State(0.0F, h, i, f));
		Identifier identifier = guiBookModelRenderState.texture();
		VertexConsumer vertexConsumer = this.bufferSource.getBuffer(bookModel.renderType(identifier));
		bookModel.renderToBuffer(poseStack, vertexConsumer, 15728880, OverlayTexture.NO_OVERLAY);
	}

	@Override
	protected float getTranslateY(int i, int j) {
		return 17 * j;
	}

	@Override
	protected String getTextureLabel() {
		return "book model";
	}
}
