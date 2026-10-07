package com.mojang.realmsclient.dto;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.LenientJsonParser;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public record UploadInfo(boolean worldClosed, @Nullable String token, URI uploadEndpoint) {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String DEFAULT_SCHEMA = "http://";
	private static final int DEFAULT_PORT = 8080;
	private static final Pattern URI_SCHEMA_PATTERN = Pattern.compile("^[a-zA-Z][-a-zA-Z0-9+.]+:");

	public static @Nullable UploadInfo parse(String string) {
		try {
			JsonObject jsonObject = LenientJsonParser.parse(string).getAsJsonObject();
			String string2 = JsonUtils.getStringOr("uploadEndpoint", jsonObject, null);
			if (string2 != null) {
				int i = JsonUtils.getIntOr("port", jsonObject, -1);
				URI uRI = assembleUri(string2, i);
				if (uRI != null) {
					boolean bl = JsonUtils.getBooleanOr("worldClosed", jsonObject, false);
					String string3 = JsonUtils.getStringOr("token", jsonObject, null);
					return new UploadInfo(bl, string3, uRI);
				}
			}
		} catch (Exception exception) {
			LOGGER.error("Could not parse UploadInfo", exception);
		}

		return null;
	}

	@VisibleForTesting
	public static @Nullable URI assembleUri(String string, int i) {
		Matcher matcher = URI_SCHEMA_PATTERN.matcher(string);
		String string2 = ensureEndpointSchema(string, matcher);

		try {
			URI uRI = new URI(string2);
			int j = selectPortOrDefault(i, uRI.getPort());
			return j != uRI.getPort() ? new URI(uRI.getScheme(), uRI.getUserInfo(), uRI.getHost(), j, uRI.getPath(), uRI.getQuery(), uRI.getFragment()) : uRI;
		} catch (URISyntaxException uRISyntaxException) {
			LOGGER.warn("Failed to parse URI {}", string2, uRISyntaxException);
			return null;
		}
	}

	private static int selectPortOrDefault(int i, int j) {
		if (i != -1) {
			return i;
		} else {
			return j != -1 ? j : 8080;
		}
	}

	private static String ensureEndpointSchema(String string, Matcher matcher) {
		return matcher.find() ? string : "http://" + string;
	}

	public static String createRequest(@Nullable String string) {
		JsonObject jsonObject = new JsonObject();
		if (string != null) {
			jsonObject.addProperty("token", string);
		}

		return jsonObject.toString();
	}
}
