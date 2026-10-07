package net.minecraft.client.renderer.state;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.core.Direction;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState} injected by mod fabric-rendering-v1</p>
 */
@Environment(EnvType.CLIENT)
public class WorldBorderRenderState implements FabricRenderState {
	public double minX;
	public double maxX;
	public double minZ;
	public double maxZ;
	public int tint;
	public double alpha;

	public List<WorldBorderRenderState.DistancePerDirection> closestBorder(double d, double e) {
		WorldBorderRenderState.DistancePerDirection[] distancePerDirections = new WorldBorderRenderState.DistancePerDirection[]{
			new WorldBorderRenderState.DistancePerDirection(Direction.NORTH, e - this.minZ),
			new WorldBorderRenderState.DistancePerDirection(Direction.SOUTH, this.maxZ - e),
			new WorldBorderRenderState.DistancePerDirection(Direction.WEST, d - this.minX),
			new WorldBorderRenderState.DistancePerDirection(Direction.EAST, this.maxX - d)
		};
		return Arrays.stream(distancePerDirections).sorted(Comparator.comparingDouble(distancePerDirection -> distancePerDirection.distance)).toList();
	}

	public void reset() {
		this.alpha = 0.0;
	}

	@Environment(EnvType.CLIENT)
	public record DistancePerDirection(Direction direction, double distance) {
	}
}
