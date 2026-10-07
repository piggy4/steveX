package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.creaking.CreakingModel;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.entity.state.CreakingRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.creaking.Creaking;

@Environment(EnvType.CLIENT)
public class CreakingRenderer<T extends Creaking> extends MobRenderer<T, CreakingRenderState, CreakingModel> {
	private static final Identifier TEXTURE_LOCATION = Identifier.withDefaultNamespace("textures/entity/creaking/creaking.png");
	private static final Identifier EYES_TEXTURE_LOCATION = Identifier.withDefaultNamespace("textures/entity/creaking/creaking_eyes.png");

	public CreakingRenderer(EntityRendererProvider.Context context) {
		super(context, new CreakingModel(context.bakeLayer(ModelLayers.CREAKING)), 0.6F);
		this.addLayer(
			new LivingEntityEmissiveLayer<>(
				this,
				creakingRenderState -> EYES_TEXTURE_LOCATION,
				(creakingRenderState, f) -> creakingRenderState.eyesGlowing ? 1.0F : 0.0F,
				new CreakingModel(context.bakeLayer(ModelLayers.CREAKING_EYES)),
				RenderTypes::eyes,
				true
			)
		);
	}

	public Identifier getTextureLocation(CreakingRenderState creakingRenderState) {
		return TEXTURE_LOCATION;
	}

	public CreakingRenderState createRenderState() {
		return new CreakingRenderState();
	}

	public void extractRenderState(T creaking, CreakingRenderState creakingRenderState, float f) {
		super.extractRenderState(creaking, creakingRenderState, f);
		creakingRenderState.attackAnimationState.copyFrom(creaking.attackAnimationState);
		creakingRenderState.invulnerabilityAnimationState.copyFrom(creaking.invulnerabilityAnimationState);
		creakingRenderState.deathAnimationState.copyFrom(creaking.deathAnimationState);
		if (creaking.isTearingDown()) {
			creakingRenderState.deathTime = 0.0F;
			creakingRenderState.hasRedOverlay = false;
			creakingRenderState.eyesGlowing = creaking.hasGlowingEyes();
		} else {
			creakingRenderState.eyesGlowing = creaking.isActive();
		}

		creakingRenderState.canMove = creaking.canMove();
	}
}
