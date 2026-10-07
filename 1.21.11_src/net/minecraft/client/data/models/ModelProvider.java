package net.minecraft.client.data.models;

import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.data.models.blockstates.BlockModelDefinitionGenerator;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelInstance;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

@Environment(EnvType.CLIENT)
public class ModelProvider implements DataProvider {
	private final PackOutput.PathProvider blockStatePathProvider;
	private final PackOutput.PathProvider itemInfoPathProvider;
	private final PackOutput.PathProvider modelPathProvider;

	public ModelProvider(PackOutput packOutput) {
		this.blockStatePathProvider = packOutput.createPathProvider(PackOutput.Target.RESOURCE_PACK, "blockstates");
		this.itemInfoPathProvider = packOutput.createPathProvider(PackOutput.Target.RESOURCE_PACK, "items");
		this.modelPathProvider = packOutput.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models");
	}

	@Override
	public CompletableFuture<?> run(CachedOutput cachedOutput) {
		ModelProvider.ItemInfoCollector itemInfoCollector = new ModelProvider.ItemInfoCollector();
		ModelProvider.BlockStateGeneratorCollector blockStateGeneratorCollector = new ModelProvider.BlockStateGeneratorCollector();
		ModelProvider.SimpleModelCollector simpleModelCollector = new ModelProvider.SimpleModelCollector();
		new BlockModelGenerators(blockStateGeneratorCollector, itemInfoCollector, simpleModelCollector).run();
		new ItemModelGenerators(itemInfoCollector, simpleModelCollector).run();
		blockStateGeneratorCollector.validate();
		itemInfoCollector.finalizeAndValidate();
		return CompletableFuture.allOf(
			blockStateGeneratorCollector.save(cachedOutput, this.blockStatePathProvider),
			simpleModelCollector.save(cachedOutput, this.modelPathProvider),
			itemInfoCollector.save(cachedOutput, this.itemInfoPathProvider)
		);
	}

	@Override
	public String getName() {
		return "Model Definitions";
	}

	@Environment(EnvType.CLIENT)
	static class BlockStateGeneratorCollector implements Consumer<BlockModelDefinitionGenerator> {
		private final Map<Block, BlockModelDefinitionGenerator> generators = new HashMap<>();

		public void accept(BlockModelDefinitionGenerator blockModelDefinitionGenerator) {
			Block block = blockModelDefinitionGenerator.block();
			BlockModelDefinitionGenerator blockModelDefinitionGenerator2 = this.generators.put(block, blockModelDefinitionGenerator);
			if (blockModelDefinitionGenerator2 != null) {
				throw new IllegalStateException("Duplicate blockstate definition for " + block);
			}
		}

		public void validate() {
			Stream<Holder.Reference<Block>> stream = BuiltInRegistries.BLOCK.listElements().filter(reference -> true);
			List<Identifier> list = stream.filter(reference -> !this.generators.containsKey(reference.value())).map(reference -> reference.key().identifier()).toList();
			if (!list.isEmpty()) {
				throw new IllegalStateException("Missing blockstate definitions for: " + list);
			}
		}

		public CompletableFuture<?> save(CachedOutput cachedOutput, PackOutput.PathProvider pathProvider) {
			Map<Block, BlockModelDefinition> map = Maps.transformValues(this.generators, BlockModelDefinitionGenerator::create);
			Function<Block, Path> function = block -> pathProvider.json(block.builtInRegistryHolder().key().identifier());
			return DataProvider.saveAll(cachedOutput, BlockModelDefinition.CODEC, function, map);
		}
	}

	@Environment(EnvType.CLIENT)
	static class ItemInfoCollector implements ItemModelOutput {
		private final Map<Item, ClientItem> itemInfos = new HashMap<>();
		private final Map<Item, Item> copies = new HashMap<>();

		@Override
		public void accept(Item item, ItemModel.Unbaked unbaked, ClientItem.Properties properties) {
			this.register(item, new ClientItem(unbaked, properties));
		}

		private void register(Item item, ClientItem clientItem) {
			ClientItem clientItem2 = this.itemInfos.put(item, clientItem);
			if (clientItem2 != null) {
				throw new IllegalStateException("Duplicate item model definition for " + item);
			}
		}

		@Override
		public void copy(Item item, Item item2) {
			this.copies.put(item2, item);
		}

		public void finalizeAndValidate() {
			BuiltInRegistries.ITEM.forEach(item -> {
				if (!this.copies.containsKey(item)) {
					if (item instanceof BlockItem blockItem && !this.itemInfos.containsKey(blockItem)) {
						Identifier identifier = ModelLocationUtils.getModelLocation(blockItem.getBlock());
						this.accept(blockItem, ItemModelUtils.plainModel(identifier));
					}
				}
			});
			this.copies.forEach((item, item2) -> {
				ClientItem clientItem = this.itemInfos.get(item2);
				if (clientItem == null) {
					throw new IllegalStateException("Missing donor: " + item2 + " -> " + item);
				}

				this.register(item, clientItem);
			});
			List<Identifier> list = BuiltInRegistries.ITEM
				.listElements()
				.filter(reference -> !this.itemInfos.containsKey(reference.value()))
				.map(reference -> reference.key().identifier())
				.toList();
			if (!list.isEmpty()) {
				throw new IllegalStateException("Missing item model definitions for: " + list);
			}
		}

		public CompletableFuture<?> save(CachedOutput cachedOutput, PackOutput.PathProvider pathProvider) {
			return DataProvider.saveAll(cachedOutput, ClientItem.CODEC, item -> pathProvider.json(item.builtInRegistryHolder().key().identifier()), this.itemInfos);
		}
	}

	@Environment(EnvType.CLIENT)
	static class SimpleModelCollector implements BiConsumer<Identifier, ModelInstance> {
		private final Map<Identifier, ModelInstance> models = new HashMap<>();

		public void accept(Identifier identifier, ModelInstance modelInstance) {
			Supplier<JsonElement> supplier = this.models.put(identifier, modelInstance);
			if (supplier != null) {
				throw new IllegalStateException("Duplicate model definition for " + identifier);
			}
		}

		public CompletableFuture<?> save(CachedOutput cachedOutput, PackOutput.PathProvider pathProvider) {
			return DataProvider.saveAll(cachedOutput, Supplier::get, pathProvider::json, this.models);
		}
	}
}
