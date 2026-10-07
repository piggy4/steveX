package net.minecraft.commands.synchronization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.PermissionProviderCheck;
import org.slf4j.Logger;

public class ArgumentUtils {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final byte NUMBER_FLAG_MIN = 1;
	private static final byte NUMBER_FLAG_MAX = 2;

	public static int createNumberFlags(boolean bl, boolean bl2) {
		int i = 0;
		if (bl) {
			i |= 1;
		}

		if (bl2) {
			i |= 2;
		}

		return i;
	}

	public static boolean numberHasMin(byte b) {
		return (b & 1) != 0;
	}

	public static boolean numberHasMax(byte b) {
		return (b & 2) != 0;
	}

	private static <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>> void serializeArgumentCap(
		JsonObject jsonObject, ArgumentTypeInfo<A, T> argumentTypeInfo, ArgumentTypeInfo.Template<A> template
	) {
		argumentTypeInfo.serializeToJson((T)template, jsonObject);
	}

	private static <T extends ArgumentType<?>> void serializeArgumentToJson(JsonObject jsonObject, T argumentType) {
		ArgumentTypeInfo.Template<T> template = ArgumentTypeInfos.unpack(argumentType);
		jsonObject.addProperty("type", "argument");
		jsonObject.addProperty("parser", String.valueOf(BuiltInRegistries.COMMAND_ARGUMENT_TYPE.getKey(template.type())));
		JsonObject jsonObject2 = new JsonObject();
		serializeArgumentCap(jsonObject2, template.type(), template);
		if (!jsonObject2.isEmpty()) {
			jsonObject.add("properties", jsonObject2);
		}
	}

	public static <S> JsonObject serializeNodeToJson(CommandDispatcher<S> commandDispatcher, CommandNode<S> commandNode) {
		JsonObject jsonObject = new JsonObject();
		switch (commandNode) {
			case RootCommandNode<S> rootCommandNode:
				jsonObject.addProperty("type", "root");
				break;
			case LiteralCommandNode<S> literalCommandNode:
				jsonObject.addProperty("type", "literal");
				break;
			case ArgumentCommandNode<S, ?> argumentCommandNode:
				serializeArgumentToJson(jsonObject, argumentCommandNode.getType());
				break;
			default:
				LOGGER.error("Could not serialize node {} ({})!", commandNode, commandNode.getClass());
				jsonObject.addProperty("type", "unknown");
		}

		Collection<CommandNode<S>> collection = commandNode.getChildren();
		if (!collection.isEmpty()) {
			JsonObject jsonObject2 = new JsonObject();

			for (CommandNode<S> commandNode2 : collection) {
				jsonObject2.add(commandNode2.getName(), serializeNodeToJson(commandDispatcher, commandNode2));
			}

			jsonObject.add("children", jsonObject2);
		}

		if (commandNode.getCommand() != null) {
			jsonObject.addProperty("executable", true);
		}

		if (commandNode.getRequirement() instanceof PermissionProviderCheck<?> permissionProviderCheck) {
			JsonElement jsonElement = PermissionCheck.CODEC
				.encodeStart(JsonOps.INSTANCE, permissionProviderCheck.test())
				.getOrThrow(stringx -> new IllegalStateException("Failed to serialize requirement: " + stringx));
			jsonObject.add("permissions", jsonElement);
		}

		if (commandNode.getRedirect() != null) {
			Collection<String> collection2 = commandDispatcher.getPath(commandNode.getRedirect());
			if (!collection2.isEmpty()) {
				JsonArray jsonArray = new JsonArray();

				for (String string : collection2) {
					jsonArray.add(string);
				}

				jsonObject.add("redirect", jsonArray);
			}
		}

		return jsonObject;
	}

	public static <T> Set<ArgumentType<?>> findUsedArgumentTypes(CommandNode<T> commandNode) {
		Set<CommandNode<T>> set = new ReferenceOpenHashSet<>();
		Set<ArgumentType<?>> set2 = new HashSet<>();
		findUsedArgumentTypes(commandNode, set2, set);
		return set2;
	}

	private static <T> void findUsedArgumentTypes(CommandNode<T> commandNode, Set<ArgumentType<?>> set, Set<CommandNode<T>> set2) {
		if (set2.add(commandNode)) {
			if (commandNode instanceof ArgumentCommandNode<T, ?> argumentCommandNode) {
				set.add(argumentCommandNode.getType());
			}

			commandNode.getChildren().forEach(commandNodex -> findUsedArgumentTypes(commandNodex, set, set2));
			CommandNode<T> commandNode2 = commandNode.getRedirect();
			if (commandNode2 != null) {
				findUsedArgumentTypes(commandNode2, set, set2);
			}
		}
	}
}
