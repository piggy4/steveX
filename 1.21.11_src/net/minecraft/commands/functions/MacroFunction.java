package net.minecraft.commands.functions;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

public class MacroFunction<T extends ExecutionCommandSource<T>> implements CommandFunction<T> {
	private static final DecimalFormat DECIMAL_FORMAT = Util.make(
		new DecimalFormat("#", DecimalFormatSymbols.getInstance(Locale.ROOT)), decimalFormat -> decimalFormat.setMaximumFractionDigits(15)
	);
	private static final int MAX_CACHE_ENTRIES = 8;
	private final List<String> parameters;
	private final Object2ObjectLinkedOpenHashMap<List<String>, InstantiatedFunction<T>> cache = new Object2ObjectLinkedOpenHashMap<>(8, 0.25F);
	private final Identifier id;
	private final List<MacroFunction.Entry<T>> entries;

	public MacroFunction(Identifier identifier, List<MacroFunction.Entry<T>> list, List<String> list2) {
		this.id = identifier;
		this.entries = list;
		this.parameters = list2;
	}

	@Override
	public Identifier id() {
		return this.id;
	}

	@Override
	public InstantiatedFunction<T> instantiate(@Nullable CompoundTag compoundTag, CommandDispatcher<T> commandDispatcher) throws FunctionInstantiationException {
		if (compoundTag == null) {
			throw new FunctionInstantiationException(Component.translatable("commands.function.error.missing_arguments", Component.translationArg(this.id())));
		}

		List<String> list = new ArrayList<>(this.parameters.size());

		for (String string : this.parameters) {
			Tag tag = compoundTag.get(string);
			if (tag == null) {
				throw new FunctionInstantiationException(Component.translatable("commands.function.error.missing_argument", Component.translationArg(this.id()), string));
			}

			list.add(stringify(tag));
		}

		InstantiatedFunction<T> instantiatedFunction = this.cache.getAndMoveToLast(list);
		if (instantiatedFunction != null) {
			return instantiatedFunction;
		}

		if (this.cache.size() >= 8) {
			this.cache.removeFirst();
		}

		InstantiatedFunction<T> instantiatedFunction2 = this.substituteAndParse(this.parameters, list, commandDispatcher);
		this.cache.put(list, instantiatedFunction2);
		return instantiatedFunction2;
	}

	private static String stringify(Tag tag) {
		return switch (tag) {
			case FloatTag(float f) -> DECIMAL_FORMAT.format(f);
			case DoubleTag(double d) -> DECIMAL_FORMAT.format(d);
			case ByteTag(byte b) -> String.valueOf(b);
			case ShortTag(short s) -> String.valueOf(s);
			case LongTag(long l) -> String.valueOf(l);
			case StringTag(String string) -> string;
			default -> tag.toString();
		};
	}

	private static void lookupValues(List<String> list, IntList intList, List<String> list2) {
		list2.clear();
		intList.forEach(i -> list2.add(list.get(i)));
	}

	private InstantiatedFunction<T> substituteAndParse(List<String> list, List<String> list2, CommandDispatcher<T> commandDispatcher) throws FunctionInstantiationException {
		List<UnboundEntryAction<T>> list3 = new ArrayList<>(this.entries.size());
		List<String> list4 = new ArrayList<>(list2.size());

		for (MacroFunction.Entry<T> entry : this.entries) {
			lookupValues(list2, entry.parameters(), list4);
			list3.add(entry.instantiate(list4, commandDispatcher, this.id));
		}

		return new PlainTextFunction<>(this.id().withPath(string -> string + "/" + list.hashCode()), list3);
	}

	interface Entry<T> {
		IntList parameters();

		UnboundEntryAction<T> instantiate(List<String> list, CommandDispatcher<T> commandDispatcher, Identifier identifier) throws FunctionInstantiationException;
	}

	static class MacroEntry<T extends ExecutionCommandSource<T>> implements MacroFunction.Entry<T> {
		private final StringTemplate template;
		private final IntList parameters;
		private final T compilationContext;

		public MacroEntry(StringTemplate stringTemplate, IntList intList, T executionCommandSource) {
			this.template = stringTemplate;
			this.parameters = intList;
			this.compilationContext = executionCommandSource;
		}

		@Override
		public IntList parameters() {
			return this.parameters;
		}

		@Override
		public UnboundEntryAction<T> instantiate(List<String> list, CommandDispatcher<T> commandDispatcher, Identifier identifier) throws FunctionInstantiationException {
			String string = this.template.substitute(list);

			try {
				return CommandFunction.parseCommand(commandDispatcher, this.compilationContext, new StringReader(string));
			} catch (CommandSyntaxException commandSyntaxException) {
				throw new FunctionInstantiationException(
					Component.translatable("commands.function.error.parse", Component.translationArg(identifier), string, commandSyntaxException.getMessage())
				);
			}
		}
	}

	static class PlainTextEntry<T> implements MacroFunction.Entry<T> {
		private final UnboundEntryAction<T> compiledAction;

		public PlainTextEntry(UnboundEntryAction<T> unboundEntryAction) {
			this.compiledAction = unboundEntryAction;
		}

		@Override
		public IntList parameters() {
			return IntLists.emptyList();
		}

		@Override
		public UnboundEntryAction<T> instantiate(List<String> list, CommandDispatcher<T> commandDispatcher, Identifier identifier) {
			return this.compiledAction;
		}
	}
}
