package net.minecraft.util.thread;

import com.google.common.collect.Queues;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

public interface StrictQueue<T extends Runnable> {
	@Nullable Runnable pop();

	boolean push(T runnable);

	boolean isEmpty();

	int size();

	final class FixedPriorityQueue implements StrictQueue<StrictQueue.RunnableWithPriority> {
		private final Queue<Runnable>[] queues;
		private final AtomicInteger size = new AtomicInteger();

		public FixedPriorityQueue(int i) {
			this.queues = new Queue[i];

			for (int j = 0; j < i; j++) {
				this.queues[j] = Queues.newConcurrentLinkedQueue();
			}
		}

		@Override
		public @Nullable Runnable pop() {
			for (Queue<Runnable> queue : this.queues) {
				Runnable runnable = queue.poll();
				if (runnable != null) {
					this.size.decrementAndGet();
					return runnable;
				}
			}

			return null;
		}

		public boolean push(StrictQueue.RunnableWithPriority runnableWithPriority) {
			int i = runnableWithPriority.priority;
			if (i < this.queues.length && i >= 0) {
				this.queues[i].add(runnableWithPriority);
				this.size.incrementAndGet();
				return true;
			} else {
				throw new IndexOutOfBoundsException(String.format(Locale.ROOT, "Priority %d not supported. Expected range [0-%d]", i, this.queues.length - 1));
			}
		}

		@Override
		public boolean isEmpty() {
			return this.size.get() == 0;
		}

		@Override
		public int size() {
			return this.size.get();
		}
	}

	final class QueueStrictQueue implements StrictQueue<Runnable> {
		private final Queue<Runnable> queue;

		public QueueStrictQueue(Queue<Runnable> queue) {
			this.queue = queue;
		}

		@Override
		public @Nullable Runnable pop() {
			return this.queue.poll();
		}

		@Override
		public boolean push(Runnable runnable) {
			return this.queue.add(runnable);
		}

		@Override
		public boolean isEmpty() {
			return this.queue.isEmpty();
		}

		@Override
		public int size() {
			return this.queue.size();
		}
	}

	record RunnableWithPriority(int priority, Runnable task) implements Runnable {

		@Override
		public void run() {
			this.task.run();
		}
	}
}
