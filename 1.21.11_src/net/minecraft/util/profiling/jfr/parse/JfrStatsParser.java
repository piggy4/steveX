package net.minecraft.util.profiling.jfr.parse;

import com.mojang.datafixers.util.Pair;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import net.minecraft.util.profiling.jfr.stats.ChunkGenStat;
import net.minecraft.util.profiling.jfr.stats.ChunkIdentification;
import net.minecraft.util.profiling.jfr.stats.CpuLoadStat;
import net.minecraft.util.profiling.jfr.stats.FileIOStat;
import net.minecraft.util.profiling.jfr.stats.FpsStat;
import net.minecraft.util.profiling.jfr.stats.GcHeapStat;
import net.minecraft.util.profiling.jfr.stats.IoSummary;
import net.minecraft.util.profiling.jfr.stats.PacketIdentification;
import net.minecraft.util.profiling.jfr.stats.StructureGenStat;
import net.minecraft.util.profiling.jfr.stats.ThreadAllocationStat;
import net.minecraft.util.profiling.jfr.stats.TickTimeStat;
import org.jspecify.annotations.Nullable;

public class JfrStatsParser {
	private Instant recordingStarted = Instant.EPOCH;
	private Instant recordingEnded = Instant.EPOCH;
	private final List<ChunkGenStat> chunkGenStats = new ArrayList<>();
	private final List<StructureGenStat> structureGenStats = new ArrayList<>();
	private final List<CpuLoadStat> cpuLoadStat = new ArrayList<>();
	private final Map<PacketIdentification, JfrStatsParser.MutableCountAndSize> receivedPackets = new HashMap<>();
	private final Map<PacketIdentification, JfrStatsParser.MutableCountAndSize> sentPackets = new HashMap<>();
	private final Map<ChunkIdentification, JfrStatsParser.MutableCountAndSize> readChunks = new HashMap<>();
	private final Map<ChunkIdentification, JfrStatsParser.MutableCountAndSize> writtenChunks = new HashMap<>();
	private final List<FileIOStat> fileWrites = new ArrayList<>();
	private final List<FileIOStat> fileReads = new ArrayList<>();
	private int garbageCollections;
	private Duration gcTotalDuration = Duration.ZERO;
	private final List<GcHeapStat> gcHeapStats = new ArrayList<>();
	private final List<ThreadAllocationStat> threadAllocationStats = new ArrayList<>();
	private final List<FpsStat> fps = new ArrayList<>();
	private final List<TickTimeStat> serverTickTimes = new ArrayList<>();
	private @Nullable Duration worldCreationDuration = null;

	private JfrStatsParser(Stream<RecordedEvent> stream) {
		this.capture(stream);
	}

	public static JfrStatsResult parse(Path path) {
		try (final RecordingFile recordingFile = new RecordingFile(path)) {
			Iterator<RecordedEvent> iterator = new Iterator<RecordedEvent>() {
				@Override
				public boolean hasNext() {
					return recordingFile.hasMoreEvents();
				}

				public RecordedEvent next() {
					if (!this.hasNext()) {
						throw new NoSuchElementException();
					}

					try {
						return recordingFile.readEvent();
					} catch (IOException iOException) {
						throw new UncheckedIOException(iOException);
					}
				}
			};
			Stream<RecordedEvent> stream = StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 1297), false);
			return new JfrStatsParser(stream).results();
		} catch (IOException iOException) {
			throw new UncheckedIOException(iOException);
		}
	}

	private JfrStatsResult results() {
		Duration duration = Duration.between(this.recordingStarted, this.recordingEnded);
		return new JfrStatsResult(
			this.recordingStarted,
			this.recordingEnded,
			duration,
			this.worldCreationDuration,
			this.fps,
			this.serverTickTimes,
			this.cpuLoadStat,
			GcHeapStat.summary(duration, this.gcHeapStats, this.gcTotalDuration, this.garbageCollections),
			ThreadAllocationStat.summary(this.threadAllocationStats),
			collectIoStats(duration, this.receivedPackets),
			collectIoStats(duration, this.sentPackets),
			collectIoStats(duration, this.writtenChunks),
			collectIoStats(duration, this.readChunks),
			FileIOStat.summary(duration, this.fileWrites),
			FileIOStat.summary(duration, this.fileReads),
			this.chunkGenStats,
			this.structureGenStats
		);
	}

	private void capture(Stream<RecordedEvent> stream) {
		stream.forEach(recordedEvent -> {
			if (recordedEvent.getEndTime().isAfter(this.recordingEnded) || this.recordingEnded.equals(Instant.EPOCH)) {
				this.recordingEnded = recordedEvent.getEndTime();
			}

			if (recordedEvent.getStartTime().isBefore(this.recordingStarted) || this.recordingStarted.equals(Instant.EPOCH)) {
				this.recordingStarted = recordedEvent.getStartTime();
			}

			switch (recordedEvent.getEventType().getName()) {
				case "minecraft.ChunkGeneration":
					this.chunkGenStats.add(ChunkGenStat.from(recordedEvent));
					break;
				case "minecraft.StructureGeneration":
					this.structureGenStats.add(StructureGenStat.from(recordedEvent));
					break;
				case "minecraft.LoadWorld":
					this.worldCreationDuration = recordedEvent.getDuration();
					break;
				case "minecraft.ClientFps":
					this.fps.add(FpsStat.from(recordedEvent, "fps"));
					break;
				case "minecraft.ServerTickTime":
					this.serverTickTimes.add(TickTimeStat.from(recordedEvent));
					break;
				case "minecraft.PacketReceived":
					this.incrementPacket(recordedEvent, recordedEvent.getInt("bytes"), this.receivedPackets);
					break;
				case "minecraft.PacketSent":
					this.incrementPacket(recordedEvent, recordedEvent.getInt("bytes"), this.sentPackets);
					break;
				case "minecraft.ChunkRegionRead":
					this.incrementChunk(recordedEvent, recordedEvent.getInt("bytes"), this.readChunks);
					break;
				case "minecraft.ChunkRegionWrite":
					this.incrementChunk(recordedEvent, recordedEvent.getInt("bytes"), this.writtenChunks);
					break;
				case "jdk.ThreadAllocationStatistics":
					this.threadAllocationStats.add(ThreadAllocationStat.from(recordedEvent));
					break;
				case "jdk.GCHeapSummary":
					this.gcHeapStats.add(GcHeapStat.from(recordedEvent));
					break;
				case "jdk.CPULoad":
					this.cpuLoadStat.add(CpuLoadStat.from(recordedEvent));
					break;
				case "jdk.FileWrite":
					this.appendFileIO(recordedEvent, this.fileWrites, "bytesWritten");
					break;
				case "jdk.FileRead":
					this.appendFileIO(recordedEvent, this.fileReads, "bytesRead");
					break;
				case "jdk.GarbageCollection":
					this.garbageCollections++;
					this.gcTotalDuration = this.gcTotalDuration.plus(recordedEvent.getDuration());
			}
		});
	}

	private void incrementPacket(RecordedEvent recordedEvent, int i, Map<PacketIdentification, JfrStatsParser.MutableCountAndSize> map) {
		map.computeIfAbsent(PacketIdentification.from(recordedEvent), packetIdentification -> new JfrStatsParser.MutableCountAndSize()).increment(i);
	}

	private void incrementChunk(RecordedEvent recordedEvent, int i, Map<ChunkIdentification, JfrStatsParser.MutableCountAndSize> map) {
		map.computeIfAbsent(ChunkIdentification.from(recordedEvent), chunkIdentification -> new JfrStatsParser.MutableCountAndSize()).increment(i);
	}

	private void appendFileIO(RecordedEvent recordedEvent, List<FileIOStat> list, String string) {
		list.add(new FileIOStat(recordedEvent.getDuration(), recordedEvent.getString("path"), recordedEvent.getLong(string)));
	}

	private static <T> IoSummary<T> collectIoStats(Duration duration, Map<T, JfrStatsParser.MutableCountAndSize> map) {
		List<Pair<T, IoSummary.CountAndSize>> list = map.entrySet().stream().map(entry -> Pair.of(entry.getKey(), entry.getValue().toCountAndSize())).toList();
		return new IoSummary<>(duration, list);
	}

	public static final class MutableCountAndSize {
		private long count;
		private long totalSize;

		public void increment(int i) {
			this.totalSize += i;
			this.count++;
		}

		public IoSummary.CountAndSize toCountAndSize() {
			return new IoSummary.CountAndSize(this.count, this.totalSize);
		}
	}
}
