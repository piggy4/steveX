package com.mojang.blaze3d.font;

import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.font.FontOption;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface GlyphProvider extends AutoCloseable {
	float BASELINE = 7.0F;

	@Override
	default void close() {
	}

	default @Nullable UnbakedGlyph getGlyph(int i) {
		return null;
	}

	IntSet getSupportedGlyphs();

	@Environment(EnvType.CLIENT)
	record Conditional(GlyphProvider provider, FontOption.Filter filter) implements AutoCloseable {
		@Override
		public void close() {
			this.provider.close();
		}
	}
}
