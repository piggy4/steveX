package net.minecraft.gizmos;

import net.minecraft.world.phys.Vec3;

public interface GizmoPrimitives {
	void addPoint(Vec3 vec3, int i, float f);

	void addLine(Vec3 vec3, Vec3 vec32, int i, float f);

	void addTriangleFan(Vec3[] vec3s, int i);

	void addQuad(Vec3 vec3, Vec3 vec32, Vec3 vec33, Vec3 vec34, int i);

	void addText(Vec3 vec3, String string, TextGizmo.Style style);
}
