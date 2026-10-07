package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.object.boat.RaftModel;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public class RaftRenderer extends AbstractBoatRenderer {
	private final EntityModel<BoatRenderState> model;
	private final Identifier texture;

	public RaftRenderer(EntityRendererProvider.Context context, ModelLayerLocation modelLayerLocation) {
		super(context);
		this.texture = modelLayerLocation.model().withPath(string -> "textures/entity/" + string + ".png");
		this.model = new RaftModel(context.bakeLayer(modelLayerLocation));
	}

	@Override
	protected EntityModel<BoatRenderState> model() {
		return this.model;
	}

	@Override
	protected RenderType renderType() {
		return this.model.renderType(this.texture);
	}
}
