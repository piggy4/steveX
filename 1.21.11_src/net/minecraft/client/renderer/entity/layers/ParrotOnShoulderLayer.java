package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ParrotRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.animal.parrot.Parrot;

@Environment(EnvType.CLIENT)
public class ParrotOnShoulderLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private final ParrotModel model;

	public ParrotOnShoulderLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderLayerParent, EntityModelSet entityModelSet) {
		super(renderLayerParent);
		this.model = new ParrotModel(entityModelSet.bakeLayer(ModelLayers.PARROT));
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, AvatarRenderState avatarRenderState, float f, float g) {
		Parrot.Variant variant = avatarRenderState.parrotOnLeftShoulder;
		if (variant != null) {
			this.submitOnShoulder(poseStack, submitNodeCollector, i, avatarRenderState, variant, f, g, true);
		}

		Parrot.Variant variant2 = avatarRenderState.parrotOnRightShoulder;
		if (variant2 != null) {
			this.submitOnShoulder(poseStack, submitNodeCollector, i, avatarRenderState, variant2, f, g, false);
		}
	}

	private void submitOnShoulder(
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		int i,
		AvatarRenderState avatarRenderState,
		Parrot.Variant variant,
		float f,
		float g,
		boolean bl
	) {
		poseStack.pushPose();
		poseStack.translate(bl ? 0.4F : -0.4F, avatarRenderState.isCrouching ? -1.3F : -1.5F, 0.0F);
		ParrotRenderState parrotRenderState = new ParrotRenderState();
		parrotRenderState.pose = ParrotModel.Pose.ON_SHOULDER;
		parrotRenderState.ageInTicks = avatarRenderState.ageInTicks;
		parrotRenderState.walkAnimationPos = avatarRenderState.walkAnimationPos;
		parrotRenderState.walkAnimationSpeed = avatarRenderState.walkAnimationSpeed;
		parrotRenderState.yRot = f;
		parrotRenderState.xRot = g;
		submitNodeCollector.submitModel(
			this.model,
			parrotRenderState,
			poseStack,
			this.model.renderType(ParrotRenderer.getVariantTexture(variant)),
			i,
			OverlayTexture.NO_OVERLAY,
			avatarRenderState.outlineColor,
			null
		);
		poseStack.popPose();
	}
}
