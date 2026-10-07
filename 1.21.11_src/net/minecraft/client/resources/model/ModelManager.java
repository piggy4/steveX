package net.minecraft.client.resources.model;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import java.io.Reader;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SpecialBlockModelRenderer;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.Zone;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.slf4j.Logger;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager} injected by mod fabric-model-loading-api-v1</p>
 */
@Environment(EnvType.CLIENT)
public class ModelManager implements PreparableReloadListener, FabricBakedModelManager {
	public static final Identifier BLOCK_OR_ITEM = Identifier.withDefaultNamespace("block_or_item");
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final FileToIdConverter MODEL_LISTER = FileToIdConverter.json("models");
	private Map<Identifier, ItemModel> bakedItemStackModels = Map.of();
	private Map<Identifier, ClientItem.Properties> itemProperties = Map.of();
	private final AtlasManager atlasManager;
	private final PlayerSkinRenderCache playerSkinRenderCache;
	private final BlockModelShaper blockModelShaper;
	private final BlockColors blockColors;
	private EntityModelSet entityModelSet = EntityModelSet.EMPTY;
	private SpecialBlockModelRenderer specialBlockModelRenderer = SpecialBlockModelRenderer.EMPTY;
	private ModelBakery.MissingModels missingModels;
	private Object2IntMap<BlockState> modelGroups = Object2IntMaps.emptyMap();

	public ModelManager(BlockColors blockColors, AtlasManager atlasManager, PlayerSkinRenderCache playerSkinRenderCache) {
		this.blockColors = blockColors;
		this.atlasManager = atlasManager;
		this.playerSkinRenderCache = playerSkinRenderCache;
		this.blockModelShaper = new BlockModelShaper(this);
	}

	public BlockStateModel getMissingBlockStateModel() {
		return this.missingModels.block();
	}

	public ItemModel getItemModel(Identifier identifier) {
		return this.bakedItemStackModels.getOrDefault(identifier, this.missingModels.item());
	}

	public ClientItem.Properties getItemProperties(Identifier identifier) {
		return this.itemProperties.getOrDefault(identifier, ClientItem.Properties.DEFAULT);
	}

	public BlockModelShaper getBlockModelShaper() {
		return this.blockModelShaper;
	}

	@Override
	public final CompletableFuture<Void> reload(
		PreparableReloadListener.SharedState sharedState, Executor executor, PreparableReloadListener.PreparationBarrier preparationBarrier, Executor executor2
	) {
		ResourceManager resourceManager = sharedState.resourceManager();
		CompletableFuture<EntityModelSet> completableFuture = CompletableFuture.supplyAsync(EntityModelSet::vanilla, executor);
		CompletableFuture<SpecialBlockModelRenderer> completableFuture2 = completableFuture.thenApplyAsync(
			entityModelSet -> SpecialBlockModelRenderer.vanilla(
				new SpecialModelRenderer.BakingContext.Simple(entityModelSet, this.atlasManager, this.playerSkinRenderCache)
			),
			executor
		);
		CompletableFuture<Map<Identifier, UnbakedModel>> completableFuture3 = loadBlockModels(resourceManager, executor);
		CompletableFuture<BlockStateModelLoader.LoadedModels> completableFuture4 = BlockStateModelLoader.loadBlockStates(resourceManager, executor);
		CompletableFuture<ClientItemInfoLoader.LoadedClientInfos> completableFuture5 = ClientItemInfoLoader.scheduleLoad(resourceManager, executor);
		CompletableFuture<ModelManager.ResolvedModels> completableFuture6 = CompletableFuture.allOf(completableFuture3, completableFuture4, completableFuture5)
			.thenApplyAsync(void_ -> discoverModelDependencies(completableFuture3.join(), completableFuture4.join(), completableFuture5.join()), executor);
		CompletableFuture<Object2IntMap<BlockState>> completableFuture7 = completableFuture4.thenApplyAsync(
			loadedModels -> buildModelGroups(this.blockColors, loadedModels), executor
		);
		AtlasManager.PendingStitchResults pendingStitchResults = sharedState.get(AtlasManager.PENDING_STITCH);
		CompletableFuture<SpriteLoader.Preparations> completableFuture8 = pendingStitchResults.get(AtlasIds.BLOCKS);
		CompletableFuture<SpriteLoader.Preparations> completableFuture9 = pendingStitchResults.get(AtlasIds.ITEMS);
		return CompletableFuture.allOf(
				completableFuture8,
				completableFuture9,
				completableFuture6,
				completableFuture7,
				completableFuture4,
				completableFuture5,
				completableFuture,
				completableFuture2,
				completableFuture3
			)
			.thenComposeAsync(
				void_ -> {
					SpriteLoader.Preparations preparations = completableFuture8.join();
					SpriteLoader.Preparations preparations2 = completableFuture9.join();
					ModelManager.ResolvedModels resolvedModels = completableFuture6.join();
					Object2IntMap<BlockState> object2IntMap = completableFuture7.join();
					Set<Identifier> set = Sets.difference(completableFuture3.join().keySet(), resolvedModels.models.keySet());
					if (!set.isEmpty()) {
						LOGGER.debug("Unreferenced models: \n{}", set.stream().sorted().map(identifier -> "\t" + identifier + "\n").collect(Collectors.joining()));
					}

					ModelBakery modelBakery = new ModelBakery(
						completableFuture.join(),
						this.atlasManager,
						this.playerSkinRenderCache,
						completableFuture4.join().models(),
						completableFuture5.join().contents(),
						resolvedModels.models(),
						resolvedModels.missing()
					);
					return loadModels(preparations, preparations2, modelBakery, object2IntMap, completableFuture.join(), completableFuture2.join(), executor);
				},
				executor
			)
			.thenCompose(preparationBarrier::wait)
			.thenAcceptAsync(this::apply, executor2);
	}

	private static CompletableFuture<Map<Identifier, UnbakedModel>> loadBlockModels(ResourceManager resourceManager, Executor executor) {
		return CompletableFuture.<Map<Identifier, Resource>>supplyAsync(() -> MODEL_LISTER.listMatchingResources(resourceManager), executor)
			.thenCompose(
				map -> {
					List<CompletableFuture<Pair<Identifier, BlockModel>>> list = new ArrayList<>(map.size());

					for (Entry<Identifier, Resource> entry : map.entrySet()) {
						list.add(CompletableFuture.supplyAsync(() -> {
							Identifier identifier = MODEL_LISTER.fileToId(entry.getKey());

							try (Reader reader = entry.getValue().openAsReader()) {
								return Pair.of(identifier, BlockModel.fromStream(reader));
							} catch (Exception exception) {
								LOGGER.error("Failed to load model {}", entry.getKey(), exception);
								return null;
							}
						}, executor));
					}

					return Util.sequence(list)
						.thenApply(listx -> listx.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond)));
				}
			);
	}

	private static ModelManager.ResolvedModels discoverModelDependencies(
		Map<Identifier, UnbakedModel> map, BlockStateModelLoader.LoadedModels loadedModels, ClientItemInfoLoader.LoadedClientInfos loadedClientInfos
	) {
		try (Zone zone = Profiler.get().zone("dependencies")) {
			ModelDiscovery modelDiscovery = new ModelDiscovery(map, MissingBlockModel.missingModel());
			modelDiscovery.addSpecialModel(ItemModelGenerator.GENERATED_ITEM_MODEL_ID, new ItemModelGenerator());
			loadedModels.models().values().forEach(modelDiscovery::addRoot);
			loadedClientInfos.contents().values().forEach(clientItem -> modelDiscovery.addRoot(clientItem.model()));
			return new ModelManager.ResolvedModels(modelDiscovery.missingModel(), modelDiscovery.resolve());
		}
	}

	private static CompletableFuture<ModelManager.ReloadState> loadModels(
		SpriteLoader.Preparations preparations,
		SpriteLoader.Preparations preparations2,
		ModelBakery modelBakery,
		Object2IntMap<BlockState> object2IntMap,
		EntityModelSet entityModelSet,
		SpecialBlockModelRenderer specialBlockModelRenderer,
		Executor executor
	) {
		final Multimap<String, Material> multimap = Multimaps.synchronizedMultimap(HashMultimap.create());
		final Multimap<String, String> multimap2 = Multimaps.synchronizedMultimap(HashMultimap.create());
		return modelBakery.bakeModels(new SpriteGetter() {
				private final TextureAtlasSprite blockMissing = preparations.missing();
				private final TextureAtlasSprite itemMissing = preparations2.missing();

				@Override
				public TextureAtlasSprite get(Material material, ModelDebugName modelDebugName) {
					Identifier identifier = material.atlasLocation();
					boolean bl = identifier.equals(ModelManager.BLOCK_OR_ITEM);
					boolean bl2 = identifier.equals(TextureAtlas.LOCATION_ITEMS);
					boolean bl3 = identifier.equals(TextureAtlas.LOCATION_BLOCKS);
					if (bl || bl2) {
						TextureAtlasSprite textureAtlasSprite = preparations2.getSprite(material.texture());
						if (textureAtlasSprite != null) {
							return textureAtlasSprite;
						}
					}

					if (bl || bl3) {
						TextureAtlasSprite textureAtlasSprite = preparations.getSprite(material.texture());
						if (textureAtlasSprite != null) {
							return textureAtlasSprite;
						}
					}

					multimap.put(modelDebugName.debugName(), material);
					return bl2 ? this.itemMissing : this.blockMissing;
				}

				@Override
				public TextureAtlasSprite reportMissingReference(String string, ModelDebugName modelDebugName) {
					multimap2.put(modelDebugName.debugName(), string);
					return this.blockMissing;
				}
			}, executor)
			.thenApply(
				bakingResult -> {
					multimap.asMap()
						.forEach(
							(string, collection) -> LOGGER.warn(
								"Missing textures in model {}:\n{}",
								string,
								collection.stream()
									.sorted(Material.COMPARATOR)
									.map(material -> "    " + material.atlasLocation() + ":" + material.texture())
									.collect(Collectors.joining("\n"))
							)
						);
					multimap2.asMap()
						.forEach(
							(string, collection) -> LOGGER.warn(
								"Missing texture references in model {}:\n{}", string, collection.stream().sorted().map(stringx -> "    " + stringx).collect(Collectors.joining("\n"))
							)
						);
					Map<BlockState, BlockStateModel> map = createBlockStateToModelDispatch(bakingResult.blockStateModels(), bakingResult.missingModels().block());
					return new ModelManager.ReloadState(bakingResult, object2IntMap, map, entityModelSet, specialBlockModelRenderer);
				}
			);
	}

	private static Map<BlockState, BlockStateModel> createBlockStateToModelDispatch(Map<BlockState, BlockStateModel> map, BlockStateModel blockStateModel) {
		try (Zone zone = Profiler.get().zone("block state dispatch")) {
			Map<BlockState, BlockStateModel> map2 = new IdentityHashMap<>(map);

			for (Block block : BuiltInRegistries.BLOCK) {
				block.getStateDefinition().getPossibleStates().forEach(blockState -> {
					if (map.putIfAbsent(blockState, blockStateModel) == null) {
						LOGGER.warn("Missing model for variant: '{}'", blockState);
					}
				});
			}

			return map2;
		}
	}

	private static Object2IntMap<BlockState> buildModelGroups(BlockColors blockColors, BlockStateModelLoader.LoadedModels loadedModels) {
		try (Zone zone = Profiler.get().zone("block groups")) {
			return ModelGroupCollector.build(blockColors, loadedModels);
		}
	}

	private void apply(ModelManager.ReloadState reloadState) {
		ModelBakery.BakingResult bakingResult = reloadState.bakedModels;
		this.bakedItemStackModels = bakingResult.itemStackModels();
		this.itemProperties = bakingResult.itemProperties();
		this.modelGroups = reloadState.modelGroups;
		this.missingModels = bakingResult.missingModels();
		this.blockModelShaper.replaceCache(reloadState.modelCache);
		this.specialBlockModelRenderer = reloadState.specialBlockModelRenderer;
		this.entityModelSet = reloadState.entityModelSet;
	}

	public boolean requiresRender(BlockState blockState, BlockState blockState2) {
		if (blockState == blockState2) {
			return false;
		}

		int i = this.modelGroups.getInt(blockState);
		if (i != -1) {
			int j = this.modelGroups.getInt(blockState2);
			if (i == j) {
				FluidState fluidState = blockState.getFluidState();
				FluidState fluidState2 = blockState2.getFluidState();
				return fluidState != fluidState2;
			}
		}

		return true;
	}

	public SpecialBlockModelRenderer specialBlockModelRenderer() {
		return this.specialBlockModelRenderer;
	}

	public Supplier<EntityModelSet> entityModels() {
		return () -> this.entityModelSet;
	}

	@Environment(EnvType.CLIENT)
	record ReloadState(
		ModelBakery.BakingResult bakedModels,
		Object2IntMap<BlockState> modelGroups,
		Map<BlockState, BlockStateModel> modelCache,
		EntityModelSet entityModelSet,
		SpecialBlockModelRenderer specialBlockModelRenderer
	) {
	}

	@Environment(EnvType.CLIENT)
	record ResolvedModels(ResolvedModel missing, Map<Identifier, ResolvedModel> models) {
	}
}
