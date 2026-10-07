package net.minecraft;

import org.apache.commons.lang3.StringEscapeUtils;

public class IdentifierException extends RuntimeException {
	public IdentifierException(String string) {
		super(StringEscapeUtils.escapeJava(string));
	}

	public IdentifierException(String string, Throwable throwable) {
		super(StringEscapeUtils.escapeJava(string), throwable);
	}
}
