package net.minecraft.world.entity.variant;

import com.mojang.datafixers.DataFixUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;

public interface PriorityProvider<Context, Condition extends PriorityProvider.SelectorCondition<Context>> {
	List<PriorityProvider.Selector<Context, Condition>> selectors();

	static <C, T> Stream<T> select(Stream<T> stream, Function<T, PriorityProvider<C, ?>> function, C object) {
		List<PriorityProvider.UnpackedEntry<C, T>> list = new ArrayList<>();
		stream.forEach(
			objectx -> {
				PriorityProvider<C, ?> priorityProvider = function.apply((T)objectx);

				for (PriorityProvider.Selector<C, ?> selector : priorityProvider.selectors()) {
					list.add(
						new PriorityProvider.UnpackedEntry<>(
							(T)objectx,
							selector.priority(),
							DataFixUtils.orElseGet((Optional<? extends PriorityProvider.SelectorCondition<C>>)selector.condition(), PriorityProvider.SelectorCondition::alwaysTrue)
						)
					);
				}
			}
		);
		list.sort(PriorityProvider.UnpackedEntry.HIGHEST_PRIORITY_FIRST);
		Iterator<PriorityProvider.UnpackedEntry<C, T>> iterator = list.iterator();
		int i = Integer.MIN_VALUE;

		while (iterator.hasNext()) {
			PriorityProvider.UnpackedEntry<C, T> unpackedEntry = iterator.next();
			if (unpackedEntry.priority < i) {
				iterator.remove();
			} else if (unpackedEntry.condition.test(object)) {
				i = unpackedEntry.priority;
			} else {
				iterator.remove();
			}
		}

		return list.stream().map(PriorityProvider.UnpackedEntry::entry);
	}

	static <C, T> Optional<T> pick(Stream<T> stream, Function<T, PriorityProvider<C, ?>> function, RandomSource randomSource, C object) {
		List<T> list = select(stream, function, object).toList();
		return Util.getRandomSafe(list, randomSource);
	}

	static <Context, Condition extends PriorityProvider.SelectorCondition<Context>> List<PriorityProvider.Selector<Context, Condition>> single(
		Condition selectorCondition, int i
	) {
		return List.of(new PriorityProvider.Selector<>(selectorCondition, i));
	}

	static <Context, Condition extends PriorityProvider.SelectorCondition<Context>> List<PriorityProvider.Selector<Context, Condition>> alwaysTrue(int i) {
		return List.of(new PriorityProvider.Selector<>(Optional.empty(), i));
	}

	record Selector<Context, Condition extends PriorityProvider.SelectorCondition<Context>>(Optional<Condition> condition, int priority) {
		public Selector(Condition selectorCondition, int i) {
			this(Optional.of(selectorCondition), i);
		}

		public Selector(int i) {
			this(Optional.empty(), i);
		}

		public static <Context, Condition extends PriorityProvider.SelectorCondition<Context>> Codec<PriorityProvider.Selector<Context, Condition>> codec(
			Codec<Condition> codec
		) {
			return RecordCodecBuilder.create(
				instance -> instance.group(
						codec.optionalFieldOf("condition").forGetter(PriorityProvider.Selector::condition),
						Codec.INT.fieldOf("priority").forGetter(PriorityProvider.Selector::priority)
					)
					.apply(instance, PriorityProvider.Selector::new)
			);
		}
	}

	@FunctionalInterface
	interface SelectorCondition<C> extends Predicate<C> {
		static <C> PriorityProvider.SelectorCondition<C> alwaysTrue() {
			return object -> true;
		}
	}

	record UnpackedEntry<C, T>(T entry, int priority, PriorityProvider.SelectorCondition<C> condition) {
		public static final Comparator<PriorityProvider.UnpackedEntry<?, ?>> HIGHEST_PRIORITY_FIRST = Comparator.comparingInt(
				PriorityProvider.UnpackedEntry::priority
			)
			.reversed();
	}
}
