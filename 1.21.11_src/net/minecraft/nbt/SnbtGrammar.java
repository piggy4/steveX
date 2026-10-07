package net.minecraft.nbt;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.primitives.UnsignedBytes;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteList;
import it.unimi.dsi.fastutil.chars.CharList;
import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import net.minecraft.network.chat.Component;
import net.minecraft.util.parsing.packrat.Atom;
import net.minecraft.util.parsing.packrat.DelayedException;
import net.minecraft.util.parsing.packrat.Dictionary;
import net.minecraft.util.parsing.packrat.NamedRule;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Scope;
import net.minecraft.util.parsing.packrat.Term;
import net.minecraft.util.parsing.packrat.commands.Grammar;
import net.minecraft.util.parsing.packrat.commands.GreedyPatternParseRule;
import net.minecraft.util.parsing.packrat.commands.GreedyPredicateParseRule;
import net.minecraft.util.parsing.packrat.commands.NumberRunParseRule;
import net.minecraft.util.parsing.packrat.commands.StringReaderTerms;
import net.minecraft.util.parsing.packrat.commands.UnquotedStringParseRule;
import org.jspecify.annotations.Nullable;

public class SnbtGrammar {
	private static final DynamicCommandExceptionType ERROR_NUMBER_PARSE_FAILURE = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("snbt.parser.number_parse_failure", object)
	);
	static final DynamicCommandExceptionType ERROR_EXPECTED_HEX_ESCAPE = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("snbt.parser.expected_hex_escape", object)
	);
	private static final DynamicCommandExceptionType ERROR_INVALID_CODEPOINT = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("snbt.parser.invalid_codepoint", object)
	);
	private static final DynamicCommandExceptionType ERROR_NO_SUCH_OPERATION = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("snbt.parser.no_such_operation", object)
	);
	static final DelayedException<CommandSyntaxException> ERROR_EXPECTED_INTEGER_TYPE = DelayedException.create(
		new SimpleCommandExceptionType(Component.translatable("snbt.parser.expected_integer_type"))
	);
	private static final DelayedException<CommandSyntaxException> ERROR_EXPECTED_FLOAT_TYPE = DelayedException.create(
		new SimpleCommandExceptionType(Component.translatable("snbt.parser.expected_float_type"))
	);
	static final DelayedException<CommandSyntaxException> ERROR_EXPECTED_NON_NEGATIVE_NUMBER = DelayedException.create(
		new SimpleCommandExceptionType(Component.translatable("snbt.parser.expected_non_negative_number"))
	);
	private static final DelayedException<CommandSyntaxException> ERROR_INVALID_CHARACTER_NAME = DelayedException.create(
		new SimpleCommandExceptionType(Component.translatable("snbt.parser.invalid_character_name"))
	);
	static final DelayedException<CommandSyntaxException> ERROR_INVALID_ARRAY_ELEMENT_TYPE = DelayedException.create(
		new SimpleCommandExceptionType(Component.translatable("snbt.parser.invalid_array_element_type"))
	);
	private static final DelayedException<CommandSyntaxException> ERROR_INVALID_UNQUOTED_START = DelayedException.create(
		new SimpleCommandExceptionType(Component.translatable("snbt.parser.invalid_unquoted_start"))
	);
	private static final DelayedException<CommandSyntaxException> ERROR_EXPECTED_UNQUOTED_STRING = DelayedException.create(
		new SimpleCommandExceptionType(Component.translatable("snbt.parser.expected_unquoted_string"))
	);
	private static final DelayedException<CommandSyntaxException> ERROR_INVALID_STRING_CONTENTS = DelayedException.create(
		new SimpleCommandExceptionType(Component.translatable("snbt.parser.invalid_string_contents"))
	);
	private static final DelayedException<CommandSyntaxException> ERROR_EXPECTED_BINARY_NUMERAL = DelayedException.create(
		new SimpleCommandExceptionType(Component.translatable("snbt.parser.expected_binary_numeral"))
	);
	private static final DelayedException<CommandSyntaxException> ERROR_UNDESCORE_NOT_ALLOWED = DelayedException.create(
		new SimpleCommandExceptionType(Component.translatable("snbt.parser.underscore_not_allowed"))
	);
	private static final DelayedException<CommandSyntaxException> ERROR_EXPECTED_DECIMAL_NUMERAL = DelayedException.create(
		new SimpleCommandExceptionType(Component.translatable("snbt.parser.expected_decimal_numeral"))
	);
	private static final DelayedException<CommandSyntaxException> ERROR_EXPECTED_HEX_NUMERAL = DelayedException.create(
		new SimpleCommandExceptionType(Component.translatable("snbt.parser.expected_hex_numeral"))
	);
	private static final DelayedException<CommandSyntaxException> ERROR_EMPTY_KEY = DelayedException.create(
		new SimpleCommandExceptionType(Component.translatable("snbt.parser.empty_key"))
	);
	private static final DelayedException<CommandSyntaxException> ERROR_LEADING_ZERO_NOT_ALLOWED = DelayedException.create(
		new SimpleCommandExceptionType(Component.translatable("snbt.parser.leading_zero_not_allowed"))
	);
	private static final DelayedException<CommandSyntaxException> ERROR_INFINITY_NOT_ALLOWED = DelayedException.create(
		new SimpleCommandExceptionType(Component.translatable("snbt.parser.infinity_not_allowed"))
	);
	private static final HexFormat HEX_ESCAPE = HexFormat.of().withUpperCase();
	private static final NumberRunParseRule BINARY_NUMERAL = new NumberRunParseRule(ERROR_EXPECTED_BINARY_NUMERAL, ERROR_UNDESCORE_NOT_ALLOWED) {
		@Override
		protected boolean isAccepted(char c) {
			return switch (c) {
				case '0', '1', '_' -> true;
				default -> false;
			};
		}
	};
	private static final NumberRunParseRule DECIMAL_NUMERAL = new NumberRunParseRule(ERROR_EXPECTED_DECIMAL_NUMERAL, ERROR_UNDESCORE_NOT_ALLOWED) {
		@Override
		protected boolean isAccepted(char c) {
			return switch (c) {
				case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '_' -> true;
				default -> false;
			};
		}
	};
	private static final NumberRunParseRule HEX_NUMERAL = new NumberRunParseRule(ERROR_EXPECTED_HEX_NUMERAL, ERROR_UNDESCORE_NOT_ALLOWED) {
		@Override
		protected boolean isAccepted(char c) {
			return switch (c) {
				case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F', '_', 'a', 'b', 'c', 'd', 'e', 'f' -> true;
				default -> false;
			};
		}
	};
	private static final GreedyPredicateParseRule PLAIN_STRING_CHUNK = new GreedyPredicateParseRule(1, ERROR_INVALID_STRING_CONTENTS) {
		@Override
		protected boolean isAccepted(char c) {
			return switch (c) {
				case '"', '\'', '\\' -> false;
				default -> true;
			};
		}
	};
	private static final StringReaderTerms.TerminalCharacters NUMBER_LOOKEAHEAD = new StringReaderTerms.TerminalCharacters(CharList.of()) {
		@Override
		protected boolean isAccepted(char c) {
			return SnbtGrammar.canStartNumber(c);
		}
	};
	private static final Pattern UNICODE_NAME = Pattern.compile("[-a-zA-Z0-9 ]+");

	static DelayedException<CommandSyntaxException> createNumberParseError(NumberFormatException numberFormatException) {
		return DelayedException.create(ERROR_NUMBER_PARSE_FAILURE, numberFormatException.getMessage());
	}

	public static @Nullable String escapeControlCharacters(char c) {
		return switch (c) {
			case '\b' -> "b";
			case '\t' -> "t";
			case '\n' -> "n";
			default -> c < ' ' ? "x" + HEX_ESCAPE.toHexDigits((byte)c) : null;
			case '\f' -> "f";
			case '\r' -> "r";
		};
	}

	private static boolean isAllowedToStartUnquotedString(char c) {
		return !canStartNumber(c);
	}

	static boolean canStartNumber(char c) {
		return switch (c) {
			case '+', '-', '.', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> true;
			default -> false;
		};
	}

	static boolean needsUnderscoreRemoval(String string) {
		return string.indexOf(95) != -1;
	}

	private static void cleanAndAppend(StringBuilder stringBuilder, String string) {
		cleanAndAppend(stringBuilder, string, needsUnderscoreRemoval(string));
	}

	static void cleanAndAppend(StringBuilder stringBuilder, String string, boolean bl) {
		if (bl) {
			for (char c : string.toCharArray()) {
				if (c != '_') {
					stringBuilder.append(c);
				}
			}
		} else {
			stringBuilder.append(string);
		}
	}

	static short parseUnsignedShort(String string, int i) {
		int j = Integer.parseInt(string, i);
		if (j >> 16 == 0) {
			return (short)j;
		} else {
			throw new NumberFormatException("out of range: " + j);
		}
	}

	private static <T> @Nullable T createFloat(
		DynamicOps<T> dynamicOps,
		SnbtGrammar.Sign sign,
		@Nullable String string,
		@Nullable String string2,
		SnbtGrammar.@Nullable Signed<String> signed,
		SnbtGrammar.@Nullable TypeSuffix typeSuffix,
		ParseState<?> parseState
	) {
		StringBuilder stringBuilder = new StringBuilder();
		sign.append(stringBuilder);
		if (string != null) {
			cleanAndAppend(stringBuilder, string);
		}

		if (string2 != null) {
			stringBuilder.append('.');
			cleanAndAppend(stringBuilder, string2);
		}

		if (signed != null) {
			stringBuilder.append('e');
			signed.sign().append(stringBuilder);
			cleanAndAppend(stringBuilder, signed.value);
		}

		try {
			String string3 = stringBuilder.toString();

			return (T)(switch (typeSuffix) {
				case null -> (Object)convertDouble(dynamicOps, parseState, string3);
				case FLOAT -> (Object)convertFloat(dynamicOps, parseState, string3);
				case DOUBLE -> (Object)convertDouble(dynamicOps, parseState, string3);
				default -> {
					parseState.errorCollector().store(parseState.mark(), ERROR_EXPECTED_FLOAT_TYPE);
					yield null;
				}
			});
		} catch (NumberFormatException numberFormatException) {
			parseState.errorCollector().store(parseState.mark(), createNumberParseError(numberFormatException));
			return null;
		}
	}

	private static <T> @Nullable T convertFloat(DynamicOps<T> dynamicOps, ParseState<?> parseState, String string) {
		float f = Float.parseFloat(string);
		if (!Float.isFinite(f)) {
			parseState.errorCollector().store(parseState.mark(), ERROR_INFINITY_NOT_ALLOWED);
			return null;
		} else {
			return dynamicOps.createFloat(f);
		}
	}

	private static <T> @Nullable T convertDouble(DynamicOps<T> dynamicOps, ParseState<?> parseState, String string) {
		double d = Double.parseDouble(string);
		if (!Double.isFinite(d)) {
			parseState.errorCollector().store(parseState.mark(), ERROR_INFINITY_NOT_ALLOWED);
			return null;
		} else {
			return dynamicOps.createDouble(d);
		}
	}

	private static String joinList(List<String> list) {
		return switch (list.size()) {
			case 0 -> "";
			case 1 -> (String)list.getFirst();
			default -> String.join("", list);
		};
	}

	public static <T> Grammar<T> createParser(DynamicOps<T> dynamicOps) {
		T object = dynamicOps.createBoolean(true);
		T object2 = dynamicOps.createBoolean(false);
		T object3 = dynamicOps.emptyMap();
		T object4 = dynamicOps.emptyList();
		Dictionary<StringReader> dictionary = new Dictionary<>();
		Atom<SnbtGrammar.Sign> atom = Atom.of("sign");
		dictionary.put(
			atom,
			Term.alternative(
				Term.sequence(StringReaderTerms.character('+'), Term.marker(atom, SnbtGrammar.Sign.PLUS)),
				Term.sequence(StringReaderTerms.character('-'), Term.marker(atom, SnbtGrammar.Sign.MINUS))
			),
			scope -> scope.getOrThrow(atom)
		);
		Atom<SnbtGrammar.IntegerSuffix> atom2 = Atom.of("integer_suffix");
		dictionary.put(
			atom2,
			Term.alternative(
				Term.sequence(
					StringReaderTerms.characters('u', 'U'),
					Term.alternative(
						Term.sequence(
							StringReaderTerms.characters('b', 'B'),
							Term.marker(atom2, new SnbtGrammar.IntegerSuffix(SnbtGrammar.SignedPrefix.UNSIGNED, SnbtGrammar.TypeSuffix.BYTE))
						),
						Term.sequence(
							StringReaderTerms.characters('s', 'S'),
							Term.marker(atom2, new SnbtGrammar.IntegerSuffix(SnbtGrammar.SignedPrefix.UNSIGNED, SnbtGrammar.TypeSuffix.SHORT))
						),
						Term.sequence(
							StringReaderTerms.characters('i', 'I'), Term.marker(atom2, new SnbtGrammar.IntegerSuffix(SnbtGrammar.SignedPrefix.UNSIGNED, SnbtGrammar.TypeSuffix.INT))
						),
						Term.sequence(
							StringReaderTerms.characters('l', 'L'),
							Term.marker(atom2, new SnbtGrammar.IntegerSuffix(SnbtGrammar.SignedPrefix.UNSIGNED, SnbtGrammar.TypeSuffix.LONG))
						)
					)
				),
				Term.sequence(
					StringReaderTerms.characters('s', 'S'),
					Term.alternative(
						Term.sequence(
							StringReaderTerms.characters('b', 'B'), Term.marker(atom2, new SnbtGrammar.IntegerSuffix(SnbtGrammar.SignedPrefix.SIGNED, SnbtGrammar.TypeSuffix.BYTE))
						),
						Term.sequence(
							StringReaderTerms.characters('s', 'S'), Term.marker(atom2, new SnbtGrammar.IntegerSuffix(SnbtGrammar.SignedPrefix.SIGNED, SnbtGrammar.TypeSuffix.SHORT))
						),
						Term.sequence(
							StringReaderTerms.characters('i', 'I'), Term.marker(atom2, new SnbtGrammar.IntegerSuffix(SnbtGrammar.SignedPrefix.SIGNED, SnbtGrammar.TypeSuffix.INT))
						),
						Term.sequence(
							StringReaderTerms.characters('l', 'L'), Term.marker(atom2, new SnbtGrammar.IntegerSuffix(SnbtGrammar.SignedPrefix.SIGNED, SnbtGrammar.TypeSuffix.LONG))
						)
					)
				),
				Term.sequence(StringReaderTerms.characters('b', 'B'), Term.marker(atom2, new SnbtGrammar.IntegerSuffix(null, SnbtGrammar.TypeSuffix.BYTE))),
				Term.sequence(StringReaderTerms.characters('s', 'S'), Term.marker(atom2, new SnbtGrammar.IntegerSuffix(null, SnbtGrammar.TypeSuffix.SHORT))),
				Term.sequence(StringReaderTerms.characters('i', 'I'), Term.marker(atom2, new SnbtGrammar.IntegerSuffix(null, SnbtGrammar.TypeSuffix.INT))),
				Term.sequence(StringReaderTerms.characters('l', 'L'), Term.marker(atom2, new SnbtGrammar.IntegerSuffix(null, SnbtGrammar.TypeSuffix.LONG)))
			),
			scope -> scope.getOrThrow(atom2)
		);
		Atom<String> atom3 = Atom.of("binary_numeral");
		dictionary.put(atom3, BINARY_NUMERAL);
		Atom<String> atom4 = Atom.of("decimal_numeral");
		dictionary.put(atom4, DECIMAL_NUMERAL);
		Atom<String> atom5 = Atom.of("hex_numeral");
		dictionary.put(atom5, HEX_NUMERAL);
		Atom<SnbtGrammar.IntegerLiteral> atom6 = Atom.of("integer_literal");
		NamedRule<StringReader, SnbtGrammar.IntegerLiteral> namedRule = dictionary.put(
			atom6,
			Term.sequence(
				Term.optional(dictionary.named(atom)),
				Term.alternative(
					Term.sequence(
						StringReaderTerms.character('0'),
						Term.cut(),
						Term.alternative(
							Term.sequence(StringReaderTerms.characters('x', 'X'), Term.cut(), dictionary.named(atom5)),
							Term.sequence(StringReaderTerms.characters('b', 'B'), dictionary.named(atom3)),
							Term.sequence(dictionary.named(atom4), Term.cut(), Term.fail(ERROR_LEADING_ZERO_NOT_ALLOWED)),
							Term.marker(atom4, "0")
						)
					),
					dictionary.named(atom4)
				),
				Term.optional(dictionary.named(atom2))
			),
			scope -> {
				SnbtGrammar.IntegerSuffix integerSuffix = scope.getOrDefault(atom2, SnbtGrammar.IntegerSuffix.EMPTY);
				SnbtGrammar.Sign sign = scope.getOrDefault(atom, SnbtGrammar.Sign.PLUS);
				String string = scope.get(atom4);
				if (string != null) {
					return new SnbtGrammar.IntegerLiteral(sign, SnbtGrammar.Base.DECIMAL, string, integerSuffix);
				}

				String string2 = scope.get(atom5);
				if (string2 != null) {
					return new SnbtGrammar.IntegerLiteral(sign, SnbtGrammar.Base.HEX, string2, integerSuffix);
				}

				String string3 = scope.getOrThrow(atom3);
				return new SnbtGrammar.IntegerLiteral(sign, SnbtGrammar.Base.BINARY, string3, integerSuffix);
			}
		);
		Atom<SnbtGrammar.TypeSuffix> atom7 = Atom.of("float_type_suffix");
		dictionary.put(
			atom7,
			Term.alternative(
				Term.sequence(StringReaderTerms.characters('f', 'F'), Term.marker(atom7, SnbtGrammar.TypeSuffix.FLOAT)),
				Term.sequence(StringReaderTerms.characters('d', 'D'), Term.marker(atom7, SnbtGrammar.TypeSuffix.DOUBLE))
			),
			scope -> scope.getOrThrow(atom7)
		);
		Atom<SnbtGrammar.Signed<String>> atom8 = Atom.of("float_exponent_part");
		dictionary.put(
			atom8,
			Term.sequence(StringReaderTerms.characters('e', 'E'), Term.optional(dictionary.named(atom)), dictionary.named(atom4)),
			scope -> new SnbtGrammar.Signed<>(scope.getOrDefault(atom, SnbtGrammar.Sign.PLUS), scope.getOrThrow(atom4))
		);
		Atom<String> atom9 = Atom.of("float_whole_part");
		Atom<String> atom10 = Atom.of("float_fraction_part");
		Atom<T> atom11 = Atom.of("float_literal");
		dictionary.putComplex(
			atom11,
			Term.sequence(
				Term.optional(dictionary.named(atom)),
				Term.alternative(
					Term.sequence(
						dictionary.namedWithAlias(atom4, atom9),
						StringReaderTerms.character('.'),
						Term.cut(),
						Term.optional(dictionary.namedWithAlias(atom4, atom10)),
						Term.optional(dictionary.named(atom8)),
						Term.optional(dictionary.named(atom7))
					),
					Term.sequence(
						StringReaderTerms.character('.'),
						Term.cut(),
						dictionary.namedWithAlias(atom4, atom10),
						Term.optional(dictionary.named(atom8)),
						Term.optional(dictionary.named(atom7))
					),
					Term.sequence(dictionary.namedWithAlias(atom4, atom9), dictionary.named(atom8), Term.cut(), Term.optional(dictionary.named(atom7))),
					Term.sequence(dictionary.namedWithAlias(atom4, atom9), Term.optional(dictionary.named(atom8)), dictionary.named(atom7))
				)
			),
			parseState -> {
				Scope scope = parseState.scope();
				SnbtGrammar.Sign sign = scope.getOrDefault(atom, SnbtGrammar.Sign.PLUS);
				String string = scope.get(atom9);
				String string2 = scope.get(atom10);
				SnbtGrammar.Signed<String> signed = scope.get(atom8);
				SnbtGrammar.TypeSuffix typeSuffix = scope.get(atom7);
				return createFloat(dynamicOps, sign, string, string2, signed, typeSuffix, parseState);
			}
		);
		Atom<String> atom12 = Atom.of("string_hex_2");
		dictionary.put(atom12, new SnbtGrammar.SimpleHexLiteralParseRule(2));
		Atom<String> atom13 = Atom.of("string_hex_4");
		dictionary.put(atom13, new SnbtGrammar.SimpleHexLiteralParseRule(4));
		Atom<String> atom14 = Atom.of("string_hex_8");
		dictionary.put(atom14, new SnbtGrammar.SimpleHexLiteralParseRule(8));
		Atom<String> atom15 = Atom.of("string_unicode_name");
		dictionary.put(atom15, new GreedyPatternParseRule(UNICODE_NAME, ERROR_INVALID_CHARACTER_NAME));
		Atom<String> atom16 = Atom.of("string_escape_sequence");
		dictionary.putComplex(
			atom16,
			Term.alternative(
				Term.sequence(StringReaderTerms.character('b'), Term.marker(atom16, "\b")),
				Term.sequence(StringReaderTerms.character('s'), Term.marker(atom16, " ")),
				Term.sequence(StringReaderTerms.character('t'), Term.marker(atom16, "\t")),
				Term.sequence(StringReaderTerms.character('n'), Term.marker(atom16, "\n")),
				Term.sequence(StringReaderTerms.character('f'), Term.marker(atom16, "\f")),
				Term.sequence(StringReaderTerms.character('r'), Term.marker(atom16, "\r")),
				Term.sequence(StringReaderTerms.character('\\'), Term.marker(atom16, "\\")),
				Term.sequence(StringReaderTerms.character('\''), Term.marker(atom16, "'")),
				Term.sequence(StringReaderTerms.character('"'), Term.marker(atom16, "\"")),
				Term.sequence(StringReaderTerms.character('x'), dictionary.named(atom12)),
				Term.sequence(StringReaderTerms.character('u'), dictionary.named(atom13)),
				Term.sequence(StringReaderTerms.character('U'), dictionary.named(atom14)),
				Term.sequence(StringReaderTerms.character('N'), StringReaderTerms.character('{'), dictionary.named(atom15), StringReaderTerms.character('}'))
			),
			parseState -> {
				Scope scope = parseState.scope();
				String string = scope.getAny(atom16);
				if (string != null) {
					return string;
				}

				String string2 = scope.getAny(atom12, atom13, atom14);
				if (string2 != null) {
					int i = HexFormat.fromHexDigits(string2);
					if (!Character.isValidCodePoint(i)) {
						parseState.errorCollector().store(parseState.mark(), DelayedException.create(ERROR_INVALID_CODEPOINT, String.format(Locale.ROOT, "U+%08X", i)));
						return null;
					} else {
						return Character.toString(i);
					}
				} else {
					String string3 = scope.getOrThrow(atom15);

					int j;
					try {
						j = Character.codePointOf(string3);
					} catch (IllegalArgumentException illegalArgumentException) {
						parseState.errorCollector().store(parseState.mark(), ERROR_INVALID_CHARACTER_NAME);
						return null;
					}

					return Character.toString(j);
				}
			}
		);
		Atom<String> atom17 = Atom.of("string_plain_contents");
		dictionary.put(atom17, PLAIN_STRING_CHUNK);
		Atom<List<String>> atom18 = Atom.of("string_chunks");
		Atom<String> atom19 = Atom.of("string_contents");
		Atom<String> atom20 = Atom.of("single_quoted_string_chunk");
		NamedRule<StringReader, String> namedRule2 = dictionary.put(
			atom20,
			Term.alternative(
				dictionary.namedWithAlias(atom17, atom19),
				Term.sequence(StringReaderTerms.character('\\'), dictionary.namedWithAlias(atom16, atom19)),
				Term.sequence(StringReaderTerms.character('"'), Term.marker(atom19, "\""))
			),
			scope -> scope.getOrThrow(atom19)
		);
		Atom<String> atom21 = Atom.of("single_quoted_string_contents");
		dictionary.put(atom21, Term.repeated(namedRule2, atom18), scope -> joinList(scope.getOrThrow(atom18)));
		Atom<String> atom22 = Atom.of("double_quoted_string_chunk");
		NamedRule<StringReader, String> namedRule3 = dictionary.put(
			atom22,
			Term.alternative(
				dictionary.namedWithAlias(atom17, atom19),
				Term.sequence(StringReaderTerms.character('\\'), dictionary.namedWithAlias(atom16, atom19)),
				Term.sequence(StringReaderTerms.character('\''), Term.marker(atom19, "'"))
			),
			scope -> scope.getOrThrow(atom19)
		);
		Atom<String> atom23 = Atom.of("double_quoted_string_contents");
		dictionary.put(atom23, Term.repeated(namedRule3, atom18), scope -> joinList(scope.getOrThrow(atom18)));
		Atom<String> atom24 = Atom.of("quoted_string_literal");
		dictionary.put(
			atom24,
			Term.alternative(
				Term.sequence(StringReaderTerms.character('"'), Term.cut(), Term.optional(dictionary.namedWithAlias(atom23, atom19)), StringReaderTerms.character('"')),
				Term.sequence(StringReaderTerms.character('\''), Term.optional(dictionary.namedWithAlias(atom21, atom19)), StringReaderTerms.character('\''))
			),
			scope -> scope.getOrThrow(atom19)
		);
		Atom<String> atom25 = Atom.of("unquoted_string");
		dictionary.put(atom25, new UnquotedStringParseRule(1, ERROR_EXPECTED_UNQUOTED_STRING));
		Atom<T> atom26 = Atom.of("literal");
		Atom<List<T>> atom27 = Atom.of("arguments");
		dictionary.put(
			atom27, Term.repeatedWithTrailingSeparator(dictionary.forward(atom26), atom27, StringReaderTerms.character(',')), scope -> scope.getOrThrow(atom27)
		);
		Atom<T> atom28 = Atom.of("unquoted_string_or_builtin");
		dictionary.putComplex(
			atom28,
			Term.sequence(
				dictionary.named(atom25), Term.optional(Term.sequence(StringReaderTerms.character('('), dictionary.named(atom27), StringReaderTerms.character(')')))
			),
			parseState -> {
				Scope scope = parseState.scope();
				String string = scope.getOrThrow(atom25);
				if (!string.isEmpty() && isAllowedToStartUnquotedString(string.charAt(0))) {
					List<T> list = scope.get(atom27);
					if (list != null) {
						SnbtOperations.BuiltinKey builtinKey = new SnbtOperations.BuiltinKey(string, list.size());
						SnbtOperations.BuiltinOperation builtinOperation = SnbtOperations.BUILTIN_OPERATIONS.get(builtinKey);
						if (builtinOperation != null) {
							return builtinOperation.run(dynamicOps, list, parseState);
						}

						parseState.errorCollector().store(parseState.mark(), DelayedException.create(ERROR_NO_SUCH_OPERATION, builtinKey.toString()));
						return null;
					} else if (string.equalsIgnoreCase("true")) {
						return object;
					} else {
						return string.equalsIgnoreCase("false") ? object2 : dynamicOps.createString(string);
					}
				} else {
					parseState.errorCollector().store(parseState.mark(), SnbtOperations.BUILTIN_IDS, ERROR_INVALID_UNQUOTED_START);
					return null;
				}
			}
		);
		Atom<String> atom29 = Atom.of("map_key");
		dictionary.put(atom29, Term.alternative(dictionary.named(atom24), dictionary.named(atom25)), scope -> scope.getAnyOrThrow(atom24, atom25));
		Atom<Entry<String, T>> atom30 = Atom.of("map_entry");
		NamedRule<StringReader, Entry<String, T>> namedRule4 = dictionary.putComplex(
			atom30, Term.sequence(dictionary.named(atom29), StringReaderTerms.character(':'), dictionary.named(atom26)), parseState -> {
				Scope scope = parseState.scope();
				String string = scope.getOrThrow(atom29);
				if (string.isEmpty()) {
					parseState.errorCollector().store(parseState.mark(), ERROR_EMPTY_KEY);
					return null;
				} else {
					T objectx = scope.getOrThrow(atom26);
					return Map.entry(string, objectx);
				}
			}
		);
		Atom<List<Entry<String, T>>> atom31 = Atom.of("map_entries");
		dictionary.put(atom31, Term.repeatedWithTrailingSeparator(namedRule4, atom31, StringReaderTerms.character(',')), scope -> scope.getOrThrow(atom31));
		Atom<T> atom32 = Atom.of("map_literal");
		dictionary.put(atom32, Term.sequence(StringReaderTerms.character('{'), dictionary.named(atom31), StringReaderTerms.character('}')), scope -> {
			List<Entry<String, T>> list = scope.getOrThrow(atom31);
			if (list.isEmpty()) {
				return object3;
			}

			Builder<T, T> builder = ImmutableMap.builderWithExpectedSize(list.size());

			for (Entry<String, T> entry : list) {
				builder.put(dynamicOps.createString(entry.getKey()), entry.getValue());
			}

			return dynamicOps.createMap(builder.buildKeepingLast());
		});
		Atom<List<T>> atom33 = Atom.of("list_entries");
		dictionary.put(
			atom33, Term.repeatedWithTrailingSeparator(dictionary.forward(atom26), atom33, StringReaderTerms.character(',')), scope -> scope.getOrThrow(atom33)
		);
		Atom<SnbtGrammar.ArrayPrefix> atom34 = Atom.of("array_prefix");
		dictionary.put(
			atom34,
			Term.alternative(
				Term.sequence(StringReaderTerms.character('B'), Term.marker(atom34, SnbtGrammar.ArrayPrefix.BYTE)),
				Term.sequence(StringReaderTerms.character('L'), Term.marker(atom34, SnbtGrammar.ArrayPrefix.LONG)),
				Term.sequence(StringReaderTerms.character('I'), Term.marker(atom34, SnbtGrammar.ArrayPrefix.INT))
			),
			scope -> scope.getOrThrow(atom34)
		);
		Atom<List<SnbtGrammar.IntegerLiteral>> atom35 = Atom.of("int_array_entries");
		dictionary.put(atom35, Term.repeatedWithTrailingSeparator(namedRule, atom35, StringReaderTerms.character(',')), scope -> scope.getOrThrow(atom35));
		Atom<T> atom36 = Atom.of("list_literal");
		dictionary.putComplex(
			atom36,
			Term.sequence(
				StringReaderTerms.character('['),
				Term.alternative(Term.sequence(dictionary.named(atom34), StringReaderTerms.character(';'), dictionary.named(atom35)), dictionary.named(atom33)),
				StringReaderTerms.character(']')
			),
			parseState -> {
				Scope scope = parseState.scope();
				SnbtGrammar.ArrayPrefix arrayPrefix = scope.get(atom34);
				if (arrayPrefix != null) {
					List<SnbtGrammar.IntegerLiteral> list = scope.getOrThrow(atom35);
					return list.isEmpty() ? arrayPrefix.create(dynamicOps) : arrayPrefix.create(dynamicOps, list, parseState);
				} else {
					List<T> list = scope.getOrThrow(atom33);
					return list.isEmpty() ? object4 : dynamicOps.createList(list.stream());
				}
			}
		);
		NamedRule<StringReader, T> namedRule5 = dictionary.putComplex(
			atom26,
			Term.alternative(
				Term.sequence(Term.positiveLookahead(NUMBER_LOOKEAHEAD), Term.alternative(dictionary.namedWithAlias(atom11, atom26), dictionary.named(atom6))),
				Term.sequence(Term.positiveLookahead(StringReaderTerms.characters('"', '\'')), Term.cut(), dictionary.named(atom24)),
				Term.sequence(Term.positiveLookahead(StringReaderTerms.character('{')), Term.cut(), dictionary.namedWithAlias(atom32, atom26)),
				Term.sequence(Term.positiveLookahead(StringReaderTerms.character('[')), Term.cut(), dictionary.namedWithAlias(atom36, atom26)),
				dictionary.namedWithAlias(atom28, atom26)
			),
			parseState -> {
				Scope scope = parseState.scope();
				String string = scope.get(atom24);
				if (string != null) {
					return dynamicOps.createString(string);
				}

				SnbtGrammar.IntegerLiteral integerLiteral = scope.get(atom6);
				return integerLiteral != null ? integerLiteral.create(dynamicOps, parseState) : scope.getOrThrow(atom26);
			}
		);
		return new Grammar<>(dictionary, namedRule5);
	}

	enum ArrayPrefix {
		BYTE(SnbtGrammar.TypeSuffix.BYTE) {
			private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.wrap(new byte[0]);

			@Override
			public <T> T create(DynamicOps<T> dynamicOps) {
				return dynamicOps.createByteList(EMPTY_BUFFER);
			}

			@Override
			public <T> @Nullable T create(DynamicOps<T> dynamicOps, List<SnbtGrammar.IntegerLiteral> list, ParseState<?> parseState) {
				ByteList byteList = new ByteArrayList();

				for (SnbtGrammar.IntegerLiteral integerLiteral : list) {
					Number number = this.buildNumber(integerLiteral, parseState);
					if (number == null) {
						return null;
					}

					byteList.add(number.byteValue());
				}

				return dynamicOps.createByteList(ByteBuffer.wrap(byteList.toByteArray()));
			}
		},
		INT(SnbtGrammar.TypeSuffix.INT, SnbtGrammar.TypeSuffix.BYTE, SnbtGrammar.TypeSuffix.SHORT) {
			@Override
			public <T> T create(DynamicOps<T> dynamicOps) {
				return dynamicOps.createIntList(IntStream.empty());
			}

			@Override
			public <T> @Nullable T create(DynamicOps<T> dynamicOps, List<SnbtGrammar.IntegerLiteral> list, ParseState<?> parseState) {
				java.util.stream.IntStream.Builder builder = IntStream.builder();

				for (SnbtGrammar.IntegerLiteral integerLiteral : list) {
					Number number = this.buildNumber(integerLiteral, parseState);
					if (number == null) {
						return null;
					}

					builder.add(number.intValue());
				}

				return dynamicOps.createIntList(builder.build());
			}
		},
		LONG(SnbtGrammar.TypeSuffix.LONG, SnbtGrammar.TypeSuffix.BYTE, SnbtGrammar.TypeSuffix.SHORT, SnbtGrammar.TypeSuffix.INT) {
			@Override
			public <T> T create(DynamicOps<T> dynamicOps) {
				return dynamicOps.createLongList(LongStream.empty());
			}

			@Override
			public <T> @Nullable T create(DynamicOps<T> dynamicOps, List<SnbtGrammar.IntegerLiteral> list, ParseState<?> parseState) {
				java.util.stream.LongStream.Builder builder = LongStream.builder();

				for (SnbtGrammar.IntegerLiteral integerLiteral : list) {
					Number number = this.buildNumber(integerLiteral, parseState);
					if (number == null) {
						return null;
					}

					builder.add(number.longValue());
				}

				return dynamicOps.createLongList(builder.build());
			}
		};

		private final SnbtGrammar.TypeSuffix defaultType;
		private final Set<SnbtGrammar.TypeSuffix> additionalTypes;

		ArrayPrefix(final SnbtGrammar.TypeSuffix typeSuffix, final SnbtGrammar.TypeSuffix... typeSuffixs) {
			this.additionalTypes = Set.of(typeSuffixs);
			this.defaultType = typeSuffix;
		}

		public boolean isAllowed(SnbtGrammar.TypeSuffix typeSuffix) {
			return typeSuffix == this.defaultType || this.additionalTypes.contains(typeSuffix);
		}

		public abstract <T> T create(DynamicOps<T> dynamicOps);

		public abstract <T> @Nullable T create(DynamicOps<T> dynamicOps, List<SnbtGrammar.IntegerLiteral> list, ParseState<?> parseState);

		protected @Nullable Number buildNumber(SnbtGrammar.IntegerLiteral integerLiteral, ParseState<?> parseState) {
			SnbtGrammar.TypeSuffix typeSuffix = this.computeType(integerLiteral.suffix);
			if (typeSuffix == null) {
				parseState.errorCollector().store(parseState.mark(), SnbtGrammar.ERROR_INVALID_ARRAY_ELEMENT_TYPE);
				return null;
			} else {
				return (Number)integerLiteral.create(JavaOps.INSTANCE, typeSuffix, parseState);
			}
		}

		private SnbtGrammar.@Nullable TypeSuffix computeType(SnbtGrammar.IntegerSuffix integerSuffix) {
			SnbtGrammar.TypeSuffix typeSuffix = integerSuffix.type();
			if (typeSuffix == null) {
				return this.defaultType;
			} else {
				return !this.isAllowed(typeSuffix) ? null : typeSuffix;
			}
		}
	}

	enum Base {
		BINARY,
		DECIMAL,
		HEX;
	}

	record IntegerLiteral(SnbtGrammar.Sign sign, SnbtGrammar.Base base, String digits, SnbtGrammar.IntegerSuffix suffix) {

		private SnbtGrammar.SignedPrefix signedOrDefault() {
			if (this.suffix.signed != null) {
				return this.suffix.signed;
			}

			return switch (this.base) {
				case BINARY, HEX -> SnbtGrammar.SignedPrefix.UNSIGNED;
				case DECIMAL -> SnbtGrammar.SignedPrefix.SIGNED;
			};
		}

		private String cleanupDigits(SnbtGrammar.Sign sign) {
			boolean bl = SnbtGrammar.needsUnderscoreRemoval(this.digits);
			if (sign != SnbtGrammar.Sign.MINUS && !bl) {
				return this.digits;
			}

			StringBuilder stringBuilder = new StringBuilder();
			sign.append(stringBuilder);
			SnbtGrammar.cleanAndAppend(stringBuilder, this.digits, bl);
			return stringBuilder.toString();
		}

		public <T> @Nullable T create(DynamicOps<T> dynamicOps, ParseState<?> parseState) {
			return this.create(dynamicOps, Objects.requireNonNullElse(this.suffix.type, SnbtGrammar.TypeSuffix.INT), parseState);
		}

		public <T> @Nullable T create(DynamicOps<T> dynamicOps, SnbtGrammar.TypeSuffix typeSuffix, ParseState<?> parseState) {
			boolean bl = this.signedOrDefault() == SnbtGrammar.SignedPrefix.SIGNED;
			if (!bl && this.sign == SnbtGrammar.Sign.MINUS) {
				parseState.errorCollector().store(parseState.mark(), SnbtGrammar.ERROR_EXPECTED_NON_NEGATIVE_NUMBER);
				return null;
			}

			String string = this.cleanupDigits(this.sign);

			int i = switch (this.base) {
				case BINARY -> 2;
				case DECIMAL -> 10;
				case HEX -> 16;
			};

			try {
				if (bl) {
					return (T)(switch (typeSuffix) {
						case BYTE -> (Object)dynamicOps.createByte(Byte.parseByte(string, i));
						case SHORT -> (Object)dynamicOps.createShort(Short.parseShort(string, i));
						case INT -> (Object)dynamicOps.createInt(Integer.parseInt(string, i));
						case LONG -> (Object)dynamicOps.createLong(Long.parseLong(string, i));
						default -> {
							parseState.errorCollector().store(parseState.mark(), SnbtGrammar.ERROR_EXPECTED_INTEGER_TYPE);
							yield null;
						}
					});
				} else {
					return (T)(switch (typeSuffix) {
						case BYTE -> (Object)dynamicOps.createByte(UnsignedBytes.parseUnsignedByte(string, i));
						case SHORT -> (Object)dynamicOps.createShort(SnbtGrammar.parseUnsignedShort(string, i));
						case INT -> (Object)dynamicOps.createInt(Integer.parseUnsignedInt(string, i));
						case LONG -> (Object)dynamicOps.createLong(Long.parseUnsignedLong(string, i));
						default -> {
							parseState.errorCollector().store(parseState.mark(), SnbtGrammar.ERROR_EXPECTED_INTEGER_TYPE);
							yield null;
						}
					});
				}
			} catch (NumberFormatException numberFormatException) {
				parseState.errorCollector().store(parseState.mark(), SnbtGrammar.createNumberParseError(numberFormatException));
				return null;
			}
		}
	}

	record IntegerSuffix(SnbtGrammar.@Nullable SignedPrefix signed, SnbtGrammar.@Nullable TypeSuffix type) {
		public static final SnbtGrammar.IntegerSuffix EMPTY = new SnbtGrammar.IntegerSuffix(null, null);
	}

	enum Sign {
		PLUS,
		MINUS;

		public void append(StringBuilder stringBuilder) {
			if (this == MINUS) {
				stringBuilder.append("-");
			}
		}
	}

	record Signed<T>(SnbtGrammar.Sign sign, T value) {
	}

	enum SignedPrefix {
		SIGNED,
		UNSIGNED;
	}

	static class SimpleHexLiteralParseRule extends GreedyPredicateParseRule {
		public SimpleHexLiteralParseRule(int i) {
			super(i, i, DelayedException.create(SnbtGrammar.ERROR_EXPECTED_HEX_ESCAPE, String.valueOf(i)));
		}

		@Override
		protected boolean isAccepted(char c) {
			return switch (c) {
				case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F', 'a', 'b', 'c', 'd', 'e', 'f' -> true;
				default -> false;
			};
		}
	}

	enum TypeSuffix {
		FLOAT,
		DOUBLE,
		BYTE,
		SHORT,
		INT,
		LONG;
	}
}
