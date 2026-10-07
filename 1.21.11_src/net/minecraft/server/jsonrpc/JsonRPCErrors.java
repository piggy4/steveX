package net.minecraft.server.jsonrpc;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

public enum JsonRPCErrors {
	PARSE_ERROR(-32700, "Parse error"),
	INVALID_REQUEST(-32600, "Invalid Request"),
	METHOD_NOT_FOUND(-32601, "Method not found"),
	INVALID_PARAMS(-32602, "Invalid params"),
	INTERNAL_ERROR(-32603, "Internal error");

	private final int errorCode;
	private final String message;

	JsonRPCErrors(final int j, final String string2) {
		this.errorCode = j;
		this.message = string2;
	}

	public JsonObject createWithUnknownId(@Nullable String string) {
		return JsonRPCUtils.createError(JsonNull.INSTANCE, this.message, this.errorCode, string);
	}

	public JsonObject createWithoutData(JsonElement jsonElement) {
		return JsonRPCUtils.createError(jsonElement, this.message, this.errorCode, null);
	}

	public JsonObject create(JsonElement jsonElement, String string) {
		return JsonRPCUtils.createError(jsonElement, this.message, this.errorCode, string);
	}
}
