package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityWithBoundingBoxRenderState;
import net.minecraft.client.renderer.blockentity.state.TestInstanceRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class TestInstanceRenderer implements BlockEntityRenderer<TestInstanceBlockEntity, TestInstanceRenderState> {
	private static final float ERROR_PADDING = 0.02F;
	private final BeaconRenderer<TestInstanceBlockEntity> beacon = new BeaconRenderer<>();
	private final BlockEntityWithBoundingBoxRenderer<TestInstanceBlockEntity> box = new BlockEntityWithBoundingBoxRenderer<>();

	public TestInstanceRenderState createRenderState() {
		return new TestInstanceRenderState();
	}

	public void extractRenderState(
		TestInstanceBlockEntity testInstanceBlockEntity,
		TestInstanceRenderState testInstanceRenderState,
		float f,
		Vec3 vec3,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
	) {
		BlockEntityRenderer.super.extractRenderState(testInstanceBlockEntity, testInstanceRenderState, f, vec3, crumblingOverlay);
		testInstanceRenderState.beaconRenderState = new BeaconRenderState();
		BlockEntityRenderState.extractBase(testInstanceBlockEntity, testInstanceRenderState.beaconRenderState, crumblingOverlay);
		BeaconRenderer.extract(testInstanceBlockEntity, testInstanceRenderState.beaconRenderState, f, vec3);
		testInstanceRenderState.blockEntityWithBoundingBoxRenderState = new BlockEntityWithBoundingBoxRenderState();
		BlockEntityRenderState.extractBase(testInstanceBlockEntity, testInstanceRenderState.blockEntityWithBoundingBoxRenderState, crumblingOverlay);
		BlockEntityWithBoundingBoxRenderer.extract(testInstanceBlockEntity, testInstanceRenderState.blockEntityWithBoundingBoxRenderState);
		testInstanceRenderState.errorMarkers.clear();

		for (TestInstanceBlockEntity.ErrorMarker errorMarker : testInstanceBlockEntity.getErrorMarkers()) {
			testInstanceRenderState.errorMarkers.add(new TestInstanceBlockEntity.ErrorMarker(errorMarker.pos(), errorMarker.text()));
		}
	}

	public void submit(
		TestInstanceRenderState testInstanceRenderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState
	) {
		this.beacon.submit(testInstanceRenderState.beaconRenderState, poseStack, submitNodeCollector, cameraRenderState);
		this.box.submit(testInstanceRenderState.blockEntityWithBoundingBoxRenderState, poseStack, submitNodeCollector, cameraRenderState);

		for (TestInstanceBlockEntity.ErrorMarker errorMarker : testInstanceRenderState.errorMarkers) {
			this.submitErrorMarker(errorMarker);
		}
	}

	private void submitErrorMarker(TestInstanceBlockEntity.ErrorMarker errorMarker) {
		BlockPos blockPos = errorMarker.pos();
		Gizmos.cuboid(new AABB(blockPos).inflate(0.02F), GizmoStyle.fill(ARGB.colorFromFloat(0.375F, 1.0F, 0.0F, 0.0F)));
		String string = errorMarker.text().getString();
		float f = 0.16F;
		Gizmos.billboardText(string, Vec3.atLowerCornerWithOffset(blockPos, 0.5, 1.2, 0.5), TextGizmo.Style.whiteAndCentered().withScale(0.16F)).setAlwaysOnTop();
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return this.beacon.shouldRenderOffScreen() || this.box.shouldRenderOffScreen();
	}

	@Override
	public int getViewDistance() {
		return Math.max(this.beacon.getViewDistance(), this.box.getViewDistance());
	}

	public boolean shouldRender(TestInstanceBlockEntity testInstanceBlockEntity, Vec3 vec3) {
		return this.beacon.shouldRender(testInstanceBlockEntity, vec3) || this.box.shouldRender(testInstanceBlockEntity, vec3);
	}
}
