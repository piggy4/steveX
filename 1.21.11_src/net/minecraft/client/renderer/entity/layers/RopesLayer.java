package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.ghast.HappyGhastModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HappyGhastRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;

@Environment(EnvType.CLIENT)
public class RopesLayer<M extends HappyGhastModel> extends RenderLayer<HappyGhastRenderState, M> {
	private final RenderType ropes;
	private final HappyGhastModel adultModel;
	private final HappyGhastModel babyModel;

	public RopesLayer(RenderLayerParent<HappyGhastRenderState, M> renderLayerParent, EntityModelSet entityModelSet, Identifier identifier) {
		super(renderLayerParent);
		this.ropes = RenderTypes.entityCutoutNoCull(identifier);
		this.adultModel = new HappyGhastModel(entityModelSet.bakeLayer(ModelLayers.HAPPY_GHAST_ROPES));
		this.babyModel = new HappyGhastModel(entityModelSet.bakeLayer(ModelLayers.HAPPY_GHAST_BABY_ROPES));
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, HappyGhastRenderState happyGhastRenderState, float f, float g) {
		if (happyGhastRenderState.isLeashHolder && happyGhastRenderState.bodyItem.is(ItemTags.HARNESSES)) {
			HappyGhastModel happyGhastModel = happyGhastRenderState.isBaby ? this.babyModel : this.adultModel;
			submitNodeCollector.submitModel(
				happyGhastModel, happyGhastRenderState, poseStack, this.ropes, i, OverlayTexture.NO_OVERLAY, happyGhastRenderState.outlineColor, null
			);
		}
	}
}
