package net.minecraft.commands.functions;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

class FunctionBuilder<T extends ExecutionCommandSource<T>> {
	private @Nullable List<UnboundEntryAction<T>> plainEntries = new ArrayList<>();
	private @Nullable List<MacroFunction.Entry<T>> macroEntries;
	private final List<String> macroArguments = new ArrayList<>();

	public void addCommand(UnboundEntryAction<T> unboundEntryAction) {
		if (this.macroEntries != null) {
			this.macroEntries.add(new MacroFunction.PlainTextEntry<>(unboundEntryAction));
		} else {
			this.plainEntries.add(unboundEntryAction);
		}
	}

	private int getArgumentIndex(String string) {
		int i = this.macroArguments.indexOf(string);
		if (i == -1) {
			i = this.macroArguments.size();
			this.macroArguments.add(string);
		}

		return i;
	}

	private IntList convertToIndices(List<String> list) {
		IntArrayList intArrayList = new IntArrayList(list.size());

		for (String string : list) {
			intArrayList.add(this.getArgumentIndex(string));
		}

		return intArrayList;
	}

	public void addMacro(String string, int i, T executionCommandSource) {
		StringTemplate stringTemplate;
		try {
			stringTemplate = StringTemplate.fromString(string);
		} catch (Exception exception) {
			throw new IllegalArgumentException("Can't parse function line " + i + ": '" + string + "'", exception);
		}

		if (this.plainEntries != null) {
			this.macroEntries = new ArrayList<>(this.plainEntries.size() + 1);

			for (UnboundEntryAction<T> unboundEntryAction : this.plainEntries) {
				this.macroEntries.add(new MacroFunction.PlainTextEntry<>(unboundEntryAction));
			}

			this.plainEntries = null;
		}

		this.macroEntries.add(new MacroFunction.MacroEntry<>(stringTemplate, this.convertToIndices(stringTemplate.variables()), executionCommandSource));
	}

	public CommandFunction<T> build(Identifier identifier) {
		return this.macroEntries != null
			? new MacroFunction<>(identifier, this.macroEntries, this.macroArguments)
			: new PlainTextFunction<>(identifier, this.plainEntries);
	}
}
