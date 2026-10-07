package net.minecraft.server.jsonrpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.jsonrpc.api.MethodInfo;
import net.minecraft.server.jsonrpc.api.ParamInfo;
import net.minecraft.server.jsonrpc.api.ResultInfo;
import net.minecraft.server.jsonrpc.api.Schema;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.jsonrpc.methods.EncodeJsonRpcException;
import net.minecraft.server.jsonrpc.methods.InvalidParameterJsonRpcException;
import org.jspecify.annotations.Nullable;

public interface IncomingRpcMethod<Params, Result> {
	MethodInfo<Params, Result> info();

	IncomingRpcMethod.Attributes attributes();

	JsonElement apply(MinecraftApi minecraftApi, @Nullable JsonElement jsonElement, ClientInfo clientInfo);

	static <Result> IncomingRpcMethod.IncomingRpcMethodBuilder<Void, Result> method(
		IncomingRpcMethod.ParameterlessRpcMethodFunction<Result> parameterlessRpcMethodFunction
	) {
		return new IncomingRpcMethod.IncomingRpcMethodBuilder<>(parameterlessRpcMethodFunction);
	}

	static <Params, Result> IncomingRpcMethod.IncomingRpcMethodBuilder<Params, Result> method(
		IncomingRpcMethod.RpcMethodFunction<Params, Result> rpcMethodFunction
	) {
		return new IncomingRpcMethod.IncomingRpcMethodBuilder<>(rpcMethodFunction);
	}

	static <Result> IncomingRpcMethod.IncomingRpcMethodBuilder<Void, Result> method(Function<MinecraftApi, Result> function) {
		return new IncomingRpcMethod.IncomingRpcMethodBuilder<>(function);
	}

	record Attributes(boolean runOnMainThread, boolean discoverable) {
	}

	class IncomingRpcMethodBuilder<Params, Result> {
		private String description = "";
		private @Nullable ParamInfo<Params> paramInfo;
		private @Nullable ResultInfo<Result> resultInfo;
		private boolean discoverable = true;
		private boolean runOnMainThread = true;
		private IncomingRpcMethod.@Nullable ParameterlessRpcMethodFunction<Result> parameterlessFunction;
		private IncomingRpcMethod.@Nullable RpcMethodFunction<Params, Result> parameterFunction;

		public IncomingRpcMethodBuilder(IncomingRpcMethod.ParameterlessRpcMethodFunction<Result> parameterlessRpcMethodFunction) {
			this.parameterlessFunction = parameterlessRpcMethodFunction;
		}

		public IncomingRpcMethodBuilder(IncomingRpcMethod.RpcMethodFunction<Params, Result> rpcMethodFunction) {
			this.parameterFunction = rpcMethodFunction;
		}

		public IncomingRpcMethodBuilder(Function<MinecraftApi, Result> function) {
			this.parameterlessFunction = (minecraftApi, clientInfo) -> function.apply(minecraftApi);
		}

		public IncomingRpcMethod.IncomingRpcMethodBuilder<Params, Result> description(String string) {
			this.description = string;
			return this;
		}

		public IncomingRpcMethod.IncomingRpcMethodBuilder<Params, Result> response(String string, Schema<Result> schema) {
			this.resultInfo = new ResultInfo<>(string, schema.info());
			return this;
		}

		public IncomingRpcMethod.IncomingRpcMethodBuilder<Params, Result> param(String string, Schema<Params> schema) {
			this.paramInfo = new ParamInfo<>(string, schema.info());
			return this;
		}

		public IncomingRpcMethod.IncomingRpcMethodBuilder<Params, Result> undiscoverable() {
			this.discoverable = false;
			return this;
		}

		public IncomingRpcMethod.IncomingRpcMethodBuilder<Params, Result> notOnMainThread() {
			this.runOnMainThread = false;
			return this;
		}

		public IncomingRpcMethod<Params, Result> build() {
			if (this.resultInfo == null) {
				throw new IllegalStateException("No response defined");
			}

			IncomingRpcMethod.Attributes attributes = new IncomingRpcMethod.Attributes(this.runOnMainThread, this.discoverable);
			MethodInfo<Params, Result> methodInfo = new MethodInfo<>(this.description, this.paramInfo, this.resultInfo);
			if (this.parameterlessFunction != null) {
				return new IncomingRpcMethod.ParameterlessMethod<>(methodInfo, attributes, this.parameterlessFunction);
			}

			if (this.parameterFunction != null) {
				if (this.paramInfo == null) {
					throw new IllegalStateException("No param schema defined");
				} else {
					return new IncomingRpcMethod.Method<>(methodInfo, attributes, this.parameterFunction);
				}
			} else {
				throw new IllegalStateException("No method defined");
			}
		}

		public IncomingRpcMethod<?, ?> register(Registry<IncomingRpcMethod<?, ?>> registry, String string) {
			return this.register(registry, Identifier.withDefaultNamespace(string));
		}

		private IncomingRpcMethod<?, ?> register(Registry<IncomingRpcMethod<?, ?>> registry, Identifier identifier) {
			return Registry.register(registry, identifier, this.build());
		}
	}

	record Method<Params, Result>(
		MethodInfo<Params, Result> info, IncomingRpcMethod.Attributes attributes, IncomingRpcMethod.RpcMethodFunction<Params, Result> function
	) implements IncomingRpcMethod<Params, Result> {
		@Override
		public JsonElement apply(MinecraftApi minecraftApi, @Nullable JsonElement jsonElement, ClientInfo clientInfo) {
			if (jsonElement != null && (jsonElement.isJsonArray() || jsonElement.isJsonObject())) {
				if (this.info.params().isEmpty()) {
					throw new IllegalArgumentException("Method defined as having parameters without describing them");
				}

				JsonElement jsonElement3;
				if (jsonElement.isJsonObject()) {
					String string = this.info.params().get().name();
					JsonElement jsonElement2 = jsonElement.getAsJsonObject().get(string);
					if (jsonElement2 == null) {
						throw new InvalidParameterJsonRpcException(String.format(Locale.ROOT, "Params passed by-name, but expected param [%s] does not exist", string));
					}

					jsonElement3 = jsonElement2;
				} else {
					JsonArray jsonArray = jsonElement.getAsJsonArray();
					if (jsonArray.isEmpty() || jsonArray.size() > 1) {
						throw new InvalidParameterJsonRpcException("Expected exactly one element in the params array");
					}

					jsonElement3 = jsonArray.get(0);
				}

				Params object = this.info.params().get().schema().codec().parse(JsonOps.INSTANCE, jsonElement3).getOrThrow(InvalidParameterJsonRpcException::new);
				Result object2 = this.function.apply(minecraftApi, object, clientInfo);
				if (this.info.result().isEmpty()) {
					throw new IllegalStateException("No result codec defined");
				} else {
					return this.info.result().get().schema().codec().encodeStart(JsonOps.INSTANCE, object2).getOrThrow(EncodeJsonRpcException::new);
				}
			} else {
				throw new InvalidParameterJsonRpcException("Expected params as array or named");
			}
		}
	}

	record ParameterlessMethod<Params, Result>(
		MethodInfo<Params, Result> info, IncomingRpcMethod.Attributes attributes, IncomingRpcMethod.ParameterlessRpcMethodFunction<Result> supplier
	) implements IncomingRpcMethod<Params, Result> {
		@Override
		public JsonElement apply(MinecraftApi minecraftApi, @Nullable JsonElement jsonElement, ClientInfo clientInfo) {
			if (jsonElement == null || jsonElement.isJsonArray() && jsonElement.getAsJsonArray().isEmpty()) {
				if (this.info.params().isPresent()) {
					throw new IllegalArgumentException("Parameterless method unexpectedly has parameter description");
				} else {
					Result object = this.supplier.apply(minecraftApi, clientInfo);
					if (this.info.result().isEmpty()) {
						throw new IllegalStateException("No result codec defined");
					} else {
						return this.info.result().get().schema().codec().encodeStart(JsonOps.INSTANCE, object).getOrThrow(InvalidParameterJsonRpcException::new);
					}
				}
			} else {
				throw new InvalidParameterJsonRpcException("Expected no params, or an empty array");
			}
		}
	}

	@FunctionalInterface
	interface ParameterlessRpcMethodFunction<Result> {
		Result apply(MinecraftApi minecraftApi, ClientInfo clientInfo);
	}

	@FunctionalInterface
	interface RpcMethodFunction<Params, Result> {
		Result apply(MinecraftApi minecraftApi, Params object, ClientInfo clientInfo);
	}
}
