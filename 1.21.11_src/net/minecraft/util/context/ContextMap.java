package net.minecraft.util.context;

import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

public class ContextMap {
	private final Map<ContextKey<?>, Object> params;

	ContextMap(Map<ContextKey<?>, Object> map) {
		this.params = map;
	}

	public boolean has(ContextKey<?> contextKey) {
		return this.params.containsKey(contextKey);
	}

	public <T> T getOrThrow(ContextKey<T> contextKey) {
		T object = (T)this.params.get(contextKey);
		if (object == null) {
			throw new NoSuchElementException(contextKey.name().toString());
		} else {
			return object;
		}
	}

	public <T> @Nullable T getOptional(ContextKey<T> contextKey) {
		return (T)this.params.get(contextKey);
	}

	@Contract("_,!null->!null; _,_->_")
	public <T> @Nullable T getOrDefault(ContextKey<T> contextKey, @Nullable T object) {
		return (T)this.params.getOrDefault(contextKey, object);
	}

	public static class Builder {
		private final Map<ContextKey<?>, Object> params = new IdentityHashMap<>();

		public <T> ContextMap.Builder withParameter(ContextKey<T> contextKey, T object) {
			this.params.put(contextKey, object);
			return this;
		}

		public <T> ContextMap.Builder withOptionalParameter(ContextKey<T> contextKey, @Nullable T object) {
			if (object == null) {
				this.params.remove(contextKey);
			} else {
				this.params.put(contextKey, object);
			}

			return this;
		}

		public <T> T getParameter(ContextKey<T> contextKey) {
			T object = (T)this.params.get(contextKey);
			if (object == null) {
				throw new NoSuchElementException(contextKey.name().toString());
			} else {
				return object;
			}
		}

		public <T> @Nullable T getOptionalParameter(ContextKey<T> contextKey) {
			return (T)this.params.get(contextKey);
		}

		public ContextMap create(ContextKeySet contextKeySet) {
			Set<ContextKey<?>> set = Sets.difference(this.params.keySet(), contextKeySet.allowed());
			if (!set.isEmpty()) {
				throw new IllegalArgumentException("Parameters not allowed in this parameter set: " + set);
			} else {
				Set<ContextKey<?>> set2 = Sets.difference(contextKeySet.required(), this.params.keySet());
				if (!set2.isEmpty()) {
					throw new IllegalArgumentException("Missing required parameters: " + set2);
				} else {
					return new ContextMap(this.params);
				}
			}
		}
	}
}
