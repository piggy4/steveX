package com.mojang.math;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Util;
import org.apache.commons.lang3.tuple.Triple;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public final class Transformation {
	private final Matrix4fc matrix;
	public static final Codec<Transformation> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				ExtraCodecs.VECTOR3F.fieldOf("translation").forGetter(transformation -> transformation.translation),
				ExtraCodecs.QUATERNIONF.fieldOf("left_rotation").forGetter(transformation -> transformation.leftRotation),
				ExtraCodecs.VECTOR3F.fieldOf("scale").forGetter(transformation -> transformation.scale),
				ExtraCodecs.QUATERNIONF.fieldOf("right_rotation").forGetter(transformation -> transformation.rightRotation)
			)
			.apply(instance, Transformation::new)
	);
	public static final Codec<Transformation> EXTENDED_CODEC = Codec.withAlternative(
		CODEC, ExtraCodecs.MATRIX4F.xmap(Transformation::new, Transformation::getMatrix)
	);
	private boolean decomposed;
	private @Nullable Vector3fc translation;
	private @Nullable Quaternionfc leftRotation;
	private @Nullable Vector3fc scale;
	private @Nullable Quaternionfc rightRotation;
	private static final Transformation IDENTITY = Util.make(() -> {
		Transformation transformation = new Transformation(new Matrix4f());
		transformation.translation = new Vector3f();
		transformation.leftRotation = new Quaternionf();
		transformation.scale = new Vector3f(1.0F, 1.0F, 1.0F);
		transformation.rightRotation = new Quaternionf();
		transformation.decomposed = true;
		return transformation;
	});

	public Transformation(@Nullable Matrix4fc matrix4fc) {
		if (matrix4fc == null) {
			this.matrix = new Matrix4f();
		} else {
			this.matrix = matrix4fc;
		}
	}

	public Transformation(@Nullable Vector3fc vector3fc, @Nullable Quaternionfc quaternionfc, @Nullable Vector3fc vector3fc2, @Nullable Quaternionfc quaternionfc2) {
		this.matrix = compose(vector3fc, quaternionfc, vector3fc2, quaternionfc2);
		this.translation = vector3fc != null ? vector3fc : new Vector3f();
		this.leftRotation = quaternionfc != null ? quaternionfc : new Quaternionf();
		this.scale = vector3fc2 != null ? vector3fc2 : new Vector3f(1.0F, 1.0F, 1.0F);
		this.rightRotation = quaternionfc2 != null ? quaternionfc2 : new Quaternionf();
		this.decomposed = true;
	}

	public static Transformation identity() {
		return IDENTITY;
	}

	public Transformation compose(Transformation transformation) {
		Matrix4f matrix4f = this.getMatrixCopy();
		matrix4f.mul(transformation.getMatrix());
		return new Transformation(matrix4f);
	}

	public @Nullable Transformation inverse() {
		if (this == IDENTITY) {
			return this;
		}

		Matrix4f matrix4f = this.getMatrixCopy().invertAffine();
		return matrix4f.isFinite() ? new Transformation(matrix4f) : null;
	}

	private void ensureDecomposed() {
		if (!this.decomposed) {
			float f = 1.0F / this.matrix.m33();
			Triple<Quaternionf, Vector3f, Quaternionf> triple = MatrixUtil.svdDecompose(new Matrix3f(this.matrix).scale(f));
			this.translation = this.matrix.getTranslation(new Vector3f()).mul(f);
			this.leftRotation = new Quaternionf(triple.getLeft());
			this.scale = new Vector3f(triple.getMiddle());
			this.rightRotation = new Quaternionf(triple.getRight());
			this.decomposed = true;
		}
	}

	private static Matrix4f compose(
		@Nullable Vector3fc vector3fc, @Nullable Quaternionfc quaternionfc, @Nullable Vector3fc vector3fc2, @Nullable Quaternionfc quaternionfc2
	) {
		Matrix4f matrix4f = new Matrix4f();
		if (vector3fc != null) {
			matrix4f.translation(vector3fc);
		}

		if (quaternionfc != null) {
			matrix4f.rotate(quaternionfc);
		}

		if (vector3fc2 != null) {
			matrix4f.scale(vector3fc2);
		}

		if (quaternionfc2 != null) {
			matrix4f.rotate(quaternionfc2);
		}

		return matrix4f;
	}

	public Matrix4fc getMatrix() {
		return this.matrix;
	}

	public Matrix4f getMatrixCopy() {
		return new Matrix4f(this.matrix);
	}

	public Vector3fc getTranslation() {
		this.ensureDecomposed();
		return this.translation;
	}

	public Quaternionfc getLeftRotation() {
		this.ensureDecomposed();
		return this.leftRotation;
	}

	public Vector3fc getScale() {
		this.ensureDecomposed();
		return this.scale;
	}

	public Quaternionfc getRightRotation() {
		this.ensureDecomposed();
		return this.rightRotation;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		} else if (object != null && this.getClass() == object.getClass()) {
			Transformation transformation = (Transformation)object;
			return Objects.equals(this.matrix, transformation.matrix);
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.matrix);
	}

	public Transformation slerp(Transformation transformation, float f) {
		return new Transformation(
			this.getTranslation().lerp(transformation.getTranslation(), f, new Vector3f()),
			this.getLeftRotation().slerp(transformation.getLeftRotation(), f, new Quaternionf()),
			this.getScale().lerp(transformation.getScale(), f, new Vector3f()),
			this.getRightRotation().slerp(transformation.getRightRotation(), f, new Quaternionf())
		);
	}
}
