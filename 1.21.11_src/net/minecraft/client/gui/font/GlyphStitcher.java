package net.minecraft.client.gui.font;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class GlyphStitcher implements AutoCloseable {
	private final TextureManager textureManager;
	private final Identifier texturePrefix;
	private final List<FontTexture> textures = new ArrayList<>();

	public GlyphStitcher(TextureManager textureManager, Identifier identifier) {
		this.textureManager = textureManager;
		this.texturePrefix = identifier;
	}

	public void reset() {
		int i = this.textures.size();
		this.textures.clear();

		for (int j = 0; j < i; j++) {
			this.textureManager.release(this.textureName(j));
		}
	}

	@Override
	public void close() {
		this.reset();
	}

	public @Nullable BakedSheetGlyph stitch(GlyphInfo glyphInfo, GlyphBitmap glyphBitmap) {
		for (FontTexture fontTexture : this.textures) {
			BakedSheetGlyph bakedSheetGlyph = fontTexture.add(glyphInfo, glyphBitmap);
			if (bakedSheetGlyph != null) {
				return bakedSheetGlyph;
			}
		}

		int i = this.textures.size();
		Identifier identifier = this.textureName(i);
		boolean bl = glyphBitmap.isColored();
		GlyphRenderTypes glyphRenderTypes = bl ? GlyphRenderTypes.createForColorTexture(identifier) : GlyphRenderTypes.createForIntensityTexture(identifier);
		FontTexture fontTexture2 = new FontTexture(identifier::toString, glyphRenderTypes, bl);
		this.textures.add(fontTexture2);
		this.textureManager.register(identifier, fontTexture2);
		return fontTexture2.add(glyphInfo, glyphBitmap);
	}

	private Identifier textureName(int i) {
		return this.texturePrefix.withSuffix("/" + i);
	}
}
