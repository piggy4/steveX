package net.minecraft.client.resources.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.io.Reader;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class BlockStateModelLoader {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final FileToIdConverter BLOCKSTATE_LISTER = FileToIdConverter.json("blockstates");

	public static CompletableFuture<BlockStateModelLoader.LoadedModels> loadBlockStates(ResourceManager resourceManager, Executor executor) {
		Function<Identifier, StateDefinition<Block, BlockState>> function = BlockStateDefinitions.definitionLocationToBlockStateMapper();
		return CompletableFuture.<Map<Identifier, List<Resource>>>supplyAsync(() -> BLOCKSTATE_LISTER.listMatchingResourceStacks(resourceManager), executor)
			.thenCompose(map -> {
				List<CompletableFuture<BlockStateModelLoader.LoadedModels>> list = new ArrayList<>(map.size());

				for (Entry<Identifier, List<Resource>> entry : map.entrySet()) {
					list.add(CompletableFuture.supplyAsync(() -> {
						Identifier identifier = BLOCKSTATE_LISTER.fileToId(entry.getKey());
						StateDefinition<Block, BlockState> stateDefinition = function.apply(identifier);
						if (stateDefinition == null) {
							LOGGER.debug("Discovered unknown block state definition {}, ignoring", identifier);
							return null;
						}

						List<Resource> listx = entry.getValue();
						List<BlockStateModelLoader.LoadedBlockModelDefinition> list2 = new ArrayList<>(listx.size());

						for (Resource resource : listx) {
							try (Reader reader = resource.openAsReader()) {
								JsonElement jsonElement = StrictJsonParser.parse(reader);
								BlockModelDefinition blockModelDefinition = BlockModelDefinition.CODEC.parse(JsonOps.INSTANCE, jsonElement).getOrThrow(JsonParseException::new);
								list2.add(new BlockStateModelLoader.LoadedBlockModelDefinition(resource.sourcePackId(), blockModelDefinition));
							} catch (Exception exception) {
								LOGGER.error("Failed to load blockstate definition {} from pack {}", identifier, resource.sourcePackId(), exception);
							}
						}

						try {
							return loadBlockStateDefinitionStack(identifier, stateDefinition, list2);
						} catch (Exception exception2) {
							LOGGER.error("Failed to load blockstate definition {}", identifier, exception2);
							return null;
						}
					}, executor));
				}

				return Util.sequence(list).thenApply(listx -> {
					Map<BlockState, BlockStateModel.UnbakedRoot> mapx = new IdentityHashMap<>();

					for (BlockStateModelLoader.LoadedModels loadedModels : listx) {
						if (loadedModels != null) {
							mapx.putAll(loadedModels.models());
						}
					}

					return new BlockStateModelLoader.LoadedModels(mapx);
				});
			});
	}

	private static BlockStateModelLoader.LoadedModels loadBlockStateDefinitionStack(
		Identifier identifier, StateDefinition<Block, BlockState> stateDefinition, List<BlockStateModelLoader.LoadedBlockModelDefinition> list
	) {
		Map<BlockState, BlockStateModel.UnbakedRoot> map = new IdentityHashMap<>();

		for (BlockStateModelLoader.LoadedBlockModelDefinition loadedBlockModelDefinition : list) {
			map.putAll(loadedBlockModelDefinition.contents.instantiate(stateDefinition, () -> identifier + "/" + loadedBlockModelDefinition.source));
		}

		return new BlockStateModelLoader.LoadedModels(map);
	}

	@Environment(EnvType.CLIENT)
	record LoadedBlockModelDefinition(String source, BlockModelDefinition contents) {
	}

	@Environment(EnvType.CLIENT)
	public record LoadedModels(Map<BlockState, BlockStateModel.UnbakedRoot> models) {
	}
}
