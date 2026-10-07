package net.minecraft.world.phys;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class Vec3 implements Position {
	public static final Codec<Vec3> CODEC = Codec.DOUBLE
		.listOf()
		.comapFlatMap(
			list -> Util.fixedSize((List<Double>)list, 3).map(listx -> new Vec3((Double)listx.get(0), (Double)listx.get(1), (Double)listx.get(2))),
			vec3 -> List.of(vec3.x(), vec3.y(), vec3.z())
		);
	public static final StreamCodec<ByteBuf, Vec3> STREAM_CODEC = new StreamCodec<ByteBuf, Vec3>() {
		public Vec3 decode(ByteBuf byteBuf) {
			return FriendlyByteBuf.readVec3(byteBuf);
		}

		public void encode(ByteBuf byteBuf, Vec3 vec3) {
			FriendlyByteBuf.writeVec3(byteBuf, vec3);
		}
	};
	public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);
	public static final Vec3 X_AXIS = new Vec3(1.0, 0.0, 0.0);
	public static final Vec3 Y_AXIS = new Vec3(0.0, 1.0, 0.0);
	public static final Vec3 Z_AXIS = new Vec3(0.0, 0.0, 1.0);
	public final double x;
	public final double y;
	public final double z;

	public static Vec3 atLowerCornerOf(Vec3i vec3i) {
		return new Vec3(vec3i.getX(), vec3i.getY(), vec3i.getZ());
	}

	public static Vec3 atLowerCornerWithOffset(Vec3i vec3i, double d, double e, double f) {
		return new Vec3(vec3i.getX() + d, vec3i.getY() + e, vec3i.getZ() + f);
	}

	public static Vec3 atCenterOf(Vec3i vec3i) {
		return atLowerCornerWithOffset(vec3i, 0.5, 0.5, 0.5);
	}

	public static Vec3 atBottomCenterOf(Vec3i vec3i) {
		return atLowerCornerWithOffset(vec3i, 0.5, 0.0, 0.5);
	}

	public static Vec3 upFromBottomCenterOf(Vec3i vec3i, double d) {
		return atLowerCornerWithOffset(vec3i, 0.5, d, 0.5);
	}

	public Vec3(double d, double e, double f) {
		this.x = d;
		this.y = e;
		this.z = f;
	}

	public Vec3(Vector3fc vector3fc) {
		this(vector3fc.x(), vector3fc.y(), vector3fc.z());
	}

	public Vec3(Vec3i vec3i) {
		this(vec3i.getX(), vec3i.getY(), vec3i.getZ());
	}

	public Vec3 vectorTo(Vec3 vec3) {
		return new Vec3(vec3.x - this.x, vec3.y - this.y, vec3.z - this.z);
	}

	public Vec3 normalize() {
		double d = Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
		return d < 1.0E-5F ? ZERO : new Vec3(this.x / d, this.y / d, this.z / d);
	}

	public double dot(Vec3 vec3) {
		return this.x * vec3.x + this.y * vec3.y + this.z * vec3.z;
	}

	public Vec3 cross(Vec3 vec3) {
		return new Vec3(this.y * vec3.z - this.z * vec3.y, this.z * vec3.x - this.x * vec3.z, this.x * vec3.y - this.y * vec3.x);
	}

	public Vec3 subtract(Vec3 vec3) {
		return this.subtract(vec3.x, vec3.y, vec3.z);
	}

	public Vec3 subtract(double d) {
		return this.subtract(d, d, d);
	}

	public Vec3 subtract(double d, double e, double f) {
		return this.add(-d, -e, -f);
	}

	public Vec3 add(double d) {
		return this.add(d, d, d);
	}

	public Vec3 add(Vec3 vec3) {
		return this.add(vec3.x, vec3.y, vec3.z);
	}

	public Vec3 add(double d, double e, double f) {
		return new Vec3(this.x + d, this.y + e, this.z + f);
	}

	public boolean closerThan(Position position, double d) {
		return this.distanceToSqr(position.x(), position.y(), position.z()) < d * d;
	}

	public double distanceTo(Vec3 vec3) {
		double d = vec3.x - this.x;
		double e = vec3.y - this.y;
		double f = vec3.z - this.z;
		return Math.sqrt(d * d + e * e + f * f);
	}

	public double distanceToSqr(Vec3 vec3) {
		double d = vec3.x - this.x;
		double e = vec3.y - this.y;
		double f = vec3.z - this.z;
		return d * d + e * e + f * f;
	}

	public double distanceToSqr(double d, double e, double f) {
		double g = d - this.x;
		double h = e - this.y;
		double i = f - this.z;
		return g * g + h * h + i * i;
	}

	public boolean closerThan(Vec3 vec3, double d, double e) {
		double f = vec3.x() - this.x;
		double g = vec3.y() - this.y;
		double h = vec3.z() - this.z;
		return Mth.lengthSquared(f, h) < Mth.square(d) && Math.abs(g) < e;
	}

	public Vec3 scale(double d) {
		return this.multiply(d, d, d);
	}

	public Vec3 reverse() {
		return this.scale(-1.0);
	}

	public Vec3 multiply(Vec3 vec3) {
		return this.multiply(vec3.x, vec3.y, vec3.z);
	}

	public Vec3 multiply(double d, double e, double f) {
		return new Vec3(this.x * d, this.y * e, this.z * f);
	}

	public Vec3 horizontal() {
		return new Vec3(this.x, 0.0, this.z);
	}

	public Vec3 offsetRandom(RandomSource randomSource, float f) {
		return this.add((randomSource.nextFloat() - 0.5F) * f, (randomSource.nextFloat() - 0.5F) * f, (randomSource.nextFloat() - 0.5F) * f);
	}

	public Vec3 offsetRandomXZ(RandomSource randomSource, float f) {
		return this.add((randomSource.nextFloat() - 0.5F) * f, 0.0, (randomSource.nextFloat() - 0.5F) * f);
	}

	public double length() {
		return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
	}

	public double lengthSqr() {
		return this.x * this.x + this.y * this.y + this.z * this.z;
	}

	public double horizontalDistance() {
		return Math.sqrt(this.x * this.x + this.z * this.z);
	}

	public double horizontalDistanceSqr() {
		return this.x * this.x + this.z * this.z;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		} else if (!(object instanceof Vec3 vec3)) {
			return false;
		} else if (Double.compare(vec3.x, this.x) != 0) {
			return false;
		} else {
			return Double.compare(vec3.y, this.y) != 0 ? false : Double.compare(vec3.z, this.z) == 0;
		}
	}

	@Override
	public int hashCode() {
		long l = Double.doubleToLongBits(this.x);
		int i = (int)(l ^ l >>> 32);
		l = Double.doubleToLongBits(this.y);
		i = 31 * i + (int)(l ^ l >>> 32);
		l = Double.doubleToLongBits(this.z);
		return 31 * i + (int)(l ^ l >>> 32);
	}

	@Override
	public String toString() {
		return "(" + this.x + ", " + this.y + ", " + this.z + ")";
	}

	public Vec3 lerp(Vec3 vec3, double d) {
		return new Vec3(Mth.lerp(d, this.x, vec3.x), Mth.lerp(d, this.y, vec3.y), Mth.lerp(d, this.z, vec3.z));
	}

	public Vec3 xRot(float f) {
		float g = Mth.cos(f);
		float h = Mth.sin(f);
		double d = this.x;
		double e = this.y * g + this.z * h;
		double i = this.z * g - this.y * h;
		return new Vec3(d, e, i);
	}

	public Vec3 yRot(float f) {
		float g = Mth.cos(f);
		float h = Mth.sin(f);
		double d = this.x * g + this.z * h;
		double e = this.y;
		double i = this.z * g - this.x * h;
		return new Vec3(d, e, i);
	}

	public Vec3 zRot(float f) {
		float g = Mth.cos(f);
		float h = Mth.sin(f);
		double d = this.x * g + this.y * h;
		double e = this.y * g - this.x * h;
		double i = this.z;
		return new Vec3(d, e, i);
	}

	public Vec3 rotateClockwise90() {
		return new Vec3(-this.z, this.y, this.x);
	}

	public static Vec3 directionFromRotation(Vec2 vec2) {
		return directionFromRotation(vec2.x, vec2.y);
	}

	public static Vec3 directionFromRotation(float f, float g) {
		float h = Mth.cos(-g * (float) (Math.PI / 180.0) - (float) Math.PI);
		float i = Mth.sin(-g * (float) (Math.PI / 180.0) - (float) Math.PI);
		float j = -Mth.cos(-f * (float) (Math.PI / 180.0));
		float k = Mth.sin(-f * (float) (Math.PI / 180.0));
		return new Vec3(i * j, k, h * j);
	}

	public Vec2 rotation() {
		float f = (float)Math.atan2(-this.x, this.z) * (180.0F / (float)Math.PI);
		float g = (float)Math.asin(-this.y / Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z)) * (180.0F / (float)Math.PI);
		return new Vec2(g, f);
	}

	public Vec3 align(EnumSet<Direction.Axis> enumSet) {
		double d = enumSet.contains(Direction.Axis.X) ? Mth.floor(this.x) : this.x;
		double e = enumSet.contains(Direction.Axis.Y) ? Mth.floor(this.y) : this.y;
		double f = enumSet.contains(Direction.Axis.Z) ? Mth.floor(this.z) : this.z;
		return new Vec3(d, e, f);
	}

	public double get(Direction.Axis axis) {
		return axis.choose(this.x, this.y, this.z);
	}

	public Vec3 with(Direction.Axis axis, double d) {
		double e = axis == Direction.Axis.X ? d : this.x;
		double f = axis == Direction.Axis.Y ? d : this.y;
		double g = axis == Direction.Axis.Z ? d : this.z;
		return new Vec3(e, f, g);
	}

	public Vec3 relative(Direction direction, double d) {
		Vec3i vec3i = direction.getUnitVec3i();
		return new Vec3(this.x + d * vec3i.getX(), this.y + d * vec3i.getY(), this.z + d * vec3i.getZ());
	}

	@Override
	public final double x() {
		return this.x;
	}

	@Override
	public final double y() {
		return this.y;
	}

	@Override
	public final double z() {
		return this.z;
	}

	public Vector3f toVector3f() {
		return new Vector3f((float)this.x, (float)this.y, (float)this.z);
	}

	public Vec3 projectedOn(Vec3 vec3) {
		return vec3.lengthSqr() == 0.0 ? vec3 : vec3.scale(this.dot(vec3)).scale(1.0 / vec3.lengthSqr());
	}

	public static Vec3 applyLocalCoordinatesToRotation(Vec2 vec2, Vec3 vec3) {
		float f = Mth.cos((vec2.y + 90.0F) * (float) (Math.PI / 180.0));
		float g = Mth.sin((vec2.y + 90.0F) * (float) (Math.PI / 180.0));
		float h = Mth.cos(-vec2.x * (float) (Math.PI / 180.0));
		float i = Mth.sin(-vec2.x * (float) (Math.PI / 180.0));
		float j = Mth.cos((-vec2.x + 90.0F) * (float) (Math.PI / 180.0));
		float k = Mth.sin((-vec2.x + 90.0F) * (float) (Math.PI / 180.0));
		Vec3 vec32 = new Vec3(f * h, i, g * h);
		Vec3 vec33 = new Vec3(f * j, k, g * j);
		Vec3 vec34 = vec32.cross(vec33).scale(-1.0);
		double d = vec32.x * vec3.z + vec33.x * vec3.y + vec34.x * vec3.x;
		double e = vec32.y * vec3.z + vec33.y * vec3.y + vec34.y * vec3.x;
		double l = vec32.z * vec3.z + vec33.z * vec3.y + vec34.z * vec3.x;
		return new Vec3(d, e, l);
	}

	public Vec3 addLocalCoordinates(Vec3 vec3) {
		return applyLocalCoordinatesToRotation(this.rotation(), vec3);
	}

	public boolean isFinite() {
		return Double.isFinite(this.x) && Double.isFinite(this.y) && Double.isFinite(this.z);
	}
}
