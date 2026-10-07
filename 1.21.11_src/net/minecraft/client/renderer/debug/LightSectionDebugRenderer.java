package net.minecraft.client.renderer.debug;

import java.time.Duration;
import java.time.Instant;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class LightSectionDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private static final Duration REFRESH_INTERVAL = Duration.ofMillis(500L);
	private static final int RADIUS = 10;
	private static final int LIGHT_AND_BLOCKS_COLOR = ARGB.colorFromFloat(0.25F, 1.0F, 1.0F, 0.0F);
	private static final int LIGHT_ONLY_COLOR = ARGB.colorFromFloat(0.125F, 0.25F, 0.125F, 0.0F);
	private final Minecraft minecraft;
	private final LightLayer lightLayer;
	private Instant lastUpdateTime = Instant.now();
	private LightSectionDebugRenderer.@Nullable SectionData data;

	public LightSectionDebugRenderer(Minecraft minecraft, LightLayer lightLayer) {
		this.minecraft = minecraft;
		this.lightLayer = lightLayer;
	}

	@Override
	public void emitGizmos(double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum, float g) {
		Instant instant = Instant.now();
		if (this.data == null || Duration.between(this.lastUpdateTime, instant).compareTo(REFRESH_INTERVAL) > 0) {
			this.lastUpdateTime = instant;
			this.data = new LightSectionDebugRenderer.SectionData(
				this.minecraft.level.getLightEngine(), SectionPos.of(this.minecraft.player.blockPosition()), 10, this.lightLayer
			);
		}

		renderEdges(this.data.lightAndBlocksShape, this.data.minPos, LIGHT_AND_BLOCKS_COLOR);
		renderEdges(this.data.lightShape, this.data.minPos, LIGHT_ONLY_COLOR);
		renderFaces(this.data.lightAndBlocksShape, this.data.minPos, LIGHT_AND_BLOCKS_COLOR);
		renderFaces(this.data.lightShape, this.data.minPos, LIGHT_ONLY_COLOR);
	}

	private static void renderFaces(DiscreteVoxelShape discreteVoxelShape, SectionPos sectionPos, int i) {
		discreteVoxelShape.forAllFaces((direction, j, k, l) -> {
			int m = j + sectionPos.getX();
			int n = k + sectionPos.getY();
			int o = l + sectionPos.getZ();
			renderFace(direction, m, n, o, i);
		});
	}

	private static void renderEdges(DiscreteVoxelShape discreteVoxelShape, SectionPos sectionPos, int i) {
		discreteVoxelShape.forAllEdges((j, k, l, m, n, o) -> {
			int p = j + sectionPos.getX();
			int q = k + sectionPos.getY();
			int r = l + sectionPos.getZ();
			int s = m + sectionPos.getX();
			int t = n + sectionPos.getY();
			int u = o + sectionPos.getZ();
			renderEdge(p, q, r, s, t, u, i);
		}, true);
	}

	private static void renderFace(Direction direction, int i, int j, int k, int l) {
		Vec3 vec3 = new Vec3(SectionPos.sectionToBlockCoord(i), SectionPos.sectionToBlockCoord(j), SectionPos.sectionToBlockCoord(k));
		Vec3 vec32 = vec3.add(16.0, 16.0, 16.0);
		Gizmos.rect(vec3, vec32, direction, GizmoStyle.fill(l));
	}

	private static void renderEdge(int i, int j, int k, int l, int m, int n, int o) {
		double d = SectionPos.sectionToBlockCoord(i);
		double e = SectionPos.sectionToBlockCoord(j);
		double f = SectionPos.sectionToBlockCoord(k);
		double g = SectionPos.sectionToBlockCoord(l);
		double h = SectionPos.sectionToBlockCoord(m);
		double p = SectionPos.sectionToBlockCoord(n);
		int q = ARGB.opaque(o);
		Gizmos.line(new Vec3(d, e, f), new Vec3(g, h, p), q);
	}

	@Environment(EnvType.CLIENT)
	static final class SectionData {
		final DiscreteVoxelShape lightAndBlocksShape;
		final DiscreteVoxelShape lightShape;
		final SectionPos minPos;

		SectionData(LevelLightEngine levelLightEngine, SectionPos sectionPos, int i, LightLayer lightLayer) {
			int j = i * 2 + 1;
			this.lightAndBlocksShape = new BitSetDiscreteVoxelShape(j, j, j);
			this.lightShape = new BitSetDiscreteVoxelShape(j, j, j);

			for (int k = 0; k < j; k++) {
				for (int l = 0; l < j; l++) {
					for (int m = 0; m < j; m++) {
						SectionPos sectionPos2 = SectionPos.of(sectionPos.x() + m - i, sectionPos.y() + l - i, sectionPos.z() + k - i);
						LayerLightSectionStorage.SectionType sectionType = levelLightEngine.getDebugSectionType(lightLayer, sectionPos2);
						if (sectionType == LayerLightSectionStorage.SectionType.LIGHT_AND_DATA) {
							this.lightAndBlocksShape.fill(m, l, k);
							this.lightShape.fill(m, l, k);
						} else if (sectionType == LayerLightSectionStorage.SectionType.LIGHT_ONLY) {
							this.lightShape.fill(m, l, k);
						}
					}
				}
			}

			this.minPos = SectionPos.of(sectionPos.x() - i, sectionPos.y() - i, sectionPos.z() - i);
		}
	}
}
