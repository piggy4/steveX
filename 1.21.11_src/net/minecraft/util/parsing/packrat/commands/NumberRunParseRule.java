package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.util.parsing.packrat.DelayedException;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Rule;
import org.jspecify.annotations.Nullable;

public abstract class NumberRunParseRule implements Rule<StringReader, String> {
	private final DelayedException<CommandSyntaxException> noValueError;
	private final DelayedException<CommandSyntaxException> underscoreNotAllowedError;

	public NumberRunParseRule(DelayedException<CommandSyntaxException> delayedException, DelayedException<CommandSyntaxException> delayedException2) {
		this.noValueError = delayedException;
		this.underscoreNotAllowedError = delayedException2;
	}

	public @Nullable String parse(ParseState<StringReader> parseState) {
		StringReader stringReader = parseState.input();
		stringReader.skipWhitespace();
		String string = stringReader.getString();
		int i = stringReader.getCursor();
		int j = i;

		while (j < string.length() && this.isAccepted(string.charAt(j))) {
			j++;
		}

		int k = j - i;
		if (k == 0) {
			parseState.errorCollector().store(parseState.mark(), this.noValueError);
			return null;
		} else if (string.charAt(i) != '_' && string.charAt(j - 1) != '_') {
			stringReader.setCursor(j);
			return string.substring(i, j);
		} else {
			parseState.errorCollector().store(parseState.mark(), this.underscoreNotAllowedError);
			return null;
		}
	}

	protected abstract boolean isAccepted(char c);
}
