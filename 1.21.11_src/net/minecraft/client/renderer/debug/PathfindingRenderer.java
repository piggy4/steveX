package net.minecraft.client.renderer.debug;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class PathfindingRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final float MAX_RENDER_DIST = 80.0F;
	private static final int MAX_TARGETING_DIST = 8;
	private static final boolean SHOW_ONLY_SELECTED = false;
	private static final boolean SHOW_OPEN_CLOSED = true;
	private static final boolean SHOW_OPEN_CLOSED_COST_MALUS = false;
	private static final boolean SHOW_OPEN_CLOSED_NODE_TYPE_WITH_TEXT = false;
	private static final boolean SHOW_OPEN_CLOSED_NODE_TYPE_WITH_BOX = true;
	private static final boolean SHOW_GROUND_LABELS = true;
	private static final float TEXT_SCALE = 0.32F;

	@Override
	public void emitGizmos(double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum, float g) {
		debugValueAccess.forEachEntity(
			DebugSubscriptions.ENTITY_PATHS, (entity, debugPathInfo) -> renderPath(d, e, f, debugPathInfo.path(), debugPathInfo.maxNodeDistance())
		);
	}

	private static void renderPath(double d, double e, double f, Path path, float g) {
		renderPath(path, g, true, true, d, e, f);
	}

	public static void renderPath(Path path, float f, boolean bl, boolean bl2, double d, double e, double g) {
		renderPathLine(path, d, e, g);
		BlockPos blockPos = path.getTarget();
		if (distanceToCamera(blockPos, d, e, g) <= 80.0F) {
			Gizmos.cuboid(
				new AABB(
					blockPos.getX() + 0.25F, blockPos.getY() + 0.25F, blockPos.getZ() + 0.25, blockPos.getX() + 0.75F, blockPos.getY() + 0.75F, blockPos.getZ() + 0.75F
				),
				GizmoStyle.fill(ARGB.colorFromFloat(0.5F, 0.0F, 1.0F, 0.0F))
			);

			for (int i = 0; i < path.getNodeCount(); i++) {
				Node node = path.getNode(i);
				if (distanceToCamera(node.asBlockPos(), d, e, g) <= 80.0F) {
					float h = i == path.getNextNodeIndex() ? 1.0F : 0.0F;
					float j = i == path.getNextNodeIndex() ? 0.0F : 1.0F;
					AABB aABB = new AABB(node.x + 0.5F - f, node.y + 0.01F * i, node.z + 0.5F - f, node.x + 0.5F + f, node.y + 0.25F + 0.01F * i, node.z + 0.5F + f);
					Gizmos.cuboid(aABB, GizmoStyle.fill(ARGB.colorFromFloat(0.5F, h, 0.0F, j)));
				}
			}
		}

		Path.DebugData debugData = path.debugData();
		if (bl && debugData != null) {
			for (Node node2 : debugData.closedSet()) {
				if (distanceToCamera(node2.asBlockPos(), d, e, g) <= 80.0F) {
					Gizmos.cuboid(
						new AABB(node2.x + 0.5F - f / 2.0F, node2.y + 0.01F, node2.z + 0.5F - f / 2.0F, node2.x + 0.5F + f / 2.0F, node2.y + 0.1, node2.z + 0.5F + f / 2.0F),
						GizmoStyle.fill(ARGB.colorFromFloat(0.5F, 1.0F, 0.8F, 0.8F))
					);
				}
			}

			for (Node node2 : debugData.openSet()) {
				if (distanceToCamera(node2.asBlockPos(), d, e, g) <= 80.0F) {
					Gizmos.cuboid(
						new AABB(node2.x + 0.5F - f / 2.0F, node2.y + 0.01F, node2.z + 0.5F - f / 2.0F, node2.x + 0.5F + f / 2.0F, node2.y + 0.1, node2.z + 0.5F + f / 2.0F),
						GizmoStyle.fill(ARGB.colorFromFloat(0.5F, 0.8F, 1.0F, 1.0F))
					);
				}
			}
		}

		if (bl2) {
			for (int k = 0; k < path.getNodeCount(); k++) {
				Node node3 = path.getNode(k);
				if (distanceToCamera(node3.asBlockPos(), d, e, g) <= 80.0F) {
					Gizmos.billboardText(
							String.valueOf(node3.type), new Vec3(node3.x + 0.5, node3.y + 0.75, node3.z + 0.5), TextGizmo.Style.whiteAndCentered().withScale(0.32F)
						)
						.setAlwaysOnTop();
					Gizmos.billboardText(
							String.format(Locale.ROOT, "%.2f", node3.costMalus),
							new Vec3(node3.x + 0.5, node3.y + 0.25, node3.z + 0.5),
							TextGizmo.Style.whiteAndCentered().withScale(0.32F)
						)
						.setAlwaysOnTop();
				}
			}
		}
	}

	public static void renderPathLine(Path path, double d, double e, double f) {
		if (path.getNodeCount() >= 2) {
			Vec3 vec3 = path.getNode(0).asVec3();

			for (int i = 1; i < path.getNodeCount(); i++) {
				Node node = path.getNode(i);
				if (distanceToCamera(node.asBlockPos(), d, e, f) > 80.0F) {
					vec3 = node.asVec3();
				} else {
					float g = (float)i / path.getNodeCount() * 0.33F;
					int j = ARGB.opaque(Mth.hsvToRgb(g, 0.9F, 0.9F));
					Gizmos.arrow(vec3.add(0.5, 0.5, 0.5), node.asVec3().add(0.5, 0.5, 0.5), j);
					vec3 = node.asVec3();
				}
			}
		}
	}

	private static float distanceToCamera(BlockPos blockPos, double d, double e, double f) {
		return (float)(Math.abs(blockPos.getX() - d) + Math.abs(blockPos.getY() - e) + Math.abs(blockPos.getZ() - f));
	}
}
