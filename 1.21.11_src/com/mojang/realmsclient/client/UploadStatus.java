package com.mojang.realmsclient.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Util;

@Environment(EnvType.CLIENT)
public class UploadStatus {
	private volatile long bytesWritten;
	private volatile long totalBytes;
	private long previousTimeSnapshot = Util.getMillis();
	private long previousBytesWritten;
	private long bytesPerSecond;

	public void setTotalBytes(long l) {
		this.totalBytes = l;
	}

	public void restart() {
		this.bytesWritten = 0L;
		this.previousTimeSnapshot = Util.getMillis();
		this.previousBytesWritten = 0L;
		this.bytesPerSecond = 0L;
	}

	public long getTotalBytes() {
		return this.totalBytes;
	}

	public long getBytesWritten() {
		return this.bytesWritten;
	}

	public void onWrite(long l) {
		this.bytesWritten = l;
	}

	public boolean uploadStarted() {
		return this.bytesWritten > 0L;
	}

	public boolean uploadCompleted() {
		return this.bytesWritten >= this.totalBytes;
	}

	public double getPercentage() {
		return Math.min((double)this.getBytesWritten() / this.getTotalBytes(), 1.0);
	}

	public void refreshBytesPerSecond() {
		long l = Util.getMillis();
		long m = l - this.previousTimeSnapshot;
		if (m >= 1000L) {
			long n = this.bytesWritten;
			this.bytesPerSecond = 1000L * (n - this.previousBytesWritten) / m;
			this.previousBytesWritten = n;
			this.previousTimeSnapshot = l;
		}
	}

	public long getBytesPerSecond() {
		return this.bytesPerSecond;
	}
}
