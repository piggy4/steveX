package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

public class HexColorArgument implements ArgumentType<Integer> {
	private static final Collection<String> EXAMPLES = Arrays.asList("F00", "FF0000");
	public static final DynamicCommandExceptionType ERROR_INVALID_HEX = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("argument.hexcolor.invalid", object)
	);

	private HexColorArgument() {
	}

	public static HexColorArgument hexColor() {
		return new HexColorArgument();
	}

	public static Integer getHexColor(CommandContext<CommandSourceStack> commandContext, String string) {
		return commandContext.getArgument(string, Integer.class);
	}

	public Integer parse(StringReader stringReader) throws CommandSyntaxException {
		String string = stringReader.readUnquotedString();

		return switch (string.length()) {
			case 3 -> ARGB.color(
				duplicateDigit(Integer.parseInt(string, 0, 1, 16)), duplicateDigit(Integer.parseInt(string, 1, 2, 16)), duplicateDigit(Integer.parseInt(string, 2, 3, 16))
			);
			case 6 -> ARGB.color(Integer.parseInt(string, 0, 2, 16), Integer.parseInt(string, 2, 4, 16), Integer.parseInt(string, 4, 6, 16));
			default -> throw ERROR_INVALID_HEX.createWithContext(stringReader, string);
		};
	}

	private static int duplicateDigit(int i) {
		return i * 17;
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> commandContext, SuggestionsBuilder suggestionsBuilder) {
		return SharedSuggestionProvider.suggest(EXAMPLES, suggestionsBuilder);
	}

	@Override
	public Collection<String> getExamples() {
		return EXAMPLES;
	}
}
