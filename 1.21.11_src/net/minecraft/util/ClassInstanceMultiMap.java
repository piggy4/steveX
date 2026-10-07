package net.minecraft.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class ClassInstanceMultiMap<T> extends AbstractCollection<T> {
	private final Map<Class<?>, List<T>> byClass = Maps.newHashMap();
	private final Class<T> baseClass;
	private final List<T> allInstances = Lists.newArrayList();

	public ClassInstanceMultiMap(Class<T> class_) {
		this.baseClass = class_;
		this.byClass.put(class_, this.allInstances);
	}

	@Override
	public boolean add(T object) {
		boolean bl = false;

		for (Entry<Class<?>, List<T>> entry : this.byClass.entrySet()) {
			if (entry.getKey().isInstance(object)) {
				bl |= entry.getValue().add(object);
			}
		}

		return bl;
	}

	@Override
	public boolean remove(Object object) {
		boolean bl = false;

		for (Entry<Class<?>, List<T>> entry : this.byClass.entrySet()) {
			if (entry.getKey().isInstance(object)) {
				List<T> list = entry.getValue();
				bl |= list.remove(object);
			}
		}

		return bl;
	}

	@Override
	public boolean contains(Object object) {
		return this.find(object.getClass()).contains(object);
	}

	public <S> Collection<S> find(Class<S> class_) {
		if (!this.baseClass.isAssignableFrom(class_)) {
			throw new IllegalArgumentException("Don't know how to search for " + class_);
		}

		List<? extends T> list = this.byClass
			.computeIfAbsent(class_, class_x -> this.allInstances.stream().filter(class_x::isInstance).collect(Util.toMutableList()));
		return (Collection<S>)Collections.unmodifiableCollection(list);
	}

	@Override
	public Iterator<T> iterator() {
		return this.allInstances.isEmpty() ? Collections.emptyIterator() : Iterators.unmodifiableIterator(this.allInstances.iterator());
	}

	public List<T> getAllInstances() {
		return ImmutableList.copyOf(this.allInstances);
	}

	@Override
	public int size() {
		return this.allInstances.size();
	}
}
