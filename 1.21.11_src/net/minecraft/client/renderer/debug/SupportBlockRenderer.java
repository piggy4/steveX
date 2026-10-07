package net.minecraft.client.renderer.debug;

import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import java.util.function.DoubleSupplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

@Environment(EnvType.CLIENT)
public class SupportBlockRenderer implements DebugRenderer.SimpleDebugRenderer {
	private final Minecraft minecraft;
	private double lastUpdateTime = Double.MIN_VALUE;
	private List<Entity> surroundEntities = Collections.emptyList();

	public SupportBlockRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void emitGizmos(double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum, float g) {
		double h = Util.getNanos();
		if (h - this.lastUpdateTime > 1.0E8) {
			this.lastUpdateTime = h;
			Entity entity = this.minecraft.gameRenderer.getMainCamera().entity();
			this.surroundEntities = ImmutableList.copyOf(entity.level().getEntities(entity, entity.getBoundingBox().inflate(16.0)));
		}

		Player player = this.minecraft.player;
		if (player != null && player.mainSupportingBlockPos.isPresent()) {
			this.drawHighlights(player, () -> 0.0, -65536);
		}

		for (Entity entity2 : this.surroundEntities) {
			if (entity2 != player) {
				this.drawHighlights(entity2, () -> this.getBias(entity2), -16711936);
			}
		}
	}

	private void drawHighlights(Entity entity, DoubleSupplier doubleSupplier, int i) {
		entity.mainSupportingBlockPos.ifPresent(blockPos -> {
			double d = doubleSupplier.getAsDouble();
			BlockPos blockPos2 = entity.getOnPos();
			this.highlightPosition(blockPos2, 0.02 + d, i);
			BlockPos blockPos3 = entity.getOnPosLegacy();
			if (!blockPos3.equals(blockPos2)) {
				this.highlightPosition(blockPos3, 0.04 + d, -16711681);
			}
		});
	}

	private double getBias(Entity entity) {
		return 0.02 * (String.valueOf(entity.getId() + 0.132453657).hashCode() % 1000) / 1000.0;
	}

	private void highlightPosition(BlockPos blockPos, double d, int i) {
		double e = blockPos.getX() - 2.0 * d;
		double f = blockPos.getY() - 2.0 * d;
		double g = blockPos.getZ() - 2.0 * d;
		double h = e + 1.0 + 4.0 * d;
		double j = f + 1.0 + 4.0 * d;
		double k = g + 1.0 + 4.0 * d;
		Gizmos.cuboid(new AABB(e, f, g, h, j, k), GizmoStyle.stroke(ARGB.color(0.4F, i)));
		VoxelShape voxelShape = this.minecraft
			.level
			.getBlockState(blockPos)
			.getCollisionShape(this.minecraft.level, blockPos, CollisionContext.empty())
			.move(blockPos);
		GizmoStyle gizmoStyle = GizmoStyle.stroke(i);

		for (AABB aABB : voxelShape.toAabbs()) {
			Gizmos.cuboid(aABB, gizmoStyle);
		}
	}
}
