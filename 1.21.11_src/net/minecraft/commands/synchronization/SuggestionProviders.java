package net.minecraft.commands.synchronization;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

public class SuggestionProviders {
	private static final Map<Identifier, SuggestionProvider<SharedSuggestionProvider>> PROVIDERS_BY_NAME = new HashMap<>();
	private static final Identifier ID_ASK_SERVER = Identifier.withDefaultNamespace("ask_server");
	public static final SuggestionProvider<SharedSuggestionProvider> ASK_SERVER = register(
		ID_ASK_SERVER, (commandContext, suggestionsBuilder) -> commandContext.getSource().customSuggestion(commandContext)
	);
	public static final SuggestionProvider<SharedSuggestionProvider> AVAILABLE_SOUNDS = register(
		Identifier.withDefaultNamespace("available_sounds"),
		(commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggestResource(commandContext.getSource().getAvailableSounds(), suggestionsBuilder)
	);
	public static final SuggestionProvider<SharedSuggestionProvider> SUMMONABLE_ENTITIES = register(
		Identifier.withDefaultNamespace("summonable_entities"),
		(commandContext, suggestionsBuilder) -> SharedSuggestionProvider.suggestResource(
			BuiltInRegistries.ENTITY_TYPE.stream().filter(entityType -> entityType.isEnabled(commandContext.getSource().enabledFeatures()) && entityType.canSummon()),
			suggestionsBuilder,
			EntityType::getKey,
			EntityType::getDescription
		)
	);

	public static <S extends SharedSuggestionProvider> SuggestionProvider<S> register(
		Identifier identifier, SuggestionProvider<SharedSuggestionProvider> suggestionProvider
	) {
		SuggestionProvider<SharedSuggestionProvider> suggestionProvider2 = PROVIDERS_BY_NAME.putIfAbsent(identifier, suggestionProvider);
		if (suggestionProvider2 != null) {
			throw new IllegalArgumentException("A command suggestion provider is already registered with the name '" + identifier + "'");
		} else {
			return new SuggestionProviders.RegisteredSuggestion(identifier, suggestionProvider);
		}
	}

	public static <S extends SharedSuggestionProvider> SuggestionProvider<S> cast(SuggestionProvider<SharedSuggestionProvider> suggestionProvider) {
		return (SuggestionProvider<S>)suggestionProvider;
	}

	public static <S extends SharedSuggestionProvider> SuggestionProvider<S> getProvider(Identifier identifier) {
		return cast(PROVIDERS_BY_NAME.getOrDefault(identifier, ASK_SERVER));
	}

	public static Identifier getName(SuggestionProvider<?> suggestionProvider) {
		return suggestionProvider instanceof SuggestionProviders.RegisteredSuggestion registeredSuggestion ? registeredSuggestion.name : ID_ASK_SERVER;
	}

	record RegisteredSuggestion(Identifier name, SuggestionProvider<SharedSuggestionProvider> delegate) implements SuggestionProvider<SharedSuggestionProvider> {

		@Override
		public CompletableFuture<Suggestions> getSuggestions(CommandContext<SharedSuggestionProvider> commandContext, SuggestionsBuilder suggestionsBuilder) throws CommandSyntaxException {
			return this.delegate.getSuggestions(commandContext, suggestionsBuilder);
		}
	}
}
