package net.minecraft.util.profiling.jfr;

import com.mojang.logging.LogUtils;
import java.net.SocketAddress;
import java.nio.file.Path;
import jdk.jfr.FlightRecorder;
import net.minecraft.core.Holder;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public interface JvmProfiler {
	JvmProfiler INSTANCE = Runtime.class.getModule().getLayer().findModule("jdk.jfr").isPresent() && FlightRecorder.isAvailable()
		? JfrProfiler.getInstance()
		: new JvmProfiler.NoOpProfiler();

	boolean start(Environment environment);

	Path stop();

	boolean isRunning();

	boolean isAvailable();

	void onServerTick(float f);

	void onClientTick(int i);

	void onPacketReceived(ConnectionProtocol connectionProtocol, PacketType<?> packetType, SocketAddress socketAddress, int i);

	void onPacketSent(ConnectionProtocol connectionProtocol, PacketType<?> packetType, SocketAddress socketAddress, int i);

	void onRegionFileRead(RegionStorageInfo regionStorageInfo, ChunkPos chunkPos, RegionFileVersion regionFileVersion, int i);

	void onRegionFileWrite(RegionStorageInfo regionStorageInfo, ChunkPos chunkPos, RegionFileVersion regionFileVersion, int i);

	@Nullable ProfiledDuration onWorldLoadedStarted();

	@Nullable ProfiledDuration onChunkGenerate(ChunkPos chunkPos, ResourceKey<Level> resourceKey, String string);

	@Nullable ProfiledDuration onStructureGenerate(ChunkPos chunkPos, ResourceKey<Level> resourceKey, Holder<Structure> holder);

	class NoOpProfiler implements JvmProfiler {
		private static final Logger LOGGER = LogUtils.getLogger();
		static final ProfiledDuration noOpCommit = bl -> {};

		@Override
		public boolean start(Environment environment) {
			LOGGER.warn("Attempted to start Flight Recorder, but it's not supported on this JVM");
			return false;
		}

		@Override
		public Path stop() {
			throw new IllegalStateException("Attempted to stop Flight Recorder, but it's not supported on this JVM");
		}

		@Override
		public boolean isRunning() {
			return false;
		}

		@Override
		public boolean isAvailable() {
			return false;
		}

		@Override
		public void onPacketReceived(ConnectionProtocol connectionProtocol, PacketType<?> packetType, SocketAddress socketAddress, int i) {
		}

		@Override
		public void onPacketSent(ConnectionProtocol connectionProtocol, PacketType<?> packetType, SocketAddress socketAddress, int i) {
		}

		@Override
		public void onRegionFileRead(RegionStorageInfo regionStorageInfo, ChunkPos chunkPos, RegionFileVersion regionFileVersion, int i) {
		}

		@Override
		public void onRegionFileWrite(RegionStorageInfo regionStorageInfo, ChunkPos chunkPos, RegionFileVersion regionFileVersion, int i) {
		}

		@Override
		public void onServerTick(float f) {
		}

		@Override
		public void onClientTick(int i) {
		}

		@Override
		public ProfiledDuration onWorldLoadedStarted() {
			return noOpCommit;
		}

		@Override
		public @Nullable ProfiledDuration onChunkGenerate(ChunkPos chunkPos, ResourceKey<Level> resourceKey, String string) {
			return null;
		}

		@Override
		public ProfiledDuration onStructureGenerate(ChunkPos chunkPos, ResourceKey<Level> resourceKey, Holder<Structure> holder) {
			return noOpCommit;
		}
	}
}
