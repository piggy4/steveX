package net.minecraft.tags;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.fabricmc.fabric.api.tag.v1.FabricTagFile;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.tag.v1.FabricTagFile} injected by mod fabric-tag-api-v1</p>
 */
public record TagFile(List<TagEntry> entries, boolean replace) implements FabricTagFile {
	public static final Codec<TagFile> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				TagEntry.CODEC.listOf().fieldOf("values").forGetter(TagFile::entries), Codec.BOOL.optionalFieldOf("replace", false).forGetter(TagFile::replace)
			)
			.apply(instance, TagFile::new)
	);
}
