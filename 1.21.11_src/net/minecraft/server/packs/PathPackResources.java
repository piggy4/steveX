package net.minecraft.server.packs;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.FileUtil;
import net.minecraft.util.Util;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class PathPackResources extends AbstractPackResources {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Joiner PATH_JOINER = Joiner.on("/");
	private final Path root;

	public PathPackResources(PackLocationInfo packLocationInfo, Path path) {
		super(packLocationInfo);
		this.root = path;
	}

	@Override
	public @Nullable IoSupplier<InputStream> getRootResource(String... strings) {
		FileUtil.validatePath(strings);
		Path path = FileUtil.resolvePath(this.root, List.of(strings));
		return Files.exists(path) ? IoSupplier.create(path) : null;
	}

	public static boolean validatePath(Path path) {
		if (!SharedConstants.DEBUG_VALIDATE_RESOURCE_PATH_CASE) {
			return true;
		}

		if (path.getFileSystem() != FileSystems.getDefault()) {
			return true;
		}

		try {
			return path.toRealPath().endsWith(path);
		} catch (IOException iOException) {
			LOGGER.warn("Failed to resolve real path for {}", path, iOException);
			return false;
		}
	}

	@Override
	public @Nullable IoSupplier<InputStream> getResource(PackType packType, Identifier identifier) {
		Path path = this.root.resolve(packType.getDirectory()).resolve(identifier.getNamespace());
		return getResource(identifier, path);
	}

	public static @Nullable IoSupplier<InputStream> getResource(Identifier identifier, Path path) {
		return FileUtil.decomposePath(identifier.getPath()).mapOrElse(list -> {
			Path path2 = FileUtil.resolvePath(path, (List<String>)list);
			return returnFileIfExists(path2);
		}, error -> {
			LOGGER.error("Invalid path {}: {}", identifier, error.message());
			return null;
		});
	}

	private static @Nullable IoSupplier<InputStream> returnFileIfExists(Path path) {
		return Files.exists(path) && validatePath(path) ? IoSupplier.create(path) : null;
	}

	@Override
	public void listResources(PackType packType, String string, String string2, PackResources.ResourceOutput resourceOutput) {
		FileUtil.decomposePath(string2).ifSuccess(list -> {
			Path path = this.root.resolve(packType.getDirectory()).resolve(string);
			listPath(string, path, (List<String>)list, resourceOutput);
		}).ifError(error -> LOGGER.error("Invalid path {}: {}", string2, error.message()));
	}

	public static void listPath(String string, Path path, List<String> list, PackResources.ResourceOutput resourceOutput) {
		Path path2 = FileUtil.resolvePath(path, list);

		try (Stream<Path> stream = Files.find(path2, Integer.MAX_VALUE, PathPackResources::isRegularFile)) {
			stream.forEach(path2x -> {
				String string2 = PATH_JOINER.join(path.relativize(path2x));
				Identifier identifier = Identifier.tryBuild(string, string2);
				if (identifier == null) {
					Util.logAndPauseIfInIde(String.format(Locale.ROOT, "Invalid path in pack: %s:%s, ignoring", string, string2));
				} else {
					resourceOutput.accept(identifier, IoSupplier.create(path2x));
				}
			});
		} catch (NoSuchFileException | NotDirectoryException var10) {
		} catch (IOException iOException) {
			LOGGER.error("Failed to list path {}", path2, iOException);
		}
	}

	private static boolean isRegularFile(Path path, BasicFileAttributes basicFileAttributes) {
		return !SharedConstants.IS_RUNNING_IN_IDE
			? basicFileAttributes.isRegularFile()
			: basicFileAttributes.isRegularFile() && !StringUtils.equalsIgnoreCase(path.getFileName().toString(), ".ds_store");
	}

	@Override
	public Set<String> getNamespaces(PackType packType) {
		Set<String> set = Sets.newHashSet();
		Path path = this.root.resolve(packType.getDirectory());

		try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(path)) {
			for (Path path2 : directoryStream) {
				String string = path2.getFileName().toString();
				if (Identifier.isValidNamespace(string)) {
					set.add(string);
				} else {
					LOGGER.warn("Non [a-z0-9_.-] character in namespace {} in pack {}, ignoring", string, this.root);
				}
			}
		} catch (NoSuchFileException | NotDirectoryException var10) {
		} catch (IOException iOException) {
			LOGGER.error("Failed to list path {}", path, iOException);
		}

		return set;
	}

	@Override
	public void close() {
	}

	public static class PathResourcesSupplier implements Pack.ResourcesSupplier {
		private final Path content;

		public PathResourcesSupplier(Path path) {
			this.content = path;
		}

		@Override
		public PackResources openPrimary(PackLocationInfo packLocationInfo) {
			return new PathPackResources(packLocationInfo, this.content);
		}

		@Override
		public PackResources openFull(PackLocationInfo packLocationInfo, Pack.Metadata metadata) {
			PackResources packResources = this.openPrimary(packLocationInfo);
			List<String> list = metadata.overlays();
			if (list.isEmpty()) {
				return packResources;
			}

			List<PackResources> list2 = new ArrayList<>(list.size());

			for (String string : list) {
				Path path = this.content.resolve(string);
				list2.add(new PathPackResources(packLocationInfo, path));
			}

			return new CompositePackResources(packResources, list2);
		}
	}
}
