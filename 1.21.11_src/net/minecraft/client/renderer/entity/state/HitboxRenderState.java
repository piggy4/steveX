package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record HitboxRenderState(
	double x0, double y0, double z0, double x1, double y1, double z1, float offsetX, float offsetY, float offsetZ, float red, float green, float blue
) {
	public HitboxRenderState(double d, double e, double f, double g, double h, double i, float j, float k, float l) {
		this(d, e, f, g, h, i, 0.0F, 0.0F, 0.0F, j, k, l);
	}
}
