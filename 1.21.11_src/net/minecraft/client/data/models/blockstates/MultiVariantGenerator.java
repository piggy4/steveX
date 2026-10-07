package net.minecraft.client.data.models.blockstates;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.VariantMutator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

@Environment(EnvType.CLIENT)
public class MultiVariantGenerator implements BlockModelDefinitionGenerator {
	private final Block block;
	private final List<MultiVariantGenerator.Entry> entries;
	private final Set<Property<?>> seenProperties;

	MultiVariantGenerator(Block block, List<MultiVariantGenerator.Entry> list, Set<Property<?>> set) {
		this.block = block;
		this.entries = list;
		this.seenProperties = set;
	}

	static Set<Property<?>> validateAndExpandProperties(Set<Property<?>> set, Block block, PropertyDispatch<?> propertyDispatch) {
		List<Property<?>> list = propertyDispatch.getDefinedProperties();
		list.forEach(property -> {
			if (block.getStateDefinition().getProperty(property.getName()) != property) {
				throw new IllegalStateException("Property " + property + " is not defined for block " + block);
			}

			if (set.contains(property)) {
				throw new IllegalStateException("Values of property " + property + " already defined for block " + block);
			}
		});
		Set<Property<?>> set2 = new HashSet<>(set);
		set2.addAll(list);
		return set2;
	}

	public MultiVariantGenerator with(PropertyDispatch<VariantMutator> propertyDispatch) {
		Set<Property<?>> set = validateAndExpandProperties(this.seenProperties, this.block, propertyDispatch);
		List<MultiVariantGenerator.Entry> list = this.entries.stream().flatMap(entry -> entry.apply(propertyDispatch)).toList();
		return new MultiVariantGenerator(this.block, list, set);
	}

	public MultiVariantGenerator with(VariantMutator variantMutator) {
		List<MultiVariantGenerator.Entry> list = this.entries.stream().flatMap(entry -> entry.apply(variantMutator)).toList();
		return new MultiVariantGenerator(this.block, list, this.seenProperties);
	}

	@Override
	public BlockModelDefinition create() {
		Map<String, BlockStateModel.Unbaked> map = new HashMap<>();

		for (MultiVariantGenerator.Entry entry : this.entries) {
			map.put(entry.properties.getKey(), entry.variant.toUnbaked());
		}

		return new BlockModelDefinition(Optional.of(new BlockModelDefinition.SimpleModelSelectors(map)), Optional.empty());
	}

	@Override
	public Block block() {
		return this.block;
	}

	public static MultiVariantGenerator.Empty dispatch(Block block) {
		return new MultiVariantGenerator.Empty(block);
	}

	public static MultiVariantGenerator dispatch(Block block, MultiVariant multiVariant) {
		return new MultiVariantGenerator(block, List.of(new MultiVariantGenerator.Entry(PropertyValueList.EMPTY, multiVariant)), Set.of());
	}

	@Environment(EnvType.CLIENT)
	public static class Empty {
		private final Block block;

		public Empty(Block block) {
			this.block = block;
		}

		public MultiVariantGenerator with(PropertyDispatch<MultiVariant> propertyDispatch) {
			Set<Property<?>> set = MultiVariantGenerator.validateAndExpandProperties(Set.of(), this.block, propertyDispatch);
			List<MultiVariantGenerator.Entry> list = propertyDispatch.getEntries()
				.entrySet()
				.stream()
				.map(entry -> new MultiVariantGenerator.Entry(entry.getKey(), entry.getValue()))
				.toList();
			return new MultiVariantGenerator(this.block, list, set);
		}
	}

	@Environment(EnvType.CLIENT)
	record Entry(PropertyValueList properties, MultiVariant variant) {

		public Stream<MultiVariantGenerator.Entry> apply(PropertyDispatch<VariantMutator> propertyDispatch) {
			return propertyDispatch.getEntries().entrySet().stream().map(entry -> {
				PropertyValueList propertyValueList = this.properties.extend(entry.getKey());
				MultiVariant multiVariant = this.variant.with(entry.getValue());
				return new MultiVariantGenerator.Entry(propertyValueList, multiVariant);
			});
		}

		public Stream<MultiVariantGenerator.Entry> apply(VariantMutator variantMutator) {
			return Stream.of(new MultiVariantGenerator.Entry(this.properties, this.variant.with(variantMutator)));
		}
	}
}
