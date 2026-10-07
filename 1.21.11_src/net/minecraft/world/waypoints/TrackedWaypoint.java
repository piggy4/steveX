package net.minecraft.world.waypoints;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.function.TriFunction;
import org.slf4j.Logger;

public abstract class TrackedWaypoint implements Waypoint {
	static final Logger LOGGER = LogUtils.getLogger();
	public static final StreamCodec<ByteBuf, TrackedWaypoint> STREAM_CODEC = StreamCodec.ofMember(TrackedWaypoint::write, TrackedWaypoint::read);
	protected final Either<UUID, String> identifier;
	private final Waypoint.Icon icon;
	private final TrackedWaypoint.Type type;

	TrackedWaypoint(Either<UUID, String> either, Waypoint.Icon icon, TrackedWaypoint.Type type) {
		this.identifier = either;
		this.icon = icon;
		this.type = type;
	}

	public Either<UUID, String> id() {
		return this.identifier;
	}

	public abstract void update(TrackedWaypoint trackedWaypoint);

	public void write(ByteBuf byteBuf) {
		FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(byteBuf);
		friendlyByteBuf.writeEither(this.identifier, UUIDUtil.STREAM_CODEC, FriendlyByteBuf::writeUtf);
		Waypoint.Icon.STREAM_CODEC.encode(friendlyByteBuf, this.icon);
		friendlyByteBuf.writeEnum(this.type);
		this.writeContents(byteBuf);
	}

	public abstract void writeContents(ByteBuf byteBuf);

	private static TrackedWaypoint read(ByteBuf byteBuf) {
		FriendlyByteBuf friendlyByteBuf = new FriendlyByteBuf(byteBuf);
		Either<UUID, String> either = friendlyByteBuf.readEither(UUIDUtil.STREAM_CODEC, FriendlyByteBuf::readUtf);
		Waypoint.Icon icon = Waypoint.Icon.STREAM_CODEC.decode(friendlyByteBuf);
		TrackedWaypoint.Type type = friendlyByteBuf.readEnum(TrackedWaypoint.Type.class);
		return type.constructor.apply(either, icon, friendlyByteBuf);
	}

	public static TrackedWaypoint setPosition(UUID uUID, Waypoint.Icon icon, Vec3i vec3i) {
		return new TrackedWaypoint.Vec3iWaypoint(uUID, icon, vec3i);
	}

	public static TrackedWaypoint setChunk(UUID uUID, Waypoint.Icon icon, ChunkPos chunkPos) {
		return new TrackedWaypoint.ChunkWaypoint(uUID, icon, chunkPos);
	}

	public static TrackedWaypoint setAzimuth(UUID uUID, Waypoint.Icon icon, float f) {
		return new TrackedWaypoint.AzimuthWaypoint(uUID, icon, f);
	}

	public static TrackedWaypoint empty(UUID uUID) {
		return new TrackedWaypoint.EmptyWaypoint(uUID);
	}

	public abstract double yawAngleToCamera(Level level, TrackedWaypoint.Camera camera, PartialTickSupplier partialTickSupplier);

	public abstract TrackedWaypoint.PitchDirection pitchDirectionToCamera(
		Level level, TrackedWaypoint.Projector projector, PartialTickSupplier partialTickSupplier
	);

	public abstract double distanceSquared(Entity entity);

	public Waypoint.Icon icon() {
		return this.icon;
	}

	static class AzimuthWaypoint extends TrackedWaypoint {
		private float angle;

		public AzimuthWaypoint(UUID uUID, Waypoint.Icon icon, float f) {
			super(Either.left(uUID), icon, TrackedWaypoint.Type.AZIMUTH);
			this.angle = f;
		}

		public AzimuthWaypoint(Either<UUID, String> either, Waypoint.Icon icon, FriendlyByteBuf friendlyByteBuf) {
			super(either, icon, TrackedWaypoint.Type.AZIMUTH);
			this.angle = friendlyByteBuf.readFloat();
		}

		@Override
		public void update(TrackedWaypoint trackedWaypoint) {
			if (trackedWaypoint instanceof TrackedWaypoint.AzimuthWaypoint azimuthWaypoint) {
				this.angle = azimuthWaypoint.angle;
			} else {
				TrackedWaypoint.LOGGER.warn("Unsupported Waypoint update operation: {}", trackedWaypoint.getClass());
			}
		}

		@Override
		public void writeContents(ByteBuf byteBuf) {
			byteBuf.writeFloat(this.angle);
		}

		@Override
		public double yawAngleToCamera(Level level, TrackedWaypoint.Camera camera, PartialTickSupplier partialTickSupplier) {
			return Mth.degreesDifference(camera.yaw(), this.angle * (180.0F / (float)Math.PI));
		}

		@Override
		public TrackedWaypoint.PitchDirection pitchDirectionToCamera(Level level, TrackedWaypoint.Projector projector, PartialTickSupplier partialTickSupplier) {
			double d = projector.projectHorizonToScreen();
			if (d < -1.0) {
				return TrackedWaypoint.PitchDirection.DOWN;
			} else {
				return d > 1.0 ? TrackedWaypoint.PitchDirection.UP : TrackedWaypoint.PitchDirection.NONE;
			}
		}

		@Override
		public double distanceSquared(Entity entity) {
			return Double.POSITIVE_INFINITY;
		}
	}

	public interface Camera {
		float yaw();

		Vec3 position();
	}

	static class ChunkWaypoint extends TrackedWaypoint {
		private ChunkPos chunkPos;

		public ChunkWaypoint(UUID uUID, Waypoint.Icon icon, ChunkPos chunkPos) {
			super(Either.left(uUID), icon, TrackedWaypoint.Type.CHUNK);
			this.chunkPos = chunkPos;
		}

		public ChunkWaypoint(Either<UUID, String> either, Waypoint.Icon icon, FriendlyByteBuf friendlyByteBuf) {
			super(either, icon, TrackedWaypoint.Type.CHUNK);
			this.chunkPos = new ChunkPos(friendlyByteBuf.readVarInt(), friendlyByteBuf.readVarInt());
		}

		@Override
		public void update(TrackedWaypoint trackedWaypoint) {
			if (trackedWaypoint instanceof TrackedWaypoint.ChunkWaypoint chunkWaypoint) {
				this.chunkPos = chunkWaypoint.chunkPos;
			} else {
				TrackedWaypoint.LOGGER.warn("Unsupported Waypoint update operation: {}", trackedWaypoint.getClass());
			}
		}

		@Override
		public void writeContents(ByteBuf byteBuf) {
			VarInt.write(byteBuf, this.chunkPos.x);
			VarInt.write(byteBuf, this.chunkPos.z);
		}

		private Vec3 position(double d) {
			return Vec3.atCenterOf(this.chunkPos.getMiddleBlockPosition((int)d));
		}

		@Override
		public double yawAngleToCamera(Level level, TrackedWaypoint.Camera camera, PartialTickSupplier partialTickSupplier) {
			Vec3 vec3 = camera.position();
			Vec3 vec32 = vec3.subtract(this.position(vec3.y())).rotateClockwise90();
			float f = (float)Mth.atan2(vec32.z(), vec32.x()) * (180.0F / (float)Math.PI);
			return Mth.degreesDifference(camera.yaw(), f);
		}

		@Override
		public TrackedWaypoint.PitchDirection pitchDirectionToCamera(Level level, TrackedWaypoint.Projector projector, PartialTickSupplier partialTickSupplier) {
			double d = projector.projectHorizonToScreen();
			if (d < -1.0) {
				return TrackedWaypoint.PitchDirection.DOWN;
			} else {
				return d > 1.0 ? TrackedWaypoint.PitchDirection.UP : TrackedWaypoint.PitchDirection.NONE;
			}
		}

		@Override
		public double distanceSquared(Entity entity) {
			return entity.distanceToSqr(Vec3.atCenterOf(this.chunkPos.getMiddleBlockPosition(entity.getBlockY())));
		}
	}

	static class EmptyWaypoint extends TrackedWaypoint {
		private EmptyWaypoint(Either<UUID, String> either, Waypoint.Icon icon, FriendlyByteBuf friendlyByteBuf) {
			super(either, icon, TrackedWaypoint.Type.EMPTY);
		}

		EmptyWaypoint(UUID uUID) {
			super(Either.left(uUID), Waypoint.Icon.NULL, TrackedWaypoint.Type.EMPTY);
		}

		@Override
		public void update(TrackedWaypoint trackedWaypoint) {
		}

		@Override
		public void writeContents(ByteBuf byteBuf) {
		}

		@Override
		public double yawAngleToCamera(Level level, TrackedWaypoint.Camera camera, PartialTickSupplier partialTickSupplier) {
			return Double.NaN;
		}

		@Override
		public TrackedWaypoint.PitchDirection pitchDirectionToCamera(Level level, TrackedWaypoint.Projector projector, PartialTickSupplier partialTickSupplier) {
			return TrackedWaypoint.PitchDirection.NONE;
		}

		@Override
		public double distanceSquared(Entity entity) {
			return Double.POSITIVE_INFINITY;
		}
	}

	public enum PitchDirection {
		NONE,
		UP,
		DOWN;
	}

	public interface Projector {
		Vec3 projectPointToScreen(Vec3 vec3);

		double projectHorizonToScreen();
	}

	enum Type {
		EMPTY(TrackedWaypoint.EmptyWaypoint::new),
		VEC3I(TrackedWaypoint.Vec3iWaypoint::new),
		CHUNK(TrackedWaypoint.ChunkWaypoint::new),
		AZIMUTH(TrackedWaypoint.AzimuthWaypoint::new);

		final TriFunction<Either<UUID, String>, Waypoint.Icon, FriendlyByteBuf, TrackedWaypoint> constructor;

		Type(final TriFunction<Either<UUID, String>, Waypoint.Icon, FriendlyByteBuf, TrackedWaypoint> triFunction) {
			this.constructor = triFunction;
		}
	}

	static class Vec3iWaypoint extends TrackedWaypoint {
		private Vec3i vector;

		public Vec3iWaypoint(UUID uUID, Waypoint.Icon icon, Vec3i vec3i) {
			super(Either.left(uUID), icon, TrackedWaypoint.Type.VEC3I);
			this.vector = vec3i;
		}

		public Vec3iWaypoint(Either<UUID, String> either, Waypoint.Icon icon, FriendlyByteBuf friendlyByteBuf) {
			super(either, icon, TrackedWaypoint.Type.VEC3I);
			this.vector = new Vec3i(friendlyByteBuf.readVarInt(), friendlyByteBuf.readVarInt(), friendlyByteBuf.readVarInt());
		}

		@Override
		public void update(TrackedWaypoint trackedWaypoint) {
			if (trackedWaypoint instanceof TrackedWaypoint.Vec3iWaypoint vec3iWaypoint) {
				this.vector = vec3iWaypoint.vector;
			} else {
				TrackedWaypoint.LOGGER.warn("Unsupported Waypoint update operation: {}", trackedWaypoint.getClass());
			}
		}

		@Override
		public void writeContents(ByteBuf byteBuf) {
			VarInt.write(byteBuf, this.vector.getX());
			VarInt.write(byteBuf, this.vector.getY());
			VarInt.write(byteBuf, this.vector.getZ());
		}

		private Vec3 position(Level level, PartialTickSupplier partialTickSupplier) {
			return this.identifier
				.left()
				.map(level::getEntity)
				.map(entity -> entity.blockPosition().distManhattan(this.vector) > 3 ? null : entity.getEyePosition(partialTickSupplier.apply(entity)))
				.orElseGet(() -> Vec3.atCenterOf(this.vector));
		}

		@Override
		public double yawAngleToCamera(Level level, TrackedWaypoint.Camera camera, PartialTickSupplier partialTickSupplier) {
			Vec3 vec3 = camera.position().subtract(this.position(level, partialTickSupplier)).rotateClockwise90();
			float f = (float)Mth.atan2(vec3.z(), vec3.x()) * (180.0F / (float)Math.PI);
			return Mth.degreesDifference(camera.yaw(), f);
		}

		@Override
		public TrackedWaypoint.PitchDirection pitchDirectionToCamera(Level level, TrackedWaypoint.Projector projector, PartialTickSupplier partialTickSupplier) {
			Vec3 vec3 = projector.projectPointToScreen(this.position(level, partialTickSupplier));
			boolean bl = vec3.z > 1.0;
			double d = bl ? -vec3.y : vec3.y;
			if (d < -1.0) {
				return TrackedWaypoint.PitchDirection.DOWN;
			}

			if (d > 1.0) {
				return TrackedWaypoint.PitchDirection.UP;
			}

			if (bl) {
				if (vec3.y > 0.0) {
					return TrackedWaypoint.PitchDirection.UP;
				}

				if (vec3.y < 0.0) {
					return TrackedWaypoint.PitchDirection.DOWN;
				}
			}

			return TrackedWaypoint.PitchDirection.NONE;
		}

		@Override
		public double distanceSquared(Entity entity) {
			return entity.distanceToSqr(Vec3.atCenterOf(this.vector));
		}
	}
}
