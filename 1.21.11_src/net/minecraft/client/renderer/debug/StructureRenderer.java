package net.minecraft.client.renderer.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.util.debug.DebugStructureInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.AABB;

@Environment(EnvType.CLIENT)
public class StructureRenderer implements DebugRenderer.SimpleDebugRenderer {
	@Override
	public void emitGizmos(double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum, float g) {
		debugValueAccess.forEachChunk(DebugSubscriptions.STRUCTURES, (chunkPos, list) -> {
			for (DebugStructureInfo debugStructureInfo : list) {
				Gizmos.cuboid(AABB.of(debugStructureInfo.boundingBox()), GizmoStyle.stroke(ARGB.colorFromFloat(1.0F, 1.0F, 1.0F, 1.0F)));

				for (DebugStructureInfo.Piece piece : debugStructureInfo.pieces()) {
					if (piece.isStart()) {
						Gizmos.cuboid(AABB.of(piece.boundingBox()), GizmoStyle.stroke(ARGB.colorFromFloat(1.0F, 0.0F, 1.0F, 0.0F)));
					} else {
						Gizmos.cuboid(AABB.of(piece.boundingBox()), GizmoStyle.stroke(ARGB.colorFromFloat(1.0F, 0.0F, 0.0F, 1.0F)));
					}
				}
			}
		});
	}
}
