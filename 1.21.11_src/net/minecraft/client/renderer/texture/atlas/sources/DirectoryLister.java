package net.minecraft.client.renderer.texture.atlas.sources;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

@Environment(EnvType.CLIENT)
public record DirectoryLister(String sourcePath, String idPrefix) implements SpriteSource {
	public static final MapCodec<DirectoryLister> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				Codec.STRING.fieldOf("source").forGetter(DirectoryLister::sourcePath), Codec.STRING.fieldOf("prefix").forGetter(DirectoryLister::idPrefix)
			)
			.apply(instance, DirectoryLister::new)
	);

	@Override
	public void run(ResourceManager resourceManager, SpriteSource.Output output) {
		FileToIdConverter fileToIdConverter = new FileToIdConverter("textures/" + this.sourcePath, ".png");
		fileToIdConverter.listMatchingResources(resourceManager).forEach((identifier, resource) -> {
			Identifier identifier2 = fileToIdConverter.fileToId(identifier).withPrefix(this.idPrefix);
			output.add(identifier2, resource);
		});
	}

	@Override
	public MapCodec<DirectoryLister> codec() {
		return MAP_CODEC;
	}
}
