package net.minecraft;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public class Optionull {
	@Deprecated
	public static <T> T orElse(@Nullable T object, T object2) {
		return Objects.requireNonNullElse(object, object2);
	}

	public static <T, R> @Nullable R map(@Nullable T object, Function<T, R> function) {
		return object == null ? null : function.apply(object);
	}

	public static <T, R> R mapOrDefault(@Nullable T object, Function<T, R> function, R object2) {
		return object == null ? object2 : function.apply(object);
	}

	public static <T, R> R mapOrElse(@Nullable T object, Function<T, R> function, Supplier<R> supplier) {
		return object == null ? supplier.get() : function.apply(object);
	}

	public static <T> @Nullable T first(Collection<T> collection) {
		Iterator<T> iterator = collection.iterator();
		return iterator.hasNext() ? iterator.next() : null;
	}

	public static <T> T firstOrDefault(Collection<T> collection, T object) {
		Iterator<T> iterator = collection.iterator();
		return iterator.hasNext() ? iterator.next() : object;
	}

	public static <T> T firstOrElse(Collection<T> collection, Supplier<T> supplier) {
		Iterator<T> iterator = collection.iterator();
		return iterator.hasNext() ? iterator.next() : supplier.get();
	}

	public static <T> boolean isNullOrEmpty(T @Nullable [] objects) {
		return objects == null || objects.length == 0;
	}

	public static boolean isNullOrEmpty(boolean @Nullable [] bls) {
		return bls == null || bls.length == 0;
	}

	public static boolean isNullOrEmpty(byte @Nullable [] bs) {
		return bs == null || bs.length == 0;
	}

	public static boolean isNullOrEmpty(char @Nullable [] cs) {
		return cs == null || cs.length == 0;
	}

	public static boolean isNullOrEmpty(short @Nullable [] ss) {
		return ss == null || ss.length == 0;
	}

	public static boolean isNullOrEmpty(int @Nullable [] is) {
		return is == null || is.length == 0;
	}

	public static boolean isNullOrEmpty(long @Nullable [] ls) {
		return ls == null || ls.length == 0;
	}

	public static boolean isNullOrEmpty(float @Nullable [] fs) {
		return fs == null || fs.length == 0;
	}

	public static boolean isNullOrEmpty(double @Nullable [] ds) {
		return ds == null || ds.length == 0;
	}
}
