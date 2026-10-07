package net.minecraft.client.resources.model;

import com.mojang.math.Transformation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

@Environment(EnvType.CLIENT)
public interface ModelState {
	Matrix4fc NO_TRANSFORM = new Matrix4f();

	default Transformation transformation() {
		return Transformation.identity();
	}

	default Matrix4fc faceTransformation(Direction direction) {
		return NO_TRANSFORM;
	}

	default Matrix4fc inverseFaceTransformation(Direction direction) {
		return NO_TRANSFORM;
	}
}
