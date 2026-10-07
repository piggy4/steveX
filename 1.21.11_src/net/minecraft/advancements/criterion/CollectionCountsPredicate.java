package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Predicate;

public interface CollectionCountsPredicate<T, P extends Predicate<T>> extends Predicate<Iterable<T>> {
	List<CollectionCountsPredicate.Entry<T, P>> unpack();

	static <T, P extends Predicate<T>> Codec<CollectionCountsPredicate<T, P>> codec(Codec<P> codec) {
		return CollectionCountsPredicate.Entry.<T, P>codec(codec).listOf().xmap(CollectionCountsPredicate::of, CollectionCountsPredicate::unpack);
	}

	@SafeVarargs
	static <T, P extends Predicate<T>> CollectionCountsPredicate<T, P> of(CollectionCountsPredicate.Entry<T, P>... entrys) {
		return of(List.of(entrys));
	}

	static <T, P extends Predicate<T>> CollectionCountsPredicate<T, P> of(List<CollectionCountsPredicate.Entry<T, P>> list) {
		return switch (list.size()) {
			case 0 -> new CollectionCountsPredicate.Zero();
			case 1 -> new CollectionCountsPredicate.Single(list.getFirst());
			default -> new CollectionCountsPredicate.Multiple(list);
		};
	}

	record Entry<T, P extends Predicate<T>>(P test, MinMaxBounds.Ints count) {
		public static <T, P extends Predicate<T>> Codec<CollectionCountsPredicate.Entry<T, P>> codec(Codec<P> codec) {
			return RecordCodecBuilder.create(
				instance -> instance.group(
						codec.fieldOf("test").forGetter(CollectionCountsPredicate.Entry::test),
						MinMaxBounds.Ints.CODEC.fieldOf("count").forGetter(CollectionCountsPredicate.Entry::count)
					)
					.apply(instance, CollectionCountsPredicate.Entry::new)
			);
		}

		public boolean test(Iterable<T> iterable) {
			int i = 0;

			for (T object : iterable) {
				if (this.test.test(object)) {
					i++;
				}
			}

			return this.count.matches(i);
		}
	}

	record Multiple<T, P extends Predicate<T>>(List<CollectionCountsPredicate.Entry<T, P>> entries) implements CollectionCountsPredicate<T, P> {
		public boolean test(Iterable<T> iterable) {
			for (CollectionCountsPredicate.Entry<T, P> entry : this.entries) {
				if (!entry.test(iterable)) {
					return false;
				}
			}

			return true;
		}

		@Override
		public List<CollectionCountsPredicate.Entry<T, P>> unpack() {
			return this.entries;
		}
	}

	record Single<T, P extends Predicate<T>>(CollectionCountsPredicate.Entry<T, P> entry) implements CollectionCountsPredicate<T, P> {
		public boolean test(Iterable<T> iterable) {
			return this.entry.test(iterable);
		}

		@Override
		public List<CollectionCountsPredicate.Entry<T, P>> unpack() {
			return List.of(this.entry);
		}
	}

	class Zero<T, P extends Predicate<T>> implements CollectionCountsPredicate<T, P> {
		public boolean test(Iterable<T> iterable) {
			return true;
		}

		@Override
		public List<CollectionCountsPredicate.Entry<T, P>> unpack() {
			return List.of();
		}
	}
}
