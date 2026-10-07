package net.minecraft.server.jsonrpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import org.jspecify.annotations.Nullable;

public class JsonRPCUtils {
	public static final String JSON_RPC_VERSION = "2.0";
	public static final String OPEN_RPC_VERSION = "1.3.2";

	public static JsonObject createSuccessResult(JsonElement jsonElement, JsonElement jsonElement2) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("jsonrpc", "2.0");
		jsonObject.add("id", jsonElement);
		jsonObject.add("result", jsonElement2);
		return jsonObject;
	}

	public static JsonObject createRequest(@Nullable Integer integer, Identifier identifier, List<JsonElement> list) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("jsonrpc", "2.0");
		if (integer != null) {
			jsonObject.addProperty("id", integer);
		}

		jsonObject.addProperty("method", identifier.toString());
		if (!list.isEmpty()) {
			JsonArray jsonArray = new JsonArray(list.size());

			for (JsonElement jsonElement : list) {
				jsonArray.add(jsonElement);
			}

			jsonObject.add("params", jsonArray);
		}

		return jsonObject;
	}

	public static JsonObject createError(JsonElement jsonElement, String string, int i, @Nullable String string2) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("jsonrpc", "2.0");
		jsonObject.add("id", jsonElement);
		JsonObject jsonObject2 = new JsonObject();
		jsonObject2.addProperty("code", i);
		jsonObject2.addProperty("message", string);
		if (string2 != null && !string2.isBlank()) {
			jsonObject2.addProperty("data", string2);
		}

		jsonObject.add("error", jsonObject2);
		return jsonObject;
	}

	public static @Nullable JsonElement getRequestId(JsonObject jsonObject) {
		return jsonObject.get("id");
	}

	public static @Nullable String getMethodName(JsonObject jsonObject) {
		return GsonHelper.getAsString(jsonObject, "method", null);
	}

	public static @Nullable JsonElement getParams(JsonObject jsonObject) {
		return jsonObject.get("params");
	}

	public static @Nullable JsonElement getResult(JsonObject jsonObject) {
		return jsonObject.get("result");
	}

	public static @Nullable JsonObject getError(JsonObject jsonObject) {
		return GsonHelper.getAsJsonObject(jsonObject, "error", null);
	}
}
