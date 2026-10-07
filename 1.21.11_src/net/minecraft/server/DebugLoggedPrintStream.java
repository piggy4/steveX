package net.minecraft.server;

import com.mojang.logging.LogUtils;
import java.io.OutputStream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class DebugLoggedPrintStream extends LoggedPrintStream {
	private static final Logger LOGGER = LogUtils.getLogger();

	public DebugLoggedPrintStream(String string, OutputStream outputStream) {
		super(string, outputStream);
	}

	@Override
	protected void logLine(@Nullable String string) {
		StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
		StackTraceElement stackTraceElement = stackTraceElements[Math.min(3, stackTraceElements.length)];
		LOGGER.info("[{}]@.({}:{}): {}", this.name, stackTraceElement.getFileName(), stackTraceElement.getLineNumber(), string);
	}
}
