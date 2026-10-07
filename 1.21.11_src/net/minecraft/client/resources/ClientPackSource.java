package net.minecraft.client.resources;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.BuiltInMetadata;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.VanillaPackResourcesBuilder;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.validation.DirectoryValidator;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ClientPackSource extends BuiltInPackSource {
	private static final PackMetadataSection VERSION_METADATA_SECTION = new PackMetadataSection(
		Component.translatable("resourcePack.vanilla.description"), SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES).minorRange()
	);
	private static final BuiltInMetadata BUILT_IN_METADATA = BuiltInMetadata.of(PackMetadataSection.CLIENT_TYPE, VERSION_METADATA_SECTION);
	public static final String HIGH_CONTRAST_PACK = "high_contrast";
	private static final Map<String, Component> SPECIAL_PACK_NAMES = Map.of(
		"programmer_art", Component.translatable("resourcePack.programmer_art.name"), "high_contrast", Component.translatable("resourcePack.high_contrast.name")
	);
	private static final PackLocationInfo VANILLA_PACK_INFO = new PackLocationInfo(
		"vanilla", Component.translatable("resourcePack.vanilla.name"), PackSource.BUILT_IN, Optional.of(CORE_PACK_INFO)
	);
	private static final PackSelectionConfig VANILLA_SELECTION_CONFIG = new PackSelectionConfig(true, Pack.Position.BOTTOM, false);
	private static final PackSelectionConfig BUILT_IN_SELECTION_CONFIG = new PackSelectionConfig(false, Pack.Position.TOP, false);
	private static final Identifier PACKS_DIR = Identifier.withDefaultNamespace("resourcepacks");
	private final @Nullable Path externalAssetDir;

	public ClientPackSource(Path path, DirectoryValidator directoryValidator) {
		super(PackType.CLIENT_RESOURCES, createVanillaPackSource(path), PACKS_DIR, directoryValidator);
		this.externalAssetDir = this.findExplodedAssetPacks(path);
	}

	private static PackLocationInfo createBuiltInPackLocation(String string, Component component) {
		return new PackLocationInfo(string, component, PackSource.BUILT_IN, Optional.of(KnownPack.vanilla(string)));
	}

	private @Nullable Path findExplodedAssetPacks(Path path) {
		if (SharedConstants.IS_RUNNING_IN_IDE && path.getFileSystem() == FileSystems.getDefault()) {
			Path path2 = path.getParent().resolve("resourcepacks");
			if (Files.isDirectory(path2)) {
				return path2;
			}
		}

		return null;
	}

	private static VanillaPackResources createVanillaPackSource(Path path) {
		VanillaPackResourcesBuilder vanillaPackResourcesBuilder = new VanillaPackResourcesBuilder()
			.setMetadata(BUILT_IN_METADATA)
			.exposeNamespace("minecraft", "realms");
		return vanillaPackResourcesBuilder.applyDevelopmentConfig().pushJarResources().pushAssetPath(PackType.CLIENT_RESOURCES, path).build(VANILLA_PACK_INFO);
	}

	@Override
	protected Component getPackTitle(String string) {
		Component component = SPECIAL_PACK_NAMES.get(string);
		return component != null ? component : Component.literal(string);
	}

	@Override
	protected @Nullable Pack createVanillaPack(PackResources packResources) {
		return Pack.readMetaAndCreate(VANILLA_PACK_INFO, fixedResources(packResources), PackType.CLIENT_RESOURCES, VANILLA_SELECTION_CONFIG);
	}

	@Override
	protected @Nullable Pack createBuiltinPack(String string, Pack.ResourcesSupplier resourcesSupplier, Component component) {
		return Pack.readMetaAndCreate(createBuiltInPackLocation(string, component), resourcesSupplier, PackType.CLIENT_RESOURCES, BUILT_IN_SELECTION_CONFIG);
	}

	@Override
	protected void populatePackList(BiConsumer<String, Function<String, Pack>> biConsumer) {
		super.populatePackList(biConsumer);
		if (this.externalAssetDir != null) {
			this.discoverPacksInPath(this.externalAssetDir, biConsumer);
		}
	}
}
