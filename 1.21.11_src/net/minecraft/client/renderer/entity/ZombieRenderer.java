package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.world.entity.monster.zombie.Zombie;

@Environment(EnvType.CLIENT)
public class ZombieRenderer extends AbstractZombieRenderer<Zombie, ZombieRenderState, ZombieModel<ZombieRenderState>> {
	public ZombieRenderer(EntityRendererProvider.Context context) {
		this(context, ModelLayers.ZOMBIE, ModelLayers.ZOMBIE_BABY, ModelLayers.ZOMBIE_ARMOR, ModelLayers.ZOMBIE_BABY_ARMOR);
	}

	public ZombieRenderState createRenderState() {
		return new ZombieRenderState();
	}

	public ZombieRenderer(
		EntityRendererProvider.Context context,
		ModelLayerLocation modelLayerLocation,
		ModelLayerLocation modelLayerLocation2,
		ArmorModelSet<ModelLayerLocation> armorModelSet,
		ArmorModelSet<ModelLayerLocation> armorModelSet2
	) {
		super(
			context,
			new ZombieModel<>(context.bakeLayer(modelLayerLocation)),
			new ZombieModel<>(context.bakeLayer(modelLayerLocation2)),
			ArmorModelSet.bake(armorModelSet, context.getModelSet(), ZombieModel::new),
			ArmorModelSet.bake(armorModelSet2, context.getModelSet(), ZombieModel::new)
		);
	}
}
