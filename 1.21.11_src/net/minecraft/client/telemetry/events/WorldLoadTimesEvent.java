package net.minecraft.client.telemetry.events;

import java.time.Duration;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.telemetry.TelemetryEventSender;
import net.minecraft.client.telemetry.TelemetryEventType;
import net.minecraft.client.telemetry.TelemetryProperty;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class WorldLoadTimesEvent {
	private final boolean newWorld;
	private final @Nullable Duration worldLoadDuration;

	public WorldLoadTimesEvent(boolean bl, @Nullable Duration duration) {
		this.worldLoadDuration = duration;
		this.newWorld = bl;
	}

	public void send(TelemetryEventSender telemetryEventSender) {
		if (this.worldLoadDuration != null) {
			telemetryEventSender.send(TelemetryEventType.WORLD_LOAD_TIMES, builder -> {
				builder.put(TelemetryProperty.WORLD_LOAD_TIME_MS, (int)this.worldLoadDuration.toMillis());
				builder.put(TelemetryProperty.NEW_WORLD, this.newWorld);
			});
		}
	}
}
