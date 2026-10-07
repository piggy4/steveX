package net.minecraft.client.resources.model;

import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

@Environment(EnvType.CLIENT)
public class EquipmentAssetManager extends SimpleJsonResourceReloadListener<EquipmentClientInfo> {
	public static final EquipmentClientInfo MISSING = new EquipmentClientInfo(Map.of());
	private static final FileToIdConverter ASSET_LISTER = FileToIdConverter.json("equipment");
	private Map<ResourceKey<EquipmentAsset>, EquipmentClientInfo> equipmentAssets = Map.of();

	public EquipmentAssetManager() {
		super(EquipmentClientInfo.CODEC, ASSET_LISTER);
	}

	protected void apply(Map<Identifier, EquipmentClientInfo> map, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		this.equipmentAssets = map.entrySet()
			.stream()
			.collect(Collectors.toUnmodifiableMap(entry -> ResourceKey.create(EquipmentAssets.ROOT_ID, entry.getKey()), Entry::getValue));
	}

	public EquipmentClientInfo get(ResourceKey<EquipmentAsset> resourceKey) {
		return this.equipmentAssets.getOrDefault(resourceKey, MISSING);
	}
}
