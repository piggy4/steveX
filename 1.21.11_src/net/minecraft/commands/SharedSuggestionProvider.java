package net.minecraft.commands;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.permissions.PermissionSetSupplier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;

public interface SharedSuggestionProvider extends PermissionSetSupplier {
	CharMatcher MATCH_SPLITTER = CharMatcher.anyOf("._/");

	Collection<String> getOnlinePlayerNames();

	default Collection<String> getCustomTabSugggestions() {
		return this.getOnlinePlayerNames();
	}

	default Collection<String> getSelectedEntities() {
		return Collections.emptyList();
	}

	Collection<String> getAllTeams();

	Stream<Identifier> getAvailableSounds();

	CompletableFuture<Suggestions> customSuggestion(CommandContext<?> commandContext);

	default Collection<SharedSuggestionProvider.TextCoordinates> getRelevantCoordinates() {
		return Collections.singleton(SharedSuggestionProvider.TextCoordinates.DEFAULT_GLOBAL);
	}

	default Collection<SharedSuggestionProvider.TextCoordinates> getAbsoluteCoordinates() {
		return Collections.singleton(SharedSuggestionProvider.TextCoordinates.DEFAULT_GLOBAL);
	}

	Set<ResourceKey<Level>> levels();

	RegistryAccess registryAccess();

	FeatureFlagSet enabledFeatures();

	default void suggestRegistryElements(
		HolderLookup<?> holderLookup, SharedSuggestionProvider.ElementSuggestionType elementSuggestionType, SuggestionsBuilder suggestionsBuilder
	) {
		if (elementSuggestionType.shouldSuggestTags()) {
			suggestResource(holderLookup.listTagIds().map(TagKey::location), suggestionsBuilder, "#");
		}

		if (elementSuggestionType.shouldSuggestElements()) {
			suggestResource(holderLookup.listElementIds().map(ResourceKey::identifier), suggestionsBuilder);
		}
	}

	static <S> CompletableFuture<Suggestions> listSuggestions(
		CommandContext<S> commandContext,
		SuggestionsBuilder suggestionsBuilder,
		ResourceKey<? extends Registry<?>> resourceKey,
		SharedSuggestionProvider.ElementSuggestionType elementSuggestionType
	) {
		return commandContext.getSource() instanceof SharedSuggestionProvider sharedSuggestionProvider
			? sharedSuggestionProvider.suggestRegistryElements(resourceKey, elementSuggestionType, suggestionsBuilder, commandContext)
			: suggestionsBuilder.buildFuture();
	}

	CompletableFuture<Suggestions> suggestRegistryElements(
		ResourceKey<? extends Registry<?>> resourceKey,
		SharedSuggestionProvider.ElementSuggestionType elementSuggestionType,
		SuggestionsBuilder suggestionsBuilder,
		CommandContext<?> commandContext
	);

	static <T> void filterResources(Iterable<T> iterable, String string, Function<T, Identifier> function, Consumer<T> consumer) {
		boolean bl = string.indexOf(58) > -1;

		for (T object : iterable) {
			Identifier identifier = function.apply(object);
			if (bl) {
				String string2 = identifier.toString();
				if (matchesSubStr(string, string2)) {
					consumer.accept(object);
				}
			} else if (matchesSubStr(string, identifier.getNamespace()) || matchesSubStr(string, identifier.getPath())) {
				consumer.accept(object);
			}
		}
	}

	static <T> void filterResources(Iterable<T> iterable, String string, String string2, Function<T, Identifier> function, Consumer<T> consumer) {
		if (string.isEmpty()) {
			iterable.forEach(consumer);
		} else {
			String string3 = Strings.commonPrefix(string, string2);
			if (!string3.isEmpty()) {
				String string4 = string.substring(string3.length());
				filterResources(iterable, string4, function, consumer);
			}
		}
	}

	static CompletableFuture<Suggestions> suggestResource(Iterable<Identifier> iterable, SuggestionsBuilder suggestionsBuilder, String string) {
		String string2 = suggestionsBuilder.getRemaining().toLowerCase(Locale.ROOT);
		filterResources(iterable, string2, string, identifier -> identifier, identifier -> suggestionsBuilder.suggest(string + identifier));
		return suggestionsBuilder.buildFuture();
	}

	static CompletableFuture<Suggestions> suggestResource(Stream<Identifier> stream, SuggestionsBuilder suggestionsBuilder, String string) {
		return suggestResource(stream::iterator, suggestionsBuilder, string);
	}

	static CompletableFuture<Suggestions> suggestResource(Iterable<Identifier> iterable, SuggestionsBuilder suggestionsBuilder) {
		String string = suggestionsBuilder.getRemaining().toLowerCase(Locale.ROOT);
		filterResources(iterable, string, identifier -> identifier, identifier -> suggestionsBuilder.suggest(identifier.toString()));
		return suggestionsBuilder.buildFuture();
	}

	static <T> CompletableFuture<Suggestions> suggestResource(
		Iterable<T> iterable, SuggestionsBuilder suggestionsBuilder, Function<T, Identifier> function, Function<T, Message> function2
	) {
		String string = suggestionsBuilder.getRemaining().toLowerCase(Locale.ROOT);
		filterResources(iterable, string, function, object -> suggestionsBuilder.suggest(function.apply(object).toString(), function2.apply(object)));
		return suggestionsBuilder.buildFuture();
	}

	static CompletableFuture<Suggestions> suggestResource(Stream<Identifier> stream, SuggestionsBuilder suggestionsBuilder) {
		return suggestResource(stream::iterator, suggestionsBuilder);
	}

	static <T> CompletableFuture<Suggestions> suggestResource(
		Stream<T> stream, SuggestionsBuilder suggestionsBuilder, Function<T, Identifier> function, Function<T, Message> function2
	) {
		return suggestResource(stream::iterator, suggestionsBuilder, function, function2);
	}

	static CompletableFuture<Suggestions> suggestCoordinates(
		String string, Collection<SharedSuggestionProvider.TextCoordinates> collection, SuggestionsBuilder suggestionsBuilder, Predicate<String> predicate
	) {
		List<String> list = Lists.newArrayList();
		if (Strings.isNullOrEmpty(string)) {
			for (SharedSuggestionProvider.TextCoordinates textCoordinates : collection) {
				String string2 = textCoordinates.x + " " + textCoordinates.y + " " + textCoordinates.z;
				if (predicate.test(string2)) {
					list.add(textCoordinates.x);
					list.add(textCoordinates.x + " " + textCoordinates.y);
					list.add(string2);
				}
			}
		} else {
			String[] strings = string.split(" ");
			if (strings.length == 1) {
				for (SharedSuggestionProvider.TextCoordinates textCoordinates2 : collection) {
					String string3 = strings[0] + " " + textCoordinates2.y + " " + textCoordinates2.z;
					if (predicate.test(string3)) {
						list.add(strings[0] + " " + textCoordinates2.y);
						list.add(string3);
					}
				}
			} else if (strings.length == 2) {
				for (SharedSuggestionProvider.TextCoordinates textCoordinates2 : collection) {
					String string3 = strings[0] + " " + strings[1] + " " + textCoordinates2.z;
					if (predicate.test(string3)) {
						list.add(string3);
					}
				}
			}
		}

		return suggest(list, suggestionsBuilder);
	}

	static CompletableFuture<Suggestions> suggest2DCoordinates(
		String string, Collection<SharedSuggestionProvider.TextCoordinates> collection, SuggestionsBuilder suggestionsBuilder, Predicate<String> predicate
	) {
		List<String> list = Lists.newArrayList();
		if (Strings.isNullOrEmpty(string)) {
			for (SharedSuggestionProvider.TextCoordinates textCoordinates : collection) {
				String string2 = textCoordinates.x + " " + textCoordinates.z;
				if (predicate.test(string2)) {
					list.add(textCoordinates.x);
					list.add(string2);
				}
			}
		} else {
			String[] strings = string.split(" ");
			if (strings.length == 1) {
				for (SharedSuggestionProvider.TextCoordinates textCoordinates2 : collection) {
					String string3 = strings[0] + " " + textCoordinates2.z;
					if (predicate.test(string3)) {
						list.add(string3);
					}
				}
			}
		}

		return suggest(list, suggestionsBuilder);
	}

	static CompletableFuture<Suggestions> suggest(Iterable<String> iterable, SuggestionsBuilder suggestionsBuilder) {
		String string = suggestionsBuilder.getRemaining().toLowerCase(Locale.ROOT);

		for (String string2 : iterable) {
			if (matchesSubStr(string, string2.toLowerCase(Locale.ROOT))) {
				suggestionsBuilder.suggest(string2);
			}
		}

		return suggestionsBuilder.buildFuture();
	}

	static CompletableFuture<Suggestions> suggest(Stream<String> stream, SuggestionsBuilder suggestionsBuilder) {
		String string = suggestionsBuilder.getRemaining().toLowerCase(Locale.ROOT);
		stream.filter(string2 -> matchesSubStr(string, string2.toLowerCase(Locale.ROOT))).forEach(suggestionsBuilder::suggest);
		return suggestionsBuilder.buildFuture();
	}

	static CompletableFuture<Suggestions> suggest(String[] strings, SuggestionsBuilder suggestionsBuilder) {
		String string = suggestionsBuilder.getRemaining().toLowerCase(Locale.ROOT);

		for (String string2 : strings) {
			if (matchesSubStr(string, string2.toLowerCase(Locale.ROOT))) {
				suggestionsBuilder.suggest(string2);
			}
		}

		return suggestionsBuilder.buildFuture();
	}

	static <T> CompletableFuture<Suggestions> suggest(
		Iterable<T> iterable, SuggestionsBuilder suggestionsBuilder, Function<T, String> function, Function<T, Message> function2
	) {
		String string = suggestionsBuilder.getRemaining().toLowerCase(Locale.ROOT);

		for (T object : iterable) {
			String string2 = function.apply(object);
			if (matchesSubStr(string, string2.toLowerCase(Locale.ROOT))) {
				suggestionsBuilder.suggest(string2, function2.apply(object));
			}
		}

		return suggestionsBuilder.buildFuture();
	}

	static boolean matchesSubStr(String string, String string2) {
		int i = 0;

		while (!string2.startsWith(string, i)) {
			int j = MATCH_SPLITTER.indexIn(string2, i);
			if (j < 0) {
				return false;
			}

			i = j + 1;
		}

		return true;
	}

	enum ElementSuggestionType {
		TAGS,
		ELEMENTS,
		ALL;

		public boolean shouldSuggestTags() {
			return this == TAGS || this == ALL;
		}

		public boolean shouldSuggestElements() {
			return this == ELEMENTS || this == ALL;
		}
	}

	class TextCoordinates {
		public static final SharedSuggestionProvider.TextCoordinates DEFAULT_LOCAL = new SharedSuggestionProvider.TextCoordinates("^", "^", "^");
		public static final SharedSuggestionProvider.TextCoordinates DEFAULT_GLOBAL = new SharedSuggestionProvider.TextCoordinates("~", "~", "~");
		public final String x;
		public final String y;
		public final String z;

		public TextCoordinates(String string, String string2, String string3) {
			this.x = string;
			this.y = string2;
			this.z = string3;
		}
	}
}
