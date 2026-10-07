package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;

@Environment(EnvType.CLIENT)
public class DragonFireballRenderer extends EntityRenderer<DragonFireball, EntityRenderState> {
	private static final Identifier TEXTURE_LOCATION = Identifier.withDefaultNamespace("textures/entity/enderdragon/dragon_fireball.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entityCutoutNoCull(TEXTURE_LOCATION);

	public DragonFireballRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	protected int getBlockLightLevel(DragonFireball dragonFireball, BlockPos blockPos) {
		return 15;
	}

	@Override
	public void submit(EntityRenderState entityRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		poseStack.pushPose();
		poseStack.scale(2.0F, 2.0F, 2.0F);
		poseStack.mulPose(cameraRenderState.orientation);
		submitNodeCollector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, vertexConsumer) -> {
			vertex(vertexConsumer, pose, entityRenderState.lightCoords, 0.0F, 0, 0, 1);
			vertex(vertexConsumer, pose, entityRenderState.lightCoords, 1.0F, 0, 1, 1);
			vertex(vertexConsumer, pose, entityRenderState.lightCoords, 1.0F, 1, 1, 0);
			vertex(vertexConsumer, pose, entityRenderState.lightCoords, 0.0F, 1, 0, 0);
		});
		poseStack.popPose();
		super.submit(entityRenderState, poseStack, submitNodeCollector, cameraRenderState);
	}

	private static void vertex(VertexConsumer vertexConsumer, PoseStack.Pose pose, int i, float f, int j, int k, int l) {
		vertexConsumer.addVertex(pose, f - 0.5F, j - 0.25F, 0.0F)
			.setColor(-1)
			.setUv(k, l)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(i)
			.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}
}
