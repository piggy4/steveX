package com.mojang.realmsclient.dto;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public enum ServiceQuality {
	GREAT(1, "icon/ping_5"),
	GOOD(2, "icon/ping_4"),
	OKAY(3, "icon/ping_3"),
	POOR(4, "icon/ping_2"),
	UNKNOWN(5, "icon/ping_unknown");

	final int value;
	private final Identifier icon;

	ServiceQuality(final int j, final String string2) {
		this.value = j;
		this.icon = Identifier.withDefaultNamespace(string2);
	}

	public static @Nullable ServiceQuality byValue(int i) {
		for (ServiceQuality serviceQuality : values()) {
			if (serviceQuality.getValue() == i) {
				return serviceQuality;
			}
		}

		return null;
	}

	public int getValue() {
		return this.value;
	}

	public Identifier getIcon() {
		return this.icon;
	}

	@Environment(EnvType.CLIENT)
	public static class RealmsServiceQualityJsonAdapter extends TypeAdapter<ServiceQuality> {
		private static final Logger LOGGER = LogUtils.getLogger();

		public void write(JsonWriter jsonWriter, ServiceQuality serviceQuality) throws IOException {
			jsonWriter.value(serviceQuality.value);
		}

		public ServiceQuality read(JsonReader jsonReader) throws IOException {
			int i = jsonReader.nextInt();
			ServiceQuality serviceQuality = ServiceQuality.byValue(i);
			if (serviceQuality == null) {
				LOGGER.warn("Unsupported ServiceQuality {}", i);
				return ServiceQuality.UNKNOWN;
			} else {
				return serviceQuality;
			}
		}
	}
}
