package net.minecraft.client.waypoints;

import com.mojang.datafixers.util.Either;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraft.world.waypoints.TrackedWaypointManager;

@Environment(EnvType.CLIENT)
public class ClientWaypointManager implements TrackedWaypointManager {
	private final Map<Either<UUID, String>, TrackedWaypoint> waypoints = new ConcurrentHashMap<>();

	public void trackWaypoint(TrackedWaypoint trackedWaypoint) {
		this.waypoints.put(trackedWaypoint.id(), trackedWaypoint);
	}

	public void updateWaypoint(TrackedWaypoint trackedWaypoint) {
		this.waypoints.get(trackedWaypoint.id()).update(trackedWaypoint);
	}

	public void untrackWaypoint(TrackedWaypoint trackedWaypoint) {
		this.waypoints.remove(trackedWaypoint.id());
	}

	public boolean hasWaypoints() {
		return !this.waypoints.isEmpty();
	}

	public void forEachWaypoint(Entity entity, Consumer<TrackedWaypoint> consumer) {
		this.waypoints
			.values()
			.stream()
			.sorted(Comparator.<TrackedWaypoint>comparingDouble(trackedWaypoint -> trackedWaypoint.distanceSquared(entity)).reversed())
			.forEachOrdered(consumer);
	}
}
