package net.minecraft.server.packs.resources;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;

@FunctionalInterface
public interface ResourceProvider {
	ResourceProvider EMPTY = identifier -> Optional.empty();

	Optional<Resource> getResource(Identifier identifier);

	default Resource getResourceOrThrow(Identifier identifier) throws FileNotFoundException {
		return this.getResource(identifier).orElseThrow(() -> new FileNotFoundException(identifier.toString()));
	}

	default InputStream open(Identifier identifier) throws IOException {
		return this.getResourceOrThrow(identifier).open();
	}

	default BufferedReader openAsReader(Identifier identifier) throws IOException {
		return this.getResourceOrThrow(identifier).openAsReader();
	}

	static ResourceProvider fromMap(Map<Identifier, Resource> map) {
		return identifier -> Optional.ofNullable(map.get(identifier));
	}
}
