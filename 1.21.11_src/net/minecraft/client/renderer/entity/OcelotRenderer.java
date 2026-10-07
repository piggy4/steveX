package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.feline.OcelotModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.FelineRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.feline.Ocelot;

@Environment(EnvType.CLIENT)
public class OcelotRenderer extends AgeableMobRenderer<Ocelot, FelineRenderState, OcelotModel> {
	private static final Identifier CAT_OCELOT_LOCATION = Identifier.withDefaultNamespace("textures/entity/cat/ocelot.png");

	public OcelotRenderer(EntityRendererProvider.Context context) {
		super(context, new OcelotModel(context.bakeLayer(ModelLayers.OCELOT)), new OcelotModel(context.bakeLayer(ModelLayers.OCELOT_BABY)), 0.4F);
	}

	public Identifier getTextureLocation(FelineRenderState felineRenderState) {
		return CAT_OCELOT_LOCATION;
	}

	public FelineRenderState createRenderState() {
		return new FelineRenderState();
	}

	public void extractRenderState(Ocelot ocelot, FelineRenderState felineRenderState, float f) {
		super.extractRenderState(ocelot, felineRenderState, f);
		felineRenderState.isCrouching = ocelot.isCrouching();
		felineRenderState.isSprinting = ocelot.isSprinting();
	}
}
