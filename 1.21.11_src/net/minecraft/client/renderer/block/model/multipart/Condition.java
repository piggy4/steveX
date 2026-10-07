package net.minecraft.client.renderer.block.model.multipart;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface Condition {
	Codec<Condition> CODEC = Codec.recursive(
		"condition",
		codec -> {
			Codec<CombinedCondition> codec2 = Codec.simpleMap(
					CombinedCondition.Operation.CODEC, codec.listOf(), StringRepresentable.keys(CombinedCondition.Operation.values())
				)
				.codec()
				.comapFlatMap(map -> {
					if (map.size() != 1) {
						return DataResult.error(() -> "Invalid map size for combiner condition, expected exactly one element");
					}

					Entry<CombinedCondition.Operation, List<Condition>> entry = map.entrySet().iterator().next();
					return DataResult.success(new CombinedCondition(entry.getKey(), entry.getValue()));
				}, combinedCondition -> Map.of(combinedCondition.operation(), combinedCondition.terms()));
			return Codec.either(codec2, KeyValueCondition.CODEC)
				.flatComapMap(either -> either.map(combinedCondition -> combinedCondition, keyValueCondition -> keyValueCondition), condition -> {
					return switch (condition) {
						case CombinedCondition combinedCondition -> DataResult.success(Either.left(combinedCondition));
						case KeyValueCondition keyValueCondition -> DataResult.success(Either.right(keyValueCondition));
						default -> DataResult.error(() -> "Unrecognized condition");
					};
				});
		}
	);

	<O, S extends StateHolder<O, S>> Predicate<S> instantiate(StateDefinition<O, S> stateDefinition);
}
