package net.minecraft.commands.execution;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.ExecutionCommandSource;
import org.jspecify.annotations.Nullable;

public interface CustomCommandExecutor<T> {
	void run(T object, ContextChain<T> contextChain, ChainModifiers chainModifiers, ExecutionControl<T> executionControl);

	interface CommandAdapter<T> extends Command<T>, CustomCommandExecutor<T> {
		@Override
		default int run(CommandContext<T> commandContext) throws CommandSyntaxException {
			throw new UnsupportedOperationException("This function should not run");
		}
	}

	abstract class WithErrorHandling<T extends ExecutionCommandSource<T>> implements CustomCommandExecutor<T> {
		public final void run(T executionCommandSource, ContextChain<T> contextChain, ChainModifiers chainModifiers, ExecutionControl<T> executionControl) {
			try {
				this.runGuarded(executionCommandSource, contextChain, chainModifiers, executionControl);
			} catch (CommandSyntaxException commandSyntaxException) {
				this.onError(commandSyntaxException, executionCommandSource, chainModifiers, executionControl.tracer());
				executionCommandSource.callback().onFailure();
			}
		}

		protected void onError(
			CommandSyntaxException commandSyntaxException, T executionCommandSource, ChainModifiers chainModifiers, @Nullable TraceCallbacks traceCallbacks
		) {
			executionCommandSource.handleError(commandSyntaxException, chainModifiers.isForked(), traceCallbacks);
		}

		protected abstract void runGuarded(
			T executionCommandSource, ContextChain<T> contextChain, ChainModifiers chainModifiers, ExecutionControl<T> executionControl
		) throws CommandSyntaxException;
	}
}
