package net.minecraft.server;

import com.mojang.logging.LogUtils;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class LoggedPrintStream extends PrintStream {
	private static final Logger LOGGER = LogUtils.getLogger();
	protected final String name;

	public LoggedPrintStream(String string, OutputStream outputStream) {
		super(outputStream, false, StandardCharsets.UTF_8);
		this.name = string;
	}

	@Override
	public void println(@Nullable String string) {
		this.logLine(string);
	}

	@Override
	public void println(@Nullable Object object) {
		this.logLine(String.valueOf(object));
	}

	protected void logLine(@Nullable String string) {
		LOGGER.info("[{}]: {}", this.name, string);
	}
}
