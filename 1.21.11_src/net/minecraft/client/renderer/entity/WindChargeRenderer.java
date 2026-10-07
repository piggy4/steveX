package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.projectile.WindChargeModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;

@Environment(EnvType.CLIENT)
public class WindChargeRenderer extends EntityRenderer<AbstractWindCharge, EntityRenderState> {
	private static final Identifier TEXTURE_LOCATION = Identifier.withDefaultNamespace("textures/entity/projectiles/wind_charge.png");
	private final WindChargeModel model;

	public WindChargeRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new WindChargeModel(context.bakeLayer(ModelLayers.WIND_CHARGE));
	}

	@Override
	public void submit(EntityRenderState entityRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		submitNodeCollector.submitModel(
			this.model,
			entityRenderState,
			poseStack,
			RenderTypes.breezeWind(TEXTURE_LOCATION, this.xOffset(entityRenderState.ageInTicks) % 1.0F, 0.0F),
			entityRenderState.lightCoords,
			OverlayTexture.NO_OVERLAY,
			entityRenderState.outlineColor,
			null
		);
		super.submit(entityRenderState, poseStack, submitNodeCollector, cameraRenderState);
	}

	protected float xOffset(float f) {
		return f * 0.03F;
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}
}
