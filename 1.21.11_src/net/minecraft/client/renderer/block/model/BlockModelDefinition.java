package net.minecraft.client.renderer.block.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.block.model.multipart.MultiPartModel;
import net.minecraft.client.renderer.block.model.multipart.Selector;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public record BlockModelDefinition(
	Optional<BlockModelDefinition.SimpleModelSelectors> simpleModels, Optional<BlockModelDefinition.MultiPartDefinition> multiPart
) {
	static final Logger LOGGER = LogUtils.getLogger();
	public static final Codec<BlockModelDefinition> CODEC = RecordCodecBuilder.<BlockModelDefinition>create(
			instance -> instance.group(
					BlockModelDefinition.SimpleModelSelectors.CODEC.optionalFieldOf("variants").forGetter(BlockModelDefinition::simpleModels),
					BlockModelDefinition.MultiPartDefinition.CODEC.optionalFieldOf("multipart").forGetter(BlockModelDefinition::multiPart)
				)
				.apply(instance, BlockModelDefinition::new)
		)
		.validate(
			blockModelDefinition -> blockModelDefinition.simpleModels().isEmpty() && blockModelDefinition.multiPart().isEmpty()
				? DataResult.error(() -> "Neither 'variants' nor 'multipart' found")
				: DataResult.success(blockModelDefinition)
		);

	public Map<BlockState, BlockStateModel.UnbakedRoot> instantiate(StateDefinition<Block, BlockState> stateDefinition, Supplier<String> supplier) {
		Map<BlockState, BlockStateModel.UnbakedRoot> map = new IdentityHashMap<>();
		this.simpleModels.ifPresent(simpleModelSelectors -> simpleModelSelectors.instantiate(stateDefinition, supplier, (blockState, unbakedRoot) -> {
			BlockStateModel.UnbakedRoot unbakedRoot2 = map.put(blockState, unbakedRoot);
			if (unbakedRoot2 != null) {
				throw new IllegalArgumentException("Overlapping definition on state: " + blockState);
			}
		}));
		this.multiPart.ifPresent(multiPartDefinition -> {
			List<BlockState> list = stateDefinition.getPossibleStates();
			BlockStateModel.UnbakedRoot unbakedRoot = multiPartDefinition.instantiate(stateDefinition);

			for (BlockState blockState : list) {
				map.putIfAbsent(blockState, unbakedRoot);
			}
		});
		return map;
	}

	@Environment(EnvType.CLIENT)
	public record MultiPartDefinition(List<Selector> selectors) {
		public static final Codec<BlockModelDefinition.MultiPartDefinition> CODEC = ExtraCodecs.nonEmptyList(Selector.CODEC.listOf())
			.xmap(BlockModelDefinition.MultiPartDefinition::new, BlockModelDefinition.MultiPartDefinition::selectors);

		public MultiPartModel.Unbaked instantiate(StateDefinition<Block, BlockState> stateDefinition) {
			Builder<MultiPartModel.Selector<BlockStateModel.Unbaked>> builder = ImmutableList.builderWithExpectedSize(this.selectors.size());

			for (Selector selector : this.selectors) {
				builder.add(new MultiPartModel.Selector<>(selector.instantiate(stateDefinition), selector.variant()));
			}

			return new MultiPartModel.Unbaked(builder.build());
		}
	}

	@Environment(EnvType.CLIENT)
	public record SimpleModelSelectors(Map<String, BlockStateModel.Unbaked> models) {
		public static final Codec<BlockModelDefinition.SimpleModelSelectors> CODEC = ExtraCodecs.nonEmptyMap(
				Codec.unboundedMap(Codec.STRING, BlockStateModel.Unbaked.CODEC)
			)
			.xmap(BlockModelDefinition.SimpleModelSelectors::new, BlockModelDefinition.SimpleModelSelectors::models);

		public void instantiate(
			StateDefinition<Block, BlockState> stateDefinition, Supplier<String> supplier, BiConsumer<BlockState, BlockStateModel.UnbakedRoot> biConsumer
		) {
			this.models.forEach((string, unbaked) -> {
				try {
					Predicate<StateHolder<Block, BlockState>> predicate = VariantSelector.predicate(stateDefinition, string);
					BlockStateModel.UnbakedRoot unbakedRoot = unbaked.asRoot();

					for (BlockState blockState : stateDefinition.getPossibleStates()) {
						if (predicate.test(blockState)) {
							biConsumer.accept(blockState, unbakedRoot);
						}
					}
				} catch (Exception exception) {
					BlockModelDefinition.LOGGER.warn("Exception loading blockstate definition: '{}' for variant: '{}': {}", supplier.get(), string, exception.getMessage());
				}
			});
		}
	}
}
