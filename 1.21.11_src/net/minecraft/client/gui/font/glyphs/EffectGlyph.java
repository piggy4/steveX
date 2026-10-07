package net.minecraft.client.gui.font.glyphs;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.font.TextRenderable;

@Environment(EnvType.CLIENT)
public interface EffectGlyph {
	TextRenderable createEffect(float f, float g, float h, float i, float j, int k, int l, float m);
}
