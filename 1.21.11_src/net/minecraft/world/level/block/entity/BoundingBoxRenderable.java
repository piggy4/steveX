package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

public interface BoundingBoxRenderable {
	BoundingBoxRenderable.Mode renderMode();

	BoundingBoxRenderable.RenderableBox getRenderableBox();

	enum Mode {
		NONE,
		BOX,
		BOX_AND_INVISIBLE_BLOCKS;
	}

	record RenderableBox(BlockPos localPos, Vec3i size) {
		public static BoundingBoxRenderable.RenderableBox fromCorners(int i, int j, int k, int l, int m, int n) {
			int o = Math.min(i, l);
			int p = Math.min(j, m);
			int q = Math.min(k, n);
			return new BoundingBoxRenderable.RenderableBox(new BlockPos(o, p, q), new Vec3i(Math.max(i, l) - o, Math.max(j, m) - p, Math.max(k, n) - q));
		}
	}
}
