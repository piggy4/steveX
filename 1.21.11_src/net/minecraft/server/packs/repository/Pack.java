package net.minecraft.server.packs.repository;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FeatureFlagsMetadataSection;
import net.minecraft.server.packs.OverlayMetadataSection;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.world.flag.FeatureFlagSet;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class Pack {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final PackLocationInfo location;
	private final Pack.ResourcesSupplier resources;
	private final Pack.Metadata metadata;
	private final PackSelectionConfig selectionConfig;

	public static @Nullable Pack readMetaAndCreate(
		PackLocationInfo packLocationInfo, Pack.ResourcesSupplier resourcesSupplier, PackType packType, PackSelectionConfig packSelectionConfig
	) {
		PackFormat packFormat = SharedConstants.getCurrentVersion().packVersion(packType);
		Pack.Metadata metadata = readPackMetadata(packLocationInfo, resourcesSupplier, packFormat, packType);
		return metadata != null ? new Pack(packLocationInfo, resourcesSupplier, metadata, packSelectionConfig) : null;
	}

	public Pack(PackLocationInfo packLocationInfo, Pack.ResourcesSupplier resourcesSupplier, Pack.Metadata metadata, PackSelectionConfig packSelectionConfig) {
		this.location = packLocationInfo;
		this.resources = resourcesSupplier;
		this.metadata = metadata;
		this.selectionConfig = packSelectionConfig;
	}

	public static Pack.@Nullable Metadata readPackMetadata(
		PackLocationInfo packLocationInfo, Pack.ResourcesSupplier resourcesSupplier, PackFormat packFormat, PackType packType
	) {
		try (PackResources packResources = resourcesSupplier.openPrimary(packLocationInfo)) {
			PackMetadataSection packMetadataSection = packResources.getMetadataSection(PackMetadataSection.forPackType(packType));
			if (packMetadataSection == null) {
				packMetadataSection = packResources.getMetadataSection(PackMetadataSection.FALLBACK_TYPE);
			}

			if (packMetadataSection == null) {
				LOGGER.warn("Missing metadata in pack {}", packLocationInfo.id());
				return null;
			} else {
				FeatureFlagsMetadataSection featureFlagsMetadataSection = packResources.getMetadataSection(FeatureFlagsMetadataSection.TYPE);
				FeatureFlagSet featureFlagSet = featureFlagsMetadataSection != null ? featureFlagsMetadataSection.flags() : FeatureFlagSet.of();
				PackCompatibility packCompatibility = PackCompatibility.forVersion(packMetadataSection.supportedFormats(), packFormat);
				OverlayMetadataSection overlayMetadataSection = packResources.getMetadataSection(OverlayMetadataSection.forPackType(packType));
				List<String> list = overlayMetadataSection != null ? overlayMetadataSection.overlaysForVersion(packFormat) : List.of();
				return new Pack.Metadata(packMetadataSection.description(), packCompatibility, featureFlagSet, list);
			}
		} catch (Exception exception) {
			LOGGER.warn("Failed to read pack {} metadata", packLocationInfo.id(), exception);
			return null;
		}
	}

	public PackLocationInfo location() {
		return this.location;
	}

	public Component getTitle() {
		return this.location.title();
	}

	public Component getDescription() {
		return this.metadata.description();
	}

	public Component getChatLink(boolean bl) {
		return this.location.createChatLink(bl, this.metadata.description);
	}

	public PackCompatibility getCompatibility() {
		return this.metadata.compatibility();
	}

	public FeatureFlagSet getRequestedFeatures() {
		return this.metadata.requestedFeatures();
	}

	public PackResources open() {
		return this.resources.openFull(this.location, this.metadata);
	}

	public String getId() {
		return this.location.id();
	}

	public PackSelectionConfig selectionConfig() {
		return this.selectionConfig;
	}

	public boolean isRequired() {
		return this.selectionConfig.required();
	}

	public boolean isFixedPosition() {
		return this.selectionConfig.fixedPosition();
	}

	public Pack.Position getDefaultPosition() {
		return this.selectionConfig.defaultPosition();
	}

	public PackSource getPackSource() {
		return this.location.source();
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		} else {
			return !(object instanceof Pack pack) ? false : this.location.equals(pack.location);
		}
	}

	@Override
	public int hashCode() {
		return this.location.hashCode();
	}

	public record Metadata(Component description, PackCompatibility compatibility, FeatureFlagSet requestedFeatures, List<String> overlays) {
	}

	public enum Position {
		TOP,
		BOTTOM;

		public <T> int insert(List<T> list, T object, Function<T, PackSelectionConfig> function, boolean bl) {
			Pack.Position position = bl ? this.opposite() : this;
			if (position == BOTTOM) {
				int i;
				for (i = 0; i < list.size(); i++) {
					PackSelectionConfig packSelectionConfig = function.apply(list.get(i));
					if (!packSelectionConfig.fixedPosition() || packSelectionConfig.defaultPosition() != this) {
						break;
					}
				}

				list.add(i, object);
				return i;
			} else {
				int i;
				for (i = list.size() - 1; i >= 0; i--) {
					PackSelectionConfig packSelectionConfig = function.apply(list.get(i));
					if (!packSelectionConfig.fixedPosition() || packSelectionConfig.defaultPosition() != this) {
						break;
					}
				}

				list.add(i + 1, object);
				return i + 1;
			}
		}

		public Pack.Position opposite() {
			return this == TOP ? BOTTOM : TOP;
		}
	}

	public interface ResourcesSupplier {
		PackResources openPrimary(PackLocationInfo packLocationInfo);

		PackResources openFull(PackLocationInfo packLocationInfo, Pack.Metadata metadata);
	}
}
