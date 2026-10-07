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
public class BreezeWindLayer extends RenderLayer<BreezeRenderState, BreezeModel> {
	private static final Identifier TEXTURE_LOCATION = Identifier.withDefaultNamespace("textures/entity/breeze/breeze_wind.png");
	private final BreezeModel model;

	public BreezeWindLayer(RenderLayerParent<BreezeRenderState, BreezeModel> renderLayerParent, EntityModelSet entityModelSet) {
		super(renderLayerParent);
		this.model = new BreezeModel(entityModelSet.bakeLayer(ModelLayers.BREEZE_WIND));
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, BreezeRenderState breezeRenderState, float f, float g) {
		RenderType renderType = RenderTypes.breezeWind(TEXTURE_LOCATION, this.xOffset(breezeRenderState.ageInTicks) % 1.0F, 0.0F);
		submitNodeCollector.order(1)
			.submitModel(this.model, breezeRenderState, poseStack, renderType, i, OverlayTexture.NO_OVERLAY, -1, null, breezeRenderState.outlineColor, null);
	}

	private float xOffset(float f) {
		return f * 0.02F;
	}
}
