package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.endermite.EndermiteModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.Endermite;

@Environment(EnvType.CLIENT)
public class EndermiteRenderer extends MobRenderer<Endermite, LivingEntityRenderState, EndermiteModel> {
	private static final Identifier ENDERMITE_LOCATION = Identifier.withDefaultNamespace("textures/entity/endermite.png");

	public EndermiteRenderer(EntityRendererProvider.Context context) {
		super(context, new EndermiteModel(context.bakeLayer(ModelLayers.ENDERMITE)), 0.3F);
	}

	@Override
	protected float getFlipDegrees() {
		return 180.0F;
	}

	@Override
	public Identifier getTextureLocation(LivingEntityRenderState livingEntityRenderState) {
		return ENDERMITE_LOCATION;
	}

	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}
}
