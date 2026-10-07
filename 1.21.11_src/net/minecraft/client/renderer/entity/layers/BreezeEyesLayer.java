package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.breeze.BreezeModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.BreezeRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public class BreezeEyesLayer extends RenderLayer<BreezeRenderState, BreezeModel> {
	private static final RenderType BREEZE_EYES = RenderTypes.breezeEyes(Identifier.withDefaultNamespace("textures/entity/breeze/breeze_eyes.png"));
	private final BreezeModel model;

	public BreezeEyesLayer(RenderLayerParent<BreezeRenderState, BreezeModel> renderLayerParent, EntityModelSet entityModelSet) {
		super(renderLayerParent);
		this.model = new BreezeModel(entityModelSet.bakeLayer(ModelLayers.BREEZE_EYES));
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, BreezeRenderState breezeRenderState, float f, float g) {
		submitNodeCollector.order(1)
			.submitModel(this.model, breezeRenderState, poseStack, BREEZE_EYES, i, OverlayTexture.NO_OVERLAY, -1, null, breezeRenderState.outlineColor, null);
	}
}
