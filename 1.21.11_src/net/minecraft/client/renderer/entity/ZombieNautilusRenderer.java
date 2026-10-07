package net.minecraft.client.renderer.entity;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.nautilus.NautilusArmorModel;
import net.minecraft.client.model.animal.nautilus.NautilusModel;
import net.minecraft.client.model.animal.nautilus.NautilusSaddleModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.nautilus.ZombieNautilusCoralModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.SimpleEquipmentLayer;
import net.minecraft.client.renderer.entity.state.NautilusRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilus;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilusVariant;

@Environment(EnvType.CLIENT)
public class ZombieNautilusRenderer extends MobRenderer<ZombieNautilus, NautilusRenderState, NautilusModel> {
	private final Map<ZombieNautilusVariant.ModelType, NautilusModel> models;

	public ZombieNautilusRenderer(EntityRendererProvider.Context context) {
		super(context, new NautilusModel(context.bakeLayer(ModelLayers.ZOMBIE_NAUTILUS)), 0.7F);
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
		this.models = bakeModels(context);
	}

	private static Map<ZombieNautilusVariant.ModelType, NautilusModel> bakeModels(EntityRendererProvider.Context context) {
		return Maps.newEnumMap(
			Map.of(
				ZombieNautilusVariant.ModelType.NORMAL,
				new NautilusModel(context.bakeLayer(ModelLayers.ZOMBIE_NAUTILUS)),
				ZombieNautilusVariant.ModelType.WARM,
				new ZombieNautilusCoralModel(context.bakeLayer(ModelLayers.ZOMBIE_NAUTILUS_CORAL))
			)
		);
	}

	public void submit(NautilusRenderState nautilusRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		if (nautilusRenderState.variant != null) {
			this.model = this.models.get(nautilusRenderState.variant.modelAndTexture().model());
			super.submit(nautilusRenderState, poseStack, submitNodeCollector, cameraRenderState);
		}
	}

	public Identifier getTextureLocation(NautilusRenderState nautilusRenderState) {
		return nautilusRenderState.variant == null ? MissingTextureAtlasSprite.getLocation() : nautilusRenderState.variant.modelAndTexture().asset().texturePath();
	}

	public NautilusRenderState createRenderState() {
		return new NautilusRenderState();
	}

	public void extractRenderState(ZombieNautilus zombieNautilus, NautilusRenderState nautilusRenderState, float f) {
		super.extractRenderState(zombieNautilus, nautilusRenderState, f);
		nautilusRenderState.saddle = zombieNautilus.getItemBySlot(EquipmentSlot.SADDLE).copy();
		nautilusRenderState.bodyArmorItem = zombieNautilus.getBodyArmorItem().copy();
		nautilusRenderState.variant = zombieNautilus.getVariant().value();
	}
}
