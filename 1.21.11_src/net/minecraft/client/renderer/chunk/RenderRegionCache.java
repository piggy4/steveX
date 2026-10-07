package net.minecraft.client.renderer.chunk;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

@Environment(EnvType.CLIENT)
public class RenderRegionCache {
	private final Long2ObjectMap<SectionCopy> sectionCopyCache = new Long2ObjectOpenHashMap<>();

	public RenderSectionRegion createRegion(Level level, long l) {
		int i = SectionPos.x(l);
		int j = SectionPos.y(l);
		int k = SectionPos.z(l);
		int m = i - 1;
		int n = j - 1;
		int o = k - 1;
		int p = i + 1;
		int q = j + 1;
		int r = k + 1;
		SectionCopy[] sectionCopys = new SectionCopy[27];

		for (int s = o; s <= r; s++) {
			for (int t = n; t <= q; t++) {
				for (int u = m; u <= p; u++) {
					int v = RenderSectionRegion.index(m, n, o, u, t, s);
					sectionCopys[v] = this.getSectionDataCopy(level, u, t, s);
				}
			}
		}

		return new RenderSectionRegion(level, m, n, o, sectionCopys);
	}

	private SectionCopy getSectionDataCopy(Level level, int i, int j, int k) {
		return this.sectionCopyCache.computeIfAbsent(SectionPos.asLong(i, j, k), l -> {
			LevelChunk levelChunk = level.getChunk(i, k);
			return new SectionCopy(levelChunk, levelChunk.getSectionIndexFromSectionY(j));
		});
	}
}
