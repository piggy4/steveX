package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.resources.Identifier;
import net.minecraft.util.parsing.packrat.DelayedException;
import net.minecraft.util.parsing.packrat.NamedRule;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Rule;
import org.jspecify.annotations.Nullable;

public abstract class ResourceLookupRule<C, V> implements Rule<StringReader, V>, ResourceSuggestion {
	private final NamedRule<StringReader, Identifier> idParser;
	protected final C context;
	private final DelayedException<CommandSyntaxException> error;

	protected ResourceLookupRule(NamedRule<StringReader, Identifier> namedRule, C object) {
		this.idParser = namedRule;
		this.context = object;
		this.error = DelayedException.create(Identifier.ERROR_INVALID);
	}

	@Override
	public @Nullable V parse(ParseState<StringReader> parseState) {
		parseState.input().skipWhitespace();
		int i = parseState.mark();
		Identifier identifier = parseState.parse(this.idParser);
		if (identifier != null) {
			try {
				return this.validateElement(parseState.input(), identifier);
			} catch (Exception exception) {
				parseState.errorCollector().store(i, this, exception);
				return null;
			}
		} else {
			parseState.errorCollector().store(i, this, this.error);
			return null;
		}
	}

	protected abstract V validateElement(ImmutableStringReader immutableStringReader, Identifier identifier) throws Exception;
}
