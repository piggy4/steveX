package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public class SetCustomModelDataFunction extends LootItemConditionalFunction {
	private static final Codec<NumberProvider> COLOR_PROVIDER_CODEC = Codec.withAlternative(NumberProviders.CODEC, ExtraCodecs.RGB_COLOR_CODEC, ConstantValue::new);
	public static final MapCodec<SetCustomModelDataFunction> CODEC = RecordCodecBuilder.mapCodec(
		instance -> commonFields(instance)
			.and(
				instance.group(
					ListOperation.StandAlone.codec(NumberProviders.CODEC, Integer.MAX_VALUE)
						.optionalFieldOf("floats")
						.forGetter(setCustomModelDataFunction -> setCustomModelDataFunction.floats),
					ListOperation.StandAlone.codec(Codec.BOOL, Integer.MAX_VALUE)
						.optionalFieldOf("flags")
						.forGetter(setCustomModelDataFunction -> setCustomModelDataFunction.flags),
					ListOperation.StandAlone.codec(Codec.STRING, Integer.MAX_VALUE)
						.optionalFieldOf("strings")
						.forGetter(setCustomModelDataFunction -> setCustomModelDataFunction.strings),
					ListOperation.StandAlone.codec(COLOR_PROVIDER_CODEC, Integer.MAX_VALUE)
						.optionalFieldOf("colors")
						.forGetter(setCustomModelDataFunction -> setCustomModelDataFunction.colors)
				)
			)
			.apply(instance, SetCustomModelDataFunction::new)
	);
	private final Optional<ListOperation.StandAlone<NumberProvider>> floats;
	private final Optional<ListOperation.StandAlone<Boolean>> flags;
	private final Optional<ListOperation.StandAlone<String>> strings;
	private final Optional<ListOperation.StandAlone<NumberProvider>> colors;

	public SetCustomModelDataFunction(
		List<LootItemCondition> list,
		Optional<ListOperation.StandAlone<NumberProvider>> optional,
		Optional<ListOperation.StandAlone<Boolean>> optional2,
		Optional<ListOperation.StandAlone<String>> optional3,
		Optional<ListOperation.StandAlone<NumberProvider>> optional4
	) {
		super(list);
		this.floats = optional;
		this.flags = optional2;
		this.strings = optional3;
		this.colors = optional4;
	}

	@Override
	public Set<ContextKey<?>> getReferencedContextParams() {
		return Stream.concat(this.floats.stream(), this.colors.stream())
			.flatMap(standAlone -> standAlone.value().stream())
			.flatMap(numberProvider -> numberProvider.getReferencedContextParams().stream())
			.collect(Collectors.toSet());
	}

	@Override
	public LootItemFunctionType<SetCustomModelDataFunction> getType() {
		return LootItemFunctions.SET_CUSTOM_MODEL_DATA;
	}

	private static <T> List<T> apply(Optional<ListOperation.StandAlone<T>> optional, List<T> list) {
		return optional.<List<T>>map(standAlone -> standAlone.apply(list)).orElse(list);
	}

	private static <T, E> List<E> apply(Optional<ListOperation.StandAlone<T>> optional, List<E> list, Function<T, E> function) {
		return optional.<List<E>>map(standAlone -> {
			List<E> list2 = standAlone.value().stream().map(function).toList();
			return standAlone.operation().apply(list, list2);
		}).orElse(list);
	}

	@Override
	public ItemStack run(ItemStack itemStack, LootContext lootContext) {
		CustomModelData customModelData = itemStack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
		itemStack.set(
			DataComponents.CUSTOM_MODEL_DATA,
			new CustomModelData(
				apply(this.floats, customModelData.floats(), numberProvider -> numberProvider.getFloat(lootContext)),
				apply(this.flags, customModelData.flags()),
				apply(this.strings, customModelData.strings()),
				apply(this.colors, customModelData.colors(), numberProvider -> numberProvider.getInt(lootContext))
			)
		);
		return itemStack;
	}
}
