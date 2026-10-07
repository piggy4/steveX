package net.minecraft.client.renderer.debug;

import com.google.common.collect.Maps;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class GameTestBlockHighlightRenderer {
	private static final int SHOW_POS_DURATION_MS = 10000;
	private static final float PADDING = 0.02F;
	private final Map<BlockPos, GameTestBlockHighlightRenderer.Marker> markers = Maps.newHashMap();

	public void highlightPos(BlockPos blockPos, BlockPos blockPos2) {
		String string = blockPos2.toShortString();
		this.markers.put(blockPos, new GameTestBlockHighlightRenderer.Marker(1610678016, string, Util.getMillis() + 10000L));
	}

	public void clear() {
		this.markers.clear();
	}

	public void emitGizmos() {
		long l = Util.getMillis();
		this.markers.entrySet().removeIf(entry -> l > entry.getValue().removeAtTime);
		this.markers.forEach((blockPos, marker) -> this.renderMarker(blockPos, marker));
	}

	private void renderMarker(BlockPos blockPos, GameTestBlockHighlightRenderer.Marker marker) {
		Gizmos.cuboid(blockPos, 0.02F, GizmoStyle.fill(marker.color()));
		if (!marker.text.isEmpty()) {
			Gizmos.billboardText(marker.text, Vec3.atLowerCornerWithOffset(blockPos, 0.5, 1.2, 0.5), TextGizmo.Style.whiteAndCentered().withScale(0.16F))
				.setAlwaysOnTop();
		}
	}

	@Environment(EnvType.CLIENT)
	record Marker(int color, String text, long removeAtTime) {
	}
}
