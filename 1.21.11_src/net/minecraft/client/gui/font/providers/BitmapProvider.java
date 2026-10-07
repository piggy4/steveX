package net.minecraft.client.gui.font.providers;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.UnbakedGlyph;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.font.CodepointMap;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class BitmapProvider implements GlyphProvider {
	static final Logger LOGGER = LogUtils.getLogger();
	private final NativeImage image;
	private final CodepointMap<BitmapProvider.Glyph> glyphs;

	BitmapProvider(NativeImage nativeImage, CodepointMap<BitmapProvider.Glyph> codepointMap) {
		this.image = nativeImage;
		this.glyphs = codepointMap;
	}

	@Override
	public void close() {
		this.image.close();
	}

	@Override
	public @Nullable UnbakedGlyph getGlyph(int i) {
		return this.glyphs.get(i);
	}

	@Override
	public IntSet getSupportedGlyphs() {
		return IntSets.unmodifiable(this.glyphs.keySet());
	}

	@Environment(EnvType.CLIENT)
	public record Definition(Identifier file, int height, int ascent, int[][] codepointGrid) implements GlyphProviderDefinition {
		private static final Codec<int[][]> CODEPOINT_GRID_CODEC = Codec.STRING.listOf().xmap(list -> {
			int i = list.size();
			int[][] is = new int[i][];

			for (int j = 0; j < i; j++) {
				is[j] = list.get(j).codePoints().toArray();
			}

			return is;
		}, is -> {
			List<String> list = new ArrayList<>(is.length);

			for (int[] js : is) {
				list.add(new String(js, 0, js.length));
			}

			return list;
		}).validate(BitmapProvider.Definition::validateDimensions);
		public static final MapCodec<BitmapProvider.Definition> CODEC = RecordCodecBuilder.<BitmapProvider.Definition>mapCodec(
				instance -> instance.group(
						Identifier.CODEC.fieldOf("file").forGetter(BitmapProvider.Definition::file),
						Codec.INT.optionalFieldOf("height", 8).forGetter(BitmapProvider.Definition::height),
						Codec.INT.fieldOf("ascent").forGetter(BitmapProvider.Definition::ascent),
						CODEPOINT_GRID_CODEC.fieldOf("chars").forGetter(BitmapProvider.Definition::codepointGrid)
					)
					.apply(instance, BitmapProvider.Definition::new)
			)
			.validate(BitmapProvider.Definition::validate);

		private static DataResult<int[][]> validateDimensions(int[][] is) {
			int i = is.length;
			if (i == 0) {
				return DataResult.error(() -> "Expected to find data in codepoint grid");
			}

			int[] js = is[0];
			int j = js.length;
			if (j == 0) {
				return DataResult.error(() -> "Expected to find data in codepoint grid");
			}

			for (int k = 1; k < i; k++) {
				int[] ks = is[k];
				if (ks.length != j) {
					return DataResult.error(
						() -> "Lines in codepoint grid have to be the same length (found: " + ks.length + " codepoints, expected: " + j + "), pad with \\u0000"
					);
				}
			}

			return DataResult.success(is);
		}

		private static DataResult<BitmapProvider.Definition> validate(BitmapProvider.Definition definition) {
			return definition.ascent > definition.height
				? DataResult.error(() -> "Ascent " + definition.ascent + " higher than height " + definition.height)
				: DataResult.success(definition);
		}

		@Override
		public GlyphProviderType type() {
			return GlyphProviderType.BITMAP;
		}

		@Override
		public Either<GlyphProviderDefinition.Loader, GlyphProviderDefinition.Reference> unpack() {
			return Either.left(this::load);
		}

		private GlyphProvider load(ResourceManager resourceManager) throws IOException {
			Identifier identifier = this.file.withPrefix("textures/");

			try (InputStream inputStream = resourceManager.open(identifier)) {
				NativeImage nativeImage = NativeImage.read(NativeImage.Format.RGBA, inputStream);
				int i = nativeImage.getWidth();
				int j = nativeImage.getHeight();
				int k = i / this.codepointGrid[0].length;
				int l = j / this.codepointGrid.length;
				float f = (float)this.height / l;
				CodepointMap<BitmapProvider.Glyph> codepointMap = new CodepointMap<>(BitmapProvider.Glyph[]::new, BitmapProvider.Glyph[][]::new);

				for (int m = 0; m < this.codepointGrid.length; m++) {
					int n = 0;

					for (int o : this.codepointGrid[m]) {
						int p = n++;
						if (o != 0) {
							int q = this.getActualGlyphWidth(nativeImage, k, l, p, m);
							BitmapProvider.Glyph glyph = codepointMap.put(o, new BitmapProvider.Glyph(f, nativeImage, p * k, m * l, k, l, (int)(0.5 + q * f) + 1, this.ascent));
							if (glyph != null) {
								BitmapProvider.LOGGER.warn("Codepoint '{}' declared multiple times in {}", Integer.toHexString(o), identifier);
							}
						}
					}
				}

				return new BitmapProvider(nativeImage, codepointMap);
			}
		}

		private int getActualGlyphWidth(NativeImage nativeImage, int i, int j, int k, int l) {
			int m;
			for (m = i - 1; m >= 0; m--) {
				int n = k * i + m;

				for (int o = 0; o < j; o++) {
					int p = l * j + o;
					if (nativeImage.getLuminanceOrAlpha(n, p) != 0) {
						return m + 1;
					}
				}
			}

			return m + 1;
		}
	}

	@Environment(EnvType.CLIENT)
	record Glyph(float scale, NativeImage image, int offsetX, int offsetY, int width, int height, int advance, int ascent) implements UnbakedGlyph {

		@Override
		public GlyphInfo info() {
			return GlyphInfo.simple(this.advance);
		}

		@Override
		public BakedGlyph bake(UnbakedGlyph.Stitcher stitcher) {
			return stitcher.stitch(
				this.info(),
				new GlyphBitmap() {
					@Override
					public float getOversample() {
						return 1.0F / Glyph.this.scale;
					}

					@Override
					public int getPixelWidth() {
						return Glyph.this.width;
					}

					@Override
					public int getPixelHeight() {
						return Glyph.this.height;
					}

					@Override
					public float getBearingTop() {
						return Glyph.this.ascent;
					}

					@Override
					public void upload(int i, int j, GpuTexture gpuTexture) {
						RenderSystem.getDevice()
							.createCommandEncoder()
							.writeToTexture(gpuTexture, Glyph.this.image, 0, 0, i, j, Glyph.this.width, Glyph.this.height, Glyph.this.offsetX, Glyph.this.offsetY);
					}

					@Override
					public boolean isColored() {
						return Glyph.this.image.format().components() > 1;
					}
				}
			);
		}
	}
}
