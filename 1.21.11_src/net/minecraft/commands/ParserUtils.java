package net.minecraft.commands;

import com.mojang.brigadier.StringReader;
import net.minecraft.CharPredicate;

public class ParserUtils {
	public static String readWhile(StringReader stringReader, CharPredicate charPredicate) {
		int i = stringReader.getCursor();

		while (stringReader.canRead() && charPredicate.test(stringReader.peek())) {
			stringReader.skip();
		}

		return stringReader.getString().substring(i, stringReader.getCursor());
	}
}
