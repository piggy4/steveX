package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.apache.commons.lang3.function.TriFunction;

public record WeatheringCopperBlocks(
	Block unaffected, Block exposed, Block weathered, Block oxidized, Block waxed, Block waxedExposed, Block waxedWeathered, Block waxedOxidized
) {
	public static <WaxedBlock extends Block, WeatheringBlock extends Block & WeatheringCopper> WeatheringCopperBlocks create(
		String string,
		TriFunction<String, Function<BlockBehaviour.Properties, Block>, BlockBehaviour.Properties, Block> triFunction,
		Function<BlockBehaviour.Properties, WaxedBlock> function,
		BiFunction<WeatheringCopper.WeatherState, BlockBehaviour.Properties, WeatheringBlock> biFunction,
		Function<WeatheringCopper.WeatherState, BlockBehaviour.Properties> function2
	) {
		return new WeatheringCopperBlocks(
			triFunction.apply(
				string, properties -> biFunction.apply(WeatheringCopper.WeatherState.UNAFFECTED, properties), function2.apply(WeatheringCopper.WeatherState.UNAFFECTED)
			),
			triFunction.apply(
				"exposed_" + string,
				properties -> biFunction.apply(WeatheringCopper.WeatherState.EXPOSED, properties),
				function2.apply(WeatheringCopper.WeatherState.EXPOSED)
			),
			triFunction.apply(
				"weathered_" + string,
				properties -> biFunction.apply(WeatheringCopper.WeatherState.WEATHERED, properties),
				function2.apply(WeatheringCopper.WeatherState.WEATHERED)
			),
			triFunction.apply(
				"oxidized_" + string,
				properties -> biFunction.apply(WeatheringCopper.WeatherState.OXIDIZED, properties),
				function2.apply(WeatheringCopper.WeatherState.OXIDIZED)
			),
			triFunction.apply("waxed_" + string, function::apply, function2.apply(WeatheringCopper.WeatherState.UNAFFECTED)),
			triFunction.apply("waxed_exposed_" + string, function::apply, function2.apply(WeatheringCopper.WeatherState.EXPOSED)),
			triFunction.apply("waxed_weathered_" + string, function::apply, function2.apply(WeatheringCopper.WeatherState.WEATHERED)),
			triFunction.apply("waxed_oxidized_" + string, function::apply, function2.apply(WeatheringCopper.WeatherState.OXIDIZED))
		);
	}

	public ImmutableBiMap<Block, Block> weatheringMapping() {
		return ImmutableBiMap.of(this.unaffected, this.exposed, this.exposed, this.weathered, this.weathered, this.oxidized);
	}

	public ImmutableBiMap<Block, Block> waxedMapping() {
		return ImmutableBiMap.of(this.unaffected, this.waxed, this.exposed, this.waxedExposed, this.weathered, this.waxedWeathered, this.oxidized, this.waxedOxidized);
	}

	public ImmutableList<Block> asList() {
		return ImmutableList.of(this.unaffected, this.waxed, this.exposed, this.waxedExposed, this.weathered, this.waxedWeathered, this.oxidized, this.waxedOxidized);
	}

	public void forEach(Consumer<Block> consumer) {
		consumer.accept(this.unaffected);
		consumer.accept(this.exposed);
		consumer.accept(this.weathered);
		consumer.accept(this.oxidized);
		consumer.accept(this.waxed);
		consumer.accept(this.waxedExposed);
		consumer.accept(this.waxedWeathered);
		consumer.accept(this.waxedOxidized);
	}
}
