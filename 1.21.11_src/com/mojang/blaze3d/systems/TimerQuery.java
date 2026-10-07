package com.mojang.blaze3d.systems;

import java.util.OptionalLong;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class TimerQuery {
	private @Nullable CommandEncoder activeEncoder;
	private @Nullable GpuQuery activeGpuQuery;

	public static TimerQuery getInstance() {
		return TimerQuery.TimerQueryLazyLoader.INSTANCE;
	}

	public boolean isRecording() {
		return this.activeGpuQuery != null;
	}

	public void beginProfile() {
		RenderSystem.assertOnRenderThread();
		if (this.activeGpuQuery != null) {
			throw new IllegalStateException("Current profile not ended");
		}

		this.activeEncoder = RenderSystem.getDevice().createCommandEncoder();
		this.activeGpuQuery = this.activeEncoder.timerQueryBegin();
	}

	public TimerQuery.FrameProfile endProfile() {
		RenderSystem.assertOnRenderThread();
		if (this.activeGpuQuery != null && this.activeEncoder != null) {
			this.activeEncoder.timerQueryEnd(this.activeGpuQuery);
			TimerQuery.FrameProfile frameProfile = new TimerQuery.FrameProfile(this.activeGpuQuery);
			this.activeGpuQuery = null;
			this.activeEncoder = null;
			return frameProfile;
		} else {
			throw new IllegalStateException("endProfile called before beginProfile");
		}
	}

	@Environment(EnvType.CLIENT)
	public static class FrameProfile {
		private static final long NO_RESULT = 0L;
		private static final long CANCELLED_RESULT = -1L;
		private final GpuQuery gpuQuery;
		private long timerResult = 0L;

		FrameProfile(GpuQuery gpuQuery) {
			this.gpuQuery = gpuQuery;
		}

		public void cancel() {
			RenderSystem.assertOnRenderThread();
			if (this.timerResult == 0L) {
				this.timerResult = -1L;
				this.gpuQuery.close();
			}
		}

		public boolean isDone() {
			RenderSystem.assertOnRenderThread();
			if (this.timerResult != 0L) {
				return true;
			} else {
				OptionalLong optionalLong = this.gpuQuery.getValue();
				if (optionalLong.isPresent()) {
					this.timerResult = optionalLong.getAsLong();
					this.gpuQuery.close();
					return true;
				} else {
					return false;
				}
			}
		}

		public long get() {
			RenderSystem.assertOnRenderThread();
			if (this.timerResult == 0L) {
				OptionalLong optionalLong = this.gpuQuery.getValue();
				if (optionalLong.isPresent()) {
					this.timerResult = optionalLong.getAsLong();
					this.gpuQuery.close();
				}
			}

			return this.timerResult;
		}
	}

	@Environment(EnvType.CLIENT)
	static class TimerQueryLazyLoader {
		static final TimerQuery INSTANCE = instantiate();

		private TimerQueryLazyLoader() {
		}

		private static TimerQuery instantiate() {
			return new TimerQuery();
		}
	}
}
