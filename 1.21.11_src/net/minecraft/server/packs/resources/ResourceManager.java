package net.minecraft.server.packs.resources;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;

public interface ResourceManager extends ResourceProvider {
	Set<String> getNamespaces();

	List<Resource> getResourceStack(Identifier identifier);

	Map<Identifier, Resource> listResources(String string, Predicate<Identifier> predicate);

	Map<Identifier, List<Resource>> listResourceStacks(String string, Predicate<Identifier> predicate);

	Stream<PackResources> listPacks();

	enum Empty implements ResourceManager {
		INSTANCE;

		@Override
		public Set<String> getNamespaces() {
			return Set.of();
		}

		@Override
		public Optional<Resource> getResource(Identifier identifier) {
			return Optional.empty();
		}

		@Override
		public List<Resource> getResourceStack(Identifier identifier) {
			return List.of();
		}

		@Override
		public Map<Identifier, Resource> listResources(String string, Predicate<Identifier> predicate) {
			return Map.of();
		}

		@Override
		public Map<Identifier, List<Resource>> listResourceStacks(String string, Predicate<Identifier> predicate) {
			return Map.of();
		}

		@Override
		public Stream<PackResources> listPacks() {
			return Stream.of();
		}
	}
}
