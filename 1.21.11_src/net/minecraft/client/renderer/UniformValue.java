package net.minecraft.client.renderer;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import org.joml.Matrix4fc;
import org.joml.Vector2fc;
import org.joml.Vector3fc;
import org.joml.Vector3ic;
import org.joml.Vector4fc;

@Environment(EnvType.CLIENT)
public interface UniformValue {
	Codec<UniformValue> CODEC = UniformValue.Type.CODEC.dispatch(UniformValue::type, type -> type.valueCodec);

	void writeTo(Std140Builder std140Builder);

	void addSize(Std140SizeCalculator std140SizeCalculator);

	UniformValue.Type type();

	@Environment(EnvType.CLIENT)
	record FloatUniform(float value) implements UniformValue {
		public static final Codec<UniformValue.FloatUniform> CODEC = Codec.FLOAT.xmap(UniformValue.FloatUniform::new, UniformValue.FloatUniform::value);

		@Override
		public void writeTo(Std140Builder std140Builder) {
			std140Builder.putFloat(this.value);
		}

		@Override
		public void addSize(Std140SizeCalculator std140SizeCalculator) {
			std140SizeCalculator.putFloat();
		}

		@Override
		public UniformValue.Type type() {
			return UniformValue.Type.FLOAT;
		}
	}

	@Environment(EnvType.CLIENT)
	record IVec3Uniform(Vector3ic value) implements UniformValue {
		public static final Codec<UniformValue.IVec3Uniform> CODEC = ExtraCodecs.VECTOR3I.xmap(UniformValue.IVec3Uniform::new, UniformValue.IVec3Uniform::value);

		@Override
		public void writeTo(Std140Builder std140Builder) {
			std140Builder.putIVec3(this.value);
		}

		@Override
		public void addSize(Std140SizeCalculator std140SizeCalculator) {
			std140SizeCalculator.putIVec3();
		}

		@Override
		public UniformValue.Type type() {
			return UniformValue.Type.IVEC3;
		}
	}

	@Environment(EnvType.CLIENT)
	record IntUniform(int value) implements UniformValue {
		public static final Codec<UniformValue.IntUniform> CODEC = Codec.INT.xmap(UniformValue.IntUniform::new, UniformValue.IntUniform::value);

		@Override
		public void writeTo(Std140Builder std140Builder) {
			std140Builder.putInt(this.value);
		}

		@Override
		public void addSize(Std140SizeCalculator std140SizeCalculator) {
			std140SizeCalculator.putInt();
		}

		@Override
		public UniformValue.Type type() {
			return UniformValue.Type.INT;
		}
	}

	@Environment(EnvType.CLIENT)
	record Matrix4x4Uniform(Matrix4fc value) implements UniformValue {
		public static final Codec<UniformValue.Matrix4x4Uniform> CODEC = ExtraCodecs.MATRIX4F
			.xmap(UniformValue.Matrix4x4Uniform::new, UniformValue.Matrix4x4Uniform::value);

		@Override
		public void writeTo(Std140Builder std140Builder) {
			std140Builder.putMat4f(this.value);
		}

		@Override
		public void addSize(Std140SizeCalculator std140SizeCalculator) {
			std140SizeCalculator.putMat4f();
		}

		@Override
		public UniformValue.Type type() {
			return UniformValue.Type.MATRIX4X4;
		}
	}

	@Environment(EnvType.CLIENT)
	enum Type implements StringRepresentable {
		INT("int", UniformValue.IntUniform.CODEC),
		IVEC3("ivec3", UniformValue.IVec3Uniform.CODEC),
		FLOAT("float", UniformValue.FloatUniform.CODEC),
		VEC2("vec2", UniformValue.Vec2Uniform.CODEC),
		VEC3("vec3", UniformValue.Vec3Uniform.CODEC),
		VEC4("vec4", UniformValue.Vec4Uniform.CODEC),
		MATRIX4X4("matrix4x4", UniformValue.Matrix4x4Uniform.CODEC);

		public static final StringRepresentable.EnumCodec<UniformValue.Type> CODEC = StringRepresentable.fromEnum(UniformValue.Type::values);
		private final String name;
		final MapCodec<? extends UniformValue> valueCodec;

		Type(final String string2, final Codec<? extends UniformValue> codec) {
			this.name = string2;
			this.valueCodec = codec.fieldOf("value");
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}

	@Environment(EnvType.CLIENT)
	record Vec2Uniform(Vector2fc value) implements UniformValue {
		public static final Codec<UniformValue.Vec2Uniform> CODEC = ExtraCodecs.VECTOR2F.xmap(UniformValue.Vec2Uniform::new, UniformValue.Vec2Uniform::value);

		@Override
		public void writeTo(Std140Builder std140Builder) {
			std140Builder.putVec2(this.value);
		}

		@Override
		public void addSize(Std140SizeCalculator std140SizeCalculator) {
			std140SizeCalculator.putVec2();
		}

		@Override
		public UniformValue.Type type() {
			return UniformValue.Type.VEC2;
		}
	}

	@Environment(EnvType.CLIENT)
	record Vec3Uniform(Vector3fc value) implements UniformValue {
		public static final Codec<UniformValue.Vec3Uniform> CODEC = ExtraCodecs.VECTOR3F.xmap(UniformValue.Vec3Uniform::new, UniformValue.Vec3Uniform::value);

		@Override
		public void writeTo(Std140Builder std140Builder) {
			std140Builder.putVec3(this.value);
		}

		@Override
		public void addSize(Std140SizeCalculator std140SizeCalculator) {
			std140SizeCalculator.putVec3();
		}

		@Override
		public UniformValue.Type type() {
			return UniformValue.Type.VEC3;
		}
	}

	@Environment(EnvType.CLIENT)
	record Vec4Uniform(Vector4fc value) implements UniformValue {
		public static final Codec<UniformValue.Vec4Uniform> CODEC = ExtraCodecs.VECTOR4F.xmap(UniformValue.Vec4Uniform::new, UniformValue.Vec4Uniform::value);

		@Override
		public void writeTo(Std140Builder std140Builder) {
			std140Builder.putVec4(this.value);
		}

		@Override
		public void addSize(Std140SizeCalculator std140SizeCalculator) {
			std140SizeCalculator.putVec4();
		}

		@Override
		public UniformValue.Type type() {
			return UniformValue.Type.VEC4;
		}
	}
}
