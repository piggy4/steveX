package net.minecraft.server.jsonrpc;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.jsonrpc.api.MethodInfo;
import net.minecraft.server.jsonrpc.api.ParamInfo;
import net.minecraft.server.jsonrpc.api.ResultInfo;
import net.minecraft.server.jsonrpc.api.Schema;
import org.jspecify.annotations.Nullable;

public interface OutgoingRpcMethod<Params, Result> {
	String NOTIFICATION_PREFIX = "notification/";

	MethodInfo<Params, Result> info();

	OutgoingRpcMethod.Attributes attributes();

	default @Nullable JsonElement encodeParams(Params object) {
		return null;
	}

	default @Nullable Result decodeResult(JsonElement jsonElement) {
		return null;
	}

	static OutgoingRpcMethod.OutgoingRpcMethodBuilder<Void, Void> notification() {
		return new OutgoingRpcMethod.OutgoingRpcMethodBuilder<>(OutgoingRpcMethod.ParmeterlessNotification::new);
	}

	static <Params> OutgoingRpcMethod.OutgoingRpcMethodBuilder<Params, Void> notificationWithParams() {
		return new OutgoingRpcMethod.OutgoingRpcMethodBuilder<>(OutgoingRpcMethod.Notification::new);
	}

	static <Result> OutgoingRpcMethod.OutgoingRpcMethodBuilder<Void, Result> request() {
		return new OutgoingRpcMethod.OutgoingRpcMethodBuilder<>(OutgoingRpcMethod.ParameterlessMethod::new);
	}

	static <Params, Result> OutgoingRpcMethod.OutgoingRpcMethodBuilder<Params, Result> requestWithParams() {
		return new OutgoingRpcMethod.OutgoingRpcMethodBuilder<>(OutgoingRpcMethod.Method::new);
	}

	record Attributes(boolean discoverable) {
	}

	@FunctionalInterface
	interface Factory<Params, Result> {
		OutgoingRpcMethod<Params, Result> create(MethodInfo<Params, Result> methodInfo, OutgoingRpcMethod.Attributes attributes);
	}

	record Method<Params, Result>(MethodInfo<Params, Result> info, OutgoingRpcMethod.Attributes attributes) implements OutgoingRpcMethod<Params, Result> {
		@Override
		public @Nullable JsonElement encodeParams(Params object) {
			if (this.info.params().isEmpty()) {
				throw new IllegalStateException("Method defined as having no parameters");
			} else {
				return this.info.params().get().schema().codec().encodeStart(JsonOps.INSTANCE, object).getOrThrow();
			}
		}

		@Override
		public Result decodeResult(JsonElement jsonElement) {
			if (this.info.result().isEmpty()) {
				throw new IllegalStateException("Method defined as having no result");
			} else {
				return this.info.result().get().schema().codec().parse(JsonOps.INSTANCE, jsonElement).getOrThrow();
			}
		}
	}

	record Notification<Params>(MethodInfo<Params, Void> info, OutgoingRpcMethod.Attributes attributes) implements OutgoingRpcMethod<Params, Void> {
		@Override
		public @Nullable JsonElement encodeParams(Params object) {
			if (this.info.params().isEmpty()) {
				throw new IllegalStateException("Method defined as having no parameters");
			} else {
				return this.info.params().get().schema().codec().encodeStart(JsonOps.INSTANCE, object).getOrThrow();
			}
		}
	}

	class OutgoingRpcMethodBuilder<Params, Result> {
		public static final OutgoingRpcMethod.Attributes DEFAULT_ATTRIBUTES = new OutgoingRpcMethod.Attributes(true);
		private final OutgoingRpcMethod.Factory<Params, Result> method;
		private String description = "";
		private @Nullable ParamInfo<Params> paramInfo;
		private @Nullable ResultInfo<Result> resultInfo;

		public OutgoingRpcMethodBuilder(OutgoingRpcMethod.Factory<Params, Result> factory) {
			this.method = factory;
		}

		public OutgoingRpcMethod.OutgoingRpcMethodBuilder<Params, Result> description(String string) {
			this.description = string;
			return this;
		}

		public OutgoingRpcMethod.OutgoingRpcMethodBuilder<Params, Result> response(String string, Schema<Result> schema) {
			this.resultInfo = new ResultInfo<>(string, schema);
			return this;
		}

		public OutgoingRpcMethod.OutgoingRpcMethodBuilder<Params, Result> param(String string, Schema<Params> schema) {
			this.paramInfo = new ParamInfo<>(string, schema);
			return this;
		}

		private OutgoingRpcMethod<Params, Result> build() {
			MethodInfo<Params, Result> methodInfo = new MethodInfo<>(this.description, this.paramInfo, this.resultInfo);
			return this.method.create(methodInfo, DEFAULT_ATTRIBUTES);
		}

		public Holder.Reference<OutgoingRpcMethod<Params, Result>> register(String string) {
			return this.register(Identifier.withDefaultNamespace("notification/" + string));
		}

		private Holder.Reference<OutgoingRpcMethod<Params, Result>> register(Identifier identifier) {
			return Registry.registerForHolder(BuiltInRegistries.OUTGOING_RPC_METHOD, identifier, this.build());
		}
	}

	record ParameterlessMethod<Result>(MethodInfo<Void, Result> info, OutgoingRpcMethod.Attributes attributes) implements OutgoingRpcMethod<Void, Result> {
		@Override
		public Result decodeResult(JsonElement jsonElement) {
			if (this.info.result().isEmpty()) {
				throw new IllegalStateException("Method defined as having no result");
			} else {
				return this.info.result().get().schema().codec().parse(JsonOps.INSTANCE, jsonElement).getOrThrow();
			}
		}
	}

	record ParmeterlessNotification(MethodInfo<Void, Void> info, OutgoingRpcMethod.Attributes attributes) implements OutgoingRpcMethod<Void, Void> {
	}
}
