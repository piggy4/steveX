package net.minecraft.client.renderer.item;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.world.item.ItemDisplayContext;

@Environment(EnvType.CLIENT)
public record ModelRenderProperties(boolean usesBlockLight, TextureAtlasSprite particleIcon, ItemTransforms transforms) {
	public static ModelRenderProperties fromResolvedModel(ModelBaker modelBaker, ResolvedModel resolvedModel, TextureSlots textureSlots) {
		TextureAtlasSprite textureAtlasSprite = resolvedModel.resolveParticleSprite(textureSlots, modelBaker);
		return new ModelRenderProperties(resolvedModel.getTopGuiLight().lightLikeBlock(), textureAtlasSprite, resolvedModel.getTopTransforms());
	}

	public void applyToLayer(ItemStackRenderState.LayerRenderState layerRenderState, ItemDisplayContext itemDisplayContext) {
		layerRenderState.setUsesBlockLight(this.usesBlockLight);
		layerRenderState.setParticleIcon(this.particleIcon);
		layerRenderState.setTransform(this.transforms.getTransform(itemDisplayContext));
	}
}
