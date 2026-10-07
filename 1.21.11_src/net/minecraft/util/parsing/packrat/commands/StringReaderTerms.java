package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.chars.CharList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.util.parsing.packrat.Control;
import net.minecraft.util.parsing.packrat.DelayedException;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Scope;
import net.minecraft.util.parsing.packrat.SuggestionSupplier;
import net.minecraft.util.parsing.packrat.Term;

public interface StringReaderTerms {
	static Term<StringReader> word(String string) {
		return new StringReaderTerms.TerminalWord(string);
	}

	static Term<StringReader> character(char c) {
		return new StringReaderTerms.TerminalCharacters(CharList.of(c)) {
			@Override
			protected boolean isAccepted(char c) {
				return c == c;
			}
		};
	}

	static Term<StringReader> characters(char c, char d) {
		return new StringReaderTerms.TerminalCharacters(CharList.of(c, d)) {
			@Override
			protected boolean isAccepted(char c) {
				return c == c || c == d;
			}
		};
	}

	static StringReader createReader(String string, int i) {
		StringReader stringReader = new StringReader(string);
		stringReader.setCursor(i);
		return stringReader;
	}

	abstract class TerminalCharacters implements Term<StringReader> {
		private final DelayedException<CommandSyntaxException> error;
		private final SuggestionSupplier<StringReader> suggestions;

		public TerminalCharacters(CharList charList) {
			String string = charList.intStream().mapToObj(Character::toString).collect(Collectors.joining("|"));
			this.error = DelayedException.create(CommandSyntaxException.BUILT_IN_EXCEPTIONS.literalIncorrect(), string);
			this.suggestions = parseState -> charList.intStream().mapToObj(Character::toString);
		}

		@Override
		public boolean parse(ParseState<StringReader> parseState, Scope scope, Control control) {
			parseState.input().skipWhitespace();
			int i = parseState.mark();
			if (parseState.input().canRead() && this.isAccepted(parseState.input().read())) {
				return true;
			}

			parseState.errorCollector().store(i, this.suggestions, this.error);
			return false;
		}

		protected abstract boolean isAccepted(char c);
	}

	final class TerminalWord implements Term<StringReader> {
		private final String value;
		private final DelayedException<CommandSyntaxException> error;
		private final SuggestionSupplier<StringReader> suggestions;

		public TerminalWord(String string) {
			this.value = string;
			this.error = DelayedException.create(CommandSyntaxException.BUILT_IN_EXCEPTIONS.literalIncorrect(), string);
			this.suggestions = parseState -> Stream.of(string);
		}

		@Override
		public boolean parse(ParseState<StringReader> parseState, Scope scope, Control control) {
			parseState.input().skipWhitespace();
			int i = parseState.mark();
			String string = parseState.input().readUnquotedString();
			if (!string.equals(this.value)) {
				parseState.errorCollector().store(i, this.suggestions, this.error);
				return false;
			} else {
				return true;
			}
		}

		@Override
		public String toString() {
			return "terminal[" + this.value + "]";
		}
	}
}
