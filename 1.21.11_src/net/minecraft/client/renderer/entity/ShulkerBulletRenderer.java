package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.projectile.ShulkerBulletModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ShulkerBulletRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.ShulkerBullet;

@Environment(EnvType.CLIENT)
public class ShulkerBulletRenderer extends EntityRenderer<ShulkerBullet, ShulkerBulletRenderState> {
	private static final Identifier TEXTURE_LOCATION = Identifier.withDefaultNamespace("textures/entity/shulker/spark.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entityTranslucent(TEXTURE_LOCATION);
	private final ShulkerBulletModel model;

	public ShulkerBulletRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new ShulkerBulletModel(context.bakeLayer(ModelLayers.SHULKER_BULLET));
	}

	protected int getBlockLightLevel(ShulkerBullet shulkerBullet, BlockPos blockPos) {
		return 15;
	}

	public void submit(
		ShulkerBulletRenderState shulkerBulletRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		poseStack.pushPose();
		float f = shulkerBulletRenderState.ageInTicks;
		poseStack.translate(0.0F, 0.15F, 0.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(Mth.sin(f * 0.1F) * 180.0F));
		poseStack.mulPose(Axis.XP.rotationDegrees(Mth.cos(f * 0.1F) * 180.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(f * 0.15F) * 360.0F));
		poseStack.scale(-0.5F, -0.5F, 0.5F);
		submitNodeCollector.submitModel(
			this.model,
			shulkerBulletRenderState,
			poseStack,
			this.model.renderType(TEXTURE_LOCATION),
			shulkerBulletRenderState.lightCoords,
			OverlayTexture.NO_OVERLAY,
			shulkerBulletRenderState.outlineColor,
			null
		);
		poseStack.scale(1.5F, 1.5F, 1.5F);
		submitNodeCollector.order(1)
			.submitModel(
				this.model,
				shulkerBulletRenderState,
				poseStack,
				RENDER_TYPE,
				shulkerBulletRenderState.lightCoords,
				OverlayTexture.NO_OVERLAY,
				654311423,
				null,
				shulkerBulletRenderState.outlineColor,
				null
			);
		poseStack.popPose();
		super.submit(shulkerBulletRenderState, poseStack, submitNodeCollector, cameraRenderState);
	}

	public ShulkerBulletRenderState createRenderState() {
		return new ShulkerBulletRenderState();
	}

	public void extractRenderState(ShulkerBullet shulkerBullet, ShulkerBulletRenderState shulkerBulletRenderState, float f) {
		super.extractRenderState(shulkerBullet, shulkerBulletRenderState, f);
		shulkerBulletRenderState.yRot = shulkerBullet.getYRot(f);
		shulkerBulletRenderState.xRot = shulkerBullet.getXRot(f);
	}
}
