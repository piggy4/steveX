package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.strider.StriderModel;
import net.minecraft.client.renderer.entity.layers.SimpleEquipmentLayer;
import net.minecraft.client.renderer.entity.state.StriderRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Strider;

@Environment(EnvType.CLIENT)
public class StriderRenderer extends AgeableMobRenderer<Strider, StriderRenderState, StriderModel> {
	private static final Identifier STRIDER_LOCATION = Identifier.withDefaultNamespace("textures/entity/strider/strider.png");
	private static final Identifier COLD_LOCATION = Identifier.withDefaultNamespace("textures/entity/strider/strider_cold.png");
	private static final float SHADOW_RADIUS = 0.5F;

	public StriderRenderer(EntityRendererProvider.Context context) {
		super(context, new StriderModel(context.bakeLayer(ModelLayers.STRIDER)), new StriderModel(context.bakeLayer(ModelLayers.STRIDER_BABY)), 0.5F);
		this.addLayer(
			new SimpleEquipmentLayer<>(
				this,
				context.getEquipmentRenderer(),
				EquipmentClientInfo.LayerType.STRIDER_SADDLE,
				striderRenderState -> striderRenderState.saddle,
				new StriderModel(context.bakeLayer(ModelLayers.STRIDER_SADDLE)),
				new StriderModel(context.bakeLayer(ModelLayers.STRIDER_BABY_SADDLE))
			)
		);
	}

	public Identifier getTextureLocation(StriderRenderState striderRenderState) {
		return striderRenderState.isSuffocating ? COLD_LOCATION : STRIDER_LOCATION;
	}

	protected float getShadowRadius(StriderRenderState striderRenderState) {
		float f = super.getShadowRadius(striderRenderState);
		return striderRenderState.isBaby ? f * 0.5F : f;
	}

	public StriderRenderState createRenderState() {
		return new StriderRenderState();
	}

	public void extractRenderState(Strider strider, StriderRenderState striderRenderState, float f) {
		super.extractRenderState(strider, striderRenderState, f);
		striderRenderState.saddle = strider.getItemBySlot(EquipmentSlot.SADDLE).copy();
		striderRenderState.isSuffocating = strider.isSuffocating();
		striderRenderState.isRidden = strider.isVehicle();
	}

	protected boolean isShaking(StriderRenderState striderRenderState) {
		return super.isShaking(striderRenderState) || striderRenderState.isSuffocating;
	}
}
