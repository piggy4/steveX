package net.minecraft.util.profiling.jfr.serialize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.LongSerializationPolicy;
import com.mojang.datafixers.util.Pair;
import java.time.Duration;
import java.util.DoubleSummaryStatistics;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.jfr.Percentiles;
import net.minecraft.util.profiling.jfr.parse.JfrStatsResult;
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
import net.minecraft.util.profiling.jfr.stats.TimedStatSummary;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class JfrResultJsonSerializer {
	private static final String BYTES_PER_SECOND = "bytesPerSecond";
	private static final String COUNT = "count";
	private static final String DURATION_NANOS_TOTAL = "durationNanosTotal";
	private static final String TOTAL_BYTES = "totalBytes";
	private static final String COUNT_PER_SECOND = "countPerSecond";
	final Gson gson = new GsonBuilder().setPrettyPrinting().setLongSerializationPolicy(LongSerializationPolicy.DEFAULT).create();

	private static void serializePacketId(PacketIdentification packetIdentification, JsonObject jsonObject) {
		jsonObject.addProperty("protocolId", packetIdentification.protocolId());
		jsonObject.addProperty("packetId", packetIdentification.packetId());
	}

	private static void serializeChunkId(ChunkIdentification chunkIdentification, JsonObject jsonObject) {
		jsonObject.addProperty("level", chunkIdentification.level());
		jsonObject.addProperty("dimension", chunkIdentification.dimension());
		jsonObject.addProperty("x", chunkIdentification.x());
		jsonObject.addProperty("z", chunkIdentification.z());
	}

	public String format(JfrStatsResult jfrStatsResult) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("startedEpoch", jfrStatsResult.recordingStarted().toEpochMilli());
		jsonObject.addProperty("endedEpoch", jfrStatsResult.recordingEnded().toEpochMilli());
		jsonObject.addProperty("durationMs", jfrStatsResult.recordingDuration().toMillis());
		Duration duration = jfrStatsResult.worldCreationDuration();
		if (duration != null) {
			jsonObject.addProperty("worldGenDurationMs", duration.toMillis());
		}

		jsonObject.add("heap", this.heap(jfrStatsResult.heapSummary()));
		jsonObject.add("cpuPercent", this.cpu(jfrStatsResult.cpuLoadStats()));
		jsonObject.add("network", this.network(jfrStatsResult));
		jsonObject.add("fileIO", this.fileIO(jfrStatsResult));
		jsonObject.add("fps", this.fps(jfrStatsResult.fps()));
		jsonObject.add("serverTick", this.serverTicks(jfrStatsResult.serverTickTimes()));
		jsonObject.add("threadAllocation", this.threadAllocations(jfrStatsResult.threadAllocationSummary()));
		jsonObject.add("chunkGen", this.chunkGen(jfrStatsResult.chunkGenSummary()));
		jsonObject.add("structureGen", this.structureGen(jfrStatsResult.structureGenStats()));
		return this.gson.toJson(jsonObject);
	}

	private JsonElement heap(GcHeapStat.Summary summary) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("allocationRateBytesPerSecond", summary.allocationRateBytesPerSecond());
		jsonObject.addProperty("gcCount", summary.totalGCs());
		jsonObject.addProperty("gcOverHeadPercent", summary.gcOverHead());
		jsonObject.addProperty("gcTotalDurationMs", summary.gcTotalDuration().toMillis());
		return jsonObject;
	}

	private JsonElement structureGen(List<StructureGenStat> list) {
		JsonObject jsonObject = new JsonObject();
		Optional<TimedStatSummary<StructureGenStat>> optional = TimedStatSummary.summary(list);
		if (optional.isEmpty()) {
			return jsonObject;
		}

		TimedStatSummary<StructureGenStat> timedStatSummary = optional.get();
		JsonArray jsonArray = new JsonArray();
		jsonObject.add("structure", jsonArray);
		list.stream().collect(Collectors.groupingBy(StructureGenStat::structureName)).forEach((string, listx) -> {
			Optional<TimedStatSummary<StructureGenStat>> optionalx = TimedStatSummary.summary(listx);
			if (!optionalx.isEmpty()) {
				TimedStatSummary<StructureGenStat> timedStatSummary2 = optionalx.get();
				JsonObject jsonObject2 = new JsonObject();
				jsonArray.add(jsonObject2);
				jsonObject2.addProperty("name", string);
				jsonObject2.addProperty("count", timedStatSummary2.count());
				jsonObject2.addProperty("durationNanosTotal", timedStatSummary2.totalDuration().toNanos());
				jsonObject2.addProperty("durationNanosAvg", timedStatSummary2.totalDuration().toNanos() / timedStatSummary2.count());
				JsonObject jsonObject3 = Util.make(new JsonObject(), jsonObject2x -> jsonObject2.add("durationNanosPercentiles", jsonObject2x));
				timedStatSummary2.percentilesNanos().forEach((integer, double_) -> jsonObject3.addProperty("p" + integer, double_));
				Function<StructureGenStat, JsonElement> function = structureGenStat -> {
					JsonObject jsonObjectxx = new JsonObject();
					jsonObjectxx.addProperty("durationNanos", structureGenStat.duration().toNanos());
					jsonObjectxx.addProperty("chunkPosX", structureGenStat.chunkPos().x);
					jsonObjectxx.addProperty("chunkPosZ", structureGenStat.chunkPos().z);
					jsonObjectxx.addProperty("structureName", structureGenStat.structureName());
					jsonObjectxx.addProperty("level", structureGenStat.level());
					jsonObjectxx.addProperty("success", structureGenStat.success());
					return jsonObjectxx;
				};
				jsonObject.add("fastest", function.apply(timedStatSummary.fastest()));
				jsonObject.add("slowest", function.apply(timedStatSummary.slowest()));
				jsonObject.add("secondSlowest", timedStatSummary.secondSlowest() != null ? function.apply(timedStatSummary.secondSlowest()) : JsonNull.INSTANCE);
			}
		});
		return jsonObject;
	}

	private JsonElement chunkGen(List<Pair<ChunkStatus, TimedStatSummary<ChunkGenStat>>> list) {
		JsonObject jsonObject = new JsonObject();
		if (list.isEmpty()) {
			return jsonObject;
		}

		jsonObject.addProperty("durationNanosTotal", list.stream().mapToDouble(pairx -> ((TimedStatSummary)pairx.getSecond()).totalDuration().toNanos()).sum());
		JsonArray jsonArray = Util.make(new JsonArray(), jsonArrayx -> jsonObject.add("status", jsonArrayx));

		for (Pair<ChunkStatus, TimedStatSummary<ChunkGenStat>> pair : list) {
			TimedStatSummary<ChunkGenStat> timedStatSummary = pair.getSecond();
			JsonObject jsonObject2 = Util.make(new JsonObject(), jsonArray::add);
			jsonObject2.addProperty("state", pair.getFirst().toString());
			jsonObject2.addProperty("count", timedStatSummary.count());
			jsonObject2.addProperty("durationNanosTotal", timedStatSummary.totalDuration().toNanos());
			jsonObject2.addProperty("durationNanosAvg", timedStatSummary.totalDuration().toNanos() / timedStatSummary.count());
			JsonObject jsonObject3 = Util.make(new JsonObject(), jsonObject2x -> jsonObject2.add("durationNanosPercentiles", jsonObject2x));
			timedStatSummary.percentilesNanos().forEach((integer, double_) -> jsonObject3.addProperty("p" + integer, double_));
			Function<ChunkGenStat, JsonElement> function = chunkGenStat -> {
				JsonObject jsonObjectx = new JsonObject();
				jsonObjectx.addProperty("durationNanos", chunkGenStat.duration().toNanos());
				jsonObjectx.addProperty("level", chunkGenStat.level());
				jsonObjectx.addProperty("chunkPosX", chunkGenStat.chunkPos().x);
				jsonObjectx.addProperty("chunkPosZ", chunkGenStat.chunkPos().z);
				jsonObjectx.addProperty("worldPosX", chunkGenStat.worldPos().x());
				jsonObjectx.addProperty("worldPosZ", chunkGenStat.worldPos().z());
				return jsonObjectx;
			};
			jsonObject2.add("fastest", function.apply(timedStatSummary.fastest()));
			jsonObject2.add("slowest", function.apply(timedStatSummary.slowest()));
			jsonObject2.add("secondSlowest", timedStatSummary.secondSlowest() != null ? function.apply(timedStatSummary.secondSlowest()) : JsonNull.INSTANCE);
		}

		return jsonObject;
	}

	private JsonElement threadAllocations(ThreadAllocationStat.Summary summary) {
		JsonArray jsonArray = new JsonArray();
		summary.allocationsPerSecondByThread().forEach((string, double_) -> jsonArray.add(Util.make(new JsonObject(), jsonObject -> {
			jsonObject.addProperty("thread", string);
			jsonObject.addProperty("bytesPerSecond", double_);
		})));
		return jsonArray;
	}

	private JsonElement serverTicks(List<TickTimeStat> list) {
		if (list.isEmpty()) {
			return JsonNull.INSTANCE;
		}

		JsonObject jsonObject = new JsonObject();
		double[] ds = list.stream().mapToDouble(tickTimeStat -> tickTimeStat.currentAverage().toNanos() / 1000000.0).toArray();
		DoubleSummaryStatistics doubleSummaryStatistics = DoubleStream.of(ds).summaryStatistics();
		jsonObject.addProperty("minMs", doubleSummaryStatistics.getMin());
		jsonObject.addProperty("averageMs", doubleSummaryStatistics.getAverage());
		jsonObject.addProperty("maxMs", doubleSummaryStatistics.getMax());
		Map<Integer, Double> map = Percentiles.evaluate(ds);
		map.forEach((integer, double_) -> jsonObject.addProperty("p" + integer, double_));
		return jsonObject;
	}

	private JsonElement fps(List<FpsStat> list) {
		if (list.isEmpty()) {
			return JsonNull.INSTANCE;
		}

		JsonObject jsonObject = new JsonObject();
		int[] is = list.stream().mapToInt(FpsStat::fps).toArray();
		IntSummaryStatistics intSummaryStatistics = IntStream.of(is).summaryStatistics();
		jsonObject.addProperty("minFPS", intSummaryStatistics.getMin());
		jsonObject.addProperty("averageFPS", intSummaryStatistics.getAverage());
		jsonObject.addProperty("maxFPS", intSummaryStatistics.getMax());
		Map<Integer, Double> map = Percentiles.evaluate(is);
		map.forEach((integer, double_) -> jsonObject.addProperty("p" + integer, double_));
		return jsonObject;
	}

	private JsonElement fileIO(JfrStatsResult jfrStatsResult) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.add("write", this.fileIoSummary(jfrStatsResult.fileWrites()));
		jsonObject.add("read", this.fileIoSummary(jfrStatsResult.fileReads()));
		jsonObject.add("chunksRead", this.ioSummary(jfrStatsResult.readChunks(), JfrResultJsonSerializer::serializeChunkId));
		jsonObject.add("chunksWritten", this.ioSummary(jfrStatsResult.writtenChunks(), JfrResultJsonSerializer::serializeChunkId));
		return jsonObject;
	}

	private JsonElement fileIoSummary(FileIOStat.Summary summary) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("totalBytes", summary.totalBytes());
		jsonObject.addProperty("count", summary.counts());
		jsonObject.addProperty("bytesPerSecond", summary.bytesPerSecond());
		jsonObject.addProperty("countPerSecond", summary.countsPerSecond());
		JsonArray jsonArray = new JsonArray();
		jsonObject.add("topContributors", jsonArray);
		summary.topTenContributorsByTotalBytes().forEach(pair -> {
			JsonObject jsonObjectx = new JsonObject();
			jsonArray.add(jsonObjectx);
			jsonObjectx.addProperty("path", pair.getFirst());
			jsonObjectx.addProperty("totalBytes", pair.getSecond());
		});
		return jsonObject;
	}

	private JsonElement network(JfrStatsResult jfrStatsResult) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.add("sent", this.ioSummary(jfrStatsResult.sentPacketsSummary(), JfrResultJsonSerializer::serializePacketId));
		jsonObject.add("received", this.ioSummary(jfrStatsResult.receivedPacketsSummary(), JfrResultJsonSerializer::serializePacketId));
		return jsonObject;
	}

	private <T> JsonElement ioSummary(IoSummary<T> ioSummary, BiConsumer<T, JsonObject> biConsumer) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("totalBytes", ioSummary.getTotalSize());
		jsonObject.addProperty("count", ioSummary.getTotalCount());
		jsonObject.addProperty("bytesPerSecond", ioSummary.getSizePerSecond());
		jsonObject.addProperty("countPerSecond", ioSummary.getCountsPerSecond());
		JsonArray jsonArray = new JsonArray();
		jsonObject.add("topContributors", jsonArray);
		ioSummary.largestSizeContributors().forEach(pair -> {
			JsonObject jsonObjectx = new JsonObject();
			jsonArray.add(jsonObjectx);
			T object = pair.getFirst();
			IoSummary.CountAndSize countAndSize = pair.getSecond();
			biConsumer.accept(object, jsonObjectx);
			jsonObjectx.addProperty("totalBytes", countAndSize.totalSize());
			jsonObjectx.addProperty("count", countAndSize.totalCount());
			jsonObjectx.addProperty("averageSize", countAndSize.averageSize());
		});
		return jsonObject;
	}

	private JsonElement cpu(List<CpuLoadStat> list) {
		JsonObject jsonObject = new JsonObject();
		BiFunction<List<CpuLoadStat>, ToDoubleFunction<CpuLoadStat>, JsonObject> biFunction = (listx, toDoubleFunction) -> {
			JsonObject jsonObjectx = new JsonObject();
			DoubleSummaryStatistics doubleSummaryStatistics = listx.stream().mapToDouble(toDoubleFunction).summaryStatistics();
			jsonObjectx.addProperty("min", doubleSummaryStatistics.getMin());
			jsonObjectx.addProperty("average", doubleSummaryStatistics.getAverage());
			jsonObjectx.addProperty("max", doubleSummaryStatistics.getMax());
			return jsonObjectx;
		};
		jsonObject.add("jvm", biFunction.apply(list, CpuLoadStat::jvm));
		jsonObject.add("userJvm", biFunction.apply(list, CpuLoadStat::userJvm));
		jsonObject.add("system", biFunction.apply(list, CpuLoadStat::system));
		return jsonObject;
	}
}
