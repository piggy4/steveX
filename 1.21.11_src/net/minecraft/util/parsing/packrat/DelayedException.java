package net.minecraft.util.parsing.packrat;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.util.parsing.packrat.commands.StringReaderTerms;

public interface DelayedException<T extends Exception> {
	T create(String string, int i);

	static DelayedException<CommandSyntaxException> create(SimpleCommandExceptionType simpleCommandExceptionType) {
		return (string, i) -> simpleCommandExceptionType.createWithContext(StringReaderTerms.createReader(string, i));
	}

	static DelayedException<CommandSyntaxException> create(DynamicCommandExceptionType dynamicCommandExceptionType, String string) {
		return (string2, i) -> dynamicCommandExceptionType.createWithContext(StringReaderTerms.createReader(string2, i), string);
	}
}
