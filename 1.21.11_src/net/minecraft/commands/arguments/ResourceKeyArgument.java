package net.minecraft.commands.arguments;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public class ResourceKeyArgument<T> implements ArgumentType<ResourceKey<T>> {
	private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "012");
	private static final DynamicCommandExceptionType ERROR_INVALID_FEATURE = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("commands.place.feature.invalid", object)
	);
	private static final DynamicCommandExceptionType ERROR_INVALID_STRUCTURE = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("commands.place.structure.invalid", object)
	);
	private static final DynamicCommandExceptionType ERROR_INVALID_TEMPLATE_POOL = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("commands.place.jigsaw.invalid", object)
	);
	private static final DynamicCommandExceptionType ERROR_INVALID_RECIPE = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("recipe.notFound", object)
	);
	private static final DynamicCommandExceptionType ERROR_INVALID_ADVANCEMENT = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("advancement.advancementNotFound", object)
	);
	final ResourceKey<? extends Registry<T>> registryKey;

	public ResourceKeyArgument(ResourceKey<? extends Registry<T>> resourceKey) {
		this.registryKey = resourceKey;
	}

	public static <T> ResourceKeyArgument<T> key(ResourceKey<? extends Registry<T>> resourceKey) {
		return new ResourceKeyArgument<>(resourceKey);
	}

	public static <T> ResourceKey<T> getRegistryKey(
		CommandContext<CommandSourceStack> commandContext,
		String string,
		ResourceKey<Registry<T>> resourceKey,
		DynamicCommandExceptionType dynamicCommandExceptionType
	) throws CommandSyntaxException {
		ResourceKey<?> resourceKey2 = commandContext.getArgument(string, ResourceKey.class);
		Optional<ResourceKey<T>> optional = resourceKey2.cast(resourceKey);
		return optional.orElseThrow(() -> dynamicCommandExceptionType.create(resourceKey2.identifier()));
	}

	private static <T> Registry<T> getRegistry(CommandContext<CommandSourceStack> commandContext, ResourceKey<? extends Registry<T>> resourceKey) {
		return commandContext.getSource().getServer().registryAccess().lookupOrThrow(resourceKey);
	}

	private static <T> Holder.Reference<T> resolveKey(
		CommandContext<CommandSourceStack> commandContext,
		String string,
		ResourceKey<Registry<T>> resourceKey,
		DynamicCommandExceptionType dynamicCommandExceptionType
	) throws CommandSyntaxException {
		ResourceKey<T> resourceKey2 = getRegistryKey(commandContext, string, resourceKey, dynamicCommandExceptionType);
		return getRegistry(commandContext, resourceKey).get(resourceKey2).orElseThrow(() -> dynamicCommandExceptionType.create(resourceKey2.identifier()));
	}

	public static Holder.Reference<ConfiguredFeature<?, ?>> getConfiguredFeature(CommandContext<CommandSourceStack> commandContext, String string) throws CommandSyntaxException {
		return resolveKey(commandContext, string, Registries.CONFIGURED_FEATURE, ERROR_INVALID_FEATURE);
	}

	public static Holder.Reference<Structure> getStructure(CommandContext<CommandSourceStack> commandContext, String string) throws CommandSyntaxException {
		return resolveKey(commandContext, string, Registries.STRUCTURE, ERROR_INVALID_STRUCTURE);
	}

	public static Holder.Reference<StructureTemplatePool> getStructureTemplatePool(CommandContext<CommandSourceStack> commandContext, String string) throws CommandSyntaxException {
		return resolveKey(commandContext, string, Registries.TEMPLATE_POOL, ERROR_INVALID_TEMPLATE_POOL);
	}

	public static RecipeHolder<?> getRecipe(CommandContext<CommandSourceStack> commandContext, String string) throws CommandSyntaxException {
		RecipeManager recipeManager = commandContext.getSource().getServer().getRecipeManager();
		ResourceKey<Recipe<?>> resourceKey = getRegistryKey(commandContext, string, Registries.RECIPE, ERROR_INVALID_RECIPE);
		return recipeManager.byKey(resourceKey).orElseThrow(() -> ERROR_INVALID_RECIPE.create(resourceKey.identifier()));
	}

	public static AdvancementHolder getAdvancement(CommandContext<CommandSourceStack> commandContext, String string) throws CommandSyntaxException {
		ResourceKey<Advancement> resourceKey = getRegistryKey(commandContext, string, Registries.ADVANCEMENT, ERROR_INVALID_ADVANCEMENT);
		AdvancementHolder advancementHolder = commandContext.getSource().getServer().getAdvancements().get(resourceKey.identifier());
		if (advancementHolder == null) {
			throw ERROR_INVALID_ADVANCEMENT.create(resourceKey.identifier());
		} else {
			return advancementHolder;
		}
	}

	public ResourceKey<T> parse(StringReader stringReader) throws CommandSyntaxException {
		Identifier identifier = Identifier.read(stringReader);
		return ResourceKey.create(this.registryKey, identifier);
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> commandContext, SuggestionsBuilder suggestionsBuilder) {
		return SharedSuggestionProvider.listSuggestions(commandContext, suggestionsBuilder, this.registryKey, SharedSuggestionProvider.ElementSuggestionType.ELEMENTS);
	}

	@Override
	public Collection<String> getExamples() {
		return EXAMPLES;
	}

	public static class Info<T> implements ArgumentTypeInfo<ResourceKeyArgument<T>, ResourceKeyArgument.Info<T>.Template> {
		public void serializeToNetwork(ResourceKeyArgument.Info<T>.Template template, FriendlyByteBuf friendlyByteBuf) {
			friendlyByteBuf.writeResourceKey(template.registryKey);
		}

		public ResourceKeyArgument.Info<T>.Template deserializeFromNetwork(FriendlyByteBuf friendlyByteBuf) {
			return new ResourceKeyArgument.Info.Template(friendlyByteBuf.readRegistryKey());
		}

		public void serializeToJson(ResourceKeyArgument.Info<T>.Template template, JsonObject jsonObject) {
			jsonObject.addProperty("registry", template.registryKey.identifier().toString());
		}

		public ResourceKeyArgument.Info<T>.Template unpack(ResourceKeyArgument<T> resourceKeyArgument) {
			return new ResourceKeyArgument.Info.Template(resourceKeyArgument.registryKey);
		}

		public final class Template implements ArgumentTypeInfo.Template<ResourceKeyArgument<T>> {
			final ResourceKey<? extends Registry<T>> registryKey;

			Template(final ResourceKey<? extends Registry<T>> resourceKey) {
				this.registryKey = resourceKey;
			}

			public ResourceKeyArgument<T> instantiate(CommandBuildContext commandBuildContext) {
				return new ResourceKeyArgument<>(this.registryKey);
			}

			@Override
			public ArgumentTypeInfo<ResourceKeyArgument<T>, ?> type() {
				return Info.this;
			}
		}
	}
}
