package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.golem.SnowGolemModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.SnowGolemHeadLayer;
import net.minecraft.client.renderer.entity.state.SnowGolemRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.golem.SnowGolem;

@Environment(EnvType.CLIENT)
public class SnowGolemRenderer extends MobRenderer<SnowGolem, SnowGolemRenderState, SnowGolemModel> {
	private static final Identifier SNOW_GOLEM_LOCATION = Identifier.withDefaultNamespace("textures/entity/snow_golem.png");

	public SnowGolemRenderer(EntityRendererProvider.Context context) {
		super(context, new SnowGolemModel(context.bakeLayer(ModelLayers.SNOW_GOLEM)), 0.5F);
		this.addLayer(new SnowGolemHeadLayer(this, context.getBlockRenderDispatcher()));
	}

	public Identifier getTextureLocation(SnowGolemRenderState snowGolemRenderState) {
		return SNOW_GOLEM_LOCATION;
	}

	public SnowGolemRenderState createRenderState() {
		return new SnowGolemRenderState();
	}

	public void extractRenderState(SnowGolem snowGolem, SnowGolemRenderState snowGolemRenderState, float f) {
		super.extractRenderState(snowGolem, snowGolemRenderState, f);
		snowGolemRenderState.hasPumpkin = snowGolem.hasPumpkin();
	}
}
