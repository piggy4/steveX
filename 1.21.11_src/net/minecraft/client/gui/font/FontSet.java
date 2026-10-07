package net.minecraft.client.gui.font;

import com.google.common.collect.Sets;
import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class FontSet implements AutoCloseable {
	private static final float LARGE_FORWARD_ADVANCE = 32.0F;
	private static final BakedGlyph INVISIBLE_MISSING_GLYPH = new BakedGlyph() {
		@Override
		public GlyphInfo info() {
			return SpecialGlyphs.MISSING;
		}

		@Override
		public TextRenderable.@Nullable Styled createGlyph(float f, float g, int i, int j, Style style, float h, float k) {
			return null;
		}
	};
	final GlyphStitcher stitcher;
	final UnbakedGlyph.Stitcher wrappedStitcher = new UnbakedGlyph.Stitcher() {
		@Override
		public BakedGlyph stitch(GlyphInfo glyphInfo, GlyphBitmap glyphBitmap) {
			return Objects.requireNonNullElse(FontSet.this.stitcher.stitch(glyphInfo, glyphBitmap), FontSet.this.missingGlyph);
		}

		@Override
		public BakedGlyph getMissing() {
			return FontSet.this.missingGlyph;
		}
	};
	private List<GlyphProvider.Conditional> allProviders = List.of();
	private List<GlyphProvider> activeProviders = List.of();
	private final Int2ObjectMap<IntList> glyphsByWidth = new Int2ObjectOpenHashMap<>();
	private final CodepointMap<FontSet.SelectedGlyphs> glyphCache = new CodepointMap<>(FontSet.SelectedGlyphs[]::new, FontSet.SelectedGlyphs[][]::new);
	private final IntFunction<FontSet.SelectedGlyphs> glyphGetter = this::computeGlyphInfo;
	BakedGlyph missingGlyph = INVISIBLE_MISSING_GLYPH;
	private final Supplier<BakedGlyph> missingGlyphGetter = () -> this.missingGlyph;
	private final FontSet.SelectedGlyphs missingSelectedGlyphs = new FontSet.SelectedGlyphs(this.missingGlyphGetter, this.missingGlyphGetter);
	private @Nullable EffectGlyph whiteGlyph;
	private final GlyphSource anyGlyphs = new FontSet.Source(false);
	private final GlyphSource nonFishyGlyphs = new FontSet.Source(true);

	public FontSet(GlyphStitcher glyphStitcher) {
		this.stitcher = glyphStitcher;
	}

	public void reload(List<GlyphProvider.Conditional> list, Set<FontOption> set) {
		this.allProviders = list;
		this.reload(set);
	}

	public void reload(Set<FontOption> set) {
		this.activeProviders = List.of();
		this.resetTextures();
		this.activeProviders = this.selectProviders(this.allProviders, set);
	}

	private void resetTextures() {
		this.stitcher.reset();
		this.glyphCache.clear();
		this.glyphsByWidth.clear();
		this.missingGlyph = Objects.requireNonNull(SpecialGlyphs.MISSING.bake(this.stitcher));
		this.whiteGlyph = SpecialGlyphs.WHITE.bake(this.stitcher);
	}

	private List<GlyphProvider> selectProviders(List<GlyphProvider.Conditional> list, Set<FontOption> set) {
		IntSet intSet = new IntOpenHashSet();
		List<GlyphProvider> list2 = new ArrayList<>();

		for (GlyphProvider.Conditional conditional : list) {
			if (conditional.filter().apply(set)) {
				list2.add(conditional.provider());
				intSet.addAll(conditional.provider().getSupportedGlyphs());
			}
		}

		Set<GlyphProvider> set2 = Sets.newHashSet();
		intSet.forEach(i -> {
			for (GlyphProvider glyphProvider : list2) {
				UnbakedGlyph unbakedGlyph = glyphProvider.getGlyph(i);
				if (unbakedGlyph != null) {
					set2.add(glyphProvider);
					if (unbakedGlyph.info() != SpecialGlyphs.MISSING) {
						this.glyphsByWidth.computeIfAbsent(Mth.ceil(unbakedGlyph.info().getAdvance(false)), ix -> new IntArrayList()).add(i);
					}
					break;
				}
			}
		});
		return list2.stream().filter(set2::contains).toList();
	}

	@Override
	public void close() {
		this.stitcher.close();
	}

	private static boolean hasFishyAdvance(GlyphInfo glyphInfo) {
		float f = glyphInfo.getAdvance(false);
		if (!(f < 0.0F) && !(f > 32.0F)) {
			float g = glyphInfo.getAdvance(true);
			return g < 0.0F || g > 32.0F;
		} else {
			return true;
		}
	}

	private FontSet.SelectedGlyphs computeGlyphInfo(int i) {
		FontSet.DelayedBake delayedBake = null;

		for (GlyphProvider glyphProvider : this.activeProviders) {
			UnbakedGlyph unbakedGlyph = glyphProvider.getGlyph(i);
			if (unbakedGlyph != null) {
				if (delayedBake == null) {
					delayedBake = new FontSet.DelayedBake(unbakedGlyph);
				}

				if (!hasFishyAdvance(unbakedGlyph.info())) {
					if (delayedBake.unbaked == unbakedGlyph) {
						return new FontSet.SelectedGlyphs(delayedBake, delayedBake);
					}

					return new FontSet.SelectedGlyphs(delayedBake, new FontSet.DelayedBake(unbakedGlyph));
				}
			}
		}

		return delayedBake != null ? new FontSet.SelectedGlyphs(delayedBake, this.missingGlyphGetter) : this.missingSelectedGlyphs;
	}

	FontSet.SelectedGlyphs getGlyph(int i) {
		return this.glyphCache.computeIfAbsent(i, this.glyphGetter);
	}

	public BakedGlyph getRandomGlyph(RandomSource randomSource, int i) {
		IntList intList = this.glyphsByWidth.get(i);
		return intList != null && !intList.isEmpty() ? this.getGlyph(intList.getInt(randomSource.nextInt(intList.size()))).nonFishy().get() : this.missingGlyph;
	}

	public EffectGlyph whiteGlyph() {
		return Objects.requireNonNull(this.whiteGlyph);
	}

	public GlyphSource source(boolean bl) {
		return bl ? this.nonFishyGlyphs : this.anyGlyphs;
	}

	@Environment(EnvType.CLIENT)
	class DelayedBake implements Supplier<BakedGlyph> {
		final UnbakedGlyph unbaked;
		private @Nullable BakedGlyph baked;

		DelayedBake(final UnbakedGlyph unbakedGlyph) {
			this.unbaked = unbakedGlyph;
		}

		public BakedGlyph get() {
			if (this.baked == null) {
				this.baked = this.unbaked.bake(FontSet.this.wrappedStitcher);
			}

			return this.baked;
		}
	}

	@Environment(EnvType.CLIENT)
	record SelectedGlyphs(Supplier<BakedGlyph> any, Supplier<BakedGlyph> nonFishy) {
		Supplier<BakedGlyph> select(boolean bl) {
			return bl ? this.nonFishy : this.any;
		}
	}

	@Environment(EnvType.CLIENT)
	public class Source implements GlyphSource {
		private final boolean filterFishyGlyphs;

		public Source(final boolean bl) {
			this.filterFishyGlyphs = bl;
		}

		@Override
		public BakedGlyph getGlyph(int i) {
			return FontSet.this.getGlyph(i).select(this.filterFishyGlyphs).get();
		}

		@Override
		public BakedGlyph getRandomGlyph(RandomSource randomSource, int i) {
			return FontSet.this.getRandomGlyph(randomSource, i);
		}
	}
}
