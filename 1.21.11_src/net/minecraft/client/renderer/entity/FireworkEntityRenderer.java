package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.FireworkRocketRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemDisplayContext;

@Environment(EnvType.CLIENT)
public class FireworkEntityRenderer extends EntityRenderer<FireworkRocketEntity, FireworkRocketRenderState> {
	private final ItemModelResolver itemModelResolver;

	public FireworkEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
	}

	public void submit(
		FireworkRocketRenderState fireworkRocketRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		poseStack.pushPose();
		poseStack.mulPose(cameraRenderState.orientation);
		if (fireworkRocketRenderState.isShotAtAngle) {
			poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
			poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
			poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
		}

		fireworkRocketRenderState.item
			.submit(poseStack, submitNodeCollector, fireworkRocketRenderState.lightCoords, OverlayTexture.NO_OVERLAY, fireworkRocketRenderState.outlineColor);
		poseStack.popPose();
		super.submit(fireworkRocketRenderState, poseStack, submitNodeCollector, cameraRenderState);
	}

	public FireworkRocketRenderState createRenderState() {
		return new FireworkRocketRenderState();
	}

	public void extractRenderState(FireworkRocketEntity fireworkRocketEntity, FireworkRocketRenderState fireworkRocketRenderState, float f) {
		super.extractRenderState(fireworkRocketEntity, fireworkRocketRenderState, f);
		fireworkRocketRenderState.isShotAtAngle = fireworkRocketEntity.isShotAtAngle();
		this.itemModelResolver.updateForNonLiving(fireworkRocketRenderState.item, fireworkRocketEntity.getItem(), ItemDisplayContext.GROUND, fireworkRocketEntity);
	}
}
