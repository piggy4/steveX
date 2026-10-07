package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BeaconRenderer<T extends BlockEntity & BeaconBeamOwner> implements BlockEntityRenderer<T, BeaconRenderState> {
	public static final Identifier BEAM_LOCATION = Identifier.withDefaultNamespace("textures/entity/beacon_beam.png");
	public static final int MAX_RENDER_Y = 2048;
	private static final float BEAM_SCALE_THRESHOLD = 96.0F;
	public static final float SOLID_BEAM_RADIUS = 0.2F;
	public static final float BEAM_GLOW_RADIUS = 0.25F;

	public BeaconRenderState createRenderState() {
		return new BeaconRenderState();
	}

	public void extractRenderState(
		T blockEntity, BeaconRenderState beaconRenderState, float f, Vec3 vec3, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(blockEntity, beaconRenderState, f, vec3, crumblingOverlay);
		extract(blockEntity, beaconRenderState, f, vec3);
	}

	public static <T extends BlockEntity & BeaconBeamOwner> void extract(T blockEntity, BeaconRenderState beaconRenderState, float f, Vec3 vec3) {
		beaconRenderState.animationTime = blockEntity.getLevel() != null ? Math.floorMod(blockEntity.getLevel().getGameTime(), 40) + f : 0.0F;
		beaconRenderState.sections = blockEntity.getBeamSections()
			.stream()
			.map(section -> new BeaconRenderState.Section(section.getColor(), section.getHeight()))
			.toList();
		float g = (float)vec3.subtract(beaconRenderState.blockPos.getCenter()).horizontalDistance();
		LocalPlayer localPlayer = Minecraft.getInstance().player;
		beaconRenderState.beamRadiusScale = localPlayer != null && localPlayer.isScoping() ? 1.0F : Math.max(1.0F, g / 96.0F);
	}

	public void submit(BeaconRenderState beaconRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
		int i = 0;

		for (int j = 0; j < beaconRenderState.sections.size(); j++) {
			BeaconRenderState.Section section = beaconRenderState.sections.get(j);
			submitBeaconBeam(
				poseStack,
				submitNodeCollector,
				beaconRenderState.beamRadiusScale,
				beaconRenderState.animationTime,
				i,
				j == beaconRenderState.sections.size() - 1 ? 2048 : section.height(),
				section.color()
			);
			i += section.height();
		}
	}

	private static void submitBeaconBeam(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, float f, float g, int i, int j, int k) {
		submitBeaconBeam(poseStack, submitNodeCollector, BEAM_LOCATION, 1.0F, g, i, j, k, 0.2F * f, 0.25F * f);
	}

	public static void submitBeaconBeam(
		PoseStack poseStack, SubmitNodeCollector submitNodeCollector, Identifier identifier, float f, float g, int i, int j, int k, float h, float l
	) {
		int m = i + j;
		poseStack.pushPose();
		poseStack.translate(0.5, 0.0, 0.5);
		float n = j < 0 ? g : -g;
		float o = Mth.frac(n * 0.2F - Mth.floor(n * 0.1F));
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(g * 2.25F - 45.0F));
		float p = 0.0F;
		float q = h;
		float r = h;
		float s = 0.0F;
		float t = -h;
		float u = 0.0F;
		float v = 0.0F;
		float w = -h;
		float x = 0.0F;
		float y = 1.0F;
		float z = -1.0F + o;
		float aa = j * f * (0.5F / h) + z;
		submitNodeCollector.submitCustomGeometry(
			poseStack,
			RenderTypes.beaconBeam(identifier, false),
			(pose, vertexConsumer) -> renderPart(pose, vertexConsumer, k, i, m, 0.0F, q, r, 0.0F, t, 0.0F, 0.0F, w, 0.0F, 1.0F, aa, z)
		);
		poseStack.popPose();
		p = -l;
		q = -l;
		r = l;
		s = -l;
		t = -l;
		u = l;
		v = l;
		w = l;
		x = 0.0F;
		y = 1.0F;
		z = -1.0F + o;
		aa = j * f + z;
		submitNodeCollector.submitCustomGeometry(
			poseStack,
			RenderTypes.beaconBeam(identifier, true),
			(pose, vertexConsumer) -> renderPart(pose, vertexConsumer, ARGB.color(32, k), i, m, p, q, r, s, t, u, v, w, 0.0F, 1.0F, aa, z)
		);
		poseStack.popPose();
	}

	private static void renderPart(
		PoseStack.Pose pose,
		VertexConsumer vertexConsumer,
		int i,
		int j,
		int k,
		float f,
		float g,
		float h,
		float l,
		float m,
		float n,
		float o,
		float p,
		float q,
		float r,
		float s,
		float t
	) {
		renderQuad(pose, vertexConsumer, i, j, k, f, g, h, l, q, r, s, t);
		renderQuad(pose, vertexConsumer, i, j, k, o, p, m, n, q, r, s, t);
		renderQuad(pose, vertexConsumer, i, j, k, h, l, o, p, q, r, s, t);
		renderQuad(pose, vertexConsumer, i, j, k, m, n, f, g, q, r, s, t);
	}

	private static void renderQuad(
		PoseStack.Pose pose, VertexConsumer vertexConsumer, int i, int j, int k, float f, float g, float h, float l, float m, float n, float o, float p
	) {
		addVertex(pose, vertexConsumer, i, k, f, g, n, o);
		addVertex(pose, vertexConsumer, i, j, f, g, n, p);
		addVertex(pose, vertexConsumer, i, j, h, l, m, p);
		addVertex(pose, vertexConsumer, i, k, h, l, m, o);
	}

	private static void addVertex(PoseStack.Pose pose, VertexConsumer vertexConsumer, int i, int j, float f, float g, float h, float k) {
		vertexConsumer.addVertex(pose, f, j, g).setColor(i).setUv(h, k).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
	}

	@Override
	public boolean shouldRender(T blockEntity, Vec3 vec3) {
		return Vec3.atCenterOf(blockEntity.getBlockPos()).multiply(1.0, 0.0, 1.0).closerThan(vec3.multiply(1.0, 0.0, 1.0), this.getViewDistance());
	}
}
