package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.ravager.RavagerModel;
import net.minecraft.client.renderer.entity.state.RavagerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.Ravager;

@Environment(EnvType.CLIENT)
public class RavagerRenderer extends MobRenderer<Ravager, RavagerRenderState, RavagerModel> {
	private static final Identifier TEXTURE_LOCATION = Identifier.withDefaultNamespace("textures/entity/illager/ravager.png");

	public RavagerRenderer(EntityRendererProvider.Context context) {
		super(context, new RavagerModel(context.bakeLayer(ModelLayers.RAVAGER)), 1.1F);
	}

	public Identifier getTextureLocation(RavagerRenderState ravagerRenderState) {
		return TEXTURE_LOCATION;
	}

	public RavagerRenderState createRenderState() {
		return new RavagerRenderState();
	}

	public void extractRenderState(Ravager ravager, RavagerRenderState ravagerRenderState, float f) {
		super.extractRenderState(ravager, ravagerRenderState, f);
		ravagerRenderState.stunnedTicksRemaining = ravager.getStunnedTick() > 0.0F ? ravager.getStunnedTick() - f : 0.0F;
		ravagerRenderState.attackTicksRemaining = ravager.getAttackTick() > 0.0F ? ravager.getAttackTick() - f : 0.0F;
		if (ravager.getRoarTick() > 0) {
			ravagerRenderState.roarAnimation = (20 - ravager.getRoarTick() + f) / 20.0F;
		} else {
			ravagerRenderState.roarAnimation = 0.0F;
		}
	}
}
