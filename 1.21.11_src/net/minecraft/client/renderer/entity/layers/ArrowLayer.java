package net.minecraft.client.renderer.entity.layers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.projectile.ArrowModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.TippableArrowRenderer;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

@Environment(EnvType.CLIENT)
public class ArrowLayer<M extends PlayerModel> extends StuckInBodyLayer<M, ArrowRenderState> {
	public ArrowLayer(LivingEntityRenderer<?, AvatarRenderState, M> livingEntityRenderer, EntityRendererProvider.Context context) {
		super(
			livingEntityRenderer,
			new ArrowModel(context.bakeLayer(ModelLayers.ARROW)),
			new ArrowRenderState(),
			TippableArrowRenderer.NORMAL_ARROW_LOCATION,
			StuckInBodyLayer.PlacementStyle.IN_CUBE
		);
	}

	@Override
	protected int numStuck(AvatarRenderState avatarRenderState) {
		return avatarRenderState.arrowCount;
	}
}
