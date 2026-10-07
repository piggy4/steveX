package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class FishingHookRenderer extends EntityRenderer<FishingHook, FishingHookRenderState> {
	private static final Identifier TEXTURE_LOCATION = Identifier.withDefaultNamespace("textures/entity/fishing_hook.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entityCutout(TEXTURE_LOCATION);
	private static final double VIEW_BOBBING_SCALE = 960.0;

	public FishingHookRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	public boolean shouldRender(FishingHook fishingHook, Frustum frustum, double d, double e, double f) {
		return super.shouldRender(fishingHook, frustum, d, e, f) && fishingHook.getPlayerOwner() != null;
	}

	public void submit(
		FishingHookRenderState fishingHookRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		poseStack.pushPose();
		poseStack.pushPose();
		poseStack.scale(0.5F, 0.5F, 0.5F);
		poseStack.mulPose(cameraRenderState.orientation);
		submitNodeCollector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, vertexConsumer) -> {
			vertex(vertexConsumer, pose, fishingHookRenderState.lightCoords, 0.0F, 0, 0, 1);
			vertex(vertexConsumer, pose, fishingHookRenderState.lightCoords, 1.0F, 0, 1, 1);
			vertex(vertexConsumer, pose, fishingHookRenderState.lightCoords, 1.0F, 1, 1, 0);
			vertex(vertexConsumer, pose, fishingHookRenderState.lightCoords, 0.0F, 1, 0, 0);
		});
		poseStack.popPose();
		float f = (float)fishingHookRenderState.lineOriginOffset.x;
		float g = (float)fishingHookRenderState.lineOriginOffset.y;
		float h = (float)fishingHookRenderState.lineOriginOffset.z;
		float i = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
		submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, vertexConsumer) -> {
			int j = 16;

			for (int k = 0; k < 16; k++) {
				float l = fraction(k, 16);
				float m = fraction(k + 1, 16);
				stringVertex(f, g, h, vertexConsumer, pose, l, m, i);
				stringVertex(f, g, h, vertexConsumer, pose, m, l, i);
			}
		});
		poseStack.popPose();
		super.submit(fishingHookRenderState, poseStack, submitNodeCollector, cameraRenderState);
	}

	public static HumanoidArm getHoldingArm(Player player) {
		return player.getMainHandItem().getItem() instanceof FishingRodItem ? player.getMainArm() : player.getMainArm().getOpposite();
	}

	private Vec3 getPlayerHandPos(Player player, float f, float g) {
		int i = getHoldingArm(player) == HumanoidArm.RIGHT ? 1 : -1;
		if (this.entityRenderDispatcher.options.getCameraType().isFirstPerson() && player == Minecraft.getInstance().player) {
			double n = 960.0 / this.entityRenderDispatcher.options.fov().get().intValue();
			Vec3 vec3 = this.entityRenderDispatcher.camera.getNearPlane().getPointOnPlane(i * 0.525F, -0.1F).scale(n).yRot(f * 0.5F).xRot(-f * 0.7F);
			return player.getEyePosition(g).add(vec3);
		} else {
			float h = Mth.lerp(g, player.yBodyRotO, player.yBodyRot) * (float) (Math.PI / 180.0);
			double d = Mth.sin(h);
			double e = Mth.cos(h);
			float j = player.getScale();
			double k = i * 0.35 * j;
			double l = 0.8 * j;
			float m = player.isCrouching() ? -0.1875F : 0.0F;
			return player.getEyePosition(g).add(-e * k - d * l, m - 0.45 * j, -d * k + e * l);
		}
	}

	private static float fraction(int i, int j) {
		return (float)i / j;
	}

	private static void vertex(VertexConsumer vertexConsumer, PoseStack.Pose pose, int i, float f, int j, int k, int l) {
		vertexConsumer.addVertex(pose, f - 0.5F, j - 0.5F, 0.0F)
			.setColor(-1)
			.setUv(k, l)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(i)
			.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	private static void stringVertex(float f, float g, float h, VertexConsumer vertexConsumer, PoseStack.Pose pose, float i, float j, float k) {
		float l = f * i;
		float m = g * (i * i + i) * 0.5F + 0.25F;
		float n = h * i;
		float o = f * j - l;
		float p = g * (j * j + j) * 0.5F + 0.25F - m;
		float q = h * j - n;
		float r = Mth.sqrt(o * o + p * p + q * q);
		o /= r;
		p /= r;
		q /= r;
		vertexConsumer.addVertex(pose, l, m, n).setColor(-16777216).setNormal(pose, o, p, q).setLineWidth(k);
	}

	public FishingHookRenderState createRenderState() {
		return new FishingHookRenderState();
	}

	public void extractRenderState(FishingHook fishingHook, FishingHookRenderState fishingHookRenderState, float f) {
		super.extractRenderState(fishingHook, fishingHookRenderState, f);
		Player player = fishingHook.getPlayerOwner();
		if (player == null) {
			fishingHookRenderState.lineOriginOffset = Vec3.ZERO;
		} else {
			float g = player.getAttackAnim(f);
			float h = Mth.sin(Mth.sqrt(g) * (float) Math.PI);
			Vec3 vec3 = this.getPlayerHandPos(player, h, f);
			Vec3 vec32 = fishingHook.getPosition(f).add(0.0, 0.25, 0.0);
			fishingHookRenderState.lineOriginOffset = vec3.subtract(vec32);
		}
	}

	protected boolean affectedByCulling(FishingHook fishingHook) {
		return false;
	}
}
