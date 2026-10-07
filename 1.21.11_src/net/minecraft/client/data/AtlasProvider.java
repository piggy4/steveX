package net.minecraft.client.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.renderer.MaterialMapper;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BellRenderer;
import net.minecraft.client.renderer.blockentity.ConduitRenderer;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import net.minecraft.client.renderer.texture.atlas.sources.DirectoryLister;
import net.minecraft.client.renderer.texture.atlas.sources.PalettedPermutations;
import net.minecraft.client.renderer.texture.atlas.sources.SingleFile;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.data.AtlasIds;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.trim.MaterialAssetGroup;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.item.equipment.trim.TrimPatterns;

@Environment(EnvType.CLIENT)
public class AtlasProvider implements DataProvider {
	private static final Identifier TRIM_PALETTE_KEY = Identifier.withDefaultNamespace("trims/color_palettes/trim_palette");
	private static final Map<String, Identifier> TRIM_PALETTE_VALUES = extractAllMaterialAssets()
		.collect(Collectors.toMap(MaterialAssetGroup.AssetInfo::suffix, assetInfo -> Identifier.withDefaultNamespace("trims/color_palettes/" + assetInfo.suffix())));
	private static final List<ResourceKey<TrimPattern>> VANILLA_PATTERNS = List.of(
		TrimPatterns.SENTRY,
		TrimPatterns.DUNE,
		TrimPatterns.COAST,
		TrimPatterns.WILD,
		TrimPatterns.WARD,
		TrimPatterns.EYE,
		TrimPatterns.VEX,
		TrimPatterns.TIDE,
		TrimPatterns.SNOUT,
		TrimPatterns.RIB,
		TrimPatterns.SPIRE,
		TrimPatterns.WAYFINDER,
		TrimPatterns.SHAPER,
		TrimPatterns.SILENCE,
		TrimPatterns.RAISER,
		TrimPatterns.HOST,
		TrimPatterns.FLOW,
		TrimPatterns.BOLT
	);
	private static final List<EquipmentClientInfo.LayerType> HUMANOID_LAYERS = List.of(
		EquipmentClientInfo.LayerType.HUMANOID, EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS
	);
	private final PackOutput.PathProvider pathProvider;

	public AtlasProvider(PackOutput packOutput) {
		this.pathProvider = packOutput.createPathProvider(PackOutput.Target.RESOURCE_PACK, "atlases");
	}

	private static List<Identifier> patternTextures() {
		List<Identifier> list = new ArrayList<>(VANILLA_PATTERNS.size() * HUMANOID_LAYERS.size());

		for (ResourceKey<TrimPattern> resourceKey : VANILLA_PATTERNS) {
			Identifier identifier = TrimPatterns.defaultAssetId(resourceKey);

			for (EquipmentClientInfo.LayerType layerType : HUMANOID_LAYERS) {
				list.add(identifier.withPath(string -> layerType.trimAssetPrefix() + "/" + string));
			}
		}

		return list;
	}

	private static SpriteSource forMaterial(Material material) {
		return new SingleFile(material.texture());
	}

	private static SpriteSource forMapper(MaterialMapper materialMapper) {
		return new DirectoryLister(materialMapper.prefix(), materialMapper.prefix() + "/");
	}

	private static List<SpriteSource> simpleMapper(MaterialMapper materialMapper) {
		return List.of(forMapper(materialMapper));
	}

	private static List<SpriteSource> noPrefixMapper(String string) {
		return List.of(new DirectoryLister(string, ""));
	}

	private static Stream<MaterialAssetGroup.AssetInfo> extractAllMaterialAssets() {
		return ItemModelGenerators.TRIM_MATERIAL_MODELS
			.stream()
			.map(ItemModelGenerators.TrimMaterialData::assets)
			.flatMap(materialAssetGroup -> Stream.concat(Stream.of(materialAssetGroup.base()), materialAssetGroup.overrides().values().stream()))
			.sorted(Comparator.comparing(MaterialAssetGroup.AssetInfo::suffix));
	}

	private static List<SpriteSource> armorTrims() {
		return List.of(new PalettedPermutations(patternTextures(), TRIM_PALETTE_KEY, TRIM_PALETTE_VALUES));
	}

	private static List<SpriteSource> blocksList() {
		return List.of(
			forMapper(Sheets.BLOCKS_MAPPER), forMapper(ConduitRenderer.MAPPER), forMaterial(BellRenderer.BELL_TEXTURE), forMaterial(EnchantTableRenderer.BOOK_TEXTURE)
		);
	}

	private static List<SpriteSource> itemsList() {
		return List.of(
			forMapper(Sheets.ITEMS_MAPPER),
			new PalettedPermutations(
				List.of(
					ItemModelGenerators.TRIM_PREFIX_HELMET,
					ItemModelGenerators.TRIM_PREFIX_CHESTPLATE,
					ItemModelGenerators.TRIM_PREFIX_LEGGINGS,
					ItemModelGenerators.TRIM_PREFIX_BOOTS
				),
				TRIM_PALETTE_KEY,
				TRIM_PALETTE_VALUES
			)
		);
	}

	private static List<SpriteSource> bannerPatterns() {
		return List.of(forMaterial(ModelBakery.BANNER_BASE), forMapper(Sheets.BANNER_MAPPER));
	}

	private static List<SpriteSource> shieldPatterns() {
		return List.of(forMaterial(ModelBakery.SHIELD_BASE), forMaterial(ModelBakery.NO_PATTERN_SHIELD), forMapper(Sheets.SHIELD_MAPPER));
	}

	private static List<SpriteSource> guiSprites() {
		return List.of(new DirectoryLister("gui/sprites", ""), new DirectoryLister("mob_effect", "mob_effect/"));
	}

	@Override
	public CompletableFuture<?> run(CachedOutput cachedOutput) {
		return CompletableFuture.allOf(
			this.storeAtlas(cachedOutput, AtlasIds.ARMOR_TRIMS, armorTrims()),
			this.storeAtlas(cachedOutput, AtlasIds.BANNER_PATTERNS, bannerPatterns()),
			this.storeAtlas(cachedOutput, AtlasIds.BEDS, simpleMapper(Sheets.BED_MAPPER)),
			this.storeAtlas(cachedOutput, AtlasIds.BLOCKS, blocksList()),
			this.storeAtlas(cachedOutput, AtlasIds.ITEMS, itemsList()),
			this.storeAtlas(cachedOutput, AtlasIds.CHESTS, simpleMapper(Sheets.CHEST_MAPPER)),
			this.storeAtlas(cachedOutput, AtlasIds.DECORATED_POT, simpleMapper(Sheets.DECORATED_POT_MAPPER)),
			this.storeAtlas(cachedOutput, AtlasIds.GUI, guiSprites()),
			this.storeAtlas(cachedOutput, AtlasIds.MAP_DECORATIONS, noPrefixMapper("map/decorations")),
			this.storeAtlas(cachedOutput, AtlasIds.PAINTINGS, noPrefixMapper("painting")),
			this.storeAtlas(cachedOutput, AtlasIds.PARTICLES, noPrefixMapper("particle")),
			this.storeAtlas(cachedOutput, AtlasIds.SHIELD_PATTERNS, shieldPatterns()),
			this.storeAtlas(cachedOutput, AtlasIds.SHULKER_BOXES, simpleMapper(Sheets.SHULKER_MAPPER)),
			this.storeAtlas(cachedOutput, AtlasIds.SIGNS, simpleMapper(Sheets.SIGN_MAPPER)),
			this.storeAtlas(cachedOutput, AtlasIds.CELESTIALS, noPrefixMapper("environment/celestial"))
		);
	}

	private CompletableFuture<?> storeAtlas(CachedOutput cachedOutput, Identifier identifier, List<SpriteSource> list) {
		return DataProvider.saveStable(cachedOutput, SpriteSources.FILE_CODEC, list, this.pathProvider.json(identifier));
	}

	@Override
	public String getName() {
		return "Atlas Definitions";
	}
}
