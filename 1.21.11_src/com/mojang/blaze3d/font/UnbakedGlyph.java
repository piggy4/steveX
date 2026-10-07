package com.mojang.blaze3d.font;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;

@Environment(EnvType.CLIENT)
public interface UnbakedGlyph {
	GlyphInfo info();

	BakedGlyph bake(UnbakedGlyph.Stitcher stitcher);

	@Environment(EnvType.CLIENT)
	interface Stitcher {
		BakedGlyph stitch(GlyphInfo glyphInfo, GlyphBitmap glyphBitmap);

		BakedGlyph getMissing();
	}
}
