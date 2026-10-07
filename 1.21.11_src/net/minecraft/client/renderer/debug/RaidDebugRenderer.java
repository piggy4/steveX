package net.minecraft.client.renderer.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.ARGB;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class RaidDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final int MAX_RENDER_DIST = 160;
	private static final float TEXT_SCALE = 0.64F;
	private final Minecraft minecraft;

	public RaidDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void emitGizmos(double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum, float g) {
		BlockPos blockPos = this.getCamera().blockPosition();
		debugValueAccess.forEachChunk(DebugSubscriptions.RAIDS, (chunkPos, list) -> {
			for (BlockPos blockPos2 : list) {
				if (blockPos.closerThan(blockPos2, 160.0)) {
					highlightRaidCenter(blockPos2);
				}
			}
		});
	}

	private static void highlightRaidCenter(BlockPos blockPos) {
		Gizmos.cuboid(blockPos, GizmoStyle.fill(ARGB.colorFromFloat(0.15F, 1.0F, 0.0F, 0.0F)));
		renderTextOverBlock("Raid center", blockPos, -65536);
	}

	private static void renderTextOverBlock(String string, BlockPos blockPos, int i) {
		Gizmos.billboardText(string, Vec3.atLowerCornerWithOffset(blockPos, 0.5, 1.3, 0.5), TextGizmo.Style.forColor(i).withScale(0.64F)).setAlwaysOnTop();
	}

	private Camera getCamera() {
		return this.minecraft.gameRenderer.getMainCamera();
	}
}
