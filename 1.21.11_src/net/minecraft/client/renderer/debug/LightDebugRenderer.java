package net.minecraft.client.renderer.debug;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.ARGB;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class LightDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
	private final Minecraft minecraft;
	private final boolean showBlockLight;
	private final boolean showSkyLight;
	private static final int MAX_RENDER_DIST = 10;

	public LightDebugRenderer(Minecraft minecraft, boolean bl, boolean bl2) {
		this.minecraft = minecraft;
		this.showBlockLight = bl;
		this.showSkyLight = bl2;
	}

	@Override
	public void emitGizmos(double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum, float g) {
		Level level = this.minecraft.level;
		BlockPos blockPos = BlockPos.containing(d, e, f);
		LongSet longSet = new LongOpenHashSet();

		for (BlockPos blockPos2 : BlockPos.betweenClosed(blockPos.offset(-10, -10, -10), blockPos.offset(10, 10, 10))) {
			int i = level.getBrightness(LightLayer.SKY, blockPos2);
			long l = SectionPos.blockToSection(blockPos2.asLong());
			if (longSet.add(l)) {
				Gizmos.billboardText(
					level.getChunkSource().getLightEngine().getDebugData(LightLayer.SKY, SectionPos.of(l)),
					new Vec3(
						SectionPos.sectionToBlockCoord(SectionPos.x(l), 8),
						SectionPos.sectionToBlockCoord(SectionPos.y(l), 8),
						SectionPos.sectionToBlockCoord(SectionPos.z(l), 8)
					),
					TextGizmo.Style.forColorAndCentered(-65536).withScale(4.8F)
				);
			}

			if (i != 15 && this.showSkyLight) {
				int j = ARGB.srgbLerp(i / 15.0F, -16776961, -16711681);
				Gizmos.billboardText(String.valueOf(i), Vec3.atLowerCornerWithOffset(blockPos2, 0.5, 0.25, 0.5), TextGizmo.Style.forColorAndCentered(j));
			}

			if (this.showBlockLight) {
				int j = level.getBrightness(LightLayer.BLOCK, blockPos2);
				if (j != 0) {
					int k = ARGB.srgbLerp(j / 15.0F, -5636096, -256);
					Gizmos.billboardText(String.valueOf(level.getBrightness(LightLayer.BLOCK, blockPos2)), Vec3.atCenterOf(blockPos2), TextGizmo.Style.forColorAndCentered(k));
				}
			}
		}
	}
}
