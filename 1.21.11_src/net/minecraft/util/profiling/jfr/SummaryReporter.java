package net.minecraft.util.profiling.jfr;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Supplier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.profiling.jfr.parse.JfrStatsParser;
import net.minecraft.util.profiling.jfr.parse.JfrStatsResult;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class SummaryReporter {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final Runnable onDeregistration;

	protected SummaryReporter(Runnable runnable) {
		this.onDeregistration = runnable;
	}

	public void recordingStopped(@Nullable Path path) {
		if (path != null) {
			this.onDeregistration.run();
			infoWithFallback(() -> "Dumped flight recorder profiling to " + path);

			JfrStatsResult jfrStatsResult;
			try {
				jfrStatsResult = JfrStatsParser.parse(path);
			} catch (Throwable throwable) {
				warnWithFallback(() -> "Failed to parse JFR recording", throwable);
				return;
			}

			try {
				infoWithFallback(jfrStatsResult::asJson);
				Path path2 = path.resolveSibling("jfr-report-" + StringUtils.substringBefore(path.getFileName().toString(), ".jfr") + ".json");
				Files.writeString(path2, jfrStatsResult.asJson(), StandardOpenOption.CREATE);
				infoWithFallback(() -> "Dumped recording summary to " + path2);
			} catch (Throwable throwable) {
				warnWithFallback(() -> "Failed to output JFR report", throwable);
			}
		}
	}

	private static void infoWithFallback(Supplier<String> supplier) {
		if (LogUtils.isLoggerActive()) {
			LOGGER.info(supplier.get());
		} else {
			Bootstrap.realStdoutPrintln(supplier.get());
		}
	}

	private static void warnWithFallback(Supplier<String> supplier, Throwable throwable) {
		if (LogUtils.isLoggerActive()) {
			LOGGER.warn(supplier.get(), throwable);
		} else {
			Bootstrap.realStdoutPrintln(supplier.get());
			throwable.printStackTrace(Bootstrap.STDOUT);
		}
	}
}
