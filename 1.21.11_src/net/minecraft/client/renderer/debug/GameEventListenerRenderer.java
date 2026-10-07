package net.minecraft.client.renderer.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.ARGB;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class GameEventListenerRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final float BOX_HEIGHT = 1.0F;

	private void forEachListener(DebugValueAccess debugValueAccess, GameEventListenerRenderer.ListenerVisitor listenerVisitor) {
		debugValueAccess.forEachBlock(
			DebugSubscriptions.GAME_EVENT_LISTENERS,
			(blockPos, debugGameEventListenerInfo) -> listenerVisitor.accept(blockPos.getCenter(), debugGameEventListenerInfo.listenerRadius())
		);
		debugValueAccess.forEachEntity(
			DebugSubscriptions.GAME_EVENT_LISTENERS,
			(entity, debugGameEventListenerInfo) -> listenerVisitor.accept(entity.position(), debugGameEventListenerInfo.listenerRadius())
		);
	}

	@Override
	public void emitGizmos(double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum, float g) {
		this.forEachListener(debugValueAccess, (vec3, i) -> {
			double dx = i * 2.0;
			Gizmos.cuboid(AABB.ofSize(vec3, dx, dx, dx), GizmoStyle.fill(ARGB.colorFromFloat(0.35F, 1.0F, 1.0F, 0.0F)));
		});
		this.forEachListener(
			debugValueAccess,
			(vec3, i) -> Gizmos.cuboid(AABB.ofSize(vec3, 0.5, 1.0, 0.5).move(0.0, 0.5, 0.0), GizmoStyle.fill(ARGB.colorFromFloat(0.35F, 1.0F, 1.0F, 0.0F)))
		);
		this.forEachListener(debugValueAccess, (vec3, i) -> {
			Gizmos.billboardText("Listener Origin", vec3.add(0.0, 1.8, 0.0), TextGizmo.Style.whiteAndCentered().withScale(0.4F));
			Gizmos.billboardText(BlockPos.containing(vec3).toString(), vec3.add(0.0, 1.5, 0.0), TextGizmo.Style.forColorAndCentered(-6959665).withScale(0.4F));
		});
		debugValueAccess.forEachEvent(
			DebugSubscriptions.GAME_EVENTS,
			(debugGameEventInfo, i, j) -> {
				Vec3 vec3 = debugGameEventInfo.pos();
				double dx = 0.4;
				AABB aABB = AABB.ofSize(vec3.add(0.0, 0.5, 0.0), 0.4, 0.9, 0.4);
				Gizmos.cuboid(aABB, GizmoStyle.fill(ARGB.colorFromFloat(0.2F, 1.0F, 1.0F, 1.0F)));
				Gizmos.billboardText(
					debugGameEventInfo.event().getRegisteredName(), vec3.add(0.0, 0.85, 0.0), TextGizmo.Style.forColorAndCentered(-7564911).withScale(0.12F)
				);
			}
		);
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	interface ListenerVisitor {
		void accept(Vec3 vec3, int i);
	}
}
