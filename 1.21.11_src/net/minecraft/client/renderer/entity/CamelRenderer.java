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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.camel.Camel;

@Environment(EnvType.CLIENT)
public class CamelRenderer extends AgeableMobRenderer<Camel, CamelRenderState, CamelModel> {
	private static final Identifier CAMEL_LOCATION = Identifier.withDefaultNamespace("textures/entity/camel/camel.png");

	public CamelRenderer(EntityRendererProvider.Context context) {
		super(context, new CamelModel(context.bakeLayer(ModelLayers.CAMEL)), new CamelModel(context.bakeLayer(ModelLayers.CAMEL_BABY)), 0.7F);
		this.addLayer(this.createCamelSaddleLayer(context));
	}

	protected SimpleEquipmentLayer<CamelRenderState, CamelModel, CamelSaddleModel> createCamelSaddleLayer(EntityRendererProvider.Context context) {
		return new SimpleEquipmentLayer<>(
			this,
			context.getEquipmentRenderer(),
			EquipmentClientInfo.LayerType.CAMEL_SADDLE,
			camelRenderState -> camelRenderState.saddle,
			new CamelSaddleModel(context.bakeLayer(ModelLayers.CAMEL_SADDLE)),
			new CamelSaddleModel(context.bakeLayer(ModelLayers.CAMEL_BABY_SADDLE))
		);
	}

	public Identifier getTextureLocation(CamelRenderState camelRenderState) {
		return CAMEL_LOCATION;
	}

	public CamelRenderState createRenderState() {
		return new CamelRenderState();
	}

	public void extractRenderState(Camel camel, CamelRenderState camelRenderState, float f) {
		super.extractRenderState(camel, camelRenderState, f);
		camelRenderState.saddle = camel.getItemBySlot(EquipmentSlot.SADDLE).copy();
		camelRenderState.isRidden = camel.isVehicle();
		camelRenderState.jumpCooldown = Math.max(camel.getJumpCooldown() - f, 0.0F);
		camelRenderState.sitAnimationState.copyFrom(camel.sitAnimationState);
		camelRenderState.sitPoseAnimationState.copyFrom(camel.sitPoseAnimationState);
		camelRenderState.sitUpAnimationState.copyFrom(camel.sitUpAnimationState);
		camelRenderState.idleAnimationState.copyFrom(camel.idleAnimationState);
		camelRenderState.dashAnimationState.copyFrom(camel.dashAnimationState);
	}
}
