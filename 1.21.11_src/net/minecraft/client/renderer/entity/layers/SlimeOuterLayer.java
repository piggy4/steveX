package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.slime.SlimeModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;

@Environment(EnvType.CLIENT)
public class SlimeOuterLayer extends RenderLayer<SlimeRenderState, SlimeModel> {
	private final SlimeModel model;

	public SlimeOuterLayer(RenderLayerParent<SlimeRenderState, SlimeModel> renderLayerParent, EntityModelSet entityModelSet) {
		super(renderLayerParent);
		this.model = new SlimeModel(entityModelSet.bakeLayer(ModelLayers.SLIME_OUTER));
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, SlimeRenderState slimeRenderState, float f, float g) {
		boolean bl = slimeRenderState.appearsGlowing() && slimeRenderState.isInvisible;
		if (!slimeRenderState.isInvisible || bl) {
			int j = LivingEntityRenderer.getOverlayCoords(slimeRenderState, 0.0F);
			if (bl) {
				submitNodeCollector.order(1)
					.submitModel(
						this.model, slimeRenderState, poseStack, RenderTypes.outline(SlimeRenderer.SLIME_LOCATION), i, j, -1, null, slimeRenderState.outlineColor, null
					);
			} else {
				submitNodeCollector.order(1)
					.submitModel(
						this.model, slimeRenderState, poseStack, RenderTypes.entityTranslucent(SlimeRenderer.SLIME_LOCATION), i, j, -1, null, slimeRenderState.outlineColor, null
					);
			}
		}
	}
}
