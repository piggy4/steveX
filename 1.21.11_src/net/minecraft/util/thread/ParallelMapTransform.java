package net.minecraft.util.thread;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class ParallelMapTransform {
	private static final int DEFAULT_TASKS_PER_THREAD = 16;

	public static <K, U, V> CompletableFuture<Map<K, V>> schedule(Map<K, U> map, BiFunction<K, U, @Nullable V> biFunction, int i, Executor executor) {
		int j = map.size();
		if (j == 0) {
			return CompletableFuture.completedFuture(Map.of());
		} else if (j == 1) {
			Entry<K, U> entry = map.entrySet().iterator().next();
			K object = entry.getKey();
			U object2 = entry.getValue();
			return CompletableFuture.supplyAsync(() -> {
				V object3 = biFunction.apply(object, object2);
				return object3 != null ? Map.of(object, object3) : Map.of();
			}, executor);
		} else {
			ParallelMapTransform.SplitterBase<K, U, V> splitterBase = j <= i
				? new ParallelMapTransform.SingleTaskSplitter<>(biFunction, j)
				: new ParallelMapTransform.BatchedTaskSplitter<>(biFunction, j, i);
			return splitterBase.scheduleTasks(map, executor);
		}
	}

	public static <K, U, V> CompletableFuture<Map<K, V>> schedule(Map<K, U> map, BiFunction<K, U, @Nullable V> biFunction, Executor executor) {
		int i = Util.maxAllowedExecutorThreads() * 16;
		return schedule(map, biFunction, i, executor);
	}

	static class BatchedTaskSplitter<K, U, V> extends ParallelMapTransform.SplitterBase<K, U, V> {
		private final Map<K, V> result;
		private final int batchSize;
		private final int firstUndersizedBatchIndex;

		BatchedTaskSplitter(BiFunction<K, U, V> biFunction, int i, int j) {
			super(biFunction, i, j);
			this.result = new HashMap<>(i);
			this.batchSize = Mth.positiveCeilDiv(i, j);
			int k = this.batchSize * j;
			int l = k - i;
			this.firstUndersizedBatchIndex = j - l;
			assert this.firstUndersizedBatchIndex > 0 && this.firstUndersizedBatchIndex <= j;
		}

		@Override
		protected CompletableFuture<?> scheduleBatch(ParallelMapTransform.Container<K, U, V> container, int i, int j, Executor executor) {
			int k = j - i;
			assert k == this.batchSize || k == this.batchSize - 1;
			return CompletableFuture.runAsync(createTask(this.result, i, j, container), executor);
		}

		@Override
		protected int batchSize(int i) {
			return i < this.firstUndersizedBatchIndex ? this.batchSize : this.batchSize - 1;
		}

		private static <K, U, V> Runnable createTask(Map<K, V> map, int i, int j, ParallelMapTransform.Container<K, U, V> container) {
			return () -> {
				for (int k = i; k < j; k++) {
					container.applyOperation(k);
				}

				synchronized (map) {
					for (int l = i; l < j; l++) {
						container.copyOut(l, map);
					}
				}
			};
		}

		@Override
		protected CompletableFuture<Map<K, V>> scheduleFinalOperation(CompletableFuture<?> completableFuture, ParallelMapTransform.Container<K, U, V> container) {
			Map<K, V> map = this.result;
			return completableFuture.thenApply(object -> map);
		}
	}

	record Container<K, U, V>(BiFunction<K, U, V> operation, @Nullable Object[] keys, @Nullable Object[] values) {
		public Container(BiFunction<K, U, V> biFunction, int i) {
			this(biFunction, new Object[i], new Object[i]);
		}

		public void put(int i, K object, U object2) {
			this.keys[i] = object;
			this.values[i] = object2;
		}

		private @Nullable K key(int i) {
			return (K)this.keys[i];
		}

		private @Nullable V output(int i) {
			return (V)this.values[i];
		}

		private @Nullable U input(int i) {
			return (U)this.values[i];
		}

		public void applyOperation(int i) {
			this.values[i] = this.operation.apply(this.key(i), this.input(i));
		}

		public void copyOut(int i, Map<K, V> map) {
			V object = this.output(i);
			if (object != null) {
				K object2 = this.key(i);
				map.put(object2, object);
			}
		}

		public int size() {
			return this.keys.length;
		}
	}

	static class SingleTaskSplitter<K, U, V> extends ParallelMapTransform.SplitterBase<K, U, V> {
		SingleTaskSplitter(BiFunction<K, U, V> biFunction, int i) {
			super(biFunction, i, i);
		}

		@Override
		protected int batchSize(int i) {
			return 1;
		}

		@Override
		protected CompletableFuture<?> scheduleBatch(ParallelMapTransform.Container<K, U, V> container, int i, int j, Executor executor) {
			assert i + 1 == j;
			return CompletableFuture.runAsync(() -> container.applyOperation(i), executor);
		}

		@Override
		protected CompletableFuture<Map<K, V>> scheduleFinalOperation(CompletableFuture<?> completableFuture, ParallelMapTransform.Container<K, U, V> container) {
			return completableFuture.thenApply(object -> {
				Map<K, V> map = new HashMap<>(container.size());

				for (int i = 0; i < container.size(); i++) {
					container.copyOut(i, map);
				}

				return map;
			});
		}
	}

	abstract static class SplitterBase<K, U, V> {
		private int lastScheduledIndex;
		private int currentIndex;
		private final CompletableFuture<?>[] tasks;
		private int batchIndex;
		private final ParallelMapTransform.Container<K, U, V> container;

		SplitterBase(BiFunction<K, U, V> biFunction, int i, int j) {
			this.container = new ParallelMapTransform.Container<>(biFunction, i);
			this.tasks = new CompletableFuture[j];
		}

		private int pendingBatchSize() {
			return this.currentIndex - this.lastScheduledIndex;
		}

		public CompletableFuture<Map<K, V>> scheduleTasks(Map<K, U> map, Executor executor) {
			map.forEach((object, object2) -> {
				this.container.put(this.currentIndex++, (K)object, (U)object2);
				if (this.pendingBatchSize() == this.batchSize(this.batchIndex)) {
					this.tasks[this.batchIndex++] = this.scheduleBatch(this.container, this.lastScheduledIndex, this.currentIndex, executor);
					this.lastScheduledIndex = this.currentIndex;
				}
			});
			assert this.currentIndex == this.container.size();
			assert this.lastScheduledIndex == this.currentIndex;
			assert this.batchIndex == this.tasks.length;
			return this.scheduleFinalOperation(CompletableFuture.allOf(this.tasks), this.container);
		}

		protected abstract int batchSize(int i);

		protected abstract CompletableFuture<?> scheduleBatch(ParallelMapTransform.Container<K, U, V> container, int i, int j, Executor executor);

		protected abstract CompletableFuture<Map<K, V>> scheduleFinalOperation(
			CompletableFuture<?> completableFuture, ParallelMapTransform.Container<K, U, V> container
		);
	}
}
