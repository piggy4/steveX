package net.minecraft.client.renderer.debug;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class NeighborsUpdateRenderer implements DebugRenderer.SimpleDebugRenderer {
	@Override
	public void emitGizmos(double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum, float g) {
		int i = DebugSubscriptions.NEIGHBOR_UPDATES.expireAfterTicks();
		double h = 1.0 / (i * 2);
		Map<BlockPos, NeighborsUpdateRenderer.LastUpdate> map = new HashMap<>();
		debugValueAccess.forEachEvent(DebugSubscriptions.NEIGHBOR_UPDATES, (blockPosx, ix, j) -> {
			long l = j - ix;
			NeighborsUpdateRenderer.LastUpdate lastUpdatex = map.getOrDefault(blockPosx, NeighborsUpdateRenderer.LastUpdate.NONE);
			map.put(blockPosx, lastUpdatex.tryCount((int)l));
		});

		for (Entry<BlockPos, NeighborsUpdateRenderer.LastUpdate> entry : map.entrySet()) {
			BlockPos blockPos = entry.getKey();
			NeighborsUpdateRenderer.LastUpdate lastUpdate = entry.getValue();
			AABB aABB = new AABB(blockPos).inflate(0.002).deflate(h * lastUpdate.age);
			Gizmos.cuboid(aABB, GizmoStyle.stroke(-1));
		}

		for (Entry<BlockPos, NeighborsUpdateRenderer.LastUpdate> entry : map.entrySet()) {
			BlockPos blockPos = entry.getKey();
			NeighborsUpdateRenderer.LastUpdate lastUpdate = entry.getValue();
			Gizmos.billboardText(String.valueOf(lastUpdate.count), Vec3.atCenterOf(blockPos), TextGizmo.Style.whiteAndCentered());
		}
	}

	@Environment(EnvType.CLIENT)
	record LastUpdate(int count, int age) {
		static final NeighborsUpdateRenderer.LastUpdate NONE = new NeighborsUpdateRenderer.LastUpdate(0, Integer.MAX_VALUE);

		public NeighborsUpdateRenderer.LastUpdate tryCount(int i) {
			if (i == this.age) {
				return new NeighborsUpdateRenderer.LastUpdate(this.count + 1, i);
			} else {
				return i < this.age ? new NeighborsUpdateRenderer.LastUpdate(1, i) : this;
			}
		}
	}
}
