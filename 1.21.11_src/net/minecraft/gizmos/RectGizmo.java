package net.minecraft.gizmos;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public record RectGizmo(Vec3 a, Vec3 b, Vec3 c, Vec3 d, GizmoStyle style) implements Gizmo {
	public static RectGizmo fromCuboidFace(Vec3 vec3, Vec3 vec32, Direction direction, GizmoStyle gizmoStyle) {
		return switch (direction) {
			case DOWN -> new RectGizmo(
				new Vec3(vec3.x, vec3.y, vec3.z), new Vec3(vec32.x, vec3.y, vec3.z), new Vec3(vec32.x, vec3.y, vec32.z), new Vec3(vec3.x, vec3.y, vec32.z), gizmoStyle
			);
			case UP -> new RectGizmo(
				new Vec3(vec3.x, vec32.y, vec3.z), new Vec3(vec3.x, vec32.y, vec32.z), new Vec3(vec32.x, vec32.y, vec32.z), new Vec3(vec32.x, vec32.y, vec3.z), gizmoStyle
			);
			case NORTH -> new RectGizmo(
				new Vec3(vec3.x, vec3.y, vec3.z), new Vec3(vec3.x, vec32.y, vec3.z), new Vec3(vec32.x, vec32.y, vec3.z), new Vec3(vec32.x, vec3.y, vec3.z), gizmoStyle
			);
			case SOUTH -> new RectGizmo(
				new Vec3(vec3.x, vec3.y, vec32.z), new Vec3(vec32.x, vec3.y, vec32.z), new Vec3(vec32.x, vec32.y, vec32.z), new Vec3(vec3.x, vec32.y, vec32.z), gizmoStyle
			);
			case WEST -> new RectGizmo(
				new Vec3(vec3.x, vec3.y, vec3.z), new Vec3(vec3.x, vec3.y, vec32.z), new Vec3(vec3.x, vec32.y, vec32.z), new Vec3(vec3.x, vec32.y, vec3.z), gizmoStyle
			);
			case EAST -> new RectGizmo(
				new Vec3(vec32.x, vec3.y, vec3.z), new Vec3(vec32.x, vec32.y, vec3.z), new Vec3(vec32.x, vec32.y, vec32.z), new Vec3(vec32.x, vec3.y, vec32.z), gizmoStyle
			);
		};
	}

	@Override
	public void emit(GizmoPrimitives gizmoPrimitives, float f) {
		if (this.style.hasFill()) {
			int i = this.style.multipliedFill(f);
			gizmoPrimitives.addQuad(this.a, this.b, this.c, this.d, i);
		}

		if (this.style.hasStroke()) {
			int i = this.style.multipliedStroke(f);
			gizmoPrimitives.addLine(this.a, this.b, i, this.style.strokeWidth());
			gizmoPrimitives.addLine(this.b, this.c, i, this.style.strokeWidth());
			gizmoPrimitives.addLine(this.c, this.d, i, this.style.strokeWidth());
			gizmoPrimitives.addLine(this.d, this.a, i, this.style.strokeWidth());
		}
	}
}
