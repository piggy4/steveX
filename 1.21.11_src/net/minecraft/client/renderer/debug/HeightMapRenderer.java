package net.minecraft.client.renderer.debug;

import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class HeightMapRenderer implements DebugRenderer.SimpleDebugRenderer {
	private final Minecraft minecraft;
	private static final int CHUNK_DIST = 2;
	private static final float BOX_HEIGHT = 0.09375F;

	public HeightMapRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	@Override
	public void emitGizmos(double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum, float g) {
		LevelAccessor levelAccessor = this.minecraft.level;
		BlockPos blockPos = BlockPos.containing(d, 0.0, f);

		for (int i = -2; i <= 2; i++) {
			for (int j = -2; j <= 2; j++) {
				ChunkAccess chunkAccess = levelAccessor.getChunk(blockPos.offset(i * 16, 0, j * 16));

				for (Entry<Heightmap.Types, Heightmap> entry : chunkAccess.getHeightmaps()) {
					Heightmap.Types types = entry.getKey();
					ChunkPos chunkPos = chunkAccess.getPos();
					Vector3f vector3f = this.getColor(types);

					for (int k = 0; k < 16; k++) {
						for (int l = 0; l < 16; l++) {
							int m = SectionPos.sectionToBlockCoord(chunkPos.x, k);
							int n = SectionPos.sectionToBlockCoord(chunkPos.z, l);
							float h = levelAccessor.getHeight(types, m, n) + types.ordinal() * 0.09375F;
							Gizmos.cuboid(
								new AABB(m + 0.25F, h, n + 0.25F, m + 0.75F, h + 0.09375F, n + 0.75F),
								GizmoStyle.fill(ARGB.colorFromFloat(1.0F, vector3f.x(), vector3f.y(), vector3f.z()))
							);
						}
					}
				}
			}
		}
	}

	private Vector3f getColor(Heightmap.Types types) {
		return switch (types) {
			case WORLD_SURFACE_WG -> new Vector3f(1.0F, 1.0F, 0.0F);
			case OCEAN_FLOOR_WG -> new Vector3f(1.0F, 0.0F, 1.0F);
			case WORLD_SURFACE -> new Vector3f(0.0F, 0.7F, 0.0F);
			case OCEAN_FLOOR -> new Vector3f(0.0F, 0.0F, 0.5F);
			case MOTION_BLOCKING -> new Vector3f(0.0F, 0.3F, 0.3F);
			case MOTION_BLOCKING_NO_LEAVES -> new Vector3f(0.0F, 0.5F, 0.5F);
		};
	}
}
