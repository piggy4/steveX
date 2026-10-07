package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface CommandArgumentParser<T> {
	T parseForCommands(StringReader stringReader) throws CommandSyntaxException;

	CompletableFuture<Suggestions> parseForSuggestions(SuggestionsBuilder suggestionsBuilder);

	default <S> CommandArgumentParser<S> mapResult(Function<T, S> function) {
		return new CommandArgumentParser<S>() {
			@Override
			public S parseForCommands(StringReader stringReader) throws CommandSyntaxException {
				return function.apply((T)CommandArgumentParser.this.parseForCommands(stringReader));
			}

			@Override
			public CompletableFuture<Suggestions> parseForSuggestions(SuggestionsBuilder suggestionsBuilder) {
				return CommandArgumentParser.this.parseForSuggestions(suggestionsBuilder);
			}
		};
	}

	default <T, O> CommandArgumentParser<T> withCodec(
		DynamicOps<O> dynamicOps, CommandArgumentParser<O> commandArgumentParser, Codec<T> codec, DynamicCommandExceptionType dynamicCommandExceptionType
	) {
		return new CommandArgumentParser<T>() {
			@Override
			public T parseForCommands(StringReader stringReader) throws CommandSyntaxException {
				int i = stringReader.getCursor();
				O object = commandArgumentParser.parseForCommands(stringReader);
				DataResult<T> dataResult = codec.parse(dynamicOps, object);
				return dataResult.getOrThrow(string -> {
					stringReader.setCursor(i);
					return dynamicCommandExceptionType.createWithContext(stringReader, string);
				});
			}

			@Override
			public CompletableFuture<Suggestions> parseForSuggestions(SuggestionsBuilder suggestionsBuilder) {
				return CommandArgumentParser.this.parseForSuggestions(suggestionsBuilder);
			}
		};
	}
}
