package net.minecraft.client.renderer.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Octree;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.AABB;
import org.apache.commons.lang3.mutable.MutableInt;

@Environment(EnvType.CLIENT)
public class OctreeDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private final Minecraft minecraft;

	public OctreeDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void emitGizmos(double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum, float g) {
		Octree octree = this.minecraft.levelRenderer.getSectionOcclusionGraph().getOctree();
		MutableInt mutableInt = new MutableInt(0);
		octree.visitNodes((node, bl, i, bl2) -> this.renderNode(node, i, bl, mutableInt, bl2), frustum, 32);
	}

	private void renderNode(Octree.Node node, int i, boolean bl, MutableInt mutableInt, boolean bl2) {
		AABB aABB = node.getAABB();
		double d = aABB.getXsize();
		long l = Math.round(d / 16.0);
		if (l == 1L) {
			mutableInt.add(1);
			int j = bl2 ? -16711936 : -1;
			Gizmos.billboardText(String.valueOf(mutableInt.intValue()), aABB.getCenter(), TextGizmo.Style.forColorAndCentered(j).withScale(4.8F));
		}

		long m = l + 5L;
		Gizmos.cuboid(
			aABB.deflate(0.1 * i),
			GizmoStyle.stroke(ARGB.colorFromFloat(bl ? 0.4F : 1.0F, getColorComponent(m, 0.3F), getColorComponent(m, 0.8F), getColorComponent(m, 0.5F)))
		);
	}

	private static float getColorComponent(long l, float f) {
		float g = 0.1F;
		return Mth.frac(f * (float)l) * 0.9F + 0.1F;
	}
}
