package net.minecraft.advancements.criterion;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

public interface MinMaxBounds<T extends Number & Comparable<T>> {
	SimpleCommandExceptionType ERROR_EMPTY = new SimpleCommandExceptionType(Component.translatable("argument.range.empty"));
	SimpleCommandExceptionType ERROR_SWAPPED = new SimpleCommandExceptionType(Component.translatable("argument.range.swapped"));

	MinMaxBounds.Bounds<T> bounds();

	default Optional<T> min() {
		return this.bounds().min;
	}

	default Optional<T> max() {
		return this.bounds().max;
	}

	default boolean isAny() {
		return this.bounds().isAny();
	}

	record Bounds<T extends Number & Comparable<T>>(Optional<T> min, Optional<T> max) {

		public boolean isAny() {
			return this.min().isEmpty() && this.max().isEmpty();
		}

		public DataResult<MinMaxBounds.Bounds<T>> validateSwappedBoundsInCodec() {
			return this.areSwapped() ? DataResult.error(() -> "Swapped bounds in range: " + this.min() + " is higher than " + this.max()) : DataResult.success(this);
		}

		public boolean areSwapped() {
			return this.min.isPresent() && this.max.isPresent() && this.min.get().compareTo(this.max.get()) > 0;
		}

		public Optional<T> asPoint() {
			Optional<T> optional = this.min();
			Optional<T> optional2 = this.max();
			return optional.equals(optional2) ? optional : Optional.empty();
		}

		public static <T extends Number & Comparable<T>> MinMaxBounds.Bounds<T> any() {
			return new MinMaxBounds.Bounds<>(Optional.empty(), Optional.empty());
		}

		public static <T extends Number & Comparable<T>> MinMaxBounds.Bounds<T> exactly(T number) {
			Optional<T> optional = Optional.of(number);
			return new MinMaxBounds.Bounds<>(optional, optional);
		}

		public static <T extends Number & Comparable<T>> MinMaxBounds.Bounds<T> between(T number, T number2) {
			return new MinMaxBounds.Bounds<>(Optional.of(number), Optional.of(number2));
		}

		public static <T extends Number & Comparable<T>> MinMaxBounds.Bounds<T> atLeast(T number) {
			return new MinMaxBounds.Bounds<>(Optional.of(number), Optional.empty());
		}

		public static <T extends Number & Comparable<T>> MinMaxBounds.Bounds<T> atMost(T number) {
			return new MinMaxBounds.Bounds<>(Optional.empty(), Optional.of(number));
		}

		public <U extends Number & Comparable<U>> MinMaxBounds.Bounds<U> map(Function<T, U> function) {
			return new MinMaxBounds.Bounds<>(this.min.map(function), this.max.map(function));
		}

		static <T extends Number & Comparable<T>> Codec<MinMaxBounds.Bounds<T>> createCodec(Codec<T> codec) {
			Codec<MinMaxBounds.Bounds<T>> codec2 = RecordCodecBuilder.create(
				instance -> instance.group(
						codec.optionalFieldOf("min").forGetter(MinMaxBounds.Bounds::min), codec.optionalFieldOf("max").forGetter(MinMaxBounds.Bounds::max)
					)
					.apply(instance, MinMaxBounds.Bounds::new)
			);
			return Codec.either(codec2, codec).xmap(either -> either.map(bounds -> bounds, object -> exactly((T)object)), bounds -> {
				Optional<T> optional = bounds.asPoint();
				return optional.isPresent() ? Either.right(optional.get()) : Either.left((MinMaxBounds.Bounds<T>)bounds);
			});
		}

		static <B extends ByteBuf, T extends Number & Comparable<T>> StreamCodec<B, MinMaxBounds.Bounds<T>> createStreamCodec(StreamCodec<B, T> streamCodec) {
			return new StreamCodec<B, MinMaxBounds.Bounds<T>>() {
				private static final int MIN_FLAG = 1;
				private static final int MAX_FLAG = 2;

				public MinMaxBounds.Bounds<T> decode(B byteBuf) {
					byte b = byteBuf.readByte();
					Optional<T> optional = (b & 1) != 0 ? Optional.of(streamCodec.decode(byteBuf)) : Optional.empty();
					Optional<T> optional2 = (b & 2) != 0 ? Optional.of(streamCodec.decode(byteBuf)) : Optional.empty();
					return new MinMaxBounds.Bounds<>(optional, optional2);
				}

				public void encode(B byteBuf, MinMaxBounds.Bounds<T> bounds) {
					Optional<T> optional = bounds.min();
					Optional<T> optional2 = bounds.max();
					byteBuf.writeByte((optional.isPresent() ? 1 : 0) | (optional2.isPresent() ? 2 : 0));
					optional.ifPresent(number -> streamCodec.encode(byteBuf, (T)number));
					optional2.ifPresent(number -> streamCodec.encode(byteBuf, (T)number));
				}
			};
		}

		public static <T extends Number & Comparable<T>> MinMaxBounds.Bounds<T> fromReader(
			StringReader stringReader, Function<String, T> function, Supplier<DynamicCommandExceptionType> supplier
		) throws CommandSyntaxException {
			if (!stringReader.canRead()) {
				throw MinMaxBounds.ERROR_EMPTY.createWithContext(stringReader);
			}

			int i = stringReader.getCursor();

			try {
				Optional<T> optional = readNumber(stringReader, function, supplier);
				Optional<T> optional2;
				if (stringReader.canRead(2) && stringReader.peek() == '.' && stringReader.peek(1) == '.') {
					stringReader.skip();
					stringReader.skip();
					optional2 = readNumber(stringReader, function, supplier);
				} else {
					optional2 = optional;
				}

				if (optional.isEmpty() && optional2.isEmpty()) {
					throw MinMaxBounds.ERROR_EMPTY.createWithContext(stringReader);
				} else {
					return new MinMaxBounds.Bounds<>(optional, optional2);
				}
			} catch (CommandSyntaxException commandSyntaxException) {
				stringReader.setCursor(i);
				throw new CommandSyntaxException(commandSyntaxException.getType(), commandSyntaxException.getRawMessage(), commandSyntaxException.getInput(), i);
			}
		}

		private static <T extends Number> Optional<T> readNumber(
			StringReader stringReader, Function<String, T> function, Supplier<DynamicCommandExceptionType> supplier
		) throws CommandSyntaxException {
			int i = stringReader.getCursor();

			while (stringReader.canRead() && isAllowedInputChar(stringReader)) {
				stringReader.skip();
			}

			String string = stringReader.getString().substring(i, stringReader.getCursor());
			if (string.isEmpty()) {
				return Optional.empty();
			}

			try {
				return Optional.of(function.apply(string));
			} catch (NumberFormatException numberFormatException) {
				throw supplier.get().createWithContext(stringReader, string);
			}
		}

		private static boolean isAllowedInputChar(StringReader stringReader) {
			char c = stringReader.peek();
			if ((c < '0' || c > '9') && c != '-') {
				return c != '.' ? false : !stringReader.canRead(2) || stringReader.peek(1) != '.';
			} else {
				return true;
			}
		}
	}

	record Doubles(MinMaxBounds.Bounds<Double> bounds, MinMaxBounds.Bounds<Double> boundsSqr) implements MinMaxBounds<Double> {
		public static final MinMaxBounds.Doubles ANY = new MinMaxBounds.Doubles(MinMaxBounds.Bounds.any());
		public static final Codec<MinMaxBounds.Doubles> CODEC = MinMaxBounds.Bounds.createCodec(Codec.DOUBLE)
			.validate(MinMaxBounds.Bounds::validateSwappedBoundsInCodec)
			.xmap(MinMaxBounds.Doubles::new, MinMaxBounds.Doubles::bounds);
		public static final StreamCodec<ByteBuf, MinMaxBounds.Doubles> STREAM_CODEC = MinMaxBounds.Bounds.createStreamCodec(ByteBufCodecs.DOUBLE)
			.map(MinMaxBounds.Doubles::new, MinMaxBounds.Doubles::bounds);

		private Doubles(MinMaxBounds.Bounds<Double> bounds) {
			this(bounds, bounds.map(Mth::square));
		}

		public static MinMaxBounds.Doubles exactly(double d) {
			return new MinMaxBounds.Doubles(MinMaxBounds.Bounds.exactly(d));
		}

		public static MinMaxBounds.Doubles between(double d, double e) {
			return new MinMaxBounds.Doubles(MinMaxBounds.Bounds.between(d, e));
		}

		public static MinMaxBounds.Doubles atLeast(double d) {
			return new MinMaxBounds.Doubles(MinMaxBounds.Bounds.atLeast(d));
		}

		public static MinMaxBounds.Doubles atMost(double d) {
			return new MinMaxBounds.Doubles(MinMaxBounds.Bounds.atMost(d));
		}

		public boolean matches(double d) {
			return this.bounds.min.isPresent() && this.bounds.min.get() > d ? false : this.bounds.max.isEmpty() || !(this.bounds.max.get() < d);
		}

		public boolean matchesSqr(double d) {
			return this.boundsSqr.min.isPresent() && this.boundsSqr.min.get() > d ? false : this.boundsSqr.max.isEmpty() || !(this.boundsSqr.max.get() < d);
		}

		public static MinMaxBounds.Doubles fromReader(StringReader stringReader) throws CommandSyntaxException {
			int i = stringReader.getCursor();
			MinMaxBounds.Bounds<Double> bounds = MinMaxBounds.Bounds.fromReader(
				stringReader, Double::parseDouble, CommandSyntaxException.BUILT_IN_EXCEPTIONS::readerInvalidDouble
			);
			if (bounds.areSwapped()) {
				stringReader.setCursor(i);
				throw ERROR_SWAPPED.createWithContext(stringReader);
			} else {
				return new MinMaxBounds.Doubles(bounds);
			}
		}
	}

	record FloatDegrees(MinMaxBounds.Bounds<Float> bounds) implements MinMaxBounds<Float> {
		public static final MinMaxBounds.FloatDegrees ANY = new MinMaxBounds.FloatDegrees(MinMaxBounds.Bounds.any());
		public static final Codec<MinMaxBounds.FloatDegrees> CODEC = MinMaxBounds.Bounds.createCodec(Codec.FLOAT)
			.xmap(MinMaxBounds.FloatDegrees::new, MinMaxBounds.FloatDegrees::bounds);
		public static final StreamCodec<ByteBuf, MinMaxBounds.FloatDegrees> STREAM_CODEC = MinMaxBounds.Bounds.createStreamCodec(ByteBufCodecs.FLOAT)
			.map(MinMaxBounds.FloatDegrees::new, MinMaxBounds.FloatDegrees::bounds);

		public static MinMaxBounds.FloatDegrees fromReader(StringReader stringReader) throws CommandSyntaxException {
			MinMaxBounds.Bounds<Float> bounds = MinMaxBounds.Bounds.fromReader(
				stringReader, Float::parseFloat, CommandSyntaxException.BUILT_IN_EXCEPTIONS::readerInvalidFloat
			);
			return new MinMaxBounds.FloatDegrees(bounds);
		}
	}

	record Ints(MinMaxBounds.Bounds<Integer> bounds, MinMaxBounds.Bounds<Long> boundsSqr) implements MinMaxBounds<Integer> {
		public static final MinMaxBounds.Ints ANY = new MinMaxBounds.Ints(MinMaxBounds.Bounds.any());
		public static final Codec<MinMaxBounds.Ints> CODEC = MinMaxBounds.Bounds.createCodec(Codec.INT)
			.validate(MinMaxBounds.Bounds::validateSwappedBoundsInCodec)
			.xmap(MinMaxBounds.Ints::new, MinMaxBounds.Ints::bounds);
		public static final StreamCodec<ByteBuf, MinMaxBounds.Ints> STREAM_CODEC = MinMaxBounds.Bounds.createStreamCodec(ByteBufCodecs.INT)
			.map(MinMaxBounds.Ints::new, MinMaxBounds.Ints::bounds);

		private Ints(MinMaxBounds.Bounds<Integer> bounds) {
			this(bounds, bounds.map(integer -> Mth.square(integer.longValue())));
		}

		public static MinMaxBounds.Ints exactly(int i) {
			return new MinMaxBounds.Ints(MinMaxBounds.Bounds.exactly(i));
		}

		public static MinMaxBounds.Ints between(int i, int j) {
			return new MinMaxBounds.Ints(MinMaxBounds.Bounds.between(i, j));
		}

		public static MinMaxBounds.Ints atLeast(int i) {
			return new MinMaxBounds.Ints(MinMaxBounds.Bounds.atLeast(i));
		}

		public static MinMaxBounds.Ints atMost(int i) {
			return new MinMaxBounds.Ints(MinMaxBounds.Bounds.atMost(i));
		}

		public boolean matches(int i) {
			return this.bounds.min.isPresent() && this.bounds.min.get() > i ? false : this.bounds.max.isEmpty() || this.bounds.max.get() >= i;
		}

		public boolean matchesSqr(long l) {
			return this.boundsSqr.min.isPresent() && this.boundsSqr.min.get() > l ? false : this.boundsSqr.max.isEmpty() || this.boundsSqr.max.get() >= l;
		}

		public static MinMaxBounds.Ints fromReader(StringReader stringReader) throws CommandSyntaxException {
			int i = stringReader.getCursor();
			MinMaxBounds.Bounds<Integer> bounds = MinMaxBounds.Bounds.fromReader(
				stringReader, Integer::parseInt, CommandSyntaxException.BUILT_IN_EXCEPTIONS::readerInvalidInt
			);
			if (bounds.areSwapped()) {
				stringReader.setCursor(i);
				throw ERROR_SWAPPED.createWithContext(stringReader);
			} else {
				return new MinMaxBounds.Ints(bounds);
			}
		}
	}
}
