package net.minecraft.gizmos;

import java.util.OptionalDouble;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

public record TextGizmo(Vec3 pos, String text, TextGizmo.Style style) implements Gizmo {
	@Override
	public void emit(GizmoPrimitives gizmoPrimitives, float f) {
		TextGizmo.Style style;
		if (f < 1.0F) {
			style = new TextGizmo.Style(ARGB.multiplyAlpha(this.style.color, f), this.style.scale, this.style.adjustLeft);
		} else {
			style = this.style;
		}

		gizmoPrimitives.addText(this.pos, this.text, style);
	}

	public record Style(int color, float scale, OptionalDouble adjustLeft) {
		public static final float DEFAULT_SCALE = 0.32F;

		public static TextGizmo.Style whiteAndCentered() {
			return new TextGizmo.Style(-1, 0.32F, OptionalDouble.empty());
		}

		public static TextGizmo.Style forColorAndCentered(int i) {
			return new TextGizmo.Style(i, 0.32F, OptionalDouble.empty());
		}

		public static TextGizmo.Style forColor(int i) {
			return new TextGizmo.Style(i, 0.32F, OptionalDouble.of(0.0));
		}

		public TextGizmo.Style withScale(float f) {
			return new TextGizmo.Style(this.color, f, this.adjustLeft);
		}

		public TextGizmo.Style withLeftAlignment(float f) {
			return new TextGizmo.Style(this.color, this.scale, OptionalDouble.of(f));
		}
	}
}
