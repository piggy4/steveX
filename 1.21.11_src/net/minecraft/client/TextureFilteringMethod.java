package net.minecraft.client;

import com.mojang.serialization.Codec;
import java.util.function.IntFunction;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ByIdMap;

@Environment(EnvType.CLIENT)
public enum TextureFilteringMethod {
	NONE(0, "options.textureFiltering.none"),
	RGSS(1, "options.textureFiltering.rgss"),
	ANISOTROPIC(2, "options.textureFiltering.anisotropic");

	private static final IntFunction<TextureFilteringMethod> BY_ID = ByIdMap.continuous(
		textureFilteringMethod -> textureFilteringMethod.id, values(), ByIdMap.OutOfBoundsStrategy.WRAP
	);
	public static final Codec<TextureFilteringMethod> LEGACY_CODEC = Codec.INT.xmap(BY_ID::apply, textureFilteringMethod -> textureFilteringMethod.id);
	private final int id;
	private final Component caption;

	TextureFilteringMethod(final int j, final String string2) {
		this.id = j;
		this.caption = Component.translatable(string2);
	}

	public Component caption() {
		return this.caption;
	}
}
