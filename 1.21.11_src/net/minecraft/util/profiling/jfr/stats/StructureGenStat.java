package net.minecraft.util.profiling.jfr.stats;

import java.time.Duration;
import jdk.jfr.consumer.RecordedEvent;
import net.minecraft.world.level.ChunkPos;

public record StructureGenStat(Duration duration, ChunkPos chunkPos, String structureName, String level, boolean success) implements TimedStat {
	public static StructureGenStat from(RecordedEvent recordedEvent) {
		return new StructureGenStat(
			recordedEvent.getDuration(),
			new ChunkPos(recordedEvent.getInt("chunkPosX"), recordedEvent.getInt("chunkPosX")),
			recordedEvent.getString("structure"),
			recordedEvent.getString("level"),
			recordedEvent.getBoolean("success")
		);
	}
}
