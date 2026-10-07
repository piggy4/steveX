package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.allay.AllayModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AllayRenderState;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.allay.Allay;

@Environment(EnvType.CLIENT)
public class AllayRenderer extends MobRenderer<Allay, AllayRenderState, AllayModel> {
	private static final Identifier ALLAY_TEXTURE = Identifier.withDefaultNamespace("textures/entity/allay/allay.png");

	public AllayRenderer(EntityRendererProvider.Context context) {
		super(context, new AllayModel(context.bakeLayer(ModelLayers.ALLAY)), 0.4F);
		this.addLayer(new ItemInHandLayer<>(this));
	}

	public Identifier getTextureLocation(AllayRenderState allayRenderState) {
		return ALLAY_TEXTURE;
	}

	public AllayRenderState createRenderState() {
		return new AllayRenderState();
	}

	public void extractRenderState(Allay allay, AllayRenderState allayRenderState, float f) {
		super.extractRenderState(allay, allayRenderState, f);
		ArmedEntityRenderState.extractArmedEntityRenderState(allay, allayRenderState, this.itemModelResolver, f);
		allayRenderState.isDancing = allay.isDancing();
		allayRenderState.isSpinning = allay.isSpinning();
		allayRenderState.spinningProgress = allay.getSpinningProgress(f);
		allayRenderState.holdingAnimationProgress = allay.getHoldingItemAnimationProgress(f);
	}

	protected int getBlockLightLevel(Allay allay, BlockPos blockPos) {
		return 15;
	}
}
