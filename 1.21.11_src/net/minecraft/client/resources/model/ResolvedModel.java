package net.minecraft.client.resources.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemDisplayContext;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface ResolvedModel extends ModelDebugName {
	boolean DEFAULT_AMBIENT_OCCLUSION = true;
	UnbakedModel.GuiLight DEFAULT_GUI_LIGHT = UnbakedModel.GuiLight.SIDE;

	UnbakedModel wrapped();

	@Nullable ResolvedModel parent();

	static TextureSlots findTopTextureSlots(ResolvedModel resolvedModel) {
		ResolvedModel resolvedModel2 = resolvedModel;
		TextureSlots.Resolver resolver = new TextureSlots.Resolver();

		while (resolvedModel2 != null) {
			resolver.addLast(resolvedModel2.wrapped().textureSlots());
			resolvedModel2 = resolvedModel2.parent();
		}

		return resolver.resolve(resolvedModel);
	}

	default TextureSlots getTopTextureSlots() {
		return findTopTextureSlots(this);
	}

	static boolean findTopAmbientOcclusion(ResolvedModel resolvedModel) {
		while (resolvedModel != null) {
			Boolean boolean_ = resolvedModel.wrapped().ambientOcclusion();
			if (boolean_ != null) {
				return boolean_;
			}

			resolvedModel = resolvedModel.parent();
		}

		return true;
	}

	default boolean getTopAmbientOcclusion() {
		return findTopAmbientOcclusion(this);
	}

	static UnbakedModel.GuiLight findTopGuiLight(ResolvedModel resolvedModel) {
		while (resolvedModel != null) {
			UnbakedModel.GuiLight guiLight = resolvedModel.wrapped().guiLight();
			if (guiLight != null) {
				return guiLight;
			}

			resolvedModel = resolvedModel.parent();
		}

		return DEFAULT_GUI_LIGHT;
	}

	default UnbakedModel.GuiLight getTopGuiLight() {
		return findTopGuiLight(this);
	}

	static UnbakedGeometry findTopGeometry(ResolvedModel resolvedModel) {
		while (resolvedModel != null) {
			UnbakedGeometry unbakedGeometry = resolvedModel.wrapped().geometry();
			if (unbakedGeometry != null) {
				return unbakedGeometry;
			}

			resolvedModel = resolvedModel.parent();
		}

		return UnbakedGeometry.EMPTY;
	}

	default UnbakedGeometry getTopGeometry() {
		return findTopGeometry(this);
	}

	default QuadCollection bakeTopGeometry(TextureSlots textureSlots, ModelBaker modelBaker, ModelState modelState) {
		return this.getTopGeometry().bake(textureSlots, modelBaker, modelState, this);
	}

	static TextureAtlasSprite resolveParticleSprite(TextureSlots textureSlots, ModelBaker modelBaker, ModelDebugName modelDebugName) {
		return modelBaker.sprites().resolveSlot(textureSlots, "particle", modelDebugName);
	}

	default TextureAtlasSprite resolveParticleSprite(TextureSlots textureSlots, ModelBaker modelBaker) {
		return resolveParticleSprite(textureSlots, modelBaker, this);
	}

	static ItemTransform findTopTransform(ResolvedModel resolvedModel, ItemDisplayContext itemDisplayContext) {
		while (resolvedModel != null) {
			ItemTransforms itemTransforms = resolvedModel.wrapped().transforms();
			if (itemTransforms != null) {
				ItemTransform itemTransform = itemTransforms.getTransform(itemDisplayContext);
				if (itemTransform != ItemTransform.NO_TRANSFORM) {
					return itemTransform;
				}
			}

			resolvedModel = resolvedModel.parent();
		}

		return ItemTransform.NO_TRANSFORM;
	}

	static ItemTransforms findTopTransforms(ResolvedModel resolvedModel) {
		ItemTransform itemTransform = findTopTransform(resolvedModel, ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
		ItemTransform itemTransform2 = findTopTransform(resolvedModel, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
		ItemTransform itemTransform3 = findTopTransform(resolvedModel, ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
		ItemTransform itemTransform4 = findTopTransform(resolvedModel, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
		ItemTransform itemTransform5 = findTopTransform(resolvedModel, ItemDisplayContext.HEAD);
		ItemTransform itemTransform6 = findTopTransform(resolvedModel, ItemDisplayContext.GUI);
		ItemTransform itemTransform7 = findTopTransform(resolvedModel, ItemDisplayContext.GROUND);
		ItemTransform itemTransform8 = findTopTransform(resolvedModel, ItemDisplayContext.FIXED);
		ItemTransform itemTransform9 = findTopTransform(resolvedModel, ItemDisplayContext.ON_SHELF);
		return new ItemTransforms(
			itemTransform, itemTransform2, itemTransform3, itemTransform4, itemTransform5, itemTransform6, itemTransform7, itemTransform8, itemTransform9
		);
	}

	default ItemTransforms getTopTransforms() {
		return findTopTransforms(this);
	}
}
