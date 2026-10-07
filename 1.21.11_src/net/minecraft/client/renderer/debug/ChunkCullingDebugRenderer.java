package net.minecraft.client.renderer.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public class ChunkCullingDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	public static final Direction[] DIRECTIONS = Direction.values();
	private final Minecraft minecraft;

	public ChunkCullingDebugRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void emitGizmos(double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum, float g) {
		LevelRenderer levelRenderer = this.minecraft.levelRenderer;
		boolean bl = this.minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.CHUNK_SECTION_PATHS);
		boolean bl2 = this.minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.CHUNK_SECTION_VISIBILITY);
		if (bl || bl2) {
			SectionOcclusionGraph sectionOcclusionGraph = levelRenderer.getSectionOcclusionGraph();

			for (SectionRenderDispatcher.RenderSection renderSection : levelRenderer.getVisibleSections()) {
				SectionOcclusionGraph.Node node = sectionOcclusionGraph.getNode(renderSection);
				if (node != null) {
					BlockPos blockPos = renderSection.getRenderOrigin();
					if (bl) {
						int i = node.step == 0 ? 0 : Mth.hsvToRgb(node.step / 50.0F, 0.9F, 0.9F);

						for (int j = 0; j < DIRECTIONS.length; j++) {
							if (node.hasSourceDirection(j)) {
								Direction direction = DIRECTIONS[j];
								Gizmos.line(
									Vec3.atLowerCornerWithOffset(blockPos, 8.0, 8.0, 8.0),
									Vec3.atLowerCornerWithOffset(blockPos, 8 - 16 * direction.getStepX(), 8 - 16 * direction.getStepY(), 8 - 16 * direction.getStepZ()),
									ARGB.opaque(i)
								);
							}
						}
					}

					if (bl2 && renderSection.getSectionMesh().hasRenderableLayers()) {
						int i = 0;

						for (Direction direction2 : DIRECTIONS) {
							for (Direction direction3 : DIRECTIONS) {
								boolean bl3 = renderSection.getSectionMesh().facesCanSeeEachother(direction2, direction3);
								if (!bl3) {
									i++;
									Gizmos.line(
										Vec3.atLowerCornerWithOffset(blockPos, 8 + 8 * direction2.getStepX(), 8 + 8 * direction2.getStepY(), 8 + 8 * direction2.getStepZ()),
										Vec3.atLowerCornerWithOffset(blockPos, 8 + 8 * direction3.getStepX(), 8 + 8 * direction3.getStepY(), 8 + 8 * direction3.getStepZ()),
										ARGB.color(255, 255, 0, 0)
									);
								}
							}
						}

						if (i > 0) {
							float h = 0.5F;
							float k = 0.2F;
							Gizmos.cuboid(renderSection.getBoundingBox().deflate(0.5), GizmoStyle.fill(ARGB.colorFromFloat(0.2F, 0.9F, 0.9F, 0.0F)));
						}
					}
				}
			}
		}

		Frustum frustum2 = levelRenderer.getCapturedFrustum();
		if (frustum2 != null) {
			Vec3 vec3 = new Vec3(frustum2.getCamX(), frustum2.getCamY(), frustum2.getCamZ());
			Vector4f[] vector4fs = frustum2.getFrustumPoints();
			this.addFrustumQuad(vec3, vector4fs, 0, 1, 2, 3, 0, 1, 1);
			this.addFrustumQuad(vec3, vector4fs, 4, 5, 6, 7, 1, 0, 0);
			this.addFrustumQuad(vec3, vector4fs, 0, 1, 5, 4, 1, 1, 0);
			this.addFrustumQuad(vec3, vector4fs, 2, 3, 7, 6, 0, 0, 1);
			this.addFrustumQuad(vec3, vector4fs, 0, 4, 7, 3, 0, 1, 0);
			this.addFrustumQuad(vec3, vector4fs, 1, 5, 6, 2, 1, 0, 1);
			this.addFrustumLine(vec3, vector4fs[0], vector4fs[1]);
			this.addFrustumLine(vec3, vector4fs[1], vector4fs[2]);
			this.addFrustumLine(vec3, vector4fs[2], vector4fs[3]);
			this.addFrustumLine(vec3, vector4fs[3], vector4fs[0]);
			this.addFrustumLine(vec3, vector4fs[4], vector4fs[5]);
			this.addFrustumLine(vec3, vector4fs[5], vector4fs[6]);
			this.addFrustumLine(vec3, vector4fs[6], vector4fs[7]);
			this.addFrustumLine(vec3, vector4fs[7], vector4fs[4]);
			this.addFrustumLine(vec3, vector4fs[0], vector4fs[4]);
			this.addFrustumLine(vec3, vector4fs[1], vector4fs[5]);
			this.addFrustumLine(vec3, vector4fs[2], vector4fs[6]);
			this.addFrustumLine(vec3, vector4fs[3], vector4fs[7]);
		}
	}

	private void addFrustumLine(Vec3 vec3, Vector4f vector4f, Vector4f vector4f2) {
		Gizmos.line(
			new Vec3(vec3.x + vector4f.x, vec3.y + vector4f.y, vec3.z + vector4f.z),
			new Vec3(vec3.x + vector4f2.x, vec3.y + vector4f2.y, vec3.z + vector4f2.z),
			-16777216
		);
	}

	private void addFrustumQuad(Vec3 vec3, Vector4f[] vector4fs, int i, int j, int k, int l, int m, int n, int o) {
		float f = 0.25F;
		Gizmos.rect(
			new Vec3(vector4fs[i].x(), vector4fs[i].y(), vector4fs[i].z()).add(vec3),
			new Vec3(vector4fs[j].x(), vector4fs[j].y(), vector4fs[j].z()).add(vec3),
			new Vec3(vector4fs[k].x(), vector4fs[k].y(), vector4fs[k].z()).add(vec3),
			new Vec3(vector4fs[l].x(), vector4fs[l].y(), vector4fs[l].z()).add(vec3),
			GizmoStyle.fill(ARGB.colorFromFloat(0.25F, m, n, o))
		);
	}
}
