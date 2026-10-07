package net.minecraft.client.renderer.block.model;

import com.mojang.math.MatrixUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Direction;
import org.joml.Math;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public record BlockElementRotation(Vector3fc origin, BlockElementRotation.RotationValue value, boolean rescale, Matrix4fc transform) {
	public BlockElementRotation(Vector3fc vector3fc, BlockElementRotation.RotationValue rotationValue, boolean bl) {
		this(vector3fc, rotationValue, bl, computeTransform(rotationValue, bl));
	}

	private static Matrix4f computeTransform(BlockElementRotation.RotationValue rotationValue, boolean bl) {
		Matrix4f matrix4f = rotationValue.transformation();
		if (bl && !MatrixUtil.isIdentity(matrix4f)) {
			Vector3fc vector3fc = computeRescale(matrix4f);
			matrix4f.scale(vector3fc);
		}

		return matrix4f;
	}

	private static Vector3fc computeRescale(Matrix4fc matrix4fc) {
		Vector3f vector3f = new Vector3f();
		float f = scaleFactorForAxis(matrix4fc, Direction.Axis.X, vector3f);
		float g = scaleFactorForAxis(matrix4fc, Direction.Axis.Y, vector3f);
		float h = scaleFactorForAxis(matrix4fc, Direction.Axis.Z, vector3f);
		return vector3f.set(f, g, h);
	}

	private static float scaleFactorForAxis(Matrix4fc matrix4fc, Direction.Axis axis, Vector3f vector3f) {
		Vector3f vector3f2 = vector3f.set(axis.getPositive().getUnitVec3f());
		Vector3f vector3f3 = matrix4fc.transformDirection(vector3f2);
		float f = Math.abs(vector3f3.x);
		float g = Math.abs(vector3f3.y);
		float h = Math.abs(vector3f3.z);
		float i = Math.max(Math.max(f, g), h);
		return 1.0F / i;
	}

	@Environment(EnvType.CLIENT)
	public record EulerXYZRotation(float x, float y, float z) implements BlockElementRotation.RotationValue {
		@Override
		public Matrix4f transformation() {
			return new Matrix4f()
				.rotationZYX(this.z * (float) (java.lang.Math.PI / 180.0), this.y * (float) (java.lang.Math.PI / 180.0), this.x * (float) (java.lang.Math.PI / 180.0));
		}
	}

	@Environment(EnvType.CLIENT)
	public interface RotationValue {
		Matrix4f transformation();
	}

	@Environment(EnvType.CLIENT)
	public record SingleAxisRotation(Direction.Axis axis, float angle) implements BlockElementRotation.RotationValue {
		@Override
		public Matrix4f transformation() {
			Matrix4f matrix4f = new Matrix4f();
			if (this.angle == 0.0F) {
				return matrix4f;
			}

			Vector3fc vector3fc = this.axis.getPositive().getUnitVec3f();
			matrix4f.rotation(this.angle * (float) (java.lang.Math.PI / 180.0), vector3fc);
			return matrix4f;
		}
	}
}
