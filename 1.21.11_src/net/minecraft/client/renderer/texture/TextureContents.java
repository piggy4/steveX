package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record TextureContents(NativeImage image, @Nullable TextureMetadataSection metadata) implements Closeable {
	public static TextureContents load(ResourceManager resourceManager, Identifier identifier) throws IOException {
		Resource resource = resourceManager.getResourceOrThrow(identifier);

		NativeImage nativeImage;
		try (InputStream inputStream = resource.open()) {
			nativeImage = NativeImage.read(inputStream);
		}

		TextureMetadataSection textureMetadataSection = resource.metadata().getSection(TextureMetadataSection.TYPE).orElse(null);
		return new TextureContents(nativeImage, textureMetadataSection);
	}

	public static TextureContents createMissing() {
		return new TextureContents(MissingTextureAtlasSprite.generateMissingImage(), null);
	}

	public boolean blur() {
		return this.metadata != null ? this.metadata.blur() : false;
	}

	public boolean clamp() {
		return this.metadata != null ? this.metadata.clamp() : false;
	}

	@Override
	public void close() {
		this.image.close();
	}
}
