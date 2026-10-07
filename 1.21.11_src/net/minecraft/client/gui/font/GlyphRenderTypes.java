package net.minecraft.client.gui.font;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public record GlyphRenderTypes(RenderType normal, RenderType seeThrough, RenderType polygonOffset, RenderPipeline guiPipeline) {
	public static GlyphRenderTypes createForIntensityTexture(Identifier identifier) {
		return new GlyphRenderTypes(
			RenderTypes.textIntensity(identifier),
			RenderTypes.textIntensitySeeThrough(identifier),
			RenderTypes.textIntensityPolygonOffset(identifier),
			RenderPipelines.GUI_TEXT_INTENSITY
		);
	}

	public static GlyphRenderTypes createForColorTexture(Identifier identifier) {
		return new GlyphRenderTypes(
			RenderTypes.text(identifier), RenderTypes.textSeeThrough(identifier), RenderTypes.textPolygonOffset(identifier), RenderPipelines.GUI_TEXT
		);
	}

	public RenderType select(Font.DisplayMode displayMode) {
		return switch (displayMode) {
			case NORMAL -> this.normal;
			case SEE_THROUGH -> this.seeThrough;
			case POLYGON_OFFSET -> this.polygonOffset;
		};
	}
}
