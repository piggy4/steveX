package net.minecraft.core;

import com.google.common.collect.AbstractIterator;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Unmodifiable;

@Unmodifiable
public class BlockPos extends Vec3i {
	public static final Codec<BlockPos> CODEC = Codec.INT_STREAM
		.<BlockPos>comapFlatMap(
			intStream -> Util.fixedSize(intStream, 3).map(is -> new BlockPos(is[0], is[1], is[2])),
			blockPos -> IntStream.of(blockPos.getX(), blockPos.getY(), blockPos.getZ())
		)
		.stable();
	public static final StreamCodec<ByteBuf, BlockPos> STREAM_CODEC = new StreamCodec<ByteBuf, BlockPos>() {
		public BlockPos decode(ByteBuf byteBuf) {
			return FriendlyByteBuf.readBlockPos(byteBuf);
		}

		public void encode(ByteBuf byteBuf, BlockPos blockPos) {
			FriendlyByteBuf.writeBlockPos(byteBuf, blockPos);
		}
	};
	public static final BlockPos ZERO = new BlockPos(0, 0, 0);
	public static final int PACKED_HORIZONTAL_LENGTH = 1 + Mth.log2(Mth.smallestEncompassingPowerOfTwo(30000000));
	public static final int PACKED_Y_LENGTH = 64 - 2 * PACKED_HORIZONTAL_LENGTH;
	private static final long PACKED_X_MASK = (1L << PACKED_HORIZONTAL_LENGTH) - 1L;
	private static final long PACKED_Y_MASK = (1L << PACKED_Y_LENGTH) - 1L;
	private static final long PACKED_Z_MASK = (1L << PACKED_HORIZONTAL_LENGTH) - 1L;
	private static final int Y_OFFSET = 0;
	private static final int Z_OFFSET = PACKED_Y_LENGTH;
	private static final int X_OFFSET = PACKED_Y_LENGTH + PACKED_HORIZONTAL_LENGTH;
	public static final int MAX_HORIZONTAL_COORDINATE = (1 << PACKED_HORIZONTAL_LENGTH) / 2 - 1;

	public BlockPos(int i, int j, int k) {
		super(i, j, k);
	}

	public BlockPos(Vec3i vec3i) {
		this(vec3i.getX(), vec3i.getY(), vec3i.getZ());
	}

	public static long offset(long l, Direction direction) {
		return offset(l, direction.getStepX(), direction.getStepY(), direction.getStepZ());
	}

	public static long offset(long l, int i, int j, int k) {
		return asLong(getX(l) + i, getY(l) + j, getZ(l) + k);
	}

	public static int getX(long l) {
		return (int)(l << 64 - X_OFFSET - PACKED_HORIZONTAL_LENGTH >> 64 - PACKED_HORIZONTAL_LENGTH);
	}

	public static int getY(long l) {
		return (int)(l << 64 - PACKED_Y_LENGTH >> 64 - PACKED_Y_LENGTH);
	}

	public static int getZ(long l) {
		return (int)(l << 64 - Z_OFFSET - PACKED_HORIZONTAL_LENGTH >> 64 - PACKED_HORIZONTAL_LENGTH);
	}

	public static BlockPos of(long l) {
		return new BlockPos(getX(l), getY(l), getZ(l));
	}

	public static BlockPos containing(double d, double e, double f) {
		return new BlockPos(Mth.floor(d), Mth.floor(e), Mth.floor(f));
	}

	public static BlockPos containing(Position position) {
		return containing(position.x(), position.y(), position.z());
	}

	public static BlockPos min(BlockPos blockPos, BlockPos blockPos2) {
		return new BlockPos(Math.min(blockPos.getX(), blockPos2.getX()), Math.min(blockPos.getY(), blockPos2.getY()), Math.min(blockPos.getZ(), blockPos2.getZ()));
	}

	public static BlockPos max(BlockPos blockPos, BlockPos blockPos2) {
		return new BlockPos(Math.max(blockPos.getX(), blockPos2.getX()), Math.max(blockPos.getY(), blockPos2.getY()), Math.max(blockPos.getZ(), blockPos2.getZ()));
	}

	public long asLong() {
		return asLong(this.getX(), this.getY(), this.getZ());
	}

	public static long asLong(int i, int j, int k) {
		long l = 0L;
		l |= (i & PACKED_X_MASK) << X_OFFSET;
		l |= (j & PACKED_Y_MASK) << 0;
		return l | (k & PACKED_Z_MASK) << Z_OFFSET;
	}

	public static long getFlatIndex(long l) {
		return l & -16L;
	}

	public BlockPos offset(int i, int j, int k) {
		return i == 0 && j == 0 && k == 0 ? this : new BlockPos(this.getX() + i, this.getY() + j, this.getZ() + k);
	}

	public Vec3 getCenter() {
		return Vec3.atCenterOf(this);
	}

	public Vec3 getBottomCenter() {
		return Vec3.atBottomCenterOf(this);
	}

	public BlockPos offset(Vec3i vec3i) {
		return this.offset(vec3i.getX(), vec3i.getY(), vec3i.getZ());
	}

	public BlockPos subtract(Vec3i vec3i) {
		return this.offset(-vec3i.getX(), -vec3i.getY(), -vec3i.getZ());
	}

	public BlockPos multiply(int i) {
		if (i == 1) {
			return this;
		} else {
			return i == 0 ? ZERO : new BlockPos(this.getX() * i, this.getY() * i, this.getZ() * i);
		}
	}

	public BlockPos above() {
		return this.relative(Direction.UP);
	}

	public BlockPos above(int i) {
		return this.relative(Direction.UP, i);
	}

	public BlockPos below() {
		return this.relative(Direction.DOWN);
	}

	public BlockPos below(int i) {
		return this.relative(Direction.DOWN, i);
	}

	public BlockPos north() {
		return this.relative(Direction.NORTH);
	}

	public BlockPos north(int i) {
		return this.relative(Direction.NORTH, i);
	}

	public BlockPos south() {
		return this.relative(Direction.SOUTH);
	}

	public BlockPos south(int i) {
		return this.relative(Direction.SOUTH, i);
	}

	public BlockPos west() {
		return this.relative(Direction.WEST);
	}

	public BlockPos west(int i) {
		return this.relative(Direction.WEST, i);
	}

	public BlockPos east() {
		return this.relative(Direction.EAST);
	}

	public BlockPos east(int i) {
		return this.relative(Direction.EAST, i);
	}

	public BlockPos relative(Direction direction) {
		return new BlockPos(this.getX() + direction.getStepX(), this.getY() + direction.getStepY(), this.getZ() + direction.getStepZ());
	}

	public BlockPos relative(Direction direction, int i) {
		return i == 0 ? this : new BlockPos(this.getX() + direction.getStepX() * i, this.getY() + direction.getStepY() * i, this.getZ() + direction.getStepZ() * i);
	}

	public BlockPos relative(Direction.Axis axis, int i) {
		if (i == 0) {
			return this;
		}

		int j = axis == Direction.Axis.X ? i : 0;
		int k = axis == Direction.Axis.Y ? i : 0;
		int l = axis == Direction.Axis.Z ? i : 0;
		return new BlockPos(this.getX() + j, this.getY() + k, this.getZ() + l);
	}

	public BlockPos rotate(Rotation rotation) {
		return switch (rotation) {
			case CLOCKWISE_90 -> new BlockPos(-this.getZ(), this.getY(), this.getX());
			case CLOCKWISE_180 -> new BlockPos(-this.getX(), this.getY(), -this.getZ());
			case COUNTERCLOCKWISE_90 -> new BlockPos(this.getZ(), this.getY(), -this.getX());
			case NONE -> this;
		};
	}

	public BlockPos cross(Vec3i vec3i) {
		return new BlockPos(
			this.getY() * vec3i.getZ() - this.getZ() * vec3i.getY(),
			this.getZ() * vec3i.getX() - this.getX() * vec3i.getZ(),
			this.getX() * vec3i.getY() - this.getY() * vec3i.getX()
		);
	}

	public BlockPos atY(int i) {
		return new BlockPos(this.getX(), i, this.getZ());
	}

	public BlockPos immutable() {
		return this;
	}

	public BlockPos.MutableBlockPos mutable() {
		return new BlockPos.MutableBlockPos(this.getX(), this.getY(), this.getZ());
	}

	public Vec3 clampLocationWithin(Vec3 vec3) {
		return new Vec3(
			Mth.clamp(vec3.x, this.getX() + 1.0E-5F, this.getX() + 1.0 - 1.0E-5F),
			Mth.clamp(vec3.y, this.getY() + 1.0E-5F, this.getY() + 1.0 - 1.0E-5F),
			Mth.clamp(vec3.z, this.getZ() + 1.0E-5F, this.getZ() + 1.0 - 1.0E-5F)
		);
	}

	public static Iterable<BlockPos> randomInCube(RandomSource randomSource, int i, BlockPos blockPos, int j) {
		return randomBetweenClosed(
			randomSource, i, blockPos.getX() - j, blockPos.getY() - j, blockPos.getZ() - j, blockPos.getX() + j, blockPos.getY() + j, blockPos.getZ() + j
		);
	}

	@Deprecated
	public static Stream<BlockPos> squareOutSouthEast(BlockPos blockPos) {
		return Stream.of(blockPos, blockPos.south(), blockPos.east(), blockPos.south().east());
	}

	public static Iterable<BlockPos> randomBetweenClosed(RandomSource randomSource, int i, int j, int k, int l, int m, int n, int o) {
		int p = m - j + 1;
		int q = n - k + 1;
		int r = o - l + 1;
		return () -> new AbstractIterator<BlockPos>() {
			final BlockPos.MutableBlockPos nextPos = new BlockPos.MutableBlockPos();
			int counter = i;

			protected BlockPos computeNext() {
				if (this.counter <= 0) {
					return this.endOfData();
				}

				BlockPos blockPos = this.nextPos.set(j + randomSource.nextInt(p), k + randomSource.nextInt(q), l + randomSource.nextInt(r));
				this.counter--;
				return blockPos;
			}
		};
	}

	public static Iterable<BlockPos> withinManhattan(BlockPos blockPos, int i, int j, int k) {
		int l = i + j + k;
		int m = blockPos.getX();
		int n = blockPos.getY();
		int o = blockPos.getZ();
		return () -> new AbstractIterator<BlockPos>() {
			private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			private int currentDepth;
			private int maxX;
			private int maxY;
			private int x;
			private int y;
			private boolean zMirror;

			protected BlockPos computeNext() {
				if (this.zMirror) {
					this.zMirror = false;
					this.cursor.setZ(o - (this.cursor.getZ() - o));
					return this.cursor;
				}

				BlockPos blockPosx;
				for (blockPosx = null; blockPosx == null; this.y++) {
					if (this.y > this.maxY) {
						this.x++;
						if (this.x > this.maxX) {
							this.currentDepth++;
							if (this.currentDepth > l) {
								return this.endOfData();
							}

							this.maxX = Math.min(i, this.currentDepth);
							this.x = -this.maxX;
						}

						this.maxY = Math.min(j, this.currentDepth - Math.abs(this.x));
						this.y = -this.maxY;
					}

					int ix = this.x;
					int jx = this.y;
					int kx = this.currentDepth - Math.abs(ix) - Math.abs(jx);
					if (kx <= k) {
						this.zMirror = kx != 0;
						blockPosx = this.cursor.set(m + ix, n + jx, o + kx);
					}
				}

				return blockPosx;
			}
		};
	}

	public static Optional<BlockPos> findClosestMatch(BlockPos blockPos, int i, int j, Predicate<BlockPos> predicate) {
		for (BlockPos blockPos2 : withinManhattan(blockPos, i, j, i)) {
			if (predicate.test(blockPos2)) {
				return Optional.of(blockPos2);
			}
		}

		return Optional.empty();
	}

	public static Stream<BlockPos> withinManhattanStream(BlockPos blockPos, int i, int j, int k) {
		return StreamSupport.stream(withinManhattan(blockPos, i, j, k).spliterator(), false);
	}

	public static Iterable<BlockPos> betweenClosed(AABB aABB) {
		BlockPos blockPos = containing(aABB.minX, aABB.minY, aABB.minZ);
		BlockPos blockPos2 = containing(aABB.maxX, aABB.maxY, aABB.maxZ);
		return betweenClosed(blockPos, blockPos2);
	}

	public static Iterable<BlockPos> betweenClosed(BlockPos blockPos, BlockPos blockPos2) {
		return betweenClosed(
			Math.min(blockPos.getX(), blockPos2.getX()),
			Math.min(blockPos.getY(), blockPos2.getY()),
			Math.min(blockPos.getZ(), blockPos2.getZ()),
			Math.max(blockPos.getX(), blockPos2.getX()),
			Math.max(blockPos.getY(), blockPos2.getY()),
			Math.max(blockPos.getZ(), blockPos2.getZ())
		);
	}

	public static Stream<BlockPos> betweenClosedStream(BlockPos blockPos, BlockPos blockPos2) {
		return StreamSupport.stream(betweenClosed(blockPos, blockPos2).spliterator(), false);
	}

	public static Stream<BlockPos> betweenClosedStream(BoundingBox boundingBox) {
		return betweenClosedStream(
			Math.min(boundingBox.minX(), boundingBox.maxX()),
			Math.min(boundingBox.minY(), boundingBox.maxY()),
			Math.min(boundingBox.minZ(), boundingBox.maxZ()),
			Math.max(boundingBox.minX(), boundingBox.maxX()),
			Math.max(boundingBox.minY(), boundingBox.maxY()),
			Math.max(boundingBox.minZ(), boundingBox.maxZ())
		);
	}

	public static Stream<BlockPos> betweenClosedStream(AABB aABB) {
		return betweenClosedStream(Mth.floor(aABB.minX), Mth.floor(aABB.minY), Mth.floor(aABB.minZ), Mth.floor(aABB.maxX), Mth.floor(aABB.maxY), Mth.floor(aABB.maxZ));
	}

	public static Stream<BlockPos> betweenClosedStream(int i, int j, int k, int l, int m, int n) {
		return StreamSupport.stream(betweenClosed(i, j, k, l, m, n).spliterator(), false);
	}

	public static Iterable<BlockPos> betweenClosed(int i, int j, int k, int l, int m, int n) {
		int o = l - i + 1;
		int p = m - j + 1;
		int q = n - k + 1;
		int r = o * p * q;
		return () -> new AbstractIterator<BlockPos>() {
			private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			private int index;

			protected BlockPos computeNext() {
				if (this.index == r) {
					return this.endOfData();
				}

				int ix = this.index % o;
				int jx = this.index / o;
				int kx = jx % p;
				int lx = jx / p;
				this.index++;
				return this.cursor.set(i + ix, j + kx, k + lx);
			}
		};
	}

	public static Iterable<BlockPos.MutableBlockPos> spiralAround(BlockPos blockPos, int i, Direction direction, Direction direction2) {
		Validate.validState(direction.getAxis() != direction2.getAxis(), "The two directions cannot be on the same axis");
		return () -> new AbstractIterator<BlockPos.MutableBlockPos>() {
			private final Direction[] directions = new Direction[]{direction, direction2, direction.getOpposite(), direction2.getOpposite()};
			private final BlockPos.MutableBlockPos cursor = blockPos.mutable().move(direction2);
			private final int legs = 4 * i;
			private int leg = -1;
			private int legSize;
			private int legIndex;
			private int lastX = this.cursor.getX();
			private int lastY = this.cursor.getY();
			private int lastZ = this.cursor.getZ();

			protected BlockPos.MutableBlockPos computeNext() {
				this.cursor.set(this.lastX, this.lastY, this.lastZ).move(this.directions[(this.leg + 4) % 4]);
				this.lastX = this.cursor.getX();
				this.lastY = this.cursor.getY();
				this.lastZ = this.cursor.getZ();
				if (this.legIndex >= this.legSize) {
					if (this.leg >= this.legs) {
						return this.endOfData();
					}

					this.leg++;
					this.legIndex = 0;
					this.legSize = this.leg / 2 + 1;
				}

				this.legIndex++;
				return this.cursor;
			}
		};
	}

	public static int breadthFirstTraversal(
		BlockPos blockPos, int i, int j, BiConsumer<BlockPos, Consumer<BlockPos>> biConsumer, Function<BlockPos, BlockPos.TraversalNodeStatus> function
	) {
		Queue<Pair<BlockPos, Integer>> queue = new ArrayDeque<>();
		LongSet longSet = new LongOpenHashSet();
		queue.add(Pair.of(blockPos, 0));
		int k = 0;

		while (!queue.isEmpty()) {
			Pair<BlockPos, Integer> pair = queue.poll();
			BlockPos blockPos2 = pair.getLeft();
			int l = pair.getRight();
			long m = blockPos2.asLong();
			if (longSet.add(m)) {
				BlockPos.TraversalNodeStatus traversalNodeStatus = function.apply(blockPos2);
				if (traversalNodeStatus != BlockPos.TraversalNodeStatus.SKIP) {
					if (traversalNodeStatus == BlockPos.TraversalNodeStatus.STOP) {
						break;
					}

					if (++k >= j) {
						return k;
					}

					if (l < i) {
						biConsumer.accept(blockPos2, blockPosx -> queue.add(Pair.of(blockPosx, l + 1)));
					}
				}
			}
		}

		return k;
	}

	public static Iterable<BlockPos> betweenCornersInDirection(AABB aABB, Vec3 vec3) {
		Vec3 vec32 = aABB.getMinPosition();
		int i = Mth.floor(vec32.x());
		int j = Mth.floor(vec32.y());
		int k = Mth.floor(vec32.z());
		Vec3 vec33 = aABB.getMaxPosition();
		int l = Mth.floor(vec33.x());
		int m = Mth.floor(vec33.y());
		int n = Mth.floor(vec33.z());
		return betweenCornersInDirection(i, j, k, l, m, n, vec3);
	}

	public static Iterable<BlockPos> betweenCornersInDirection(BlockPos blockPos, BlockPos blockPos2, Vec3 vec3) {
		return betweenCornersInDirection(blockPos.getX(), blockPos.getY(), blockPos.getZ(), blockPos2.getX(), blockPos2.getY(), blockPos2.getZ(), vec3);
	}

	public static Iterable<BlockPos> betweenCornersInDirection(int i, int j, int k, int l, int m, int n, Vec3 vec3) {
		int o = Math.min(i, l);
		int p = Math.min(j, m);
		int q = Math.min(k, n);
		int r = Math.max(i, l);
		int s = Math.max(j, m);
		int t = Math.max(k, n);
		int u = r - o;
		int v = s - p;
		int w = t - q;
		int x = vec3.x >= 0.0 ? o : r;
		int y = vec3.y >= 0.0 ? p : s;
		int z = vec3.z >= 0.0 ? q : t;
		List<Direction.Axis> list = Direction.axisStepOrder(vec3);
		Direction.Axis axis = list.get(0);
		Direction.Axis axis2 = list.get(1);
		Direction.Axis axis3 = list.get(2);
		Direction direction = vec3.get(axis) >= 0.0 ? axis.getPositive() : axis.getNegative();
		Direction direction2 = vec3.get(axis2) >= 0.0 ? axis2.getPositive() : axis2.getNegative();
		Direction direction3 = vec3.get(axis3) >= 0.0 ? axis3.getPositive() : axis3.getNegative();
		int aa = axis.choose(u, v, w);
		int ab = axis2.choose(u, v, w);
		int ac = axis3.choose(u, v, w);
		return () -> new AbstractIterator<BlockPos>() {
			private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			private int firstIndex;
			private int secondIndex;
			private int thirdIndex;
			private boolean end;
			private final int firstDirX = direction.getStepX();
			private final int firstDirY = direction.getStepY();
			private final int firstDirZ = direction.getStepZ();
			private final int secondDirX = direction2.getStepX();
			private final int secondDirY = direction2.getStepY();
			private final int secondDirZ = direction2.getStepZ();
			private final int thirdDirX = direction3.getStepX();
			private final int thirdDirY = direction3.getStepY();
			private final int thirdDirZ = direction3.getStepZ();

			protected BlockPos computeNext() {
				if (this.end) {
					return this.endOfData();
				}

				this.cursor
					.set(
						x + this.firstDirX * this.firstIndex + this.secondDirX * this.secondIndex + this.thirdDirX * this.thirdIndex,
						y + this.firstDirY * this.firstIndex + this.secondDirY * this.secondIndex + this.thirdDirY * this.thirdIndex,
						z + this.firstDirZ * this.firstIndex + this.secondDirZ * this.secondIndex + this.thirdDirZ * this.thirdIndex
					);
				if (this.thirdIndex < ac) {
					this.thirdIndex++;
				} else if (this.secondIndex < ab) {
					this.secondIndex++;
					this.thirdIndex = 0;
				} else if (this.firstIndex < aa) {
					this.firstIndex++;
					this.thirdIndex = 0;
					this.secondIndex = 0;
				} else {
					this.end = true;
				}

				return this.cursor;
			}
		};
	}

	public static class MutableBlockPos extends BlockPos {
		public MutableBlockPos() {
			this(0, 0, 0);
		}

		public MutableBlockPos(int i, int j, int k) {
			super(i, j, k);
		}

		public MutableBlockPos(double d, double e, double f) {
			this(Mth.floor(d), Mth.floor(e), Mth.floor(f));
		}

		@Override
		public BlockPos offset(int i, int j, int k) {
			return super.offset(i, j, k).immutable();
		}

		@Override
		public BlockPos multiply(int i) {
			return super.multiply(i).immutable();
		}

		@Override
		public BlockPos relative(Direction direction, int i) {
			return super.relative(direction, i).immutable();
		}

		@Override
		public BlockPos relative(Direction.Axis axis, int i) {
			return super.relative(axis, i).immutable();
		}

		@Override
		public BlockPos rotate(Rotation rotation) {
			return super.rotate(rotation).immutable();
		}

		public BlockPos.MutableBlockPos set(int i, int j, int k) {
			this.setX(i);
			this.setY(j);
			this.setZ(k);
			return this;
		}

		public BlockPos.MutableBlockPos set(double d, double e, double f) {
			return this.set(Mth.floor(d), Mth.floor(e), Mth.floor(f));
		}

		public BlockPos.MutableBlockPos set(Vec3i vec3i) {
			return this.set(vec3i.getX(), vec3i.getY(), vec3i.getZ());
		}

		public BlockPos.MutableBlockPos set(long l) {
			return this.set(getX(l), getY(l), getZ(l));
		}

		public BlockPos.MutableBlockPos set(AxisCycle axisCycle, int i, int j, int k) {
			return this.set(axisCycle.cycle(i, j, k, Direction.Axis.X), axisCycle.cycle(i, j, k, Direction.Axis.Y), axisCycle.cycle(i, j, k, Direction.Axis.Z));
		}

		public BlockPos.MutableBlockPos setWithOffset(Vec3i vec3i, Direction direction) {
			return this.set(vec3i.getX() + direction.getStepX(), vec3i.getY() + direction.getStepY(), vec3i.getZ() + direction.getStepZ());
		}

		public BlockPos.MutableBlockPos setWithOffset(Vec3i vec3i, int i, int j, int k) {
			return this.set(vec3i.getX() + i, vec3i.getY() + j, vec3i.getZ() + k);
		}

		public BlockPos.MutableBlockPos setWithOffset(Vec3i vec3i, Vec3i vec3i2) {
			return this.set(vec3i.getX() + vec3i2.getX(), vec3i.getY() + vec3i2.getY(), vec3i.getZ() + vec3i2.getZ());
		}

		public BlockPos.MutableBlockPos move(Direction direction) {
			return this.move(direction, 1);
		}

		public BlockPos.MutableBlockPos move(Direction direction, int i) {
			return this.set(this.getX() + direction.getStepX() * i, this.getY() + direction.getStepY() * i, this.getZ() + direction.getStepZ() * i);
		}

		public BlockPos.MutableBlockPos move(int i, int j, int k) {
			return this.set(this.getX() + i, this.getY() + j, this.getZ() + k);
		}

		public BlockPos.MutableBlockPos move(Vec3i vec3i) {
			return this.set(this.getX() + vec3i.getX(), this.getY() + vec3i.getY(), this.getZ() + vec3i.getZ());
		}

		public BlockPos.MutableBlockPos clamp(Direction.Axis axis, int i, int j) {
			return switch (axis) {
				case X -> this.set(Mth.clamp(this.getX(), i, j), this.getY(), this.getZ());
				case Y -> this.set(this.getX(), Mth.clamp(this.getY(), i, j), this.getZ());
				case Z -> this.set(this.getX(), this.getY(), Mth.clamp(this.getZ(), i, j));
			};
		}

		public BlockPos.MutableBlockPos setX(int i) {
			super.setX(i);
			return this;
		}

		public BlockPos.MutableBlockPos setY(int i) {
			super.setY(i);
			return this;
		}

		public BlockPos.MutableBlockPos setZ(int i) {
			super.setZ(i);
			return this;
		}

		@Override
		public BlockPos immutable() {
			return new BlockPos(this);
		}
	}

	public enum TraversalNodeStatus {
		ACCEPT,
		SKIP,
		STOP;
	}
}
