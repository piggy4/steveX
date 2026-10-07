package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.breeze.BreezeModel;
import net.minecraft.client.renderer.entity.layers.BreezeEyesLayer;
import net.minecraft.client.renderer.entity.layers.BreezeWindLayer;
import net.minecraft.client.renderer.entity.state.BreezeRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.breeze.Breeze;

@Environment(EnvType.CLIENT)
public class BreezeRenderer extends MobRenderer<Breeze, BreezeRenderState, BreezeModel> {
	private static final Identifier TEXTURE_LOCATION = Identifier.withDefaultNamespace("textures/entity/breeze/breeze.png");

	public BreezeRenderer(EntityRendererProvider.Context context) {
		super(context, new BreezeModel(context.bakeLayer(ModelLayers.BREEZE)), 0.5F);
		this.addLayer(new BreezeWindLayer(this, context.getModelSet()));
		this.addLayer(new BreezeEyesLayer(this, context.getModelSet()));
	}

	public Identifier getTextureLocation(BreezeRenderState breezeRenderState) {
		return TEXTURE_LOCATION;
	}

	public BreezeRenderState createRenderState() {
		return new BreezeRenderState();
	}

	public void extractRenderState(Breeze breeze, BreezeRenderState breezeRenderState, float f) {
		super.extractRenderState(breeze, breezeRenderState, f);
		breezeRenderState.idle.copyFrom(breeze.idle);
		breezeRenderState.shoot.copyFrom(breeze.shoot);
		breezeRenderState.slide.copyFrom(breeze.slide);
		breezeRenderState.slideBack.copyFrom(breeze.slideBack);
		breezeRenderState.inhale.copyFrom(breeze.inhale);
		breezeRenderState.longJump.copyFrom(breeze.longJump);
	}
}
