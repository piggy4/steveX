package net.minecraft.commands.arguments;

import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.SnbtGrammar;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.util.parsing.packrat.Atom;
import net.minecraft.util.parsing.packrat.Dictionary;
import net.minecraft.util.parsing.packrat.NamedRule;
import net.minecraft.util.parsing.packrat.Term;
import net.minecraft.util.parsing.packrat.commands.Grammar;
import net.minecraft.util.parsing.packrat.commands.IdentifierParseRule;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.Nullable;

public class ResourceOrIdArgument<T> implements ArgumentType<Holder<T>> {
	private static final Collection<String> EXAMPLES = List.of("foo", "foo:bar", "012", "{}", "true");
	public static final DynamicCommandExceptionType ERROR_FAILED_TO_PARSE = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("argument.resource_or_id.failed_to_parse", object)
	);
	public static final Dynamic2CommandExceptionType ERROR_NO_SUCH_ELEMENT = new Dynamic2CommandExceptionType(
		(object, object2) -> Component.translatableEscape("argument.resource_or_id.no_such_element", object, object2)
	);
	public static final DynamicOps<Tag> OPS = NbtOps.INSTANCE;
	private final HolderLookup.Provider registryLookup;
	private final Optional<? extends HolderLookup.RegistryLookup<T>> elementLookup;
	private final Codec<T> codec;
	private final Grammar<ResourceOrIdArgument.Result<T, Tag>> grammar;
	private final ResourceKey<? extends Registry<T>> registryKey;

	protected ResourceOrIdArgument(CommandBuildContext commandBuildContext, ResourceKey<? extends Registry<T>> resourceKey, Codec<T> codec) {
		this.registryLookup = commandBuildContext;
		this.elementLookup = commandBuildContext.lookup(resourceKey);
		this.registryKey = resourceKey;
		this.codec = codec;
		this.grammar = createGrammar(resourceKey, OPS);
	}

	public static <T, O> Grammar<ResourceOrIdArgument.Result<T, O>> createGrammar(ResourceKey<? extends Registry<T>> resourceKey, DynamicOps<O> dynamicOps) {
		Grammar<O> grammar = SnbtGrammar.createParser(dynamicOps);
		Dictionary<StringReader> dictionary = new Dictionary<>();
		Atom<ResourceOrIdArgument.Result<T, O>> atom = Atom.of("result");
		Atom<Identifier> atom2 = Atom.of("id");
		Atom<O> atom3 = Atom.of("value");
		dictionary.put(atom2, IdentifierParseRule.INSTANCE);
		dictionary.put(atom3, grammar.top().value());
		NamedRule<StringReader, ResourceOrIdArgument.Result<T, O>> namedRule = dictionary.put(
			atom, Term.alternative(dictionary.named(atom2), dictionary.named(atom3)), scope -> {
				Identifier identifier = scope.get(atom2);
				if (identifier != null) {
					return new ResourceOrIdArgument.ReferenceResult<>(ResourceKey.create(resourceKey, identifier));
				}

				O object = scope.getOrThrow(atom3);
				return new ResourceOrIdArgument.InlineResult<>(object);
			}
		);
		return new Grammar<>(dictionary, namedRule);
	}

	public static ResourceOrIdArgument.LootTableArgument lootTable(CommandBuildContext commandBuildContext) {
		return new ResourceOrIdArgument.LootTableArgument(commandBuildContext);
	}

	public static Holder<LootTable> getLootTable(CommandContext<CommandSourceStack> commandContext, String string) throws CommandSyntaxException {
		return getResource(commandContext, string);
	}

	public static ResourceOrIdArgument.LootModifierArgument lootModifier(CommandBuildContext commandBuildContext) {
		return new ResourceOrIdArgument.LootModifierArgument(commandBuildContext);
	}

	public static Holder<LootItemFunction> getLootModifier(CommandContext<CommandSourceStack> commandContext, String string) {
		return getResource(commandContext, string);
	}

	public static ResourceOrIdArgument.LootPredicateArgument lootPredicate(CommandBuildContext commandBuildContext) {
		return new ResourceOrIdArgument.LootPredicateArgument(commandBuildContext);
	}

	public static Holder<LootItemCondition> getLootPredicate(CommandContext<CommandSourceStack> commandContext, String string) {
		return getResource(commandContext, string);
	}

	public static ResourceOrIdArgument.DialogArgument dialog(CommandBuildContext commandBuildContext) {
		return new ResourceOrIdArgument.DialogArgument(commandBuildContext);
	}

	public static Holder<Dialog> getDialog(CommandContext<CommandSourceStack> commandContext, String string) {
		return getResource(commandContext, string);
	}

	private static <T> Holder<T> getResource(CommandContext<CommandSourceStack> commandContext, String string) {
		return commandContext.getArgument(string, Holder.class);
	}

	public @Nullable Holder<T> parse(StringReader stringReader) throws CommandSyntaxException {
		return this.parse(stringReader, this.grammar, OPS);
	}

	private <O> @Nullable Holder<T> parse(StringReader stringReader, Grammar<ResourceOrIdArgument.Result<T, O>> grammar, DynamicOps<O> dynamicOps) throws CommandSyntaxException {
		ResourceOrIdArgument.Result<T, O> result = grammar.parseForCommands(stringReader);
		return this.elementLookup.isEmpty()
			? null
			: result.parse(stringReader, this.registryLookup, dynamicOps, this.codec, (HolderLookup.RegistryLookup<T>)this.elementLookup.get());
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> commandContext, SuggestionsBuilder suggestionsBuilder) {
		return SharedSuggestionProvider.listSuggestions(commandContext, suggestionsBuilder, this.registryKey, SharedSuggestionProvider.ElementSuggestionType.ELEMENTS);
	}

	@Override
	public Collection<String> getExamples() {
		return EXAMPLES;
	}

	public static class DialogArgument extends ResourceOrIdArgument<Dialog> {
		protected DialogArgument(CommandBuildContext commandBuildContext) {
			super(commandBuildContext, Registries.DIALOG, Dialog.DIRECT_CODEC);
		}
	}

	public record InlineResult<T, O>(O value) implements ResourceOrIdArgument.Result<T, O> {
		@Override
		public Holder<T> parse(
			ImmutableStringReader immutableStringReader,
			HolderLookup.Provider provider,
			DynamicOps<O> dynamicOps,
			Codec<T> codec,
			HolderLookup.RegistryLookup<T> registryLookup
		) throws CommandSyntaxException {
			return Holder.direct(
				codec.parse(provider.createSerializationContext(dynamicOps), this.value)
					.getOrThrow(string -> ResourceOrIdArgument.ERROR_FAILED_TO_PARSE.createWithContext(immutableStringReader, string))
			);
		}
	}

	public static class LootModifierArgument extends ResourceOrIdArgument<LootItemFunction> {
		protected LootModifierArgument(CommandBuildContext commandBuildContext) {
			super(commandBuildContext, Registries.ITEM_MODIFIER, LootItemFunctions.ROOT_CODEC);
		}
	}

	public static class LootPredicateArgument extends ResourceOrIdArgument<LootItemCondition> {
		protected LootPredicateArgument(CommandBuildContext commandBuildContext) {
			super(commandBuildContext, Registries.PREDICATE, LootItemCondition.DIRECT_CODEC);
		}
	}

	public static class LootTableArgument extends ResourceOrIdArgument<LootTable> {
		protected LootTableArgument(CommandBuildContext commandBuildContext) {
			super(commandBuildContext, Registries.LOOT_TABLE, LootTable.DIRECT_CODEC);
		}
	}

	public record ReferenceResult<T, O>(ResourceKey<T> key) implements ResourceOrIdArgument.Result<T, O> {
		@Override
		public Holder<T> parse(
			ImmutableStringReader immutableStringReader,
			HolderLookup.Provider provider,
			DynamicOps<O> dynamicOps,
			Codec<T> codec,
			HolderLookup.RegistryLookup<T> registryLookup
		) throws CommandSyntaxException {
			return registryLookup.get(this.key)
				.orElseThrow(() -> ResourceOrIdArgument.ERROR_NO_SUCH_ELEMENT.createWithContext(immutableStringReader, this.key.identifier(), this.key.registry()));
		}
	}

	public sealed interface Result<T, O> permits ResourceOrIdArgument.InlineResult, ResourceOrIdArgument.ReferenceResult {
		Holder<T> parse(
			ImmutableStringReader immutableStringReader,
			HolderLookup.Provider provider,
			DynamicOps<O> dynamicOps,
			Codec<T> codec,
			HolderLookup.RegistryLookup<T> registryLookup
		) throws CommandSyntaxException;
	}
}
