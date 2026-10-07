package net.minecraft.client.gui.font;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.util.RandomSource;

@Environment(EnvType.CLIENT)
public record SingleSpriteSource(BakedGlyph glyph) implements GlyphSource {
	@Override
	public BakedGlyph getGlyph(int i) {
		return this.glyph;
	}

	@Override
	public BakedGlyph getRandomGlyph(RandomSource randomSource, int i) {
		return this.glyph;
	}
}
