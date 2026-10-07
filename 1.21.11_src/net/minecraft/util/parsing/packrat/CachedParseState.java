package net.minecraft.util.parsing.packrat;

import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public abstract class CachedParseState<S> implements ParseState<S> {
	private CachedParseState.@Nullable PositionCache[] positionCache = new CachedParseState.PositionCache[256];
	private final ErrorCollector<S> errorCollector;
	private final Scope scope = new Scope();
	private CachedParseState.@Nullable SimpleControl[] controlCache = new CachedParseState.SimpleControl[16];
	private int nextControlToReturn;
	private final CachedParseState<S>.Silent silent = new CachedParseState.Silent();

	protected CachedParseState(ErrorCollector<S> errorCollector) {
		this.errorCollector = errorCollector;
	}

	@Override
	public Scope scope() {
		return this.scope;
	}

	@Override
	public ErrorCollector<S> errorCollector() {
		return this.errorCollector;
	}

	@Override
	public <T> @Nullable T parse(NamedRule<S, T> namedRule) {
		int i = this.mark();
		CachedParseState.PositionCache positionCache = this.getCacheForPosition(i);
		int j = positionCache.findKeyIndex(namedRule.name());
		if (j != -1) {
			CachedParseState.CacheEntry<T> cacheEntry = positionCache.getValue(j);
			if (cacheEntry != null) {
				if (cacheEntry == CachedParseState.CacheEntry.NEGATIVE) {
					return null;
				}

				this.restore(cacheEntry.markAfterParse);
				return cacheEntry.value;
			}
		} else {
			j = positionCache.allocateNewEntry(namedRule.name());
		}

		T object = namedRule.value().parse(this);
		CachedParseState.CacheEntry<T> cacheEntry2;
		if (object == null) {
			cacheEntry2 = CachedParseState.CacheEntry.negativeEntry();
		} else {
			int k = this.mark();
			cacheEntry2 = new CachedParseState.CacheEntry<>(object, k);
		}

		positionCache.setValue(j, cacheEntry2);
		return object;
	}

	private CachedParseState.PositionCache getCacheForPosition(int i) {
		int j = this.positionCache.length;
		if (i >= j) {
			int k = Util.growByHalf(j, i + 1);
			CachedParseState.PositionCache[] positionCaches = new CachedParseState.PositionCache[k];
			System.arraycopy(this.positionCache, 0, positionCaches, 0, j);
			this.positionCache = positionCaches;
		}

		CachedParseState.PositionCache positionCache = this.positionCache[i];
		if (positionCache == null) {
			positionCache = new CachedParseState.PositionCache();
			this.positionCache[i] = positionCache;
		}

		return positionCache;
	}

	@Override
	public Control acquireControl() {
		int i = this.controlCache.length;
		if (this.nextControlToReturn >= i) {
			int j = Util.growByHalf(i, this.nextControlToReturn + 1);
			CachedParseState.SimpleControl[] simpleControls = new CachedParseState.SimpleControl[j];
			System.arraycopy(this.controlCache, 0, simpleControls, 0, i);
			this.controlCache = simpleControls;
		}

		int j = this.nextControlToReturn++;
		CachedParseState.SimpleControl simpleControl = this.controlCache[j];
		if (simpleControl == null) {
			simpleControl = new CachedParseState.SimpleControl();
			this.controlCache[j] = simpleControl;
		} else {
			simpleControl.reset();
		}

		return simpleControl;
	}

	@Override
	public void releaseControl() {
		this.nextControlToReturn--;
	}

	@Override
	public ParseState<S> silent() {
		return this.silent;
	}

	record CacheEntry<T>(@Nullable T value, int markAfterParse) {
		public static final CachedParseState.CacheEntry<?> NEGATIVE = new CachedParseState.CacheEntry(null, -1);

		public static <T> CachedParseState.CacheEntry<T> negativeEntry() {
			return (CachedParseState.CacheEntry<T>)NEGATIVE;
		}
	}

	static class PositionCache {
		public static final int ENTRY_STRIDE = 2;
		private static final int NOT_FOUND = -1;
		private Object[] atomCache = new Object[16];
		private int nextKey;

		public int findKeyIndex(Atom<?> atom) {
			for (int i = 0; i < this.nextKey; i += 2) {
				if (this.atomCache[i] == atom) {
					return i;
				}
			}

			return -1;
		}

		public int allocateNewEntry(Atom<?> atom) {
			int i = this.nextKey;
			this.nextKey += 2;
			int j = i + 1;
			int k = this.atomCache.length;
			if (j >= k) {
				int l = Util.growByHalf(k, j + 1);
				Object[] objects = new Object[l];
				System.arraycopy(this.atomCache, 0, objects, 0, k);
				this.atomCache = objects;
			}

			this.atomCache[i] = atom;
			return i;
		}

		public <T> CachedParseState.@Nullable CacheEntry<T> getValue(int i) {
			return (CachedParseState.CacheEntry<T>)this.atomCache[i + 1];
		}

		public void setValue(int i, CachedParseState.CacheEntry<?> cacheEntry) {
			this.atomCache[i + 1] = cacheEntry;
		}
	}

	class Silent implements ParseState<S> {
		private final ErrorCollector<S> silentCollector = new ErrorCollector.Nop<>();

		@Override
		public ErrorCollector<S> errorCollector() {
			return this.silentCollector;
		}

		@Override
		public Scope scope() {
			return CachedParseState.this.scope();
		}

		@Override
		public <T> @Nullable T parse(NamedRule<S, T> namedRule) {
			return CachedParseState.this.parse(namedRule);
		}

		@Override
		public S input() {
			return CachedParseState.this.input();
		}

		@Override
		public int mark() {
			return CachedParseState.this.mark();
		}

		@Override
		public void restore(int i) {
			CachedParseState.this.restore(i);
		}

		@Override
		public Control acquireControl() {
			return CachedParseState.this.acquireControl();
		}

		@Override
		public void releaseControl() {
			CachedParseState.this.releaseControl();
		}

		@Override
		public ParseState<S> silent() {
			return this;
		}
	}

	static class SimpleControl implements Control {
		private boolean hasCut;

		@Override
		public void cut() {
			this.hasCut = true;
		}

		@Override
		public boolean hasCut() {
			return this.hasCut;
		}

		public void reset() {
			this.hasCut = false;
		}
	}
}
