package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public class HuskRenderer extends ZombieRenderer {
	private static final Identifier HUSK_LOCATION = Identifier.withDefaultNamespace("textures/entity/zombie/husk.png");

	public HuskRenderer(EntityRendererProvider.Context context) {
		super(context, ModelLayers.HUSK, ModelLayers.HUSK_BABY, ModelLayers.HUSK_ARMOR, ModelLayers.HUSK_BABY_ARMOR);
	}

	@Override
	public Identifier getTextureLocation(ZombieRenderState zombieRenderState) {
		return HUSK_LOCATION;
	}
}
