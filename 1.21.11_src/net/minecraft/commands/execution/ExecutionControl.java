package net.minecraft.commands.execution;

import net.minecraft.commands.ExecutionCommandSource;
import org.jspecify.annotations.Nullable;

public interface ExecutionControl<T> {
	void queueNext(EntryAction<T> entryAction);

	void tracer(@Nullable TraceCallbacks traceCallbacks);

	@Nullable TraceCallbacks tracer();

	Frame currentFrame();

	static <T extends ExecutionCommandSource<T>> ExecutionControl<T> create(ExecutionContext<T> executionContext, Frame frame) {
		return new ExecutionControl<T>() {
			@Override
			public void queueNext(EntryAction<T> entryAction) {
				executionContext.queueNext(new CommandQueueEntry<>(frame, entryAction));
			}

			@Override
			public void tracer(@Nullable TraceCallbacks traceCallbacks) {
				executionContext.tracer(traceCallbacks);
			}

			@Override
			public @Nullable TraceCallbacks tracer() {
				return executionContext.tracer();
			}

			@Override
			public Frame currentFrame() {
				return frame;
			}
		};
	}
}
