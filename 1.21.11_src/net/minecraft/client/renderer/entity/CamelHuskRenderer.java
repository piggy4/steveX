package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.camel.CamelModel;
import net.minecraft.client.model.animal.camel.CamelSaddleModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.SimpleEquipmentLayer;
import net.minecraft.client.renderer.entity.state.CamelRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public class CamelHuskRenderer extends CamelRenderer {
	private static final Identifier CAMEL_HUSK_LOCATION = Identifier.withDefaultNamespace("textures/entity/camel/camel_husk.png");

	public CamelHuskRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected SimpleEquipmentLayer<CamelRenderState, CamelModel, CamelSaddleModel> createCamelSaddleLayer(EntityRendererProvider.Context context) {
		return new SimpleEquipmentLayer<>(
			this,
			context.getEquipmentRenderer(),
			EquipmentClientInfo.LayerType.CAMEL_HUSK_SADDLE,
			camelRenderState -> camelRenderState.saddle,
			new CamelSaddleModel(context.bakeLayer(ModelLayers.CAMEL_HUSK_SADDLE)),
			new CamelSaddleModel(context.bakeLayer(ModelLayers.CAMEL_HUSK_BABY_SADDLE))
		);
	}

	@Override
	public Identifier getTextureLocation(CamelRenderState camelRenderState) {
		return CAMEL_HUSK_LOCATION;
	}
}
