package net.minecraft.gizmos;

import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

public record PointGizmo(Vec3 pos, int color, float size) implements Gizmo {
	@Override
	public void emit(GizmoPrimitives gizmoPrimitives, float f) {
		gizmoPrimitives.addPoint(this.pos, ARGB.multiplyAlpha(this.color, f), this.size);
	}
}
