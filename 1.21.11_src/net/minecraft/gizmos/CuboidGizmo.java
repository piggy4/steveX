package net.minecraft.gizmos;

import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record CuboidGizmo(AABB aabb, GizmoStyle style, boolean coloredCornerStroke) implements Gizmo {
	@Override
	public void emit(GizmoPrimitives gizmoPrimitives, float f) {
		double d = this.aabb.minX;
		double e = this.aabb.minY;
		double g = this.aabb.minZ;
		double h = this.aabb.maxX;
		double i = this.aabb.maxY;
		double j = this.aabb.maxZ;
		if (this.style.hasFill()) {
			int k = this.style.multipliedFill(f);
			gizmoPrimitives.addQuad(new Vec3(h, e, g), new Vec3(h, i, g), new Vec3(h, i, j), new Vec3(h, e, j), k);
			gizmoPrimitives.addQuad(new Vec3(d, e, g), new Vec3(d, e, j), new Vec3(d, i, j), new Vec3(d, i, g), k);
			gizmoPrimitives.addQuad(new Vec3(d, e, g), new Vec3(d, i, g), new Vec3(h, i, g), new Vec3(h, e, g), k);
			gizmoPrimitives.addQuad(new Vec3(d, e, j), new Vec3(h, e, j), new Vec3(h, i, j), new Vec3(d, i, j), k);
			gizmoPrimitives.addQuad(new Vec3(d, i, g), new Vec3(d, i, j), new Vec3(h, i, j), new Vec3(h, i, g), k);
			gizmoPrimitives.addQuad(new Vec3(d, e, g), new Vec3(h, e, g), new Vec3(h, e, j), new Vec3(d, e, j), k);
		}

		if (this.style.hasStroke()) {
			int k = this.style.multipliedStroke(f);
			gizmoPrimitives.addLine(new Vec3(d, e, g), new Vec3(h, e, g), this.coloredCornerStroke ? ARGB.multiply(k, -34953) : k, this.style.strokeWidth());
			gizmoPrimitives.addLine(new Vec3(d, e, g), new Vec3(d, i, g), this.coloredCornerStroke ? ARGB.multiply(k, -8913033) : k, this.style.strokeWidth());
			gizmoPrimitives.addLine(new Vec3(d, e, g), new Vec3(d, e, j), this.coloredCornerStroke ? ARGB.multiply(k, -8947713) : k, this.style.strokeWidth());
			gizmoPrimitives.addLine(new Vec3(h, e, g), new Vec3(h, i, g), k, this.style.strokeWidth());
			gizmoPrimitives.addLine(new Vec3(h, i, g), new Vec3(d, i, g), k, this.style.strokeWidth());
			gizmoPrimitives.addLine(new Vec3(d, i, g), new Vec3(d, i, j), k, this.style.strokeWidth());
			gizmoPrimitives.addLine(new Vec3(d, i, j), new Vec3(d, e, j), k, this.style.strokeWidth());
			gizmoPrimitives.addLine(new Vec3(d, e, j), new Vec3(h, e, j), k, this.style.strokeWidth());
			gizmoPrimitives.addLine(new Vec3(h, e, j), new Vec3(h, e, g), k, this.style.strokeWidth());
			gizmoPrimitives.addLine(new Vec3(d, i, j), new Vec3(h, i, j), k, this.style.strokeWidth());
			gizmoPrimitives.addLine(new Vec3(h, e, j), new Vec3(h, i, j), k, this.style.strokeWidth());
			gizmoPrimitives.addLine(new Vec3(h, i, g), new Vec3(h, i, j), k, this.style.strokeWidth());
		}
	}
}
