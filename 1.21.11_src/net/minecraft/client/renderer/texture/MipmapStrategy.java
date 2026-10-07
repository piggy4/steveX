package net.minecraft.client.renderer.texture;

import com.mojang.serialization.Codec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.StringRepresentable;

@Environment(EnvType.CLIENT)
public enum MipmapStrategy implements StringRepresentable {
	AUTO("auto"),
	MEAN("mean"),
	CUTOUT("cutout"),
	STRICT_CUTOUT("strict_cutout"),
	DARK_CUTOUT("dark_cutout");

	public static final Codec<MipmapStrategy> CODEC = StringRepresentable.fromValues(MipmapStrategy::values);
	private final String name;

	MipmapStrategy(final String string2) {
		this.name = string2;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}
}
