package net.minecraft.commands.functions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.util.List;

public record StringTemplate(List<String> segments, List<String> variables) {
	public static StringTemplate fromString(String string) {
		Builder<String> builder = ImmutableList.builder();
		Builder<String> builder2 = ImmutableList.builder();
		int i = string.length();
		int j = 0;
		int k = string.indexOf(36);

		while (k != -1) {
			if (k != i - 1 && string.charAt(k + 1) == '(') {
				builder.add(string.substring(j, k));
				int l = string.indexOf(41, k + 1);
				if (l == -1) {
					throw new IllegalArgumentException("Unterminated macro variable");
				}

				String string2 = string.substring(k + 2, l);
				if (!isValidVariableName(string2)) {
					throw new IllegalArgumentException("Invalid macro variable name '" + string2 + "'");
				}

				builder2.add(string2);
				j = l + 1;
				k = string.indexOf(36, j);
			} else {
				k = string.indexOf(36, k + 1);
			}
		}

		if (j == 0) {
			throw new IllegalArgumentException("No variables in macro");
		}

		if (j != i) {
			builder.add(string.substring(j));
		}

		return new StringTemplate(builder.build(), builder2.build());
	}

	public static boolean isValidVariableName(String string) {
		for (int i = 0; i < string.length(); i++) {
			char c = string.charAt(i);
			if (!Character.isLetterOrDigit(c) && c != '_') {
				return false;
			}
		}

		return true;
	}

	public String substitute(List<String> list) {
		StringBuilder stringBuilder = new StringBuilder();

		for (int i = 0; i < this.variables.size(); i++) {
			stringBuilder.append(this.segments.get(i)).append(list.get(i));
			CommandFunction.checkCommandLineLength(stringBuilder);
		}

		if (this.segments.size() > this.variables.size()) {
			stringBuilder.append(this.segments.getLast());
		}

		CommandFunction.checkCommandLineLength(stringBuilder);
		return stringBuilder.toString();
	}
}
