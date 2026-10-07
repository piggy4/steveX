package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.ghast.HappyGhastHarnessModel;
import net.minecraft.client.model.animal.ghast.HappyGhastModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.RopesLayer;
import net.minecraft.client.renderer.entity.layers.SimpleEquipmentLayer;
import net.minecraft.client.renderer.entity.state.HappyGhastRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.phys.AABB;

@Environment(EnvType.CLIENT)
public class HappyGhastRenderer extends AgeableMobRenderer<HappyGhast, HappyGhastRenderState, HappyGhastModel> {
	private static final Identifier GHAST_LOCATION = Identifier.withDefaultNamespace("textures/entity/ghast/happy_ghast.png");
	private static final Identifier GHAST_BABY_LOCATION = Identifier.withDefaultNamespace("textures/entity/ghast/happy_ghast_baby.png");
	private static final Identifier GHAST_ROPES = Identifier.withDefaultNamespace("textures/entity/ghast/happy_ghast_ropes.png");

	public HappyGhastRenderer(EntityRendererProvider.Context context) {
		super(context, new HappyGhastModel(context.bakeLayer(ModelLayers.HAPPY_GHAST)), new HappyGhastModel(context.bakeLayer(ModelLayers.HAPPY_GHAST_BABY)), 2.0F);
		this.addLayer(
			new SimpleEquipmentLayer<>(
				this,
				context.getEquipmentRenderer(),
				EquipmentClientInfo.LayerType.HAPPY_GHAST_BODY,
				happyGhastRenderState -> happyGhastRenderState.bodyItem,
				new HappyGhastHarnessModel(context.bakeLayer(ModelLayers.HAPPY_GHAST_HARNESS)),
				new HappyGhastHarnessModel(context.bakeLayer(ModelLayers.HAPPY_GHAST_BABY_HARNESS))
			)
		);
		this.addLayer(new RopesLayer<>(this, context.getModelSet(), GHAST_ROPES));
	}

	public Identifier getTextureLocation(HappyGhastRenderState happyGhastRenderState) {
		return happyGhastRenderState.isBaby ? GHAST_BABY_LOCATION : GHAST_LOCATION;
	}

	public HappyGhastRenderState createRenderState() {
		return new HappyGhastRenderState();
	}

	protected AABB getBoundingBoxForCulling(HappyGhast happyGhast) {
		AABB aABB = super.getBoundingBoxForCulling(happyGhast);
		float f = happyGhast.getBbHeight();
		return aABB.setMinY(aABB.minY - f / 2.0F);
	}

	public void extractRenderState(HappyGhast happyGhast, HappyGhastRenderState happyGhastRenderState, float f) {
		super.extractRenderState(happyGhast, happyGhastRenderState, f);
		happyGhastRenderState.bodyItem = happyGhast.getItemBySlot(EquipmentSlot.BODY).copy();
		happyGhastRenderState.isRidden = happyGhast.isVehicle();
		happyGhastRenderState.isLeashHolder = happyGhast.isLeashHolder();
	}
}
