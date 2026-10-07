package net.minecraft.util;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Queues;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import java.util.Deque;
import org.jspecify.annotations.Nullable;

public final class SequencedPriorityIterator<T> extends AbstractIterator<T> {
	private static final int MIN_PRIO = Integer.MIN_VALUE;
	private @Nullable Deque<T> highestPrioQueue = null;
	private int highestPrio = Integer.MIN_VALUE;
	private final Int2ObjectMap<Deque<T>> queuesByPriority = new Int2ObjectOpenHashMap<>();

	public void add(T object, int i) {
		if (i == this.highestPrio && this.highestPrioQueue != null) {
			this.highestPrioQueue.addLast(object);
		} else {
			Deque<T> deque = this.queuesByPriority.computeIfAbsent(i, ix -> Queues.newArrayDeque());
			deque.addLast(object);
			if (i >= this.highestPrio) {
				this.highestPrioQueue = deque;
				this.highestPrio = i;
			}
		}
	}

	@Override
	protected @Nullable T computeNext() {
		if (this.highestPrioQueue == null) {
			return this.endOfData();
		}

		T object = this.highestPrioQueue.removeFirst();
		if (object == null) {
			return this.endOfData();
		}

		if (this.highestPrioQueue.isEmpty()) {
			this.switchCacheToNextHighestPrioQueue();
		}

		return object;
	}

	private void switchCacheToNextHighestPrioQueue() {
		int i = Integer.MIN_VALUE;
		Deque<T> deque = null;

		for (Entry<Deque<T>> entry : Int2ObjectMaps.fastIterable(this.queuesByPriority)) {
			Deque<T> deque2 = entry.getValue();
			int j = entry.getIntKey();
			if (j > i && !deque2.isEmpty()) {
				i = j;
				deque = deque2;
				if (j == this.highestPrio - 1) {
					break;
				}
			}
		}

		this.highestPrio = i;
		this.highestPrioQueue = deque;
	}
}
