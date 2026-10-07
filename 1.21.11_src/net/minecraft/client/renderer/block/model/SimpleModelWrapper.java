package net.minecraft.client.renderer.block.model;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.QuadCollection;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public record SimpleModelWrapper(QuadCollection quads, boolean useAmbientOcclusion, TextureAtlasSprite particleIcon) implements BlockModelPart {
	private static final Logger LOGGER = LogUtils.getLogger();

	public static BlockModelPart bake(ModelBaker modelBaker, Identifier identifier, ModelState modelState) {
		ResolvedModel resolvedModel = modelBaker.getModel(identifier);
		TextureSlots textureSlots = resolvedModel.getTopTextureSlots();
		boolean bl = resolvedModel.getTopAmbientOcclusion();
		TextureAtlasSprite textureAtlasSprite = resolvedModel.resolveParticleSprite(textureSlots, modelBaker);
		QuadCollection quadCollection = resolvedModel.bakeTopGeometry(textureSlots, modelBaker, modelState);
		Multimap<Identifier, Identifier> multimap = null;

		for (BakedQuad bakedQuad : quadCollection.getAll()) {
			TextureAtlasSprite textureAtlasSprite2 = bakedQuad.sprite();
			if (!textureAtlasSprite2.atlasLocation().equals(TextureAtlas.LOCATION_BLOCKS)) {
				if (multimap == null) {
					multimap = HashMultimap.create();
				}

				multimap.put(textureAtlasSprite2.atlasLocation(), textureAtlasSprite2.contents().name());
			}
		}

		if (multimap != null) {
			LOGGER.warn("Rejecting block model {}, since it contains sprites from outside of supported atlas: {}", identifier, multimap);
			return modelBaker.missingBlockModelPart();
		} else {
			return new SimpleModelWrapper(quadCollection, bl, textureAtlasSprite);
		}
	}

	@Override
	public List<BakedQuad> getQuads(@Nullable Direction direction) {
		return this.quads.getQuads(direction);
	}
}
