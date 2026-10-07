package net.minecraft.client.gui.font;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class PlayerGlyphProvider {
	static final GlyphInfo GLYPH_INFO = GlyphInfo.simple(8.0F);
	final PlayerSkinRenderCache playerSkinRenderCache;
	private final LoadingCache<FontDescription.PlayerSprite, GlyphSource> wrapperCache = CacheBuilder.newBuilder()
		.expireAfterAccess(PlayerSkinRenderCache.CACHE_DURATION)
		.build(new CacheLoader<FontDescription.PlayerSprite, GlyphSource>() {
			public GlyphSource load(FontDescription.PlayerSprite playerSprite) {
				final Supplier<PlayerSkinRenderCache.RenderInfo> supplier = PlayerGlyphProvider.this.playerSkinRenderCache.createLookup(playerSprite.profile());
				final boolean bl = playerSprite.hat();
				return new SingleSpriteSource(new BakedGlyph() {
					@Override
					public GlyphInfo info() {
						return PlayerGlyphProvider.GLYPH_INFO;
					}

					@Override
					public TextRenderable.Styled createGlyph(float f, float g, int i, int j, Style style, float h, float k) {
						return new PlayerGlyphProvider.Instance(supplier, bl, f, g, i, j, k, style);
					}
				});
			}
		});

	public PlayerGlyphProvider(PlayerSkinRenderCache playerSkinRenderCache) {
		this.playerSkinRenderCache = playerSkinRenderCache;
	}

	public GlyphSource sourceForPlayer(FontDescription.PlayerSprite playerSprite) {
		return this.wrapperCache.getUnchecked(playerSprite);
	}

	@Environment(EnvType.CLIENT)
	record Instance(Supplier<PlayerSkinRenderCache.RenderInfo> skin, boolean hat, float x, float y, int color, int shadowColor, float shadowOffset, Style style)
		implements PlainTextRenderable {
		@Override
		public void renderSprite(Matrix4f matrix4f, VertexConsumer vertexConsumer, int i, float f, float g, float h, int j) {
			float k = f + this.left();
			float l = f + this.right();
			float m = g + this.top();
			float n = g + this.bottom();
			renderQuad(matrix4f, vertexConsumer, i, k, l, m, n, h, j, 8.0F, 8.0F, 8, 8, 64, 64);
			if (this.hat) {
				renderQuad(matrix4f, vertexConsumer, i, k, l, m, n, h, j, 40.0F, 8.0F, 8, 8, 64, 64);
			}
		}

		private static void renderQuad(
			Matrix4f matrix4f, VertexConsumer vertexConsumer, int i, float f, float g, float h, float j, float k, int l, float m, float n, int o, int p, int q, int r
		) {
			float s = (m + 0.0F) / q;
			float t = (m + o) / q;
			float u = (n + 0.0F) / r;
			float v = (n + p) / r;
			vertexConsumer.addVertex(matrix4f, f, h, k).setUv(s, u).setColor(l).setLight(i);
			vertexConsumer.addVertex(matrix4f, f, j, k).setUv(s, v).setColor(l).setLight(i);
			vertexConsumer.addVertex(matrix4f, g, j, k).setUv(t, v).setColor(l).setLight(i);
			vertexConsumer.addVertex(matrix4f, g, h, k).setUv(t, u).setColor(l).setLight(i);
		}

		@Override
		public RenderType renderType(Font.DisplayMode displayMode) {
			return this.skin.get().glyphRenderTypes().select(displayMode);
		}

		@Override
		public RenderPipeline guiPipeline() {
			return this.skin.get().glyphRenderTypes().guiPipeline();
		}

		@Override
		public GpuTextureView textureView() {
			return this.skin.get().textureView();
		}
	}
}
