package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Optional;

public record StringTag(String value) implements PrimitiveTag {
	private static final int SELF_SIZE_IN_BYTES = 36;
	public static final TagType<StringTag> TYPE = new TagType.VariableSize<StringTag>() {
		public StringTag load(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
			return StringTag.valueOf(readAccounted(dataInput, nbtAccounter));
		}

		@Override
		public StreamTagVisitor.ValueResult parse(DataInput dataInput, StreamTagVisitor streamTagVisitor, NbtAccounter nbtAccounter) throws IOException {
			return streamTagVisitor.visit(readAccounted(dataInput, nbtAccounter));
		}

		private static String readAccounted(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
			nbtAccounter.accountBytes(36L);
			String string = dataInput.readUTF();
			nbtAccounter.accountBytes(2L, string.length());
			return string;
		}

		@Override
		public void skip(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
			StringTag.skipString(dataInput);
		}

		@Override
		public String getName() {
			return "STRING";
		}

		@Override
		public String getPrettyName() {
			return "TAG_String";
		}
	};
	private static final StringTag EMPTY = new StringTag("");
	private static final char DOUBLE_QUOTE = '"';
	private static final char SINGLE_QUOTE = '\'';
	private static final char ESCAPE = '\\';
	private static final char NOT_SET = '\u0000';

	@Deprecated(forRemoval = true)
	public StringTag {
	}

	public static void skipString(DataInput dataInput) throws IOException {
		dataInput.skipBytes(dataInput.readUnsignedShort());
	}

	public static StringTag valueOf(String string) {
		return string.isEmpty() ? EMPTY : new StringTag(string);
	}

	@Override
	public void write(DataOutput dataOutput) throws IOException {
		dataOutput.writeUTF(this.value);
	}

	@Override
	public int sizeInBytes() {
		return 36 + 2 * this.value.length();
	}

	@Override
	public byte getId() {
		return 8;
	}

	@Override
	public TagType<StringTag> getType() {
		return TYPE;
	}

	@Override
	public String toString() {
		StringTagVisitor stringTagVisitor = new StringTagVisitor();
		stringTagVisitor.visitString(this);
		return stringTagVisitor.build();
	}

	public StringTag copy() {
		return this;
	}

	@Override
	public Optional<String> asString() {
		return Optional.of(this.value);
	}

	@Override
	public void accept(TagVisitor tagVisitor) {
		tagVisitor.visitString(this);
	}

	public static String quoteAndEscape(String string) {
		StringBuilder stringBuilder = new StringBuilder();
		quoteAndEscape(string, stringBuilder);
		return stringBuilder.toString();
	}

	public static void quoteAndEscape(String string, StringBuilder stringBuilder) {
		int i = stringBuilder.length();
		stringBuilder.append(' ');
		char c = 0;

		for (int j = 0; j < string.length(); j++) {
			char d = string.charAt(j);
			if (d == '\\') {
				stringBuilder.append("\\\\");
			} else if (d != '"' && d != '\'') {
				String string2 = SnbtGrammar.escapeControlCharacters(d);
				if (string2 != null) {
					stringBuilder.append('\\');
					stringBuilder.append(string2);
				} else {
					stringBuilder.append(d);
				}
			} else {
				if (c == 0) {
					c = (char)(d == '"' ? 39 : 34);
				}

				if (c == d) {
					stringBuilder.append('\\');
				}

				stringBuilder.append(d);
			}
		}

		if (c == 0) {
			c = '"';
		}

		stringBuilder.setCharAt(i, c);
		stringBuilder.append(c);
	}

	public static String escapeWithoutQuotes(String string) {
		StringBuilder stringBuilder = new StringBuilder();
		escapeWithoutQuotes(string, stringBuilder);
		return stringBuilder.toString();
	}

	public static void escapeWithoutQuotes(String string, StringBuilder stringBuilder) {
		for (int i = 0; i < string.length(); i++) {
			char c = string.charAt(i);
			switch (c) {
				case '"':
				case '\'':
				case '\\':
					stringBuilder.append('\\');
					stringBuilder.append(c);
					break;
				default:
					String string2 = SnbtGrammar.escapeControlCharacters(c);
					if (string2 != null) {
						stringBuilder.append('\\');
						stringBuilder.append(string2);
					} else {
						stringBuilder.append(c);
					}
			}
		}
	}

	@Override
	public StreamTagVisitor.ValueResult accept(StreamTagVisitor streamTagVisitor) {
		return streamTagVisitor.visit(this.value);
	}
}
