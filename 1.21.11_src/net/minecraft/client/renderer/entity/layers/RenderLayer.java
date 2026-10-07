package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public abstract class RenderLayer<S extends EntityRenderState, M extends EntityModel<? super S>> {
	private final RenderLayerParent<S, M> renderer;

	public RenderLayer(RenderLayerParent<S, M> renderLayerParent) {
		this.renderer = renderLayerParent;
	}

	protected static <S extends LivingEntityRenderState> void coloredCutoutModelCopyLayerRender(
		Model<? super S> model, Identifier identifier, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, S livingEntityRenderState, int j, int k
	) {
		if (!livingEntityRenderState.isInvisible) {
			renderColoredCutoutModel(model, identifier, poseStack, submitNodeCollector, i, livingEntityRenderState, j, k);
		}
	}

	protected static <S extends LivingEntityRenderState> void renderColoredCutoutModel(
		Model<? super S> model, Identifier identifier, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, S livingEntityRenderState, int j, int k
	) {
		submitNodeCollector.order(k)
			.submitModel(
				model,
				livingEntityRenderState,
				poseStack,
				RenderTypes.entityCutoutNoCull(identifier),
				i,
				LivingEntityRenderer.getOverlayCoords(livingEntityRenderState, 0.0F),
				j,
				null,
				livingEntityRenderState.outlineColor,
				null
			);
	}

	public M getParentModel() {
		return this.renderer.getModel();
	}

	public abstract void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, S entityRenderState, float f, float g);
}
