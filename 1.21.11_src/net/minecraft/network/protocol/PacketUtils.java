package net.minecraft.network.protocol;

import com.mojang.logging.LogUtils;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class PacketUtils {
	private static final Logger LOGGER = LogUtils.getLogger();

	public static <T extends PacketListener> void ensureRunningOnSameThread(Packet<T> packet, T packetListener, ServerLevel serverLevel) throws RunningOnDifferentThreadException {
		ensureRunningOnSameThread(packet, packetListener, serverLevel.getServer().packetProcessor());
	}

	public static <T extends PacketListener> void ensureRunningOnSameThread(Packet<T> packet, T packetListener, PacketProcessor packetProcessor) throws RunningOnDifferentThreadException {
		if (!packetProcessor.isSameThread()) {
			packetProcessor.scheduleIfPossible(packetListener, packet);
			throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
		}
	}

	public static <T extends PacketListener> ReportedException makeReportedException(Exception exception, Packet<T> packet, T packetListener) {
		if (exception instanceof ReportedException reportedException) {
			fillCrashReport(reportedException.getReport(), packetListener, packet);
			return reportedException;
		} else {
			CrashReport crashReport = CrashReport.forThrowable(exception, "Main thread packet handler");
			fillCrashReport(crashReport, packetListener, packet);
			return new ReportedException(crashReport);
		}
	}

	public static <T extends PacketListener> void fillCrashReport(CrashReport crashReport, T packetListener, @Nullable Packet<T> packet) {
		if (packet != null) {
			CrashReportCategory crashReportCategory = crashReport.addCategory("Incoming Packet");
			crashReportCategory.setDetail("Type", () -> packet.type().toString());
			crashReportCategory.setDetail("Is Terminal", () -> Boolean.toString(packet.isTerminal()));
			crashReportCategory.setDetail("Is Skippable", () -> Boolean.toString(packet.isSkippable()));
		}

		packetListener.fillCrashReport(crashReport);
	}
}
