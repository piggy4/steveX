package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.polarbear.PolarBearModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.PolarBearRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.polarbear.PolarBear;

@Environment(EnvType.CLIENT)
public class PolarBearRenderer extends AgeableMobRenderer<PolarBear, PolarBearRenderState, PolarBearModel> {
	private static final Identifier BEAR_LOCATION = Identifier.withDefaultNamespace("textures/entity/bear/polarbear.png");

	public PolarBearRenderer(EntityRendererProvider.Context context) {
		super(context, new PolarBearModel(context.bakeLayer(ModelLayers.POLAR_BEAR)), new PolarBearModel(context.bakeLayer(ModelLayers.POLAR_BEAR_BABY)), 0.9F);
	}

	public Identifier getTextureLocation(PolarBearRenderState polarBearRenderState) {
		return BEAR_LOCATION;
	}

	public PolarBearRenderState createRenderState() {
		return new PolarBearRenderState();
	}

	public void extractRenderState(PolarBear polarBear, PolarBearRenderState polarBearRenderState, float f) {
		super.extractRenderState(polarBear, polarBearRenderState, f);
		polarBearRenderState.standScale = polarBear.getStandingAnimationScale(f);
	}
}
