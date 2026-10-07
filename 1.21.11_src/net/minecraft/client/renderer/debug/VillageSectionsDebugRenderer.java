package net.minecraft.client.renderer.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.SectionPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;

@Environment(EnvType.CLIENT)
public class VillageSectionsDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	@Override
	public void emitGizmos(double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum, float g) {
		debugValueAccess.forEachBlock(DebugSubscriptions.VILLAGE_SECTIONS, (blockPos, unit) -> {
			SectionPos sectionPos = SectionPos.of(blockPos);
			Gizmos.cuboid(sectionPos.center(), GizmoStyle.fill(ARGB.colorFromFloat(0.15F, 0.2F, 1.0F, 0.2F)));
		});
	}
}
