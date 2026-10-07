package net.minecraft.client.gui;

import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;
import com.ibm.icu.text.Bidi;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.font.EmptyArea;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringDecomposer;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class Font {
	private static final float EFFECT_DEPTH = 0.01F;
	private static final float OVER_EFFECT_DEPTH = 0.01F;
	private static final float UNDER_EFFECT_DEPTH = -0.01F;
	public static final float SHADOW_DEPTH = 0.03F;
	public final int lineHeight = 9;
	private final RandomSource random = RandomSource.create();
	final Font.Provider provider;
	private final StringSplitter splitter;

	public Font(Font.Provider provider) {
		this.provider = provider;
		this.splitter = new StringSplitter((i, style) -> this.getGlyphSource(style.getFont()).getGlyph(i).info().getAdvance(style.isBold()));
	}

	private GlyphSource getGlyphSource(FontDescription fontDescription) {
		return this.provider.glyphs(fontDescription);
	}

	public String bidirectionalShaping(String string) {
		try {
			Bidi bidi = new Bidi(new ArabicShaping(8).shape(string), 127);
			bidi.setReorderingMode(0);
			return bidi.writeReordered(2);
		} catch (ArabicShapingException var3) {
			return string;
		}
	}

	public void drawInBatch(
		String string, float f, float g, int i, boolean bl, Matrix4f matrix4f, MultiBufferSource multiBufferSource, Font.DisplayMode displayMode, int j, int k
	) {
		Font.PreparedText preparedText = this.prepareText(string, f, g, i, bl, j);
		preparedText.visit(Font.GlyphVisitor.forMultiBufferSource(multiBufferSource, matrix4f, displayMode, k));
	}

	public void drawInBatch(
		Component component, float f, float g, int i, boolean bl, Matrix4f matrix4f, MultiBufferSource multiBufferSource, Font.DisplayMode displayMode, int j, int k
	) {
		Font.PreparedText preparedText = this.prepareText(component.getVisualOrderText(), f, g, i, bl, false, j);
		preparedText.visit(Font.GlyphVisitor.forMultiBufferSource(multiBufferSource, matrix4f, displayMode, k));
	}

	public void drawInBatch(
		FormattedCharSequence formattedCharSequence,
		float f,
		float g,
		int i,
		boolean bl,
		Matrix4f matrix4f,
		MultiBufferSource multiBufferSource,
		Font.DisplayMode displayMode,
		int j,
		int k
	) {
		Font.PreparedText preparedText = this.prepareText(formattedCharSequence, f, g, i, bl, false, j);
		preparedText.visit(Font.GlyphVisitor.forMultiBufferSource(multiBufferSource, matrix4f, displayMode, k));
	}

	public void drawInBatch8xOutline(
		FormattedCharSequence formattedCharSequence, float f, float g, int i, int j, Matrix4f matrix4f, MultiBufferSource multiBufferSource, int k
	) {
		Font.PreparedTextBuilder preparedTextBuilder = new Font.PreparedTextBuilder(0.0F, 0.0F, j, false, false);

		for (int l = -1; l <= 1; l++) {
			for (int m = -1; m <= 1; m++) {
				if (l != 0 || m != 0) {
					float[] fs = new float[]{f};
					int n = l;
					int o = m;
					formattedCharSequence.accept((lx, style, mx) -> {
						boolean bl = style.isBold();
						BakedGlyph bakedGlyph = this.getGlyph(mx, style);
						preparedTextBuilder.x = fs[0] + n * bakedGlyph.info().getShadowOffset();
						preparedTextBuilder.y = g + o * bakedGlyph.info().getShadowOffset();
						fs[0] += bakedGlyph.info().getAdvance(bl);
						return preparedTextBuilder.accept(lx, style.withColor(j), bakedGlyph);
					});
				}
			}
		}

		Font.GlyphVisitor glyphVisitor = Font.GlyphVisitor.forMultiBufferSource(multiBufferSource, matrix4f, Font.DisplayMode.NORMAL, k);

		for (TextRenderable.Styled styled : preparedTextBuilder.glyphs) {
			glyphVisitor.acceptGlyph(styled);
		}

		Font.PreparedTextBuilder preparedTextBuilder2 = new Font.PreparedTextBuilder(f, g, i, false, true);
		formattedCharSequence.accept(preparedTextBuilder2);
		preparedTextBuilder2.visit(Font.GlyphVisitor.forMultiBufferSource(multiBufferSource, matrix4f, Font.DisplayMode.POLYGON_OFFSET, k));
	}

	BakedGlyph getGlyph(int i, Style style) {
		GlyphSource glyphSource = this.getGlyphSource(style.getFont());
		BakedGlyph bakedGlyph = glyphSource.getGlyph(i);
		if (style.isObfuscated() && i != 32) {
			int j = Mth.ceil(bakedGlyph.info().getAdvance(false));
			bakedGlyph = glyphSource.getRandomGlyph(this.random, j);
		}

		return bakedGlyph;
	}

	public Font.PreparedText prepareText(String string, float f, float g, int i, boolean bl, int j) {
		if (this.isBidirectional()) {
			string = this.bidirectionalShaping(string);
		}

		Font.PreparedTextBuilder preparedTextBuilder = new Font.PreparedTextBuilder(f, g, i, j, bl, false);
		StringDecomposer.iterateFormatted(string, Style.EMPTY, preparedTextBuilder);
		return preparedTextBuilder;
	}

	public Font.PreparedText prepareText(FormattedCharSequence formattedCharSequence, float f, float g, int i, boolean bl, boolean bl2, int j) {
		Font.PreparedTextBuilder preparedTextBuilder = new Font.PreparedTextBuilder(f, g, i, j, bl, bl2);
		formattedCharSequence.accept(preparedTextBuilder);
		return preparedTextBuilder;
	}

	public int width(String string) {
		return Mth.ceil(this.splitter.stringWidth(string));
	}

	public int width(FormattedText formattedText) {
		return Mth.ceil(this.splitter.stringWidth(formattedText));
	}

	public int width(FormattedCharSequence formattedCharSequence) {
		return Mth.ceil(this.splitter.stringWidth(formattedCharSequence));
	}

	public String plainSubstrByWidth(String string, int i, boolean bl) {
		return bl ? this.splitter.plainTailByWidth(string, i, Style.EMPTY) : this.splitter.plainHeadByWidth(string, i, Style.EMPTY);
	}

	public String plainSubstrByWidth(String string, int i) {
		return this.splitter.plainHeadByWidth(string, i, Style.EMPTY);
	}

	public FormattedText substrByWidth(FormattedText formattedText, int i) {
		return this.splitter.headByWidth(formattedText, i, Style.EMPTY);
	}

	public int wordWrapHeight(FormattedText formattedText, int i) {
		return 9 * this.splitter.splitLines(formattedText, i, Style.EMPTY).size();
	}

	public List<FormattedCharSequence> split(FormattedText formattedText, int i) {
		return Language.getInstance().getVisualOrder(this.splitter.splitLines(formattedText, i, Style.EMPTY));
	}

	public List<FormattedText> splitIgnoringLanguage(FormattedText formattedText, int i) {
		return this.splitter.splitLines(formattedText, i, Style.EMPTY);
	}

	public boolean isBidirectional() {
		return Language.getInstance().isDefaultRightToLeft();
	}

	public StringSplitter getSplitter() {
		return this.splitter;
	}

	@Environment(EnvType.CLIENT)
	public enum DisplayMode {
		NORMAL,
		SEE_THROUGH,
		POLYGON_OFFSET;
	}

	@Environment(EnvType.CLIENT)
	public interface GlyphVisitor {
		static Font.GlyphVisitor forMultiBufferSource(MultiBufferSource multiBufferSource, Matrix4f matrix4f, Font.DisplayMode displayMode, int i) {
			return new Font.GlyphVisitor() {
				@Override
				public void acceptGlyph(TextRenderable.Styled styled) {
					this.render(styled);
				}

				@Override
				public void acceptEffect(TextRenderable textRenderable) {
					this.render(textRenderable);
				}

				private void render(TextRenderable textRenderable) {
					VertexConsumer vertexConsumer = multiBufferSource.getBuffer(textRenderable.renderType(displayMode));
					textRenderable.render(matrix4f, vertexConsumer, i, false);
				}
			};
		}

		default void acceptGlyph(TextRenderable.Styled styled) {
		}

		default void acceptEffect(TextRenderable textRenderable) {
		}

		default void acceptEmptyArea(EmptyArea emptyArea) {
		}
	}

	@Environment(EnvType.CLIENT)
	public interface PreparedText {
		void visit(Font.GlyphVisitor glyphVisitor);

		@Nullable ScreenRectangle bounds();
	}

	@Environment(EnvType.CLIENT)
	class PreparedTextBuilder implements FormattedCharSink, Font.PreparedText {
		private final boolean drawShadow;
		private final int color;
		private final int backgroundColor;
		private final boolean includeEmpty;
		float x;
		float y;
		private float left = Float.MAX_VALUE;
		private float top = Float.MAX_VALUE;
		private float right = -Float.MAX_VALUE;
		private float bottom = -Float.MAX_VALUE;
		private float backgroundLeft = Float.MAX_VALUE;
		private float backgroundTop = Float.MAX_VALUE;
		private float backgroundRight = -Float.MAX_VALUE;
		private float backgroundBottom = -Float.MAX_VALUE;
		final List<TextRenderable.Styled> glyphs = new ArrayList<>();
		private @Nullable List<TextRenderable> effects;
		private @Nullable List<EmptyArea> emptyAreas;

		public PreparedTextBuilder(final float f, final float g, final int i, final boolean bl, final boolean bl2) {
			this(f, g, i, 0, bl, bl2);
		}

		public PreparedTextBuilder(final float f, final float g, final int i, final int j, final boolean bl, final boolean bl2) {
			this.x = f;
			this.y = g;
			this.drawShadow = bl;
			this.color = i;
			this.backgroundColor = j;
			this.includeEmpty = bl2;
			this.markBackground(f, g, 0.0F);
		}

		private void markSize(float f, float g, float h, float i) {
			this.left = Math.min(this.left, f);
			this.top = Math.min(this.top, g);
			this.right = Math.max(this.right, h);
			this.bottom = Math.max(this.bottom, i);
		}

		private void markBackground(float f, float g, float h) {
			if (ARGB.alpha(this.backgroundColor) != 0) {
				this.backgroundLeft = Math.min(this.backgroundLeft, f - 1.0F);
				this.backgroundTop = Math.min(this.backgroundTop, g - 1.0F);
				this.backgroundRight = Math.max(this.backgroundRight, f + h);
				this.backgroundBottom = Math.max(this.backgroundBottom, g + 9.0F);
				this.markSize(this.backgroundLeft, this.backgroundTop, this.backgroundRight, this.backgroundBottom);
			}
		}

		private void addGlyph(TextRenderable.Styled styled) {
			this.glyphs.add(styled);
			this.markSize(styled.left(), styled.top(), styled.right(), styled.bottom());
		}

		private void addEffect(TextRenderable textRenderable) {
			if (this.effects == null) {
				this.effects = new ArrayList<>();
			}

			this.effects.add(textRenderable);
			this.markSize(textRenderable.left(), textRenderable.top(), textRenderable.right(), textRenderable.bottom());
		}

		private void addEmptyGlyph(EmptyArea emptyArea) {
			if (this.emptyAreas == null) {
				this.emptyAreas = new ArrayList<>();
			}

			this.emptyAreas.add(emptyArea);
		}

		@Override
		public boolean accept(int i, Style style, int j) {
			BakedGlyph bakedGlyph = Font.this.getGlyph(j, style);
			return this.accept(i, style, bakedGlyph);
		}

		public boolean accept(int i, Style style, BakedGlyph bakedGlyph) {
			GlyphInfo glyphInfo = bakedGlyph.info();
			boolean bl = style.isBold();
			TextColor textColor = style.getColor();
			int j = this.getTextColor(textColor);
			int k = this.getShadowColor(style, j);
			float f = glyphInfo.getAdvance(bl);
			float g = i == 0 ? this.x - 1.0F : this.x;
			float h = glyphInfo.getShadowOffset();
			float l = bl ? glyphInfo.getBoldOffset() : 0.0F;
			TextRenderable.Styled styled = bakedGlyph.createGlyph(this.x, this.y, j, k, style, l, h);
			if (styled != null) {
				this.addGlyph(styled);
			} else if (this.includeEmpty) {
				this.addEmptyGlyph(new EmptyArea(this.x, this.y, f, 7.0F, 9.0F, style));
			}

			this.markBackground(this.x, this.y, f);
			if (style.isStrikethrough()) {
				this.addEffect(Font.this.provider.effect().createEffect(g, this.y + 4.5F - 1.0F, this.x + f, this.y + 4.5F, 0.01F, j, k, h));
			}

			if (style.isUnderlined()) {
				this.addEffect(Font.this.provider.effect().createEffect(g, this.y + 9.0F - 1.0F, this.x + f, this.y + 9.0F, 0.01F, j, k, h));
			}

			this.x += f;
			return true;
		}

		@Override
		public void visit(Font.GlyphVisitor glyphVisitor) {
			if (ARGB.alpha(this.backgroundColor) != 0) {
				glyphVisitor.acceptEffect(
					Font.this.provider
						.effect()
						.createEffect(this.backgroundLeft, this.backgroundTop, this.backgroundRight, this.backgroundBottom, -0.01F, this.backgroundColor, 0, 0.0F)
				);
			}

			for (TextRenderable.Styled styled : this.glyphs) {
				glyphVisitor.acceptGlyph(styled);
			}

			if (this.effects != null) {
				for (TextRenderable textRenderable : this.effects) {
					glyphVisitor.acceptEffect(textRenderable);
				}
			}

			if (this.emptyAreas != null) {
				for (EmptyArea emptyArea : this.emptyAreas) {
					glyphVisitor.acceptEmptyArea(emptyArea);
				}
			}
		}

		private int getTextColor(@Nullable TextColor textColor) {
			if (textColor != null) {
				int i = ARGB.alpha(this.color);
				int j = textColor.getValue();
				return ARGB.color(i, j);
			} else {
				return this.color;
			}
		}

		private int getShadowColor(Style style, int i) {
			Integer integer = style.getShadowColor();
			if (integer != null) {
				float f = ARGB.alphaFloat(i);
				float g = ARGB.alphaFloat(integer);
				return f != 1.0F ? ARGB.color(ARGB.as8BitChannel(f * g), integer) : integer;
			} else {
				return this.drawShadow ? ARGB.scaleRGB(i, 0.25F) : 0;
			}
		}

		@Override
		public @Nullable ScreenRectangle bounds() {
			if (!(this.left >= this.right) && !(this.top >= this.bottom)) {
				int i = Mth.floor(this.left);
				int j = Mth.floor(this.top);
				int k = Mth.ceil(this.right);
				int l = Mth.ceil(this.bottom);
				return new ScreenRectangle(i, j, k - i, l - j);
			} else {
				return null;
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public interface Provider {
		GlyphSource glyphs(FontDescription fontDescription);

		EffectGlyph effect();
	}
}
