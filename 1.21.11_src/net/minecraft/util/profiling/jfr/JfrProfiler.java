package net.minecraft.util.profiling.jfr;

import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.SocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jdk.jfr.Configuration;
import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import jdk.jfr.FlightRecorderListener;
import jdk.jfr.Recording;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FileUtil;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.util.profiling.jfr.event.ChunkGenerationEvent;
import net.minecraft.util.profiling.jfr.event.ChunkRegionReadEvent;
import net.minecraft.util.profiling.jfr.event.ChunkRegionWriteEvent;
import net.minecraft.util.profiling.jfr.event.ClientFpsEvent;
import net.minecraft.util.profiling.jfr.event.NetworkSummaryEvent;
import net.minecraft.util.profiling.jfr.event.PacketReceivedEvent;
import net.minecraft.util.profiling.jfr.event.PacketSentEvent;
import net.minecraft.util.profiling.jfr.event.ServerTickTimeEvent;
import net.minecraft.util.profiling.jfr.event.StructureGenerationEvent;
import net.minecraft.util.profiling.jfr.event.WorldLoadFinishedEvent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class JfrProfiler implements JvmProfiler {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final String ROOT_CATEGORY = "Minecraft";
	public static final String WORLD_GEN_CATEGORY = "World Generation";
	public static final String TICK_CATEGORY = "Ticking";
	public static final String NETWORK_CATEGORY = "Network";
	public static final String STORAGE_CATEGORY = "Storage";
	private static final List<Class<? extends Event>> CUSTOM_EVENTS = List.of(
		ChunkGenerationEvent.class,
		ChunkRegionReadEvent.class,
		ChunkRegionWriteEvent.class,
		PacketReceivedEvent.class,
		PacketSentEvent.class,
		NetworkSummaryEvent.class,
		ServerTickTimeEvent.class,
		ClientFpsEvent.class,
		StructureGenerationEvent.class,
		WorldLoadFinishedEvent.class
	);
	private static final String FLIGHT_RECORDER_CONFIG = "/flightrecorder-config.jfc";
	private static final DateTimeFormatter DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
		.appendPattern("yyyy-MM-dd-HHmmss")
		.toFormatter(Locale.ROOT)
		.withZone(ZoneId.systemDefault());
	private static final JfrProfiler INSTANCE = new JfrProfiler();
	@Nullable Recording recording;
	private int currentFPS;
	private float currentAverageTickTimeServer;
	private final Map<String, NetworkSummaryEvent.SumAggregation> networkTrafficByAddress = new ConcurrentHashMap<>();
	private final Runnable periodicClientFps = () -> new ClientFpsEvent(this.currentFPS).commit();
	private final Runnable periodicServerTickTime = () -> new ServerTickTimeEvent(this.currentAverageTickTimeServer).commit();
	private final Runnable periodicNetworkSummary = () -> {
		Iterator<NetworkSummaryEvent.SumAggregation> iterator = this.networkTrafficByAddress.values().iterator();

		while (iterator.hasNext()) {
			iterator.next().commitEvent();
			iterator.remove();
		}
	};

	private JfrProfiler() {
		CUSTOM_EVENTS.forEach(FlightRecorder::register);
		this.registerPeriodicEvents();
		FlightRecorder.addListener(new FlightRecorderListener() {
			@Override
			public void recordingStateChanged(Recording recording) {
				switch (recording.getState()) {
					case STOPPED:
						JfrProfiler.this.registerPeriodicEvents();
					case NEW:
					case DELAYED:
					case RUNNING:
					case CLOSED:
				}
			}
		});
	}

	void registerPeriodicEvents() {
		addPeriodicEvent(ClientFpsEvent.class, this.periodicClientFps);
		addPeriodicEvent(ServerTickTimeEvent.class, this.periodicServerTickTime);
		addPeriodicEvent(NetworkSummaryEvent.class, this.periodicNetworkSummary);
	}

	private static void addPeriodicEvent(Class<? extends Event> class_, Runnable runnable) {
		FlightRecorder.removePeriodicEvent(runnable);
		FlightRecorder.addPeriodicEvent(class_, runnable);
	}

	public static JfrProfiler getInstance() {
		return INSTANCE;
	}

	@Override
	public boolean start(Environment environment) {
		URL uRL = JfrProfiler.class.getResource("/flightrecorder-config.jfc");
		if (uRL == null) {
			LOGGER.warn("Could not find default flight recorder config at {}", "/flightrecorder-config.jfc");
			return false;
		}

		try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(uRL.openStream(), StandardCharsets.UTF_8))) {
			return this.start(bufferedReader, environment);
		} catch (IOException iOException) {
			LOGGER.warn("Failed to start flight recorder using configuration at {}", uRL, iOException);
			return false;
		}
	}

	@Override
	public Path stop() {
		if (this.recording == null) {
			throw new IllegalStateException("Not currently profiling");
		}

		this.networkTrafficByAddress.clear();
		Path path = this.recording.getDestination();
		this.recording.stop();
		return path;
	}

	@Override
	public boolean isRunning() {
		return this.recording != null;
	}

	@Override
	public boolean isAvailable() {
		return FlightRecorder.isAvailable();
	}

	private boolean start(Reader reader, Environment environment) {
		if (this.isRunning()) {
			LOGGER.warn("Profiling already in progress");
			return false;
		}

		try {
			Configuration configuration = Configuration.create(reader);
			String string = DATE_TIME_FORMATTER.format(Instant.now());
			this.recording = Util.make(new Recording(configuration), recording -> {
				CUSTOM_EVENTS.forEach(recording::enable);
				recording.setDumpOnExit(true);
				recording.setToDisk(true);
				recording.setName(String.format(Locale.ROOT, "%s-%s-%s", environment.getDescription(), SharedConstants.getCurrentVersion().name(), string));
			});
			Path path = Paths.get(String.format(Locale.ROOT, "debug/%s-%s.jfr", environment.getDescription(), string));
			FileUtil.createDirectoriesSafe(path.getParent());
			this.recording.setDestination(path);
			this.recording.start();
			this.setupSummaryListener();
		} catch (IOException | ParseException exception) {
			LOGGER.warn("Failed to start jfr profiling", exception);
			return false;
		}

		LOGGER.info(
			"Started flight recorder profiling id({}):name({}) - will dump to {} on exit or stop command",
			this.recording.getId(),
			this.recording.getName(),
			this.recording.getDestination()
		);
		return true;
	}

	private void setupSummaryListener() {
		FlightRecorder.addListener(new FlightRecorderListener() {
			final SummaryReporter summaryReporter = new SummaryReporter(() -> JfrProfiler.this.recording = null);

			@Override
			public void recordingStateChanged(Recording recording) {
				if (recording == JfrProfiler.this.recording) {
					switch (recording.getState()) {
						case STOPPED:
							this.summaryReporter.recordingStopped(recording.getDestination());
							FlightRecorder.removeListener(this);
						case NEW:
						case DELAYED:
						case RUNNING:
						case CLOSED:
					}
				}
			}
		});
	}

	@Override
	public void onClientTick(int i) {
		if (ClientFpsEvent.TYPE.isEnabled()) {
			this.currentFPS = i;
		}
	}

	@Override
	public void onServerTick(float f) {
		if (ServerTickTimeEvent.TYPE.isEnabled()) {
			this.currentAverageTickTimeServer = f;
		}
	}

	@Override
	public void onPacketReceived(ConnectionProtocol connectionProtocol, PacketType<?> packetType, SocketAddress socketAddress, int i) {
		if (PacketReceivedEvent.TYPE.isEnabled()) {
			new PacketReceivedEvent(connectionProtocol.id(), packetType.flow().id(), packetType.id().toString(), socketAddress, i).commit();
		}

		if (NetworkSummaryEvent.TYPE.isEnabled()) {
			this.networkStatFor(socketAddress).trackReceivedPacket(i);
		}
	}

	@Override
	public void onPacketSent(ConnectionProtocol connectionProtocol, PacketType<?> packetType, SocketAddress socketAddress, int i) {
		if (PacketSentEvent.TYPE.isEnabled()) {
			new PacketSentEvent(connectionProtocol.id(), packetType.flow().id(), packetType.id().toString(), socketAddress, i).commit();
		}

		if (NetworkSummaryEvent.TYPE.isEnabled()) {
			this.networkStatFor(socketAddress).trackSentPacket(i);
		}
	}

	private NetworkSummaryEvent.SumAggregation networkStatFor(SocketAddress socketAddress) {
		return this.networkTrafficByAddress.computeIfAbsent(socketAddress.toString(), NetworkSummaryEvent.SumAggregation::new);
	}

	@Override
	public void onRegionFileRead(RegionStorageInfo regionStorageInfo, ChunkPos chunkPos, RegionFileVersion regionFileVersion, int i) {
		if (ChunkRegionReadEvent.TYPE.isEnabled()) {
			new ChunkRegionReadEvent(regionStorageInfo, chunkPos, regionFileVersion, i).commit();
		}
	}

	@Override
	public void onRegionFileWrite(RegionStorageInfo regionStorageInfo, ChunkPos chunkPos, RegionFileVersion regionFileVersion, int i) {
		if (ChunkRegionWriteEvent.TYPE.isEnabled()) {
			new ChunkRegionWriteEvent(regionStorageInfo, chunkPos, regionFileVersion, i).commit();
		}
	}

	@Override
	public @Nullable ProfiledDuration onWorldLoadedStarted() {
		if (!WorldLoadFinishedEvent.TYPE.isEnabled()) {
			return null;
		}

		WorldLoadFinishedEvent worldLoadFinishedEvent = new WorldLoadFinishedEvent();
		worldLoadFinishedEvent.begin();
		return bl -> worldLoadFinishedEvent.commit();
	}

	@Override
	public @Nullable ProfiledDuration onChunkGenerate(ChunkPos chunkPos, ResourceKey<Level> resourceKey, String string) {
		if (!ChunkGenerationEvent.TYPE.isEnabled()) {
			return null;
		}

		ChunkGenerationEvent chunkGenerationEvent = new ChunkGenerationEvent(chunkPos, resourceKey, string);
		chunkGenerationEvent.begin();
		return bl -> chunkGenerationEvent.commit();
	}

	@Override
	public @Nullable ProfiledDuration onStructureGenerate(ChunkPos chunkPos, ResourceKey<Level> resourceKey, Holder<Structure> holder) {
		if (!StructureGenerationEvent.TYPE.isEnabled()) {
			return null;
		}

		StructureGenerationEvent structureGenerationEvent = new StructureGenerationEvent(chunkPos, holder, resourceKey);
		structureGenerationEvent.begin();
		return bl -> {
			structureGenerationEvent.success = bl;
			structureGenerationEvent.commit();
		};
	}
}
