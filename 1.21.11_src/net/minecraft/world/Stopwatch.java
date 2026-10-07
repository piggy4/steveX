package net.minecraft.world;

public record Stopwatch(long creationTime, long accumulatedElapsedTime) {
	public Stopwatch(long l) {
		this(l, 0L);
	}

	public long elapsedMilliseconds(long l) {
		long m = l - this.creationTime;
		return this.accumulatedElapsedTime + m;
	}

	public double elapsedSeconds(long l) {
		return this.elapsedMilliseconds(l) / 1000.0;
	}
}
