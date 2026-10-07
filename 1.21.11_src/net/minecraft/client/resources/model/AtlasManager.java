package net.minecraft.client.resources.model;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.metadata.gui.GuiMetadataSection;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class AtlasManager implements PreparableReloadListener, MaterialSet, AutoCloseable {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final List<AtlasManager.AtlasConfig> KNOWN_ATLASES = List.of(
		new AtlasManager.AtlasConfig(Sheets.ARMOR_TRIMS_SHEET, AtlasIds.ARMOR_TRIMS, false),
		new AtlasManager.AtlasConfig(Sheets.BANNER_SHEET, AtlasIds.BANNER_PATTERNS, false),
		new AtlasManager.AtlasConfig(Sheets.BED_SHEET, AtlasIds.BEDS, false),
		new AtlasManager.AtlasConfig(TextureAtlas.LOCATION_BLOCKS, AtlasIds.BLOCKS, true),
		new AtlasManager.AtlasConfig(TextureAtlas.LOCATION_ITEMS, AtlasIds.ITEMS, false),
		new AtlasManager.AtlasConfig(Sheets.CHEST_SHEET, AtlasIds.CHESTS, false),
		new AtlasManager.AtlasConfig(Sheets.DECORATED_POT_SHEET, AtlasIds.DECORATED_POT, false),
		new AtlasManager.AtlasConfig(Sheets.GUI_SHEET, AtlasIds.GUI, false, Set.of(GuiMetadataSection.TYPE)),
		new AtlasManager.AtlasConfig(Sheets.MAP_DECORATIONS_SHEET, AtlasIds.MAP_DECORATIONS, false),
		new AtlasManager.AtlasConfig(Sheets.PAINTINGS_SHEET, AtlasIds.PAINTINGS, false),
		new AtlasManager.AtlasConfig(TextureAtlas.LOCATION_PARTICLES, AtlasIds.PARTICLES, false),
		new AtlasManager.AtlasConfig(Sheets.SHIELD_SHEET, AtlasIds.SHIELD_PATTERNS, false),
		new AtlasManager.AtlasConfig(Sheets.SHULKER_SHEET, AtlasIds.SHULKER_BOXES, false),
		new AtlasManager.AtlasConfig(Sheets.SIGN_SHEET, AtlasIds.SIGNS, false),
		new AtlasManager.AtlasConfig(Sheets.CELESTIAL_SHEET, AtlasIds.CELESTIALS, false)
	);
	public static final PreparableReloadListener.StateKey<AtlasManager.PendingStitchResults> PENDING_STITCH = new PreparableReloadListener.StateKey<>();
	private final Map<Identifier, AtlasManager.AtlasEntry> atlasByTexture = new HashMap<>();
	private final Map<Identifier, AtlasManager.AtlasEntry> atlasById = new HashMap<>();
	private Map<Material, TextureAtlasSprite> materialLookup = Map.of();
	private int maxMipmapLevels;

	public AtlasManager(TextureManager textureManager, int i) {
		for (AtlasManager.AtlasConfig atlasConfig : KNOWN_ATLASES) {
			TextureAtlas textureAtlas = new TextureAtlas(atlasConfig.textureId);
			textureManager.register(atlasConfig.textureId, textureAtlas);
			AtlasManager.AtlasEntry atlasEntry = new AtlasManager.AtlasEntry(textureAtlas, atlasConfig);
			this.atlasByTexture.put(atlasConfig.textureId, atlasEntry);
			this.atlasById.put(atlasConfig.definitionLocation, atlasEntry);
		}

		this.maxMipmapLevels = i;
	}

	public TextureAtlas getAtlasOrThrow(Identifier identifier) {
		AtlasManager.AtlasEntry atlasEntry = this.atlasById.get(identifier);
		if (atlasEntry == null) {
			throw new IllegalArgumentException("Invalid atlas id: " + identifier);
		} else {
			return atlasEntry.atlas();
		}
	}

	public void forEach(BiConsumer<Identifier, TextureAtlas> biConsumer) {
		this.atlasById.forEach((identifier, atlasEntry) -> biConsumer.accept(identifier, atlasEntry.atlas));
	}

	public void updateMaxMipLevel(int i) {
		this.maxMipmapLevels = i;
	}

	@Override
	public void close() {
		this.materialLookup = Map.of();
		this.atlasById.values().forEach(AtlasManager.AtlasEntry::close);
		this.atlasById.clear();
		this.atlasByTexture.clear();
	}

	@Override
	public TextureAtlasSprite get(Material material) {
		TextureAtlasSprite textureAtlasSprite = this.materialLookup.get(material);
		if (textureAtlasSprite != null) {
			return textureAtlasSprite;
		} else {
			Identifier identifier = material.atlasLocation();
			AtlasManager.AtlasEntry atlasEntry = this.atlasByTexture.get(identifier);
			if (atlasEntry == null) {
				throw new IllegalArgumentException("Invalid atlas texture id: " + identifier);
			} else {
				return atlasEntry.atlas().missingSprite();
			}
		}
	}

	@Override
	public void prepareSharedState(PreparableReloadListener.SharedState sharedState) {
		int i = this.atlasById.size();
		List<AtlasManager.PendingStitch> list = new ArrayList<>(i);
		Map<Identifier, CompletableFuture<SpriteLoader.Preparations>> map = new HashMap<>(i);
		List<CompletableFuture<?>> list2 = new ArrayList<>(i);
		this.atlasById.forEach((identifier, atlasEntry) -> {
			CompletableFuture<SpriteLoader.Preparations> completableFuturex = new CompletableFuture<>();
			map.put(identifier, completableFuturex);
			list.add(new AtlasManager.PendingStitch(atlasEntry, completableFuturex));
			list2.add(completableFuturex.thenCompose(SpriteLoader.Preparations::readyForUpload));
		});
		CompletableFuture<?> completableFuture = CompletableFuture.allOf(list2.toArray(CompletableFuture[]::new));
		sharedState.set(PENDING_STITCH, new AtlasManager.PendingStitchResults(list, map, completableFuture));
	}

	@Override
	public CompletableFuture<Void> reload(
		PreparableReloadListener.SharedState sharedState, Executor executor, PreparableReloadListener.PreparationBarrier preparationBarrier, Executor executor2
	) {
		AtlasManager.PendingStitchResults pendingStitchResults = sharedState.get(PENDING_STITCH);
		ResourceManager resourceManager = sharedState.resourceManager();
		pendingStitchResults.pendingStitches
			.forEach(pendingStitch -> pendingStitch.entry.scheduleLoad(resourceManager, executor, this.maxMipmapLevels).whenComplete((preparations, throwable) -> {
				if (preparations != null) {
					pendingStitch.preparations.complete(preparations);
				} else {
					pendingStitch.preparations.completeExceptionally(throwable);
				}
			}));
		return pendingStitchResults.allReadyToUpload
			.thenCompose(preparationBarrier::wait)
			.thenAcceptAsync(object -> this.updateSpriteMaps(pendingStitchResults), executor2);
	}

	private void updateSpriteMaps(AtlasManager.PendingStitchResults pendingStitchResults) {
		this.materialLookup = pendingStitchResults.joinAndUpload();
		Map<Identifier, TextureAtlasSprite> map = new HashMap<>();
		this.materialLookup
			.forEach(
				(material, textureAtlasSprite) -> {
					if (!material.texture().equals(MissingTextureAtlasSprite.getLocation())) {
						TextureAtlasSprite textureAtlasSprite2 = map.putIfAbsent(material.texture(), textureAtlasSprite);
						if (textureAtlasSprite2 != null) {
							LOGGER.warn(
								"Duplicate sprite {} from atlas {}, already defined in atlas {}. This will be rejected in a future version",
								material.texture(),
								material.atlasLocation(),
								textureAtlasSprite2.atlasLocation()
							);
						}
					}
				}
			);
	}

	@Environment(EnvType.CLIENT)
	public record AtlasConfig(Identifier textureId, Identifier definitionLocation, boolean createMipmaps, Set<MetadataSectionType<?>> additionalMetadata) {

		public AtlasConfig(Identifier identifier, Identifier identifier2, boolean bl) {
			this(identifier, identifier2, bl, Set.of());
		}
	}

	@Environment(EnvType.CLIENT)
	record AtlasEntry(TextureAtlas atlas, AtlasManager.AtlasConfig config) implements AutoCloseable {

		@Override
		public void close() {
			this.atlas.clearTextureData();
		}

		CompletableFuture<SpriteLoader.Preparations> scheduleLoad(ResourceManager resourceManager, Executor executor, int i) {
			return SpriteLoader.create(this.atlas)
				.loadAndStitch(resourceManager, this.config.definitionLocation, this.config.createMipmaps ? i : 0, executor, this.config.additionalMetadata);
		}
	}

	@Environment(EnvType.CLIENT)
	record PendingStitch(AtlasManager.AtlasEntry entry, CompletableFuture<SpriteLoader.Preparations> preparations) {

		public void joinAndUpload(Map<Material, TextureAtlasSprite> map) {
			SpriteLoader.Preparations preparations = this.preparations.join();
			this.entry.atlas.upload(preparations);
			preparations.regions().forEach((identifier, textureAtlasSprite) -> map.put(new Material(this.entry.config.textureId, identifier), textureAtlasSprite));
		}
	}

	@Environment(EnvType.CLIENT)
	public static class PendingStitchResults {
		final List<AtlasManager.PendingStitch> pendingStitches;
		private final Map<Identifier, CompletableFuture<SpriteLoader.Preparations>> stitchFuturesById;
		final CompletableFuture<?> allReadyToUpload;

		PendingStitchResults(
			List<AtlasManager.PendingStitch> list, Map<Identifier, CompletableFuture<SpriteLoader.Preparations>> map, CompletableFuture<?> completableFuture
		) {
			this.pendingStitches = list;
			this.stitchFuturesById = map;
			this.allReadyToUpload = completableFuture;
		}

		public Map<Material, TextureAtlasSprite> joinAndUpload() {
			Map<Material, TextureAtlasSprite> map = new HashMap<>();
			this.pendingStitches.forEach(pendingStitch -> pendingStitch.joinAndUpload(map));
			return map;
		}

		public CompletableFuture<SpriteLoader.Preparations> get(Identifier identifier) {
			return Objects.requireNonNull(this.stitchFuturesById.get(identifier));
		}
	}
}
