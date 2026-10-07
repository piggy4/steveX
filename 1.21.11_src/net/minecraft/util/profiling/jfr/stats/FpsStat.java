package net.minecraft.util.profiling.jfr.stats;

import jdk.jfr.consumer.RecordedEvent;

public record FpsStat(int fps) {
	public static FpsStat from(RecordedEvent recordedEvent, String string) {
		return new FpsStat(recordedEvent.getInt(string));
	}
}
