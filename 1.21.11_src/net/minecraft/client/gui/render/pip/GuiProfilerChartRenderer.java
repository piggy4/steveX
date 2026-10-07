package net.minecraft.client.gui.render.pip;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.render.state.pip.GuiProfilerChartRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ResultField;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class GuiProfilerChartRenderer extends PictureInPictureRenderer<GuiProfilerChartRenderState> {
	public GuiProfilerChartRenderer(MultiBufferSource.BufferSource bufferSource) {
		super(bufferSource);
	}

	@Override
	public Class<GuiProfilerChartRenderState> getRenderStateClass() {
		return GuiProfilerChartRenderState.class;
	}

	protected void renderToTexture(GuiProfilerChartRenderState guiProfilerChartRenderState, PoseStack poseStack) {
		double d = 0.0;
		poseStack.translate(0.0F, -5.0F, 0.0F);
		Matrix4f matrix4f = poseStack.last().pose();

		for (ResultField resultField : guiProfilerChartRenderState.chartData()) {
			int i = Mth.floor(resultField.percentage / 4.0) + 1;
			VertexConsumer vertexConsumer = this.bufferSource.getBuffer(RenderTypes.debugTriangleFan());
			int j = ARGB.opaque(resultField.getColor());
			int k = ARGB.multiply(j, -8355712);
			vertexConsumer.addVertex(matrix4f, 0.0F, 0.0F, 0.0F).setColor(j);

			for (int l = i; l >= 0; l--) {
				float f = (float)((d + resultField.percentage * l / i) * (float) (Math.PI * 2) / 100.0);
				float g = Mth.sin(f) * 105.0F;
				float h = Mth.cos(f) * 105.0F * 0.5F;
				vertexConsumer.addVertex(matrix4f, g, h, 0.0F).setColor(j);
			}

			vertexConsumer = this.bufferSource.getBuffer(RenderTypes.debugQuads());

			for (int l = i; l > 0; l--) {
				float f = (float)((d + resultField.percentage * l / i) * (float) (Math.PI * 2) / 100.0);
				float g = Mth.sin(f) * 105.0F;
				float h = Mth.cos(f) * 105.0F * 0.5F;
				float m = (float)((d + resultField.percentage * (l - 1) / i) * (float) (Math.PI * 2) / 100.0);
				float n = Mth.sin(m) * 105.0F;
				float o = Mth.cos(m) * 105.0F * 0.5F;
				if (!((h + o) / 2.0F < 0.0F)) {
					vertexConsumer.addVertex(matrix4f, g, h, 0.0F).setColor(k);
					vertexConsumer.addVertex(matrix4f, g, h + 10.0F, 0.0F).setColor(k);
					vertexConsumer.addVertex(matrix4f, n, o + 10.0F, 0.0F).setColor(k);
					vertexConsumer.addVertex(matrix4f, n, o, 0.0F).setColor(k);
				}
			}

			d += resultField.percentage;
		}
	}

	@Override
	protected float getTranslateY(int i, int j) {
		return i / 2.0F;
	}

	@Override
	protected String getTextureLabel() {
		return "profiler chart";
	}
}
