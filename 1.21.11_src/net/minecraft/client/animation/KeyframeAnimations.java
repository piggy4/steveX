package net.minecraft.client.animation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class KeyframeAnimations {
	public static Vector3f posVec(float f, float g, float h) {
		return new Vector3f(f, -g, h);
	}

	public static Vector3f degreeVec(float f, float g, float h) {
		return new Vector3f(f * (float) (Math.PI / 180.0), g * (float) (Math.PI / 180.0), h * (float) (Math.PI / 180.0));
	}

	public static Vector3f scaleVec(double d, double e, double f) {
		return new Vector3f((float)(d - 1.0), (float)(e - 1.0), (float)(f - 1.0));
	}
}
