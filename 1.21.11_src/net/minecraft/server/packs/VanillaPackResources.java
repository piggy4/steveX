package net.minecraft.server.packs;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.FileUtil;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class VanillaPackResources implements PackResources {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final PackLocationInfo location;
	private final BuiltInMetadata metadata;
	private final Set<String> namespaces;
	private final List<Path> rootPaths;
	private final Map<PackType, List<Path>> pathsForType;

	VanillaPackResources(PackLocationInfo packLocationInfo, BuiltInMetadata builtInMetadata, Set<String> set, List<Path> list, Map<PackType, List<Path>> map) {
		this.location = packLocationInfo;
		this.metadata = builtInMetadata;
		this.namespaces = set;
		this.rootPaths = list;
		this.pathsForType = map;
	}

	@Override
	public @Nullable IoSupplier<InputStream> getRootResource(String... strings) {
		FileUtil.validatePath(strings);
		List<String> list = List.of(strings);

		for (Path path : this.rootPaths) {
			Path path2 = FileUtil.resolvePath(path, list);
			if (Files.exists(path2) && PathPackResources.validatePath(path2)) {
				return IoSupplier.create(path2);
			}
		}

		return null;
	}

	public void listRawPaths(PackType packType, Identifier identifier, Consumer<Path> consumer) {
		FileUtil.decomposePath(identifier.getPath()).ifSuccess(list -> {
			String string = identifier.getNamespace();

			for (Path path : this.pathsForType.get(packType)) {
				Path path2 = path.resolve(string);
				consumer.accept(FileUtil.resolvePath(path2, (List<String>)list));
			}
		}).ifError(error -> LOGGER.error("Invalid path {}: {}", identifier, error.message()));
	}

	@Override
	public void listResources(PackType packType, String string, String string2, PackResources.ResourceOutput resourceOutput) {
		FileUtil.decomposePath(string2).ifSuccess(list -> {
			List<Path> list2 = this.pathsForType.get(packType);
			int i = list2.size();
			if (i == 1) {
				getResources(resourceOutput, string, list2.get(0), (List<String>)list);
			} else if (i > 1) {
				Map<Identifier, IoSupplier<InputStream>> map = new HashMap<>();

				for (int j = 0; j < i - 1; j++) {
					getResources(map::putIfAbsent, string, list2.get(j), (List<String>)list);
				}

				Path path = list2.get(i - 1);
				if (map.isEmpty()) {
					getResources(resourceOutput, string, path, (List<String>)list);
				} else {
					getResources(map::putIfAbsent, string, path, (List<String>)list);
					map.forEach(resourceOutput);
				}
			}
		}).ifError(error -> LOGGER.error("Invalid path {}: {}", string2, error.message()));
	}

	private static void getResources(PackResources.ResourceOutput resourceOutput, String string, Path path, List<String> list) {
		Path path2 = path.resolve(string);
		PathPackResources.listPath(string, path2, list, resourceOutput);
	}

	@Override
	public @Nullable IoSupplier<InputStream> getResource(PackType packType, Identifier identifier) {
		return FileUtil.decomposePath(identifier.getPath()).mapOrElse(list -> {
			String string = identifier.getNamespace();

			for (Path path : this.pathsForType.get(packType)) {
				Path path2 = FileUtil.resolvePath(path.resolve(string), (List<String>)list);
				if (Files.exists(path2) && PathPackResources.validatePath(path2)) {
					return IoSupplier.create(path2);
				}
			}

			return null;
		}, error -> {
			LOGGER.error("Invalid path {}: {}", identifier, error.message());
			return null;
		});
	}

	@Override
	public Set<String> getNamespaces(PackType packType) {
		return this.namespaces;
	}

	@Override
	public <T> @Nullable T getMetadataSection(MetadataSectionType<T> metadataSectionType) {
		IoSupplier<InputStream> ioSupplier = this.getRootResource("pack.mcmeta");
		if (ioSupplier != null) {
			try (InputStream inputStream = ioSupplier.get()) {
				T object = AbstractPackResources.getMetadataFromStream(metadataSectionType, inputStream, this.location);
				if (object != null) {
					return object;
				}
			} catch (IOException var8) {
			}
		}

		return this.metadata.get(metadataSectionType);
	}

	@Override
	public PackLocationInfo location() {
		return this.location;
	}

	@Override
	public void close() {
	}

	public ResourceProvider asProvider() {
		return identifier -> Optional.ofNullable(this.getResource(PackType.CLIENT_RESOURCES, identifier))
			.map(ioSupplier -> new Resource(this, (IoSupplier<InputStream>)ioSupplier));
	}
}
