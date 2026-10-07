package net.minecraft.client.animation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public record Keyframe(float timestamp, Vector3fc preTarget, Vector3fc postTarget, AnimationChannel.Interpolation interpolation) {
	public Keyframe(float f, Vector3fc vector3fc, AnimationChannel.Interpolation interpolation) {
		this(f, vector3fc, vector3fc, interpolation);
	}
}
