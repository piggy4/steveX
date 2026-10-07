package net.minecraft.client.renderer.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.debug.DebugGoalInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class GoalSelectorDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final int MAX_RENDER_DIST = 160;
	private final Minecraft minecraft;

	public GoalSelectorDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void emitGizmos(double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum, float g) {
		Camera camera = this.minecraft.gameRenderer.getMainCamera();
		BlockPos blockPos = BlockPos.containing(camera.position().x, 0.0, camera.position().z);
		debugValueAccess.forEachEntity(DebugSubscriptions.GOAL_SELECTORS, (entity, debugGoalInfo) -> {
			if (blockPos.closerThan(entity.blockPosition(), 160.0)) {
				for (int i = 0; i < debugGoalInfo.goals().size(); i++) {
					DebugGoalInfo.DebugGoal debugGoal = debugGoalInfo.goals().get(i);
					double dx = entity.getBlockX() + 0.5;
					double ex = entity.getY() + 2.0 + i * 0.25;
					double fx = entity.getBlockZ() + 0.5;
					int j = debugGoal.isRunning() ? -16711936 : -3355444;
					Gizmos.billboardText(debugGoal.name(), new Vec3(dx, ex, fx), TextGizmo.Style.forColorAndCentered(j));
				}
			}
		});
	}
}
