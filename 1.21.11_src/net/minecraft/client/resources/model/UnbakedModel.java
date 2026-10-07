package net.minecraft.client.resources.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface UnbakedModel {
	String PARTICLE_TEXTURE_REFERENCE = "particle";

	default @Nullable Boolean ambientOcclusion() {
		return null;
	}

	default UnbakedModel.@Nullable GuiLight guiLight() {
		return null;
	}

	default @Nullable ItemTransforms transforms() {
		return null;
	}

	default TextureSlots.Data textureSlots() {
		return TextureSlots.Data.EMPTY;
	}

	default @Nullable UnbakedGeometry geometry() {
		return null;
	}

	default @Nullable Identifier parent() {
		return null;
	}

	@Environment(EnvType.CLIENT)
	enum GuiLight {
		FRONT("front"),
		SIDE("side");

		private final String name;

		GuiLight(final String string2) {
			this.name = string2;
		}

		public static UnbakedModel.GuiLight getByName(String string) {
			for (UnbakedModel.GuiLight guiLight : values()) {
				if (guiLight.name.equals(string)) {
					return guiLight;
				}
			}

			throw new IllegalArgumentException("Invalid gui light: " + string);
		}

		public boolean lightLikeBlock() {
			return this == SIDE;
		}
	}
}
