package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.boat.BoatModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

@Environment(EnvType.CLIENT)
public class BoatRenderer extends AbstractBoatRenderer {
	private final Model.Simple waterPatchModel;
	private final Identifier texture;
	private final EntityModel<BoatRenderState> model;

	public BoatRenderer(EntityRendererProvider.Context context, ModelLayerLocation modelLayerLocation) {
		super(context);
		this.texture = modelLayerLocation.model().withPath(string -> "textures/entity/" + string + ".png");
		this.waterPatchModel = new Model.Simple(context.bakeLayer(ModelLayers.BOAT_WATER_PATCH), identifier -> RenderTypes.waterMask());
		this.model = new BoatModel(context.bakeLayer(modelLayerLocation));
	}

	@Override
	protected EntityModel<BoatRenderState> model() {
		return this.model;
	}

	@Override
	protected RenderType renderType() {
		return this.model.renderType(this.texture);
	}

	@Override
	protected void submitTypeAdditions(BoatRenderState boatRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i) {
		if (!boatRenderState.isUnderWater) {
			submitNodeCollector.submitModel(
				this.waterPatchModel,
				Unit.INSTANCE,
				poseStack,
				this.waterPatchModel.renderType(this.texture),
				i,
				OverlayTexture.NO_OVERLAY,
				boatRenderState.outlineColor,
				null
			);
		}
	}
}
