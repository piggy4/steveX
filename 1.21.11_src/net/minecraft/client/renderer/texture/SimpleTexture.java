package net.minecraft.client.renderer.texture;

import java.io.IOException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

@Environment(EnvType.CLIENT)
public class SimpleTexture extends ReloadableTexture {
	public SimpleTexture(Identifier identifier) {
		super(identifier);
	}

	@Override
	public TextureContents loadContents(ResourceManager resourceManager) throws IOException {
		return TextureContents.load(resourceManager, this.resourceId());
	}
}
