package net.minecraft.util.random;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

public final class WeightedList<E> {
	private static final int FLAT_THRESHOLD = 64;
	private final int totalWeight;
	private final List<Weighted<E>> items;
	private final WeightedList.@Nullable Selector<E> selector;

	WeightedList(List<? extends Weighted<E>> list) {
		this.items = List.copyOf(list);
		this.totalWeight = WeightedRandom.getTotalWeight(list, Weighted::weight);
		if (this.totalWeight == 0) {
			this.selector = null;
		} else if (this.totalWeight < 64) {
			this.selector = new WeightedList.Flat<>(this.items, this.totalWeight);
		} else {
			this.selector = new WeightedList.Compact<>(this.items);
		}
	}

	public static <E> WeightedList<E> of() {
		return new WeightedList<>(List.of());
	}

	public static <E> WeightedList<E> of(E object) {
		return new WeightedList<>(List.of(new Weighted<>(object, 1)));
	}

	@SafeVarargs
	public static <E> WeightedList<E> of(Weighted<E>... weighteds) {
		return new WeightedList<>(List.of(weighteds));
	}

	public static <E> WeightedList<E> of(List<Weighted<E>> list) {
		return new WeightedList<>(list);
	}

	public static <E> WeightedList.Builder<E> builder() {
		return new WeightedList.Builder<>();
	}

	public boolean isEmpty() {
		return this.items.isEmpty();
	}

	public <T> WeightedList<T> map(Function<E, T> function) {
		return new WeightedList(Lists.transform(this.items, weighted -> weighted.map((Function<E, E>)function)));
	}

	public Optional<E> getRandom(RandomSource randomSource) {
		if (this.selector == null) {
			return Optional.empty();
		}

		int i = randomSource.nextInt(this.totalWeight);
		return Optional.of(this.selector.get(i));
	}

	public E getRandomOrThrow(RandomSource randomSource) {
		if (this.selector == null) {
			throw new IllegalStateException("Weighted list has no elements");
		}

		int i = randomSource.nextInt(this.totalWeight);
		return this.selector.get(i);
	}

	public List<Weighted<E>> unwrap() {
		return this.items;
	}

	public static <E> Codec<WeightedList<E>> codec(Codec<E> codec) {
		return Weighted.codec(codec).listOf().xmap(WeightedList::of, WeightedList::unwrap);
	}

	public static <E> Codec<WeightedList<E>> codec(MapCodec<E> mapCodec) {
		return Weighted.codec(mapCodec).listOf().xmap(WeightedList::of, WeightedList::unwrap);
	}

	public static <E> Codec<WeightedList<E>> nonEmptyCodec(Codec<E> codec) {
		return ExtraCodecs.nonEmptyList(Weighted.codec(codec).listOf()).xmap(WeightedList::of, WeightedList::unwrap);
	}

	public static <E> Codec<WeightedList<E>> nonEmptyCodec(MapCodec<E> mapCodec) {
		return ExtraCodecs.nonEmptyList(Weighted.codec(mapCodec).listOf()).xmap(WeightedList::of, WeightedList::unwrap);
	}

	public static <E, B extends ByteBuf> StreamCodec<B, WeightedList<E>> streamCodec(StreamCodec<B, E> streamCodec) {
		return Weighted.streamCodec(streamCodec).apply(ByteBufCodecs.list()).map(WeightedList::of, WeightedList::unwrap);
	}

	public boolean contains(E object) {
		for (Weighted<E> weighted : this.items) {
			if (weighted.value().equals(object)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean equals(@Nullable Object object) {
		if (this == object) {
			return true;
		} else {
			return !(object instanceof WeightedList<?> weightedList)
				? false
				: this.totalWeight == weightedList.totalWeight && Objects.equals(this.items, weightedList.items);
		}
	}

	@Override
	public int hashCode() {
		int i = this.totalWeight;
		return 31 * i + this.items.hashCode();
	}

	public static class Builder<E> {
		private final ImmutableList.Builder<Weighted<E>> result = ImmutableList.builder();

		public WeightedList.Builder<E> add(E object) {
			return this.add(object, 1);
		}

		public WeightedList.Builder<E> add(E object, int i) {
			this.result.add(new Weighted<>(object, i));
			return this;
		}

		public WeightedList<E> build() {
			return new WeightedList<>(this.result.build());
		}
	}

	static class Compact<E> implements WeightedList.Selector<E> {
		private final Weighted<?>[] entries;

		Compact(List<Weighted<E>> list) {
			this.entries = list.toArray(Weighted[]::new);
		}

		@Override
		public E get(int i) {
			for (Weighted<?> weighted : this.entries) {
				i -= weighted.weight();
				if (i < 0) {
					return (E)weighted.value();
				}
			}

			throw new IllegalStateException(i + " exceeded total weight");
		}
	}

	static class Flat<E> implements WeightedList.Selector<E> {
		private final Object[] entries;

		Flat(List<Weighted<E>> list, int i) {
			this.entries = new Object[i];
			int j = 0;

			for (Weighted<E> weighted : list) {
				int k = weighted.weight();
				Arrays.fill(this.entries, j, j + k, weighted.value());
				j += k;
			}
		}

		@Override
		public E get(int i) {
			return (E)this.entries[i];
		}
	}

	interface Selector<E> {
		E get(int i);
	}
}
