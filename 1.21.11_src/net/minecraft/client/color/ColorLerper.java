package net.minecraft.client.color;

import com.google.common.collect.Maps;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;

@Environment(EnvType.CLIENT)
public class ColorLerper {
	public static final DyeColor[] MUSIC_NOTE_COLORS = new DyeColor[]{
		DyeColor.WHITE,
		DyeColor.LIGHT_GRAY,
		DyeColor.LIGHT_BLUE,
		DyeColor.BLUE,
		DyeColor.CYAN,
		DyeColor.GREEN,
		DyeColor.LIME,
		DyeColor.YELLOW,
		DyeColor.ORANGE,
		DyeColor.PINK,
		DyeColor.RED,
		DyeColor.MAGENTA
	};

	public static int getLerpedColor(ColorLerper.Type type, float f) {
		int i = Mth.floor(f);
		int j = i / type.colorDuration;
		int k = type.colors.length;
		int l = j % k;
		int m = (j + 1) % k;
		float g = (i % type.colorDuration + Mth.frac(f)) / type.colorDuration;
		int n = type.getColor(type.colors[l]);
		int o = type.getColor(type.colors[m]);
		return ARGB.srgbLerp(g, n, o);
	}

	static int getModifiedColor(DyeColor dyeColor, float f) {
		if (dyeColor == DyeColor.WHITE) {
			return -1644826;
		}

		int i = dyeColor.getTextureDiffuseColor();
		return ARGB.color(255, Mth.floor(ARGB.red(i) * f), Mth.floor(ARGB.green(i) * f), Mth.floor(ARGB.blue(i) * f));
	}

	@Environment(EnvType.CLIENT)
	public enum Type {
		SHEEP(25, DyeColor.values(), 0.75F),
		MUSIC_NOTE(30, ColorLerper.MUSIC_NOTE_COLORS, 1.25F);

		final int colorDuration;
		private final Map<DyeColor, Integer> colorByDye;
		final DyeColor[] colors;

		Type(final int j, final DyeColor[] dyeColors, final float f) {
			this.colorDuration = j;
			this.colorByDye = Maps.newHashMap(
				Arrays.stream(dyeColors).collect(Collectors.toMap(dyeColor -> (DyeColor)dyeColor, dyeColor -> ColorLerper.getModifiedColor(dyeColor, f)))
			);
			this.colors = dyeColors;
		}

		public final int getColor(DyeColor dyeColor) {
			return this.colorByDye.get(dyeColor);
		}
	}
}
