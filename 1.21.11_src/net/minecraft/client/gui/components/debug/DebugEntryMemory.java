package net.minecraft.client.gui.components.debug;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugEntryMemory implements DebugScreenEntry {
	private static final Identifier GROUP = Identifier.withDefaultNamespace("memory");
	private final DebugEntryMemory.AllocationRateCalculator allocationRateCalculator = new DebugEntryMemory.AllocationRateCalculator();

	@Override
	public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
		long l = Runtime.getRuntime().maxMemory();
		long m = Runtime.getRuntime().totalMemory();
		long n = Runtime.getRuntime().freeMemory();
		long o = m - n;
		debugScreenDisplayer.addToGroup(
			GROUP,
			List.of(
				String.format(Locale.ROOT, "Mem: %2d%% %03d/%03dMB", o * 100L / l, bytesToMegabytes(o), bytesToMegabytes(l)),
				String.format(Locale.ROOT, "Allocation rate: %03dMB/s", bytesToMegabytes(this.allocationRateCalculator.bytesAllocatedPerSecond(o))),
				String.format(Locale.ROOT, "Allocated: %2d%% %03dMB", m * 100L / l, bytesToMegabytes(m))
			)
		);
	}

	private static long bytesToMegabytes(long l) {
		return l / 1024L / 1024L;
	}

	@Override
	public boolean isAllowed(boolean bl) {
		return true;
	}

	@Environment(EnvType.CLIENT)
	static class AllocationRateCalculator {
		private static final int UPDATE_INTERVAL_MS = 500;
		private static final List<GarbageCollectorMXBean> GC_MBEANS = ManagementFactory.getGarbageCollectorMXBeans();
		private long lastTime = 0L;
		private long lastHeapUsage = -1L;
		private long lastGcCounts = -1L;
		private long lastRate = 0L;

		long bytesAllocatedPerSecond(long l) {
			long m = System.currentTimeMillis();
			if (m - this.lastTime < 500L) {
				return this.lastRate;
			}

			long n = gcCounts();
			if (this.lastTime != 0L && n == this.lastGcCounts) {
				double d = (double)TimeUnit.SECONDS.toMillis(1L) / (m - this.lastTime);
				long o = l - this.lastHeapUsage;
				this.lastRate = Math.round(o * d);
			}

			this.lastTime = m;
			this.lastHeapUsage = l;
			this.lastGcCounts = n;
			return this.lastRate;
		}

		private static long gcCounts() {
			long l = 0L;

			for (GarbageCollectorMXBean garbageCollectorMXBean : GC_MBEANS) {
				l += garbageCollectorMXBean.getCollectionCount();
			}

			return l;
		}
	}
}
