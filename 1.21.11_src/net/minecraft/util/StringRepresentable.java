package net.minecraft.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Keyable;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public interface StringRepresentable {
	int PRE_BUILT_MAP_THRESHOLD = 16;

	String getSerializedName();

	static <E extends Enum<E> & StringRepresentable> StringRepresentable.EnumCodec<E> fromEnum(Supplier<E[]> supplier) {
		return fromEnumWithMapping(supplier, string -> string);
	}

	static <E extends Enum<E> & StringRepresentable> StringRepresentable.EnumCodec<E> fromEnumWithMapping(
		Supplier<E[]> supplier, Function<String, String> function
	) {
		E[] enums = (E[])supplier.get();
		Function<String, E> function2 = createNameLookup(enums, enum_ -> function.apply(enum_.getSerializedName()));
		return new StringRepresentable.EnumCodec<>(enums, function2);
	}

	static <T extends StringRepresentable> Codec<T> fromValues(Supplier<T[]> supplier) {
		T[] stringRepresentables = (T[])supplier.get();
		Function<String, T> function = createNameLookup(stringRepresentables);
		ToIntFunction<T> toIntFunction = Util.createIndexLookup(Arrays.asList(stringRepresentables));
		return new StringRepresentable.StringRepresentableCodec<>(stringRepresentables, function, toIntFunction);
	}

	static <T extends StringRepresentable> Function<String, @Nullable T> createNameLookup(T[] stringRepresentables) {
		return createNameLookup(stringRepresentables, StringRepresentable::getSerializedName);
	}

	static <T> Function<String, @Nullable T> createNameLookup(T[] objects, Function<T, String> function) {
		if (objects.length > 16) {
			Map<String, T> map = Arrays.<T>stream(objects).collect(Collectors.toMap(function, object -> (T)object));
			return map::get;
		} else {
			return string -> {
				for (T object : objects) {
					if (function.apply(object).equals(string)) {
						return object;
					}
				}

				return null;
			};
		}
	}

	static Keyable keys(StringRepresentable[] stringRepresentables) {
		return new Keyable() {
			@Override
			public <T> Stream<T> keys(DynamicOps<T> dynamicOps) {
				return Arrays.stream(stringRepresentables).map(StringRepresentable::getSerializedName).map(dynamicOps::createString);
			}
		};
	}

	class EnumCodec<E extends Enum<E> & StringRepresentable> extends StringRepresentable.StringRepresentableCodec<E> {
		private final Function<String, @Nullable E> resolver;

		public EnumCodec(E[] enums, Function<String, E> function) {
			super(enums, function, object -> object.ordinal());
			this.resolver = function;
		}

		public @Nullable E byName(String string) {
			return this.resolver.apply(string);
		}

		public E byName(String string, E enum_) {
			return Objects.requireNonNullElse(this.byName(string), enum_);
		}

		public E byName(String string, Supplier<? extends E> supplier) {
			return Objects.requireNonNullElseGet(this.byName(string), supplier);
		}
	}

	class StringRepresentableCodec<S extends StringRepresentable> implements Codec<S> {
		private final Codec<S> codec;

		public StringRepresentableCodec(S[] stringRepresentables, Function<String, @Nullable S> function, ToIntFunction<S> toIntFunction) {
			this.codec = ExtraCodecs.orCompressed(
				Codec.stringResolver(StringRepresentable::getSerializedName, function),
				ExtraCodecs.idResolverCodec(toIntFunction, i -> i >= 0 && i < stringRepresentables.length ? stringRepresentables[i] : null, -1)
			);
		}

		@Override
		public <T> DataResult<Pair<S, T>> decode(DynamicOps<T> dynamicOps, T object) {
			return this.codec.decode(dynamicOps, object);
		}

		public <T> DataResult<T> encode(S stringRepresentable, DynamicOps<T> dynamicOps, T object) {
			return this.codec.encode(stringRepresentable, dynamicOps, object);
		}
	}
}
