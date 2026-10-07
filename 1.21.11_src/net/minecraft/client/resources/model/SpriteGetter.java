package net.minecraft.client.resources.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.renderer.v1.sprite.FabricErrorCollectingSpriteGetter;
import net.minecraft.client.renderer.block.model.TextureSlots;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.renderer.v1.sprite.FabricErrorCollectingSpriteGetter} injected by mod fabric-renderer-api-v1</p>
 */
@Environment(EnvType.CLIENT)
public interface SpriteGetter extends FabricErrorCollectingSpriteGetter {
	TextureAtlasSprite get(Material material, ModelDebugName modelDebugName);

	TextureAtlasSprite reportMissingReference(String string, ModelDebugName modelDebugName);

	default TextureAtlasSprite resolveSlot(TextureSlots textureSlots, String string, ModelDebugName modelDebugName) {
		Material material = textureSlots.getMaterial(string);
		return material != null ? this.get(material, modelDebugName) : this.reportMissingReference(string, modelDebugName);
	}
}
