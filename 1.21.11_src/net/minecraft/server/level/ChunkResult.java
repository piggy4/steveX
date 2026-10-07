package net.minecraft.server.level;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public interface ChunkResult<T> {
	static <T> ChunkResult<T> of(T object) {
		return new ChunkResult.Success<>(object);
	}

	static <T> ChunkResult<T> error(String string) {
		return error(() -> string);
	}

	static <T> ChunkResult<T> error(Supplier<String> supplier) {
		return new ChunkResult.Fail<>(supplier);
	}

	boolean isSuccess();

	@Nullable T orElse(@Nullable T object);

	static <R> @Nullable R orElse(ChunkResult<? extends R> chunkResult, @Nullable R object) {
		R object2 = (R)chunkResult.orElse(null);
		return object2 != null ? object2 : object;
	}

	@Nullable String getError();

	ChunkResult<T> ifSuccess(Consumer<T> consumer);

	<R> ChunkResult<R> map(Function<T, R> function);

	<E extends Throwable> T orElseThrow(Supplier<E> supplier) throws E;

	record Fail<T>(Supplier<String> error) implements ChunkResult<T> {
		@Override
		public boolean isSuccess() {
			return false;
		}

		@Override
		public @Nullable T orElse(@Nullable T object) {
			return object;
		}

		@Override
		public String getError() {
			return this.error.get();
		}

		@Override
		public ChunkResult<T> ifSuccess(Consumer<T> consumer) {
			return this;
		}

		@Override
		public <R> ChunkResult<R> map(Function<T, R> function) {
			return new ChunkResult.Fail(this.error);
		}

		@Override
		public <E extends Throwable> T orElseThrow(Supplier<E> supplier) throws E {
			throw supplier.get();
		}
	}

	record Success<T>(T value) implements ChunkResult<T> {
		@Override
		public boolean isSuccess() {
			return true;
		}

		@Override
		public T orElse(@Nullable T object) {
			return this.value;
		}

		@Override
		public @Nullable String getError() {
			return null;
		}

		@Override
		public ChunkResult<T> ifSuccess(Consumer<T> consumer) {
			consumer.accept(this.value);
			return this;
		}

		@Override
		public <R> ChunkResult<R> map(Function<T, R> function) {
			return new ChunkResult.Success<>(function.apply(this.value));
		}

		@Override
		public <E extends Throwable> T orElseThrow(Supplier<E> supplier) throws E {
			return this.value;
		}
	}
}
