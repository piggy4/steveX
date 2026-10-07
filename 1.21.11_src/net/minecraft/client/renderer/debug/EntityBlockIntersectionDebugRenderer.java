package net.minecraft.client.renderer.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;

@Environment(EnvType.CLIENT)
public class EntityBlockIntersectionDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final float PADDING = 0.02F;

	@Override
	public void emitGizmos(double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum, float g) {
		debugValueAccess.forEachBlock(
			DebugSubscriptions.ENTITY_BLOCK_INTERSECTIONS,
			(blockPos, debugEntityBlockIntersection) -> Gizmos.cuboid(blockPos, 0.02F, GizmoStyle.fill(debugEntityBlockIntersection.color()))
		);
	}
}
