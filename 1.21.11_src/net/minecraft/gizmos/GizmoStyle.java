package net.minecraft.gizmos;

import net.minecraft.util.ARGB;

public record GizmoStyle(int stroke, float strokeWidth, int fill) {
	private static final float DEFAULT_WIDTH = 2.5F;

	public static GizmoStyle stroke(int i) {
		return new GizmoStyle(i, 2.5F, 0);
	}

	public static GizmoStyle stroke(int i, float f) {
		return new GizmoStyle(i, f, 0);
	}

	public static GizmoStyle fill(int i) {
		return new GizmoStyle(0, 0.0F, i);
	}

	public static GizmoStyle strokeAndFill(int i, float f, int j) {
		return new GizmoStyle(i, f, j);
	}

	public boolean hasFill() {
		return this.fill != 0;
	}

	public boolean hasStroke() {
		return this.stroke != 0 && this.strokeWidth > 0.0F;
	}

	public int multipliedStroke(float f) {
		return ARGB.multiplyAlpha(this.stroke, f);
	}

	public int multipliedFill(float f) {
		return ARGB.multiplyAlpha(this.fill, f);
	}
}
