package net.minecraft.client.gui.font.providers;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Util;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;

@Environment(EnvType.CLIENT)
public record TrueTypeGlyphProviderDefinition(Identifier location, float size, float oversample, TrueTypeGlyphProviderDefinition.Shift shift, String skip)
	implements GlyphProviderDefinition {
	private static final Codec<String> SKIP_LIST_CODEC = Codec.withAlternative(Codec.STRING, Codec.STRING.listOf(), list -> String.join("", list));
	public static final MapCodec<TrueTypeGlyphProviderDefinition> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				Identifier.CODEC.fieldOf("file").forGetter(TrueTypeGlyphProviderDefinition::location),
				Codec.FLOAT.optionalFieldOf("size", 11.0F).forGetter(TrueTypeGlyphProviderDefinition::size),
				Codec.FLOAT.optionalFieldOf("oversample", 1.0F).forGetter(TrueTypeGlyphProviderDefinition::oversample),
				TrueTypeGlyphProviderDefinition.Shift.CODEC
					.optionalFieldOf("shift", TrueTypeGlyphProviderDefinition.Shift.NONE)
					.forGetter(TrueTypeGlyphProviderDefinition::shift),
				SKIP_LIST_CODEC.optionalFieldOf("skip", "").forGetter(TrueTypeGlyphProviderDefinition::skip)
			)
			.apply(instance, TrueTypeGlyphProviderDefinition::new)
	);

	@Override
	public GlyphProviderType type() {
		return GlyphProviderType.TTF;
	}

	@Override
	public Either<GlyphProviderDefinition.Loader, GlyphProviderDefinition.Reference> unpack() {
		return Either.left(this::load);
	}

	private GlyphProvider load(ResourceManager resourceManager) throws IOException {
		FT_Face fT_Face = null;
		ByteBuffer byteBuffer = null;

		try (InputStream inputStream = resourceManager.open(this.location.withPrefix("font/"))) {
			byteBuffer = TextureUtil.readResource(inputStream);
			synchronized (FreeTypeUtil.LIBRARY_LOCK) {
				try (MemoryStack memoryStack = MemoryStack.stackPush()) {
					PointerBuffer pointerBuffer = memoryStack.mallocPointer(1);
					FreeTypeUtil.assertError(FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), byteBuffer, 0L, pointerBuffer), "Initializing font face");
					fT_Face = FT_Face.create(pointerBuffer.get());
				}

				String string = FreeType.FT_Get_Font_Format(fT_Face);
				if (!"TrueType".equals(string)) {
					throw new IOException("Font is not in TTF format, was " + string);
				}

				FreeTypeUtil.assertError(FreeType.FT_Select_Charmap(fT_Face, FreeType.FT_ENCODING_UNICODE), "Find unicode charmap");
				return new TrueTypeGlyphProvider(byteBuffer, fT_Face, this.size, this.oversample, this.shift.x, this.shift.y, this.skip);
			}
		} catch (Exception exception) {
			synchronized (FreeTypeUtil.LIBRARY_LOCK) {
				if (fT_Face != null) {
					FreeType.FT_Done_Face(fT_Face);
				}
			}

			MemoryUtil.memFree(byteBuffer);
			throw exception;
		}
	}

	@Environment(EnvType.CLIENT)
	public record Shift(float x, float y) {
		public static final TrueTypeGlyphProviderDefinition.Shift NONE = new TrueTypeGlyphProviderDefinition.Shift(0.0F, 0.0F);
		public static final Codec<TrueTypeGlyphProviderDefinition.Shift> CODEC = Codec.floatRange(-512.0F, 512.0F)
			.listOf()
			.comapFlatMap(
				list -> Util.fixedSize((List<Float>)list, 2).map(listx -> new TrueTypeGlyphProviderDefinition.Shift((Float)listx.get(0), (Float)listx.get(1))),
				shift -> List.of(shift.x, shift.y)
			);
	}
}
