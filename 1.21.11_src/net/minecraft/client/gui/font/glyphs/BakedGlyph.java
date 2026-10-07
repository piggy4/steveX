package net.minecraft.client.gui.font.glyphs;

import com.mojang.blaze3d.font.GlyphInfo;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface BakedGlyph {
	GlyphInfo info();

	TextRenderable.@Nullable Styled createGlyph(float f, float g, int i, int j, Style style, float h, float k);
}
