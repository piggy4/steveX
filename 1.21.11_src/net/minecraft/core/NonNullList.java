package net.minecraft.core;

import com.google.common.collect.Lists;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public class NonNullList<E> extends AbstractList<E> {
	private final List<E> list;
	private final @Nullable E defaultValue;

	public static <E> NonNullList<E> create() {
		return new NonNullList<>(Lists.newArrayList(), null);
	}

	public static <E> NonNullList<E> createWithCapacity(int i) {
		return new NonNullList<>(Lists.newArrayListWithCapacity(i), null);
	}

	public static <E> NonNullList<E> withSize(int i, E object) {
		Objects.requireNonNull(object);
		Object[] objects = new Object[i];
		Arrays.fill(objects, object);
		return new NonNullList<>(Arrays.asList((E[])objects), object);
	}

	@SafeVarargs
	public static <E> NonNullList<E> of(E object, E... objects) {
		return new NonNullList<>(Arrays.asList(objects), object);
	}

	protected NonNullList(List<E> list, @Nullable E object) {
		this.list = list;
		this.defaultValue = object;
	}

	@Override
	public E get(int i) {
		return this.list.get(i);
	}

	@Override
	public E set(int i, E object) {
		Objects.requireNonNull(object);
		return this.list.set(i, object);
	}

	@Override
	public void add(int i, E object) {
		Objects.requireNonNull(object);
		this.list.add(i, object);
	}

	@Override
	public E remove(int i) {
		return this.list.remove(i);
	}

	@Override
	public int size() {
		return this.list.size();
	}

	@Override
	public void clear() {
		if (this.defaultValue == null) {
			super.clear();
		} else {
			for (int i = 0; i < this.size(); i++) {
				this.set(i, this.defaultValue);
			}
		}
	}
}
