package net.minecraft.client.renderer.texture.atlas.sources;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public record SingleFile(Identifier resourceId, Optional<Identifier> spriteId) implements SpriteSource {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final MapCodec<SingleFile> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				Identifier.CODEC.fieldOf("resource").forGetter(SingleFile::resourceId), Identifier.CODEC.optionalFieldOf("sprite").forGetter(SingleFile::spriteId)
			)
			.apply(instance, SingleFile::new)
	);

	public SingleFile(Identifier identifier) {
		this(identifier, Optional.empty());
	}

	@Override
	public void run(ResourceManager resourceManager, SpriteSource.Output output) {
		Identifier identifier = TEXTURE_ID_CONVERTER.idToFile(this.resourceId);
		Optional<Resource> optional = resourceManager.getResource(identifier);
		if (optional.isPresent()) {
			output.add(this.spriteId.orElse(this.resourceId), optional.get());
		} else {
			LOGGER.warn("Missing sprite: {}", identifier);
		}
	}

	@Override
	public MapCodec<SingleFile> codec() {
		return MAP_CODEC;
	}
}
