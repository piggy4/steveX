package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.nautilus.NautilusArmorModel;
import net.minecraft.client.model.animal.nautilus.NautilusModel;
import net.minecraft.client.model.animal.nautilus.NautilusSaddleModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.SimpleEquipmentLayer;
import net.minecraft.client.renderer.entity.state.NautilusRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;

@Environment(EnvType.CLIENT)
public class NautilusRenderer<T extends AbstractNautilus> extends AgeableMobRenderer<T, NautilusRenderState, NautilusModel> {
	private static final Identifier NAUTILUS_LOCATION = Identifier.withDefaultNamespace("textures/entity/nautilus/nautilus.png");
	private static final Identifier NAUTILUS_BABY_LOCATION = Identifier.withDefaultNamespace("textures/entity/nautilus/nautilus_baby.png");

	public NautilusRenderer(EntityRendererProvider.Context context) {
		super(context, new NautilusModel(context.bakeLayer(ModelLayers.NAUTILUS)), new NautilusModel(context.bakeLayer(ModelLayers.NAUTILUS_BABY)), 0.7F);
		this.addLayer(
			new SimpleEquipmentLayer<>(
				this,
				context.getEquipmentRenderer(),
				EquipmentClientInfo.LayerType.NAUTILUS_BODY,
				nautilusRenderState -> nautilusRenderState.bodyArmorItem,
				new NautilusArmorModel(context.bakeLayer(ModelLayers.NAUTILUS_ARMOR)),
				null
			)
		);
		this.addLayer(
			new SimpleEquipmentLayer<>(
				this,
				context.getEquipmentRenderer(),
				EquipmentClientInfo.LayerType.NAUTILUS_SADDLE,
				nautilusRenderState -> nautilusRenderState.saddle,
				new NautilusSaddleModel(context.bakeLayer(ModelLayers.NAUTILUS_SADDLE)),
				null
			)
		);
	}

	public Identifier getTextureLocation(NautilusRenderState nautilusRenderState) {
		return nautilusRenderState.isBaby ? NAUTILUS_BABY_LOCATION : NAUTILUS_LOCATION;
	}

	public NautilusRenderState createRenderState() {
		return new NautilusRenderState();
	}

	public void extractRenderState(T abstractNautilus, NautilusRenderState nautilusRenderState, float f) {
		super.extractRenderState(abstractNautilus, nautilusRenderState, f);
		nautilusRenderState.saddle = abstractNautilus.getItemBySlot(EquipmentSlot.SADDLE).copy();
		nautilusRenderState.bodyArmorItem = abstractNautilus.getBodyArmorItem().copy();
	}
}
