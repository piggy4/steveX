package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.util.parsing.packrat.DelayedException;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Rule;
import org.jspecify.annotations.Nullable;

public abstract class GreedyPredicateParseRule implements Rule<StringReader, String> {
	private final int minSize;
	private final int maxSize;
	private final DelayedException<CommandSyntaxException> error;

	public GreedyPredicateParseRule(int i, DelayedException<CommandSyntaxException> delayedException) {
		this(i, Integer.MAX_VALUE, delayedException);
	}

	public GreedyPredicateParseRule(int i, int j, DelayedException<CommandSyntaxException> delayedException) {
		this.minSize = i;
		this.maxSize = j;
		this.error = delayedException;
	}

	public @Nullable String parse(ParseState<StringReader> parseState) {
		StringReader stringReader = parseState.input();
		String string = stringReader.getString();
		int i = stringReader.getCursor();
		int j = i;

		while (j < string.length() && this.isAccepted(string.charAt(j)) && j - i < this.maxSize) {
			j++;
		}

		int k = j - i;
		if (k < this.minSize) {
			parseState.errorCollector().store(parseState.mark(), this.error);
			return null;
		} else {
			stringReader.setCursor(j);
			return string.substring(i, j);
		}
	}

	protected abstract boolean isAccepted(char c);
}
