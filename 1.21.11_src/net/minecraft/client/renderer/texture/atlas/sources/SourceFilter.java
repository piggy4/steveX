package net.minecraft.client.renderer.texture.atlas.sources;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.IdentifierPattern;

@Environment(EnvType.CLIENT)
public record SourceFilter(IdentifierPattern filter) implements SpriteSource {
	public static final MapCodec<SourceFilter> MAP_CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(IdentifierPattern.CODEC.fieldOf("pattern").forGetter(SourceFilter::filter)).apply(instance, SourceFilter::new)
	);

	@Override
	public void run(ResourceManager resourceManager, SpriteSource.Output output) {
		output.removeAll(this.filter.locationPredicate());
	}

	@Override
	public MapCodec<SourceFilter> codec() {
		return MAP_CODEC;
	}
}
