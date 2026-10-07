package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public abstract class StuckInBodyLayer<M extends PlayerModel, S> extends RenderLayer<AvatarRenderState, M> {
	private final Model<S> model;
	private final S modelState;
	private final Identifier texture;
	private final StuckInBodyLayer.PlacementStyle placementStyle;

	public StuckInBodyLayer(
		LivingEntityRenderer<?, AvatarRenderState, M> livingEntityRenderer,
		Model<S> model,
		S object,
		Identifier identifier,
		StuckInBodyLayer.PlacementStyle placementStyle
	) {
		super(livingEntityRenderer);
		this.model = model;
		this.modelState = object;
		this.texture = identifier;
		this.placementStyle = placementStyle;
	}

	protected abstract int numStuck(AvatarRenderState avatarRenderState);

	private void submitStuckItem(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, float f, float g, float h, int j) {
		float k = Mth.sqrt(f * f + h * h);
		float l = (float)(Math.atan2(f, h) * 180.0F / (float)Math.PI);
		float m = (float)(Math.atan2(g, k) * 180.0F / (float)Math.PI);
		poseStack.mulPose(Axis.YP.rotationDegrees(l - 90.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(m));
		submitNodeCollector.submitModel(this.model, this.modelState, poseStack, this.model.renderType(this.texture), i, OverlayTexture.NO_OVERLAY, j, null);
	}

	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, AvatarRenderState avatarRenderState, float f, float g) {
		int j = this.numStuck(avatarRenderState);
		if (j > 0) {
			RandomSource randomSource = RandomSource.create(avatarRenderState.id);

			for (int k = 0; k < j; k++) {
				poseStack.pushPose();
				ModelPart modelPart = this.getParentModel().getRandomBodyPart(randomSource);
				ModelPart.Cube cube = modelPart.getRandomCube(randomSource);
				modelPart.translateAndRotate(poseStack);
				float h = randomSource.nextFloat();
				float l = randomSource.nextFloat();
				float m = randomSource.nextFloat();
				if (this.placementStyle == StuckInBodyLayer.PlacementStyle.ON_SURFACE) {
					int n = randomSource.nextInt(3);
					switch (n) {
						case 0:
							h = snapToFace(h);
							break;
						case 1:
							l = snapToFace(l);
							break;
						default:
							m = snapToFace(m);
					}
				}

				poseStack.translate(Mth.lerp(h, cube.minX, cube.maxX) / 16.0F, Mth.lerp(l, cube.minY, cube.maxY) / 16.0F, Mth.lerp(m, cube.minZ, cube.maxZ) / 16.0F);
				this.submitStuckItem(poseStack, submitNodeCollector, i, -(h * 2.0F - 1.0F), -(l * 2.0F - 1.0F), -(m * 2.0F - 1.0F), avatarRenderState.outlineColor);
				poseStack.popPose();
			}
		}
	}

	private static float snapToFace(float f) {
		return f > 0.5F ? 1.0F : 0.5F;
	}

	@Environment(EnvType.CLIENT)
	public enum PlacementStyle {
		IN_CUBE,
		ON_SURFACE;
	}
}
