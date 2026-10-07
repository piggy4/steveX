package net.minecraft.commands.arguments;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.apache.commons.io.FilenameUtils;

public class ResourceSelectorArgument<T> implements ArgumentType<Collection<Holder.Reference<T>>> {
	private static final Collection<String> EXAMPLES = List.of("minecraft:*", "*:asset", "*");
	public static final Dynamic2CommandExceptionType ERROR_NO_MATCHES = new Dynamic2CommandExceptionType(
		(object, object2) -> Component.translatableEscape("argument.resource_selector.not_found", object, object2)
	);
	final ResourceKey<? extends Registry<T>> registryKey;
	private final HolderLookup<T> registryLookup;

	ResourceSelectorArgument(CommandBuildContext commandBuildContext, ResourceKey<? extends Registry<T>> resourceKey) {
		this.registryKey = resourceKey;
		this.registryLookup = commandBuildContext.lookupOrThrow(resourceKey);
	}

	public Collection<Holder.Reference<T>> parse(StringReader stringReader) throws CommandSyntaxException {
		String string = ensureNamespaced(readPattern(stringReader));
		List<Holder.Reference<T>> list = this.registryLookup.listElements().filter(reference -> matches(string, reference.key().identifier())).toList();
		if (list.isEmpty()) {
			throw ERROR_NO_MATCHES.createWithContext(stringReader, string, this.registryKey.identifier());
		} else {
			return list;
		}
	}

	public static <T> Collection<Holder.Reference<T>> parse(StringReader stringReader, HolderLookup<T> holderLookup) {
		String string = ensureNamespaced(readPattern(stringReader));
		return holderLookup.listElements().filter(reference -> matches(string, reference.key().identifier())).toList();
	}

	private static String readPattern(StringReader stringReader) {
		int i = stringReader.getCursor();

		while (stringReader.canRead() && isAllowedPatternCharacter(stringReader.peek())) {
			stringReader.skip();
		}

		return stringReader.getString().substring(i, stringReader.getCursor());
	}

	private static boolean isAllowedPatternCharacter(char c) {
		return Identifier.isAllowedInIdentifier(c) || c == '*' || c == '?';
	}

	private static String ensureNamespaced(String string) {
		return !string.contains(":") ? "minecraft:" + string : string;
	}

	private static boolean matches(String string, Identifier identifier) {
		return FilenameUtils.wildcardMatch(identifier.toString(), string);
	}

	public static <T> ResourceSelectorArgument<T> resourceSelector(CommandBuildContext commandBuildContext, ResourceKey<? extends Registry<T>> resourceKey) {
		return new ResourceSelectorArgument<>(commandBuildContext, resourceKey);
	}

	public static <T> Collection<Holder.Reference<T>> getSelectedResources(CommandContext<CommandSourceStack> commandContext, String string) {
		return commandContext.getArgument(string, Collection.class);
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> commandContext, SuggestionsBuilder suggestionsBuilder) {
		return SharedSuggestionProvider.listSuggestions(commandContext, suggestionsBuilder, this.registryKey, SharedSuggestionProvider.ElementSuggestionType.ELEMENTS);
	}

	@Override
	public Collection<String> getExamples() {
		return EXAMPLES;
	}

	public static class Info<T> implements ArgumentTypeInfo<ResourceSelectorArgument<T>, ResourceSelectorArgument.Info<T>.Template> {
		public void serializeToNetwork(ResourceSelectorArgument.Info<T>.Template template, FriendlyByteBuf friendlyByteBuf) {
			friendlyByteBuf.writeResourceKey(template.registryKey);
		}

		public ResourceSelectorArgument.Info<T>.Template deserializeFromNetwork(FriendlyByteBuf friendlyByteBuf) {
			return new ResourceSelectorArgument.Info.Template(friendlyByteBuf.readRegistryKey());
		}

		public void serializeToJson(ResourceSelectorArgument.Info<T>.Template template, JsonObject jsonObject) {
			jsonObject.addProperty("registry", template.registryKey.identifier().toString());
		}

		public ResourceSelectorArgument.Info<T>.Template unpack(ResourceSelectorArgument<T> resourceSelectorArgument) {
			return new ResourceSelectorArgument.Info.Template(resourceSelectorArgument.registryKey);
		}

		public final class Template implements ArgumentTypeInfo.Template<ResourceSelectorArgument<T>> {
			final ResourceKey<? extends Registry<T>> registryKey;

			Template(final ResourceKey<? extends Registry<T>> resourceKey) {
				this.registryKey = resourceKey;
			}

			public ResourceSelectorArgument<T> instantiate(CommandBuildContext commandBuildContext) {
				return new ResourceSelectorArgument<>(commandBuildContext, this.registryKey);
			}

			@Override
			public ArgumentTypeInfo<ResourceSelectorArgument<T>, ?> type() {
				return Info.this;
			}
		}
	}
}
