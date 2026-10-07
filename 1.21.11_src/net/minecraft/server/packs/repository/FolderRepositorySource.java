package net.minecraft.server.packs.repository;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.linkfs.LinkFileSystem;
import net.minecraft.util.FileUtil;
import net.minecraft.world.level.validation.ContentValidationException;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.minecraft.world.level.validation.ForbiddenSymlinkInfo;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class FolderRepositorySource implements RepositorySource {
	static final Logger LOGGER = LogUtils.getLogger();
	private static final PackSelectionConfig DISCOVERED_PACK_SELECTION_CONFIG = new PackSelectionConfig(false, Pack.Position.TOP, false);
	private final Path folder;
	private final PackType packType;
	private final PackSource packSource;
	private final DirectoryValidator validator;

	public FolderRepositorySource(Path path, PackType packType, PackSource packSource, DirectoryValidator directoryValidator) {
		this.folder = path;
		this.packType = packType;
		this.packSource = packSource;
		this.validator = directoryValidator;
	}

	private static String nameFromPath(Path path) {
		return path.getFileName().toString();
	}

	@Override
	public void loadPacks(Consumer<Pack> consumer) {
		try {
			FileUtil.createDirectoriesSafe(this.folder);
			discoverPacks(this.folder, this.validator, (path, resourcesSupplier) -> {
				PackLocationInfo packLocationInfo = this.createDiscoveredFilePackInfo(path);
				Pack pack = Pack.readMetaAndCreate(packLocationInfo, resourcesSupplier, this.packType, DISCOVERED_PACK_SELECTION_CONFIG);
				if (pack != null) {
					consumer.accept(pack);
				}
			});
		} catch (IOException iOException) {
			LOGGER.warn("Failed to list packs in {}", this.folder, iOException);
		}
	}

	private PackLocationInfo createDiscoveredFilePackInfo(Path path) {
		String string = nameFromPath(path);
		return new PackLocationInfo("file/" + string, Component.literal(string), this.packSource, Optional.empty());
	}

	public static void discoverPacks(Path path, DirectoryValidator directoryValidator, BiConsumer<Path, Pack.ResourcesSupplier> biConsumer) throws IOException {
		FolderRepositorySource.FolderPackDetector folderPackDetector = new FolderRepositorySource.FolderPackDetector(directoryValidator);

		try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(path)) {
			for (Path path2 : directoryStream) {
				try {
					List<ForbiddenSymlinkInfo> list = new ArrayList<>();
					Pack.ResourcesSupplier resourcesSupplier = folderPackDetector.detectPackResources(path2, list);
					if (!list.isEmpty()) {
						LOGGER.warn("Ignoring potential pack entry: {}", ContentValidationException.getMessage(path2, list));
					} else if (resourcesSupplier != null) {
						biConsumer.accept(path2, resourcesSupplier);
					} else {
						LOGGER.info("Found non-pack entry '{}', ignoring", path2);
					}
				} catch (IOException iOException) {
					LOGGER.warn("Failed to read properties of '{}', ignoring", path2, iOException);
				}
			}
		}
	}

	static class FolderPackDetector extends PackDetector<Pack.ResourcesSupplier> {
		protected FolderPackDetector(DirectoryValidator directoryValidator) {
			super(directoryValidator);
		}

		protected Pack.@Nullable ResourcesSupplier createZipPack(Path path) {
			FileSystem fileSystem = path.getFileSystem();
			if (fileSystem != FileSystems.getDefault() && !(fileSystem instanceof LinkFileSystem)) {
				FolderRepositorySource.LOGGER.info("Can't open pack archive at {}", path);
				return null;
			} else {
				return new FilePackResources.FileResourcesSupplier(path);
			}
		}

		protected Pack.ResourcesSupplier createDirectoryPack(Path path) {
			return new PathPackResources.PathResourcesSupplier(path);
		}
	}
}
