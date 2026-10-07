package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.util.parsing.packrat.DelayedException;
import net.minecraft.util.parsing.packrat.Dictionary;
import net.minecraft.util.parsing.packrat.ErrorCollector;
import net.minecraft.util.parsing.packrat.ErrorEntry;
import net.minecraft.util.parsing.packrat.NamedRule;
import net.minecraft.util.parsing.packrat.ParseState;

public record Grammar<T>(Dictionary<StringReader> rules, NamedRule<StringReader, T> top) implements CommandArgumentParser<T> {
	public Grammar {
		rules.checkAllBound();
	}

	public Optional<T> parse(ParseState<StringReader> parseState) {
		return parseState.parseTopRule(this.top);
	}

	@Override
	public T parseForCommands(StringReader stringReader) throws CommandSyntaxException {
		ErrorCollector.LongestOnly<StringReader> longestOnly = new ErrorCollector.LongestOnly<>();
		StringReaderParserState stringReaderParserState = new StringReaderParserState(longestOnly, stringReader);
		Optional<T> optional = this.parse(stringReaderParserState);
		if (optional.isPresent()) {
			return optional.get();
		}

		List<ErrorEntry<StringReader>> list = longestOnly.entries();
		List<Exception> list2 = list.stream().<Exception>mapMulti((errorEntry, consumer) -> {
			if (errorEntry.reason() instanceof DelayedException<?> delayedException) {
				consumer.accept(delayedException.create(stringReader.getString(), errorEntry.cursor()));
			} else if (errorEntry.reason() instanceof Exception exceptionx) {
				consumer.accept(exceptionx);
			}
		}).toList();

		for (Exception exception : list2) {
			if (exception instanceof CommandSyntaxException commandSyntaxException) {
				throw commandSyntaxException;
			}
		}

		if (list2.size() == 1 && list2.get(0) instanceof RuntimeException runtimeException) {
			throw runtimeException;
		} else {
			throw new IllegalStateException("Failed to parse: " + list.stream().map(ErrorEntry::toString).collect(Collectors.joining(", ")));
		}
	}

	@Override
	public CompletableFuture<Suggestions> parseForSuggestions(SuggestionsBuilder suggestionsBuilder) {
		StringReader stringReader = new StringReader(suggestionsBuilder.getInput());
		stringReader.setCursor(suggestionsBuilder.getStart());
		ErrorCollector.LongestOnly<StringReader> longestOnly = new ErrorCollector.LongestOnly<>();
		StringReaderParserState stringReaderParserState = new StringReaderParserState(longestOnly, stringReader);
		this.parse(stringReaderParserState);
		List<ErrorEntry<StringReader>> list = longestOnly.entries();
		if (list.isEmpty()) {
			return suggestionsBuilder.buildFuture();
		}

		SuggestionsBuilder suggestionsBuilder2 = suggestionsBuilder.createOffset(longestOnly.cursor());

		for (ErrorEntry<StringReader> errorEntry : list) {
			if (errorEntry.suggestions() instanceof ResourceSuggestion resourceSuggestion) {
				SharedSuggestionProvider.suggestResource(resourceSuggestion.possibleResources(), suggestionsBuilder2);
			} else {
				SharedSuggestionProvider.suggest(errorEntry.suggestions().possibleValues(stringReaderParserState), suggestionsBuilder2);
			}
		}

		return suggestionsBuilder2.buildFuture();
	}
}
