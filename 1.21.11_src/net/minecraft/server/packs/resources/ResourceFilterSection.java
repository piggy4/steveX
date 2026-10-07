package net.minecraft.server.packs.resources;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.util.IdentifierPattern;

public class ResourceFilterSection {
	private static final Codec<ResourceFilterSection> CODEC = RecordCodecBuilder.create(
		instance -> instance.group(Codec.list(IdentifierPattern.CODEC).fieldOf("block").forGetter(resourceFilterSection -> resourceFilterSection.blockList))
			.apply(instance, ResourceFilterSection::new)
	);
	public static final MetadataSectionType<ResourceFilterSection> TYPE = new MetadataSectionType<>("filter", CODEC);
	private final List<IdentifierPattern> blockList;

	public ResourceFilterSection(List<IdentifierPattern> list) {
		this.blockList = List.copyOf(list);
	}

	public boolean isNamespaceFiltered(String string) {
		return this.blockList.stream().anyMatch(identifierPattern -> identifierPattern.namespacePredicate().test(string));
	}

	public boolean isPathFiltered(String string) {
		return this.blockList.stream().anyMatch(identifierPattern -> identifierPattern.pathPredicate().test(string));
	}
}
