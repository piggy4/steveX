package net.minecraft.util.profiling;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public class Zone implements AutoCloseable {
	public static final Zone INACTIVE = new Zone(null);
	private final @Nullable ProfilerFiller profiler;

	Zone(@Nullable ProfilerFiller profilerFiller) {
		this.profiler = profilerFiller;
	}

	public Zone addText(String string) {
		if (this.profiler != null) {
			this.profiler.addZoneText(string);
		}

		return this;
	}

	public Zone addText(Supplier<String> supplier) {
		if (this.profiler != null) {
			this.profiler.addZoneText(supplier.get());
		}

		return this;
	}

	public Zone addValue(long l) {
		if (this.profiler != null) {
			this.profiler.addZoneValue(l);
		}

		return this;
	}

	public Zone setColor(int i) {
		if (this.profiler != null) {
			this.profiler.setZoneColor(i);
		}

		return this;
	}

	@Override
	public void close() {
		if (this.profiler != null) {
			this.profiler.pop();
		}
	}
}
