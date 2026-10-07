package net.minecraft;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.storage.DataVersion;
import org.slf4j.Logger;

public class DetectedVersion {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final WorldVersion BUILT_IN = createBuiltIn(UUID.randomUUID().toString().replaceAll("-", ""), "Development Version");

	public static WorldVersion createBuiltIn(String string, String string2) {
		return createBuiltIn(string, string2, true);
	}

	public static WorldVersion createBuiltIn(String string, String string2, boolean bl) {
		return new WorldVersion.Simple(
			string, string2, new DataVersion(4671, "main"), SharedConstants.getProtocolVersion(), PackFormat.of(75, 0), PackFormat.of(94, 1), new Date(), bl
		);
	}

	private static WorldVersion createFromJson(JsonObject jsonObject) {
		JsonObject jsonObject2 = GsonHelper.getAsJsonObject(jsonObject, "pack_version");
		return new WorldVersion.Simple(
			GsonHelper.getAsString(jsonObject, "id"),
			GsonHelper.getAsString(jsonObject, "name"),
			new DataVersion(GsonHelper.getAsInt(jsonObject, "world_version"), GsonHelper.getAsString(jsonObject, "series_id", "main")),
			GsonHelper.getAsInt(jsonObject, "protocol_version"),
			PackFormat.of(GsonHelper.getAsInt(jsonObject2, "resource_major"), GsonHelper.getAsInt(jsonObject2, "resource_minor")),
			PackFormat.of(GsonHelper.getAsInt(jsonObject2, "data_major"), GsonHelper.getAsInt(jsonObject2, "data_minor")),
			Date.from(ZonedDateTime.parse(GsonHelper.getAsString(jsonObject, "build_time")).toInstant()),
			GsonHelper.getAsBoolean(jsonObject, "stable")
		);
	}

	public static WorldVersion tryDetectVersion() {
		try (InputStream inputStream = DetectedVersion.class.getResourceAsStream("/version.json")) {
			if (inputStream == null) {
				LOGGER.warn("Missing version information!");
				return BUILT_IN;
			}

			try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
				return createFromJson(GsonHelper.parse(inputStreamReader));
			}
		} catch (IOException | JsonParseException exception) {
			throw new IllegalStateException("Game version information is corrupt", exception);
		}
	}
}
