package net.minecraft.resources;

import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public class FileToIdConverter {
	private final String prefix;
	private final String extension;

	public FileToIdConverter(String string, String string2) {
		this.prefix = string;
		this.extension = string2;
	}

	public static FileToIdConverter json(String string) {
		return new FileToIdConverter(string, ".json");
	}

	public static FileToIdConverter registry(ResourceKey<? extends Registry<?>> resourceKey) {
		return json(Registries.elementsDirPath(resourceKey));
	}

	public Identifier idToFile(Identifier identifier) {
		return identifier.withPath(this.prefix + "/" + identifier.getPath() + this.extension);
	}

	public Identifier fileToId(Identifier identifier) {
		String string = identifier.getPath();
		return identifier.withPath(string.substring(this.prefix.length() + 1, string.length() - this.extension.length()));
	}

	public Map<Identifier, Resource> listMatchingResources(ResourceManager resourceManager) {
		return resourceManager.listResources(this.prefix, identifier -> identifier.getPath().endsWith(this.extension));
	}

	public Map<Identifier, List<Resource>> listMatchingResourceStacks(ResourceManager resourceManager) {
		return resourceManager.listResourceStacks(this.prefix, identifier -> identifier.getPath().endsWith(this.extension));
	}
}
