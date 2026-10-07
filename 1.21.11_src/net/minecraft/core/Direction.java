package net.minecraft.core;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Contract;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public enum Direction implements StringRepresentable {
	DOWN(0, 1, -1, "down", Direction.AxisDirection.NEGATIVE, Direction.Axis.Y, new Vec3i(0, -1, 0)),
	UP(1, 0, -1, "up", Direction.AxisDirection.POSITIVE, Direction.Axis.Y, new Vec3i(0, 1, 0)),
	NORTH(2, 3, 2, "north", Direction.AxisDirection.NEGATIVE, Direction.Axis.Z, new Vec3i(0, 0, -1)),
	SOUTH(3, 2, 0, "south", Direction.AxisDirection.POSITIVE, Direction.Axis.Z, new Vec3i(0, 0, 1)),
	WEST(4, 5, 1, "west", Direction.AxisDirection.NEGATIVE, Direction.Axis.X, new Vec3i(-1, 0, 0)),
	EAST(5, 4, 3, "east", Direction.AxisDirection.POSITIVE, Direction.Axis.X, new Vec3i(1, 0, 0));

	public static final StringRepresentable.EnumCodec<Direction> CODEC = StringRepresentable.fromEnum(Direction::values);
	public static final Codec<Direction> VERTICAL_CODEC = CODEC.validate(Direction::verifyVertical);
	public static final IntFunction<Direction> BY_ID = ByIdMap.continuous(Direction::get3DDataValue, values(), ByIdMap.OutOfBoundsStrategy.WRAP);
	public static final StreamCodec<ByteBuf, Direction> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Direction::get3DDataValue);
	@Deprecated
	public static final Codec<Direction> LEGACY_ID_CODEC = Codec.BYTE.xmap(Direction::from3DDataValue, direction -> (byte)direction.get3DDataValue());
	@Deprecated
	public static final Codec<Direction> LEGACY_ID_CODEC_2D = Codec.BYTE.xmap(Direction::from2DDataValue, direction -> (byte)direction.get2DDataValue());
	private static final ImmutableList<Direction.Axis> YXZ_AXIS_ORDER = ImmutableList.of(Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z);
	private static final ImmutableList<Direction.Axis> YZX_AXIS_ORDER = ImmutableList.of(Direction.Axis.Y, Direction.Axis.Z, Direction.Axis.X);
	private final int data3d;
	private final int oppositeIndex;
	private final int data2d;
	private final String name;
	private final Direction.Axis axis;
	private final Direction.AxisDirection axisDirection;
	private final Vec3i normal;
	private final Vec3 normalVec3;
	private final Vector3fc normalVec3f;
	private static final Direction[] VALUES = values();
	private static final Direction[] BY_3D_DATA = Arrays.stream(VALUES).sorted(Comparator.comparingInt(direction -> direction.data3d)).toArray(Direction[]::new);
	private static final Direction[] BY_2D_DATA = Arrays.stream(VALUES)
		.filter(direction -> direction.getAxis().isHorizontal())
		.sorted(Comparator.comparingInt(direction -> direction.data2d))
		.toArray(Direction[]::new);

	Direction(
		final int j, final int k, final int l, final String string2, final Direction.AxisDirection axisDirection, final Direction.Axis axis, final Vec3i vec3i
	) {
		this.data3d = j;
		this.data2d = l;
		this.oppositeIndex = k;
		this.name = string2;
		this.axis = axis;
		this.axisDirection = axisDirection;
		this.normal = vec3i;
		this.normalVec3 = Vec3.atLowerCornerOf(vec3i);
		this.normalVec3f = new Vector3f(vec3i.getX(), vec3i.getY(), vec3i.getZ());
	}

	public static Direction[] orderedByNearest(Entity entity) {
		float f = entity.getViewXRot(1.0F) * (float) (Math.PI / 180.0);
		float g = -entity.getViewYRot(1.0F) * (float) (Math.PI / 180.0);
		float h = Mth.sin(f);
		float i = Mth.cos(f);
		float j = Mth.sin(g);
		float k = Mth.cos(g);
		boolean bl = j > 0.0F;
		boolean bl2 = h < 0.0F;
		boolean bl3 = k > 0.0F;
		float l = bl ? j : -j;
		float m = bl2 ? -h : h;
		float n = bl3 ? k : -k;
		float o = l * i;
		float p = n * i;
		Direction direction = bl ? EAST : WEST;
		Direction direction2 = bl2 ? UP : DOWN;
		Direction direction3 = bl3 ? SOUTH : NORTH;
		if (l > n) {
			if (m > o) {
				return makeDirectionArray(direction2, direction, direction3);
			} else {
				return p > m ? makeDirectionArray(direction, direction3, direction2) : makeDirectionArray(direction, direction2, direction3);
			}
		} else if (m > p) {
			return makeDirectionArray(direction2, direction3, direction);
		} else {
			return o > m ? makeDirectionArray(direction3, direction, direction2) : makeDirectionArray(direction3, direction2, direction);
		}
	}

	private static Direction[] makeDirectionArray(Direction direction, Direction direction2, Direction direction3) {
		return new Direction[]{direction, direction2, direction3, direction3.getOpposite(), direction2.getOpposite(), direction.getOpposite()};
	}

	public static Direction rotate(Matrix4fc matrix4fc, Direction direction) {
		Vector3f vector3f = matrix4fc.transformDirection(direction.normalVec3f, new Vector3f());
		return getApproximateNearest(vector3f.x(), vector3f.y(), vector3f.z());
	}

	public static Collection<Direction> allShuffled(RandomSource randomSource) {
		return Util.shuffledCopy(values(), randomSource);
	}

	public static Stream<Direction> stream() {
		return Stream.of(VALUES);
	}

	public static float getYRot(Direction direction) {
		return switch (direction) {
			case NORTH -> 180.0F;
			case SOUTH -> 0.0F;
			case WEST -> 90.0F;
			case EAST -> -90.0F;
			default -> throw new IllegalStateException("No y-Rot for vertical axis: " + direction);
		};
	}

	public Quaternionf getRotation() {
		return switch (this) {
			case DOWN -> new Quaternionf().rotationX((float) Math.PI);
			case UP -> new Quaternionf();
			case NORTH -> new Quaternionf().rotationXYZ((float) (Math.PI / 2), 0.0F, (float) Math.PI);
			case SOUTH -> new Quaternionf().rotationX((float) (Math.PI / 2));
			case WEST -> new Quaternionf().rotationXYZ((float) (Math.PI / 2), 0.0F, (float) (Math.PI / 2));
			case EAST -> new Quaternionf().rotationXYZ((float) (Math.PI / 2), 0.0F, (float) (-Math.PI / 2));
		};
	}

	public int get3DDataValue() {
		return this.data3d;
	}

	public int get2DDataValue() {
		return this.data2d;
	}

	public Direction.AxisDirection getAxisDirection() {
		return this.axisDirection;
	}

	public static Direction getFacingAxis(Entity entity, Direction.Axis axis) {
		return switch (axis) {
			case X -> EAST.isFacingAngle(entity.getViewYRot(1.0F)) ? EAST : WEST;
			case Y -> entity.getViewXRot(1.0F) < 0.0F ? UP : DOWN;
			case Z -> SOUTH.isFacingAngle(entity.getViewYRot(1.0F)) ? SOUTH : NORTH;
		};
	}

	public Direction getOpposite() {
		return from3DDataValue(this.oppositeIndex);
	}

	public Direction getClockWise(Direction.Axis axis) {
		return switch (axis) {
			case X -> this != WEST && this != EAST ? this.getClockWiseX() : this;
			case Y -> this != UP && this != DOWN ? this.getClockWise() : this;
			case Z -> this != NORTH && this != SOUTH ? this.getClockWiseZ() : this;
		};
	}

	public Direction getCounterClockWise(Direction.Axis axis) {
		return switch (axis) {
			case X -> this != WEST && this != EAST ? this.getCounterClockWiseX() : this;
			case Y -> this != UP && this != DOWN ? this.getCounterClockWise() : this;
			case Z -> this != NORTH && this != SOUTH ? this.getCounterClockWiseZ() : this;
		};
	}

	public Direction getClockWise() {
		return switch (this) {
			case NORTH -> EAST;
			case SOUTH -> WEST;
			case WEST -> NORTH;
			case EAST -> SOUTH;
			default -> throw new IllegalStateException("Unable to get Y-rotated facing of " + this);
		};
	}

	private Direction getClockWiseX() {
		return switch (this) {
			case DOWN -> SOUTH;
			case UP -> NORTH;
			case NORTH -> DOWN;
			case SOUTH -> UP;
			default -> throw new IllegalStateException("Unable to get X-rotated facing of " + this);
		};
	}

	private Direction getCounterClockWiseX() {
		return switch (this) {
			case DOWN -> NORTH;
			case UP -> SOUTH;
			case NORTH -> UP;
			case SOUTH -> DOWN;
			default -> throw new IllegalStateException("Unable to get X-rotated facing of " + this);
		};
	}

	private Direction getClockWiseZ() {
		return switch (this) {
			case DOWN -> WEST;
			case UP -> EAST;
			default -> throw new IllegalStateException("Unable to get Z-rotated facing of " + this);
			case WEST -> UP;
			case EAST -> DOWN;
		};
	}

	private Direction getCounterClockWiseZ() {
		return switch (this) {
			case DOWN -> EAST;
			case UP -> WEST;
			default -> throw new IllegalStateException("Unable to get Z-rotated facing of " + this);
			case WEST -> DOWN;
			case EAST -> UP;
		};
	}

	public Direction getCounterClockWise() {
		return switch (this) {
			case NORTH -> WEST;
			case SOUTH -> EAST;
			case WEST -> SOUTH;
			case EAST -> NORTH;
			default -> throw new IllegalStateException("Unable to get CCW facing of " + this);
		};
	}

	public int getStepX() {
		return this.normal.getX();
	}

	public int getStepY() {
		return this.normal.getY();
	}

	public int getStepZ() {
		return this.normal.getZ();
	}

	public Vector3f step() {
		return new Vector3f(this.normalVec3f);
	}

	public String getName() {
		return this.name;
	}

	public Direction.Axis getAxis() {
		return this.axis;
	}

	public static @Nullable Direction byName(String string) {
		return CODEC.byName(string);
	}

	public static Direction from3DDataValue(int i) {
		return BY_3D_DATA[Mth.abs(i % BY_3D_DATA.length)];
	}

	public static Direction from2DDataValue(int i) {
		return BY_2D_DATA[Mth.abs(i % BY_2D_DATA.length)];
	}

	public static Direction fromYRot(double d) {
		return from2DDataValue(Mth.floor(d / 90.0 + 0.5) & 3);
	}

	public static Direction fromAxisAndDirection(Direction.Axis axis, Direction.AxisDirection axisDirection) {
		return switch (axis) {
			case X -> axisDirection == Direction.AxisDirection.POSITIVE ? EAST : WEST;
			case Y -> axisDirection == Direction.AxisDirection.POSITIVE ? UP : DOWN;
			case Z -> axisDirection == Direction.AxisDirection.POSITIVE ? SOUTH : NORTH;
		};
	}

	public float toYRot() {
		return (this.data2d & 3) * 90;
	}

	public static Direction getRandom(RandomSource randomSource) {
		return Util.getRandom(VALUES, randomSource);
	}

	public static Direction getApproximateNearest(double d, double e, double f) {
		return getApproximateNearest((float)d, (float)e, (float)f);
	}

	public static Direction getApproximateNearest(float f, float g, float h) {
		Direction direction = NORTH;
		float i = Float.MIN_VALUE;

		for (Direction direction2 : VALUES) {
			float j = f * direction2.normal.getX() + g * direction2.normal.getY() + h * direction2.normal.getZ();
			if (j > i) {
				i = j;
				direction = direction2;
			}
		}

		return direction;
	}

	public static Direction getApproximateNearest(Vec3 vec3) {
		return getApproximateNearest(vec3.x, vec3.y, vec3.z);
	}

	@Contract("_,_,_,!null->!null;_,_,_,_->_")
	public static @Nullable Direction getNearest(int i, int j, int k, @Nullable Direction direction) {
		int l = Math.abs(i);
		int m = Math.abs(j);
		int n = Math.abs(k);
		if (l > n && l > m) {
			return i < 0 ? WEST : EAST;
		} else if (n > l && n > m) {
			return k < 0 ? NORTH : SOUTH;
		} else if (m > l && m > n) {
			return j < 0 ? DOWN : UP;
		} else {
			return direction;
		}
	}

	@Contract("_,!null->!null;_,_->_")
	public static @Nullable Direction getNearest(Vec3i vec3i, @Nullable Direction direction) {
		return getNearest(vec3i.getX(), vec3i.getY(), vec3i.getZ(), direction);
	}

	@Override
	public String toString() {
		return this.name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	private static DataResult<Direction> verifyVertical(Direction direction) {
		return direction.getAxis().isVertical() ? DataResult.success(direction) : DataResult.error(() -> "Expected a vertical direction");
	}

	public static Direction get(Direction.AxisDirection axisDirection, Direction.Axis axis) {
		for (Direction direction : VALUES) {
			if (direction.getAxisDirection() == axisDirection && direction.getAxis() == axis) {
				return direction;
			}
		}

		throw new IllegalArgumentException("No such direction: " + axisDirection + " " + axis);
	}

	public static ImmutableList<Direction.Axis> axisStepOrder(Vec3 vec3) {
		return Math.abs(vec3.x) < Math.abs(vec3.z) ? YZX_AXIS_ORDER : YXZ_AXIS_ORDER;
	}

	public Vec3i getUnitVec3i() {
		return this.normal;
	}

	public Vec3 getUnitVec3() {
		return this.normalVec3;
	}

	public Vector3fc getUnitVec3f() {
		return this.normalVec3f;
	}

	public boolean isFacingAngle(float f) {
		float g = f * (float) (Math.PI / 180.0);
		float h = -Mth.sin(g);
		float i = Mth.cos(g);
		return this.normal.getX() * h + this.normal.getZ() * i > 0.0F;
	}

	public enum Axis implements StringRepresentable, Predicate<Direction> {
		X("x") {
			@Override
			public int choose(int i, int j, int k) {
				return i;
			}

			@Override
			public boolean choose(boolean bl, boolean bl2, boolean bl3) {
				return bl;
			}

			@Override
			public double choose(double d, double e, double f) {
				return d;
			}

			@Override
			public Direction getPositive() {
				return Direction.EAST;
			}

			@Override
			public Direction getNegative() {
				return Direction.WEST;
			}
		},
		Y("y") {
			@Override
			public int choose(int i, int j, int k) {
				return j;
			}

			@Override
			public double choose(double d, double e, double f) {
				return e;
			}

			@Override
			public boolean choose(boolean bl, boolean bl2, boolean bl3) {
				return bl2;
			}

			@Override
			public Direction getPositive() {
				return Direction.UP;
			}

			@Override
			public Direction getNegative() {
				return Direction.DOWN;
			}
		},
		Z("z") {
			@Override
			public int choose(int i, int j, int k) {
				return k;
			}

			@Override
			public double choose(double d, double e, double f) {
				return f;
			}

			@Override
			public boolean choose(boolean bl, boolean bl2, boolean bl3) {
				return bl3;
			}

			@Override
			public Direction getPositive() {
				return Direction.SOUTH;
			}

			@Override
			public Direction getNegative() {
				return Direction.NORTH;
			}
		};

		public static final Direction.Axis[] VALUES = values();
		public static final StringRepresentable.EnumCodec<Direction.Axis> CODEC = StringRepresentable.fromEnum(Direction.Axis::values);
		private final String name;

		Axis(final String string2) {
			this.name = string2;
		}

		public static Direction.@Nullable Axis byName(String string) {
			return CODEC.byName(string);
		}

		public String getName() {
			return this.name;
		}

		public boolean isVertical() {
			return this == Y;
		}

		public boolean isHorizontal() {
			return this == X || this == Z;
		}

		public abstract Direction getPositive();

		public abstract Direction getNegative();

		public Direction[] getDirections() {
			return new Direction[]{this.getPositive(), this.getNegative()};
		}

		@Override
		public String toString() {
			return this.name;
		}

		public static Direction.Axis getRandom(RandomSource randomSource) {
			return Util.getRandom(VALUES, randomSource);
		}

		public boolean test(@Nullable Direction direction) {
			return direction != null && direction.getAxis() == this;
		}

		public Direction.Plane getPlane() {
			return switch (this) {
				case X, Z -> Direction.Plane.HORIZONTAL;
				case Y -> Direction.Plane.VERTICAL;
			};
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}

		public abstract int choose(int i, int j, int k);

		public abstract double choose(double d, double e, double f);

		public abstract boolean choose(boolean bl, boolean bl2, boolean bl3);
	}

	public enum AxisDirection {
		POSITIVE(1, "Towards positive"),
		NEGATIVE(-1, "Towards negative");

		private final int step;
		private final String name;

		AxisDirection(final int j, final String string2) {
			this.step = j;
			this.name = string2;
		}

		public int getStep() {
			return this.step;
		}

		public String getName() {
			return this.name;
		}

		@Override
		public String toString() {
			return this.name;
		}

		public Direction.AxisDirection opposite() {
			return this == POSITIVE ? NEGATIVE : POSITIVE;
		}
	}

	public enum Plane implements Iterable<Direction>, Predicate<Direction> {
		HORIZONTAL(new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}, new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}),
		VERTICAL(new Direction[]{Direction.UP, Direction.DOWN}, new Direction.Axis[]{Direction.Axis.Y});

		private final Direction[] faces;
		private final Direction.Axis[] axis;

		Plane(final Direction[] directions, final Direction.Axis[] axiss) {
			this.faces = directions;
			this.axis = axiss;
		}

		public Direction getRandomDirection(RandomSource randomSource) {
			return Util.getRandom(this.faces, randomSource);
		}

		public Direction.Axis getRandomAxis(RandomSource randomSource) {
			return Util.getRandom(this.axis, randomSource);
		}

		public boolean test(@Nullable Direction direction) {
			return direction != null && direction.getAxis().getPlane() == this;
		}

		@Override
		public Iterator<Direction> iterator() {
			return Iterators.forArray(this.faces);
		}

		public Stream<Direction> stream() {
			return Arrays.stream(this.faces);
		}

		public List<Direction> shuffledCopy(RandomSource randomSource) {
			return Util.shuffledCopy(this.faces, randomSource);
		}

		public int length() {
			return this.faces.length;
		}
	}
}
